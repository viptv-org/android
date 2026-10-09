package org.viptv.app

import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Private effect interfaces never cross player/UI/history boundaries. */
internal interface NativeTorrentAcquisitionEffect {
    fun waitReady(): NativeTorrentHandleEffect
    fun cancel()
    fun cancelAndJoin(): Boolean
    fun close()
}

internal interface NativeTorrentHandleEffect {
    fun validatedCapability(): NativeTorrentCapability
    fun stop()
    fun stopAndJoin(): Boolean
    fun close()
    fun diagnostic(): String? = null
    fun firstFrame() {}
}

/** Only the strict adapter constructs this capability after shared metadata validation. */
internal class NativeTorrentCapability private constructor(internal val url: String) {
    override fun toString() = "NativeTorrentCapability(<redacted>)"
    companion object {
        fun runtimeValidated(url: String): NativeTorrentCapability {
            val uri = try { URI(url) } catch (_: Exception) { throw NativeTorrentCoordinatorUnavailable() }
            if (uri.scheme != "http" || uri.host != "127.0.0.1" || uri.port !in 1..65535 || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null || !Regex("/media/[0-9a-f]{64}").matches(uri.rawPath ?: "")) throw NativeTorrentCoordinatorUnavailable()
            return NativeTorrentCapability(url)
        }
        fun validated(url: String, selectedIndex: UInt): NativeTorrentCapability {
            val uri = try { URI(url) } catch (_: Exception) { throw NativeTorrentCoordinatorUnavailable() }
            val path = Regex("/[0-9a-f]{64}/${selectedIndex}/stream\\.[A-Za-z0-9]{1,8}")
            if (uri.scheme != "http" || uri.host != "127.0.0.1" || uri.port !in 1..65535 ||
                uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null || !path.matches(uri.rawPath ?: "")
            ) throw NativeTorrentCoordinatorUnavailable()
            return NativeTorrentCapability(url)
        }
    }
}

internal class NativeTorrentCoordinatorUnavailable : RuntimeException("Native playback unavailable")

/** Register ownership before IO; retain cancellation authority after readiness and failed settlement. */
internal class NativeTorrentOwnedWork(
    private val begin: () -> NativeTorrentAcquisitionEffect,
    private val nowNanos: () -> Long,
) : NativeTorrentCacheWork {
    internal val ready = CompletableDeferred<NativeTorrentCapability>()
    private val finished = CountDownLatch(1)
    private var acquisition: NativeTorrentAcquisitionEffect? = null
    private var handle: NativeTorrentHandleEffect? = null
    private var cancelled = false
    private var validating = false
    private var cancellationAtNanos: Long? = null
    private var started = false
    private var closed = false

    @Synchronized internal fun diagnostic(): String? = handle?.diagnostic()
    @Synchronized internal fun firstFrame() { if (!cancelled) handle?.firstFrame() }

    private var cancellationFailed = false
    private val joins = mutableListOf<JoinReceipt>()

    @Synchronized fun start() {
        if (started) return
        started = true
        workers.execute {
            try {
                synchronized(this) { if (cancelled) throw NativeTorrentCoordinatorUnavailable() }
                val acquired = begin()
                synchronized(this) {
                    acquisition = acquired
                    if (cancelled) cancelAcquisition(acquired)
                }
                val prepared = acquired.waitReady()
                synchronized(this) {
                    handle = prepared
                    if (cancelled) cancelHandle(prepared)
                    if (cancelled) throw NativeTorrentCoordinatorUnavailable()
                    validating = true
                }
                val capability = prepared.validatedCapability()
                synchronized(this) {
                    if (cancelled) throw NativeTorrentCoordinatorUnavailable()
                    ready.complete(capability)
                    validating = false
                }
            } catch (error: Exception) {
                synchronized(this) {
                    ready.completeExceptionally(nativeTorrentFailure(error))
                    validating = false
                }
            } finally { finished.countDown() }
        }
    }

    override fun preventReads() = cancel()

    @Synchronized override fun cancel() {
        if (cancelled) return
        cancelled = true
        cancellationAtNanos = nowNanos()
        // Metadata refusal invalidates authority during validation. Let the worker
        // publish that measured cause instead of replacing it with cleanup's cancellation.
        if (!validating) ready.completeExceptionally(NativeTorrentCoordinatorUnavailable())
        // Joins launch together, including across all cache works. Each FFI call
        // has its own engine timer; the caller enforces the common scope deadline.
        acquisition?.let(::cancelAcquisition)
        handle?.let(::cancelHandle)
        if (!started) finished.countDown()
    }

    @Synchronized internal fun settlementDeadlineNanos(): Long =
        (cancellationAtNanos ?: nowNanos()) + NativeTorrentCacheLimits.SETTLEMENT_NANOS

    private fun cancelAcquisition(value: NativeTorrentAcquisitionEffect) {
        try { value.cancel() } catch (_: Exception) { cancellationFailed = true }
        startJoin { value.cancelAndJoin() }
    }

    private fun cancelHandle(value: NativeTorrentHandleEffect) {
        try { value.stop() } catch (_: Exception) { cancellationFailed = true }
        startJoin { value.stopAndJoin() }
    }

    private fun startJoin(effect: () -> Boolean) {
        val receipt = JoinReceipt()
        joins.add(receipt)
        workers.execute {
            try { receipt.settled = effect() } catch (_: Exception) { receipt.settled = false }
            finally { receipt.finished.countDown() }
        }
    }

    override fun join(deadlineNanos: Long): Boolean {
        val commonDeadline = minOf(deadlineNanos, settlementDeadlineNanos())
        if (!await(finished, commonDeadline)) return false
        val snapshot = synchronized(this) { if (!cancelled || cancellationFailed) return false; joins.toList() }
        return snapshot.all { await(it.finished, commonDeadline) && it.settled } && nowNanos() <= commonDeadline
    }

    private fun await(latch: CountDownLatch, deadline: Long): Boolean =
        latch.await((deadline - nowNanos()).coerceAtLeast(0), TimeUnit.NANOSECONDS)

    @Synchronized override fun closeAfterSettlement() {
        check(cancelled && !cancellationFailed && finished.count == 0L && joins.all { it.finished.count == 0L && it.settled })
        if (closed) return
        closed = true
        handle?.close(); acquisition?.close()
        handle = null; acquisition = null
    }
    override fun toString() = "NativeTorrentOwnedWork(<redacted>)"
    private class JoinReceipt {
        val finished = CountDownLatch(1)
        @Volatile var settled = false
    }
    companion object {
        // Blocking OS IO must not starve another grant's cancellation/join.
        private val workers = Executors.newCachedThreadPool { task ->
            Thread(task, "native-transport-effect").apply { isDaemon = true }
        }
    }
}

