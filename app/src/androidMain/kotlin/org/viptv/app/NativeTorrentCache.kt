package org.viptv.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.IdentityHashMap
import java.util.UUID

/** Platform limits are supplied to the generic engine; full payload accounting stays there. */
internal object NativeTorrentCacheLimits {
    const val PAYLOAD_BYTES = 2_147_483_648L
    const val CONTROL_BYTES = 67_108_864L
    const val METAINFO_BYTES = 4_194_304L
    const val SETTLEMENT_NANOS = 2_000_000_000L
}

/** Blocking native/storage effects run on the controller's IO dispatcher. */
internal interface NativeTorrentCacheManager {
    fun hasFailedSettlement(): Boolean
    fun closeAfterSettlement()
}

/** Retain acquisition authority for its entire lifetime, including after a handle is ready. */
internal interface NativeTorrentCacheWork {
    fun preventReads()
    fun cancel()
    fun join(deadlineNanos: Long): Boolean
    fun closeAfterSettlement()
}

internal class NativeTorrentCacheUnavailable : RuntimeException("Native cache unavailable")

/** No account/profile/server identifiers, source inputs or grants are written to this store. */
internal class NativeTorrentCache private constructor(
    private val root: File,
    private val lockFile: RandomAccessFile,
    private val lock: FileLock,
    private val effects: NativeTorrentCacheStorage,
    private val nowNanos: () -> Long,
) {
    private enum class State { Opening, Available, Closing, Closed, Unavailable }
    private var state = State.Opening
    private var directory: File? = null
    private var manager: NativeTorrentCacheManager? = null
    private val works = IdentityHashMap<NativeTorrentCacheWork, NativeTorrentControlReservation>()
    private val reservations = IdentityHashMap<NativeTorrentControlReservation, Long>()
    private var controlBytes = 0L

    val isAvailable: Boolean
        @Synchronized get() {
            if (state == State.Available && manager?.hasFailedSettlement() != false) quarantine()
            return state == State.Available
        }

    val reservedControlBytes: Long
        @Synchronized get() = controlBytes

    override fun toString() = "NativeTorrentCache(<redacted>)"

    /** Control responses, retained grant input and native metadata use one aggregate ceiling. */
    @Synchronized
    fun reserveControl(bytes: Long): NativeTorrentControlReservation {
        requireAvailable()
        if (bytes <= 0 || bytes > NativeTorrentCacheLimits.CONTROL_BYTES - controlBytes) {
            throw NativeTorrentCacheUnavailable()
        }
        val free = safely { effects.availableBytes(requireNotNull(directory)) }
        if (bytes > free || controlBytes > free - bytes) throw NativeTorrentCacheUnavailable()
        return NativeTorrentControlReservation(this).also {
            reservations[it] = bytes
            controlBytes += bytes
        }
    }

    fun reserveMetainfo(bytes: Long): NativeTorrentControlReservation {
        if (bytes <= 0 || bytes > NativeTorrentCacheLimits.METAINFO_BYTES) {
            throw NativeTorrentCacheUnavailable()
        }
        return reserveControl(bytes)
    }

    /** Registration occurs before begin/wait IO; the reservation cannot be freed by caller close. */
    @Synchronized
    fun register(work: NativeTorrentCacheWork, reservation: NativeTorrentControlReservation) {
        requireAvailable()
        if (!reservations.containsKey(reservation) || works.containsKey(work) ||
            works.values.any { it === reservation }
        ) throw NativeTorrentCacheUnavailable()
        works[work] = reservation
    }

    /** Candidate failure retires only that candidate; other current grants retain authority. */
    @Synchronized
    fun retire(work: NativeTorrentCacheWork): Boolean {
        synchronized(this) {
            if (!works.containsKey(work)) return state != State.Unavailable
            if (state != State.Available) return false
        }
        val deadline = nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS
        val joined = try {
            work.preventReads()
            work.cancel()
            work.join(deadline) && nowNanos() <= deadline && manager?.hasFailedSettlement() == false
        } catch (_: Exception) { false }
        synchronized(this) {
            if (!joined) {
                quarantine()
                return false
            }
            return try {
                work.closeAfterSettlement()
                works.remove(work)?.let(::releaseSettled)
                true
            } catch (_: Exception) {
                quarantine()
                false
            }
        }
    }

    /** Scope changes execute stop -> join -> manager close -> owned deletion -> unlock. */
    @Synchronized
    fun closeScope(): Boolean {
        val snapshot = synchronized(this) {
            if (state == State.Closed) return true
            if (state != State.Available) return false
            state = State.Closing
            works.keys.toList()
        }
        val deadline = nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS
        try {
            snapshot.forEach { it.preventReads() }
            snapshot.forEach { it.cancel() }
            if (snapshot.any { !it.join(deadline) || nowNanos() > deadline } ||
                manager?.hasFailedSettlement() != false
            ) throw NativeTorrentCacheUnavailable()
            snapshot.forEach { it.closeAfterSettlement() }
            manager?.closeAfterSettlement()
            effects.deleteOwned(requireNotNull(directory))
            synchronized(this) {
                // Deletion failure keeps all accounting and the exclusive lock.
                works.clear()
                reservations.clear()
                controlBytes = 0
                manager = null
                state = State.Closed
                lock.release()
                lockFile.close()
            }
            return true
        } catch (_: Exception) {
            synchronized(this) { quarantine() }
            return false
        }
    }

    @Synchronized
    internal fun release(reservation: NativeTorrentControlReservation) {
        // A finally/close in a cancelled caller is not evidence of joined native IO.
        if (state != State.Available || works.values.any { it === reservation }) return
        releaseSettled(reservation)
    }

    private fun releaseSettled(reservation: NativeTorrentControlReservation) {
        reservations.remove(reservation)?.let { controlBytes -= it }
    }

    private fun requireAvailable() {
        if (state != State.Available || manager?.hasFailedSettlement() != false) {
            if (state == State.Available) quarantine()
            throw NativeTorrentCacheUnavailable()
        }
    }

    @Synchronized
    fun managerForAdmission(): NativeTorrentCacheManager {
        requireAvailable()
        return requireNotNull(manager)
    }

    private fun quarantine() {
        state = State.Unavailable
        works.keys.forEach {
            try { it.preventReads() } catch (_: Exception) { }
            try { it.cancel() } catch (_: Exception) { }
        }
        // A failed owner must survive coroutine/controller disposal and JVM GC.
        synchronized(quarantined) { if (quarantined.none { it === this }) quarantined.add(this) }
    }

    companion object {
        private val quarantined = mutableListOf<NativeTorrentCache>()

        /** [noBackupDirectory] must be Context.noBackupFilesDir; never generic cache/files storage. */
        fun open(
            noBackupDirectory: File,
            createManager: (File, Long) -> NativeTorrentCacheManager,
            effects: NativeTorrentCacheStorage = NativeTorrentCacheStorage(),
            nowNanos: () -> Long = System::nanoTime,
        ): NativeTorrentCache {
            val root = safely { effects.createRoot(noBackupDirectory) }
            val ownerLock = safely { effects.lockRoot(root) }
            val owner = NativeTorrentCache(root, ownerLock.first, ownerLock.second, effects, nowNanos)
            try {
                // Root lock proves no previous native manager is active in this private tree.
                effects.removeInactiveOwned(root)
                owner.directory = effects.createOwned(root)
                owner.manager = createManager(requireNotNull(owner.directory), NativeTorrentCacheLimits.PAYLOAD_BYTES)
                owner.state = State.Available
            } catch (_: Exception) {
                owner.quarantine()
            }
            return owner
        }

        private fun <T> safely(effect: () -> T): T = try { effect() } catch (_: Exception) {
            // Storage exception causes often contain the private path.
            throw NativeTorrentCacheUnavailable()
        }
    }
}

internal class NativeTorrentControlReservation internal constructor(private val owner: NativeTorrentCache) : AutoCloseable {
    override fun close() = owner.release(this)
    override fun toString() = "NativeTorrentControlReservation(<redacted>)"
}

/** Only the known app-owned tree is inspected; symbolic links are rejected, never followed. */
internal open class NativeTorrentCacheStorage {
    companion object {
        private const val ROOT = "viptv-native-transport"
        private const val MARKER = ".viptv-owned-native-cache"
        private const val MARKER_VALUE = "viptv-native-cache-v1"
        private val OWNED_NAME = Regex("epoch-[0-9a-f]{32}")
    }

    open fun availableBytes(directory: File): Long = directory.usableSpace

    open fun createRoot(noBackupDirectory: File): File {
        val parent = noBackupDirectory.canonicalFile
        check(parent.isDirectory)
        val root = File(parent, ROOT)
        check(root.canonicalFile == root.absoluteFile)
        check(root.isDirectory || root.mkdir())
        privateDirectory(root)
        return root
    }

    open fun lockRoot(root: File): Pair<RandomAccessFile, FileLock> {
        val path = File(root, ".owner.lock")
        check(path.canonicalFile == path.absoluteFile)
        val file = RandomAccessFile(path, "rw")
        try {
            val lock = file.channel.tryLock() ?: throw NativeTorrentCacheUnavailable()
            return file to lock
        } catch (_: Exception) {
            file.close()
            throw NativeTorrentCacheUnavailable()
        }
    }