/** One stable Rust-approved authorization epoch; playback generations do not create another cache. */
internal class NativeTorrentCoordinator(
    internal val cache: NativeTorrentCache,
    private val nowNanos: () -> Long,
    private val isCurrent: (Long) -> Boolean,
    private val preventPlayerReads: () -> Unit,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val beginAcquisition: (NativeTorrentCache, NativePlaybackControl, () -> Long) -> NativeTorrentAcquisitionEffect = ::beginNativeTorrentAcquisition,
) {
    internal class Candidate internal constructor(
        internal val generation: Long,
        internal val control: NativePlaybackControl,
        internal val work: NativeTorrentOwnedWork,
        internal val capability: NativeTorrentCapability,
    ) {
        internal var retired = false
        internal val retirement = CompletableDeferred<Boolean>()
        override fun toString() = "NativeTorrentCandidate(<redacted>)"
    }
    private var active: Candidate? = null
    private val controls = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<NativePlaybackControl, Boolean>())
    private val owned = java.util.concurrent.ConcurrentHashMap<NativePlaybackControl, NativeTorrentOwnedWork>()
    private var closed = false

    /** Register the backend-control owner before negotiation/start IO or any grant acceptance. */
    suspend fun ownControl(control: NativePlaybackControl, generation: Long) = withContext(main) {
        if (closed || !isCurrent(generation)) throw NativeTorrentCoordinatorUnavailable()
        controls.add(control)
    }

    suspend fun prepare(control: NativePlaybackControl, generation: Long): Candidate {
        val work = NativeTorrentOwnedWork({ beginAcquisition(cache, control) { remainingStartupMillis(control) } }, nowNanos)
        try {
            withContext(main) {
                if (closed || !isCurrent(generation)) throw NativeTorrentCoordinatorUnavailable()
                controls.add(control)
                owned[control] = work
            }
            withContext(io) {
                val reservation = cache.reserveMetainfo(NativeTorrentCacheLimits.METAINFO_BYTES)
                try { cache.register(work, reservation) } catch (error: Exception) { reservation.close(); throw error }
                work.start()
            }
            val capability = work.ready.await()
            return withContext(main) {
                if (closed || !isCurrent(generation)) throw NativeTorrentCoordinatorUnavailable()
                if (remainingStartupMillis(control) <= 0) throw NativeTorrentCoordinatorUnavailable()
                control.authorize()
                Candidate(generation, control, work, capability)
            }
        } catch (error: Exception) {
            withContext(NonCancellable + main) {
                val deadline = minOf(nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS, work.settlementDeadlineNanos())
                control.retireLocal()
                work.cancel()
                val settled = withContext(io) {
                    val native = cache.retire(work, minOf(deadline, work.settlementDeadlineNanos()))
                    val http = control.joinLocal(deadline)
                    if (!native || !http || nowNanos() > deadline) cache.retainFailedSettlement()
                    native && http && cache.isAvailable
                }
                if (settled) {
                    owned.remove(control)
                    runCatching { control.stop() }
                    controls.remove(control)
                }
            }
            throw error
        }
    }

    private fun remainingStartupMillis(control: NativePlaybackControl): Long {
        return control.startupRemainingMillis()
    }

    /** Called only at the accepted player-open boundary, while the old player still exists. */
    suspend fun accept(candidate: Candidate, open: suspend (NativeTorrentCapability) -> Unit) = withContext(main) {
        try {
            if (closed || candidate.retired || !isCurrent(candidate.generation)) throw NativeTorrentCoordinatorUnavailable()
            candidate.control.authorize()
        } catch (error: Exception) { retire(candidate); throw error }
        val outgoing = active
        preventPlayerReads()
        active = candidate
        if (outgoing != null && outgoing !== candidate && !retire(outgoing)) {
            retire(candidate)
            throw NativeTorrentCoordinatorUnavailable()
        }
        try {
            if (candidate.retired || active !== candidate || !cache.isAvailable) throw NativeTorrentCoordinatorUnavailable()
            candidate.control.authorize()
            open(candidate.capability)
        }
        catch (error: Exception) { retire(candidate); throw error }
    }

    /** Stop Media3 before invalidating the local byte capability or remote authority. */
    suspend fun retire(candidate: Candidate): Boolean = withContext(NonCancellable + main) {
        if (candidate.retired) return@withContext candidate.retirement.await()
        candidate.retired = true
        val deadline = minOf(nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS, candidate.work.settlementDeadlineNanos())
        if (active === candidate) { preventPlayerReads(); active = null }
        candidate.control.retireLocal()
        candidate.work.cancel()
        val settled = withContext(io) {
            val native = cache.retire(candidate.work, minOf(deadline, candidate.work.settlementDeadlineNanos()))
            val http = candidate.control.joinLocal(deadline)
            if (!native || !http || nowNanos() > deadline) cache.retainFailedSettlement()
            native && http && cache.isAvailable
        }
        // Remote tombstone/release is best effort after joined local byte shutdown.
        if (settled) {
            owned.remove(candidate.control)
            runCatching { candidate.control.stop() }
            controls.remove(candidate.control)
        }
        // Every concurrent caller observes the same bounded cleanup attempt,
        // including its remote release, rather than racing a premature receipt.
        candidate.retirement.complete(settled)
        settled
    }

    suspend fun stop(): Boolean = active?.let { retire(it) } ?: cache.isAvailable

    /** The control's read-prevention callback calls this immediately after stopping its Media3 reads. */
    fun cancelNative(control: NativePlaybackControl) { owned[control]?.cancel() }

    /** Back/start failure also retires owners which have not reached native acquisition yet. */
    suspend fun retireControl(control: NativePlaybackControl): Boolean = withContext(NonCancellable + main) {
        active?.takeIf { it.control === control }?.let { return@withContext retire(it) }
        val work = owned[control]
        val deadline = minOf(nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS,
            work?.settlementDeadlineNanos() ?: Long.MAX_VALUE)
        control.retireLocal()
        work?.cancel()
        val settled = withContext(io) {
            val native = work?.let { cache.retire(it, deadline) } ?: true
            val http = control.joinLocal(deadline)
            if (!native || !http || nowNanos() > deadline) cache.retainFailedSettlement()
            native && http && cache.isAvailable
        }
        if (settled) {
            owned.remove(control)
            runCatching { control.stop() }
            controls.remove(control)
        }
        settled
    }

    suspend fun background() = withContext(main) {
        preventPlayerReads()
        controls.toList().forEach { it.background() }
    }

    /** The caller restores Media3 only after backend revalidation and this main-thread fence. */
    suspend fun foreground(): Candidate? = withContext(main) {
        val candidate = active ?: return@withContext null
        candidate.control.foreground()
        if (closed || active !== candidate || candidate.retired) throw NativeTorrentCoordinatorUnavailable()
        candidate.control.authorize()
        candidate
    }

    suspend fun authorizeActive(): Candidate? = withContext(main) {
        val candidate = active ?: return@withContext null
        if (closed || candidate.retired) throw NativeTorrentCoordinatorUnavailable()
        candidate.control.authorize()
        candidate
    }

    /** Sign-out/profile/server/revocation/close effects; failed ownership remains quarantined. */
    suspend fun closeScope(clearContent: Boolean = false): Boolean = withContext(NonCancellable + main) {
        preventPlayerReads()
        closed = true
        val deadline = minOf(nowNanos() + NativeTorrentCacheLimits.SETTLEMENT_NANOS,
            owned.values.minOfOrNull { it.settlementDeadlineNanos() } ?: Long.MAX_VALUE)
        val snapshot = controls.toList()
        snapshot.forEach { it.retireLocal() }
        val workSnapshot = owned.values.toList()
        workSnapshot.forEach { it.cancel() }
        val current = active
        active = null
        val locallySettled = withContext(io) {
            val native = workSnapshot.all { cache.retire(it, minOf(deadline, it.settlementDeadlineNanos())) }
            val http = snapshot.all { it.joinLocal(deadline) }
            if (!native || !http || nowNanos() > deadline) cache.retainFailedSettlement()
            native && http && cache.isAvailable
        }
        // The manager remains owned until these registered control bodies settle.
        if (locallySettled) snapshot.forEach { runCatching { it.stop() } }
        val settled = withContext(io) { locallySettled && cache.closeScope(clearContent) }
        current?.retired = true
        current?.retirement?.complete(settled)
        if (settled) { controls.clear(); owned.clear() }
        settled
    }
    override fun toString() = "NativeTorrentCoordinator(<redacted>)"
}