    open fun createOwned(root: File): File {
        val directory = File(root, "epoch-${UUID.randomUUID().toString().replace("-", "")}")
        File(root, ".owned-${directory.name}").writeText(MARKER_VALUE)
        check(directory.mkdir())
        privateDirectory(directory)
        File(directory, MARKER).writeText(MARKER_VALUE)
        return directory
    }

    open fun removeInactiveOwned(root: File) {
        requireNotNull(root.listFiles()).filter { it.name.startsWith(".owned-epoch-") }.forEach {
            check(it.canonicalFile == it.absoluteFile && it.isFile)
            check(it.length() == MARKER_VALUE.length.toLong() && it.readText() == MARKER_VALUE)
            val name = it.name.removePrefix(".owned-")
            check(OWNED_NAME.matches(name))
            val directory = File(root, name)
            check(directory.canonicalFile == directory.absoluteFile)
            if (directory.exists()) deleteOwned(directory) else check(it.delete())
        }
        // Retain compatibility with identified directories created before the
        // root ownership receipt was written; unknown names stay untouched.
        requireNotNull(root.listFiles()).filter { OWNED_NAME.matches(it.name) }.forEach {
            check(it.canonicalFile == it.absoluteFile && it.isDirectory)
            val marker = File(it, MARKER)
            // Similar names without our exact marker remain unrelated app data.
            if (!marker.exists()) return@forEach
            check(marker.canonicalFile == marker.absoluteFile && marker.isFile)
            check(marker.length() == MARKER_VALUE.length.toLong())
            if (marker.readText() == MARKER_VALUE) deleteOwned(it)
        }
    }

    open fun deleteOwned(directory: File) {
        check(OWNED_NAME.matches(directory.name))
        val marker = File(directory, MARKER)
        val receipt = File(directory.parentFile, ".owned-${directory.name}")
        check(directory.canonicalFile == directory.absoluteFile && marker.canonicalFile == marker.absoluteFile)
        check(receipt.canonicalFile == receipt.absoluteFile)
        val identifiedByReceipt = receipt.isFile && receipt.length() == MARKER_VALUE.length.toLong() && receipt.readText() == MARKER_VALUE
        check(identifiedByReceipt || (marker.isFile && marker.length() == MARKER_VALUE.length.toLong() && marker.readText() == MARKER_VALUE))
        // The identity marker remains until every other owned entry was removed.
        requireNotNull(directory.listFiles()).filter { it.name != MARKER }.forEach(::deleteTree)
        if (marker.exists()) check(marker.delete())
        check(directory.delete())
        if (receipt.exists()) check(receipt.delete())
    }

    private fun privateDirectory(directory: File) {
        check(directory.setReadable(false, false) && directory.setReadable(true, true))
        check(directory.setWritable(false, false) && directory.setWritable(true, true))
        check(directory.setExecutable(false, false) && directory.setExecutable(true, true))
    }

    private fun deleteTree(file: File) {
        check(file.canonicalFile == file.absoluteFile)
        if (file.isDirectory) requireNotNull(file.listFiles()).forEach(::deleteTree)
        check(file.delete())
    }
}
