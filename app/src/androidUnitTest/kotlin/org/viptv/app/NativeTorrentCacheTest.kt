package org.viptv.app

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NativeTorrentCacheTest {
    private class Manager(private val events: MutableList<String>) : NativeTorrentCacheManager {
        var failed = false
        override fun hasFailedSettlement() = failed
        override fun closeAfterSettlement() { events.add("manager.close") }
    }

    private class Work(private val id: String, private val events: MutableList<String>) : NativeTorrentCacheWork {
        var settled = true
        override fun preventReads() { events.add("$id.reads.off") }
        override fun cancel() { events.add("$id.cancel") }
        override fun join(deadlineNanos: Long): Boolean { events.add("$id.join"); return settled }
        override fun closeAfterSettlement() { events.add("$id.close") }
    }

    @Test fun duplicateOwnerAndRandomIdentityUseRealPrivateStorage() {
        val parent = Files.createTempDirectory("native-cache-owner").toFile()
        val events = mutableListOf<String>()
        var path: File? = null
        val cache = NativeTorrentCache.open(parent, { directory, bytes ->
            path = directory
            assertEquals(NativeTorrentCacheLimits.PAYLOAD_BYTES, bytes)
            Manager(events)
        })
        assertTrue(cache.isAvailable)
        assertTrue(requireNotNull(path).name.matches(Regex("epoch-[0-9a-f]{32}")))
        assertFailsWith<NativeTorrentCacheUnavailable> { NativeTorrentCache.open(parent, { _, _ -> Manager(events) }) }
        File(requireNotNull(path), "owned-payload").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(cache.closeScope())
        assertFalse(requireNotNull(path).exists())
        val next = NativeTorrentCache.open(parent, { directory, _ ->
            assertNotEquals(path, directory)
            Manager(events)
        })
        assertTrue(next.closeScope())
        parent.deleteRecursively()
    }

    @Test fun controlAggregateAndPerMetainfoRefusalPreserveOutgoing() {
        val parent = Files.createTempDirectory("native-cache-budget").toFile()
        val events = mutableListOf<String>()
        val cache = NativeTorrentCache.open(parent, { _, _ -> Manager(events) })
        val outgoing = Work("outgoing", events)
        val outgoingCharge = cache.reserveMetainfo(NativeTorrentCacheLimits.METAINFO_BYTES)
        cache.register(outgoing, outgoingCharge)
        val other = cache.reserveControl(NativeTorrentCacheLimits.CONTROL_BYTES - NativeTorrentCacheLimits.METAINFO_BYTES)
        assertFailsWith<NativeTorrentCacheUnavailable> { cache.reserveControl(1) }
        assertFailsWith<NativeTorrentCacheUnavailable> { cache.reserveMetainfo(NativeTorrentCacheLimits.METAINFO_BYTES + 1) }
        assertTrue(cache.isAvailable)
        assertTrue(events.isEmpty())
        outgoingCharge.close()
        assertEquals(NativeTorrentCacheLimits.CONTROL_BYTES, cache.reservedControlBytes)
        other.close()
        assertEquals(NativeTorrentCacheLimits.METAINFO_BYTES, cache.reservedControlBytes)
        assertTrue(cache.retire(outgoing))
        assertEquals(0, cache.reservedControlBytes)
        assertTrue(cache.closeScope())
        parent.deleteRecursively()
    }

    @Test fun diskFullRefusesCandidateWhileOutgoingRemainsUsable() {
        val parent = Files.createTempDirectory("native-cache-disk").toFile()
        val events = mutableListOf<String>()
        val storage = object : NativeTorrentCacheStorage() {
            var free = 100L
            override fun availableBytes(directory: File) = free
        }
        val cache = NativeTorrentCache.open(parent, { _, _ -> Manager(events) }, storage)
        val outgoing = Work("outgoing", events)
        cache.register(outgoing, cache.reserveControl(50))
        storage.free = 0
        assertFailsWith<NativeTorrentCacheUnavailable> { cache.reserveControl(1) }
        assertTrue(cache.isAvailable)
        assertTrue(events.isEmpty())
        assertEquals(50, cache.reservedControlBytes)
        assertTrue(cache.closeScope())
        parent.deleteRecursively()
    }

    @Test fun retiringOneGrantDoesNotCancelItsReplacementAndScopeJoinsBeforeDeletion() {
        val parent = Files.createTempDirectory("native-cache-order").toFile()
        val events = mutableListOf<String>()
        val storage = object : NativeTorrentCacheStorage() {
            override fun deleteOwned(directory: File) { events.add("delete"); super.deleteOwned(directory) }
        }
        val cache = NativeTorrentCache.open(parent, { _, _ -> Manager(events) }, storage)
        val outgoing = Work("outgoing", events)
        val candidate = Work("candidate", events)
        cache.register(outgoing, cache.reserveControl(1))
        cache.register(candidate, cache.reserveControl(1))
        assertTrue(cache.retire(outgoing))
        assertEquals(listOf("outgoing.reads.off", "outgoing.cancel", "outgoing.join", "outgoing.close"), events)
        events.clear()
        assertTrue(cache.closeScope())
        assertEquals(listOf("candidate.reads.off", "candidate.cancel", "candidate.join", "candidate.close", "manager.close", "delete"), events)
        assertTrue(cache.closeScope())
        parent.deleteRecursively()
    }

    @Test fun unresolvedSettlementKeepsAccountingCacheAndOwnerLock() {
        val parent = Files.createTempDirectory("native-cache-unsettled").toFile()
        val events = mutableListOf<String>()
        var path: File? = null
        val cache = NativeTorrentCache.open(parent, { directory, _ -> path = directory; Manager(events) })
        val work = Work("stalled", events).apply { settled = false }
        val charge = cache.reserveControl(123)
        cache.register(work, charge)
        assertFalse(cache.retire(work))
        charge.close()
        work.settled = true
        assertFalse(cache.closeScope())
        assertFalse(cache.isAvailable)
        assertTrue(requireNotNull(path).exists())
        assertEquals(123, cache.reservedControlBytes)
        assertFailsWith<NativeTorrentCacheUnavailable> { NativeTorrentCache.open(parent, { _, _ -> Manager(events) }) }
        assertFalse(events.contains("manager.close"))
        // Quarantined ownership deliberately survives until process exit.
    }

    @Test fun cleanupFailureKeepsAccountingAndBlocksAnotherScope() {
        val parent = Files.createTempDirectory("native-cache-delete-failure").toFile()
        val events = mutableListOf<String>()
        var path: File? = null
        val storage = object : NativeTorrentCacheStorage() {
            override fun deleteOwned(directory: File) { throw IllegalStateException(directory.path) }
        }
        val cache = NativeTorrentCache.open(parent, { directory, _ -> path = directory; Manager(events) }, storage)
        cache.reserveControl(321)
        assertFalse(cache.closeScope())
        assertFalse(cache.isAvailable)
        assertEquals(321, cache.reservedControlBytes)
        assertTrue(requireNotNull(path).exists())
        assertFailsWith<NativeTorrentCacheUnavailable> { NativeTorrentCache.open(parent, { _, _ -> Manager(events) }) }
        assertFalse(cache.toString().contains(parent.path))
    }

    @Test fun restartDeletesOnlyIdentifiedInactiveOwnedStorage() {
        val parent = Files.createTempDirectory("native-cache-restart").toFile()
        val storage = NativeTorrentCacheStorage()
        val root = storage.createRoot(parent)
        val inactive = storage.createOwned(root)
        File(inactive, "retained-payload").writeText("fixture")
        val unrelated = File(root, "unrelated-app-data").apply { mkdir() }
        val unmarked = File(root, "epoch-${"1".repeat(32)}").apply { mkdir() }
        val accountHistory = File(parent, "account-history").apply { writeText("preserve") }
        val events = mutableListOf<String>()
        val cache = NativeTorrentCache.open(parent, { directory, _ ->
            assertFalse(inactive.exists())
            assertNotEquals(inactive, directory)
            assertTrue(unrelated.exists())
            assertTrue(unmarked.exists())
            assertTrue(accountHistory.exists())
            Manager(events)
        })
        assertTrue(cache.closeScope())
        assertTrue(accountHistory.exists())
        parent.deleteRecursively()
    }

    @Test fun processCrashReleasesExclusiveLockAndRestartReapsItsIdentifiedCache() {
        val parent = Files.createTempDirectory("native-cache-process-restart").toFile()
        val classpath = listOf(
            System.getProperty("java.class.path"),
            File(NativeTorrentCache::class.java.protectionDomain.codeSource.location.toURI()).path,
            File(NativeTorrentCacheCrashFixture::class.java.protectionDomain.codeSource.location.toURI()).path,
            File(Unit::class.java.protectionDomain.codeSource.location.toURI()).path,
        ).distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath,
            NativeTorrentCacheCrashFixture::class.java.name, parent.path,
        ).redirectErrorStream(true).start()
        try {
            assertEquals("READY", process.inputStream.bufferedReader().readLine())
            val storage = NativeTorrentCacheStorage()
            val root = storage.createRoot(parent)
            val old = requireNotNull(root.listFiles()).single { it.name.startsWith("epoch-") }
            assertTrue(File(old, "fixture-payload").exists())
            assertFailsWith<NativeTorrentCacheUnavailable> {
                NativeTorrentCache.open(parent, { _, _ -> Manager(mutableListOf()) })
            }
            process.outputStream.write(10)
            process.outputStream.flush()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            val next = NativeTorrentCache.open(parent, { _, _ ->
                assertFalse(old.exists())
                Manager(mutableListOf())
            })
            assertTrue(next.closeScope())
        } finally {
            process.destroyForcibly()
            parent.deleteRecursively()
        }
    }

    @Test fun joinedResultPastSharedDeadlineStillQuarantinesAccounting() {
        val parent = Files.createTempDirectory("native-cache-deadline").toFile()
        var elapsed = 0L
        val cache = NativeTorrentCache.open(parent, { _, _ -> Manager(mutableListOf()) }, nowNanos = { elapsed })
        val work = object : NativeTorrentCacheWork {
            override fun preventReads() = Unit
            override fun cancel() = Unit
            override fun join(deadlineNanos: Long): Boolean { elapsed = deadlineNanos + 1; return true }
            override fun closeAfterSettlement() = error("late receipt must not release accounting")
        }
        cache.register(work, cache.reserveControl(456))
        assertFalse(cache.closeScope())
        assertFalse(cache.isAvailable)
        assertEquals(456, cache.reservedControlBytes)
    }

    @Test fun ownedSymlinkIsRejectedWithoutDeletingItsTarget() {
        val parent = Files.createTempDirectory("native-cache-symlink").toFile()
        val target = Files.createTempDirectory("native-unrelated-target").toFile()
        val storage = NativeTorrentCacheStorage()
        val root = storage.createRoot(parent)
        Files.createSymbolicLink(File(root, "epoch-${"a".repeat(32)}").toPath(), target.toPath())
        val cache = NativeTorrentCache.open(parent, { _, _ -> error("must not admit") })
        assertFalse(cache.isAvailable)
        assertTrue(target.exists())
        target.deleteRecursively()
    }
}

/** Separate JVM exercises kernel-lock release on process exit, without restoring native authority. */
internal object NativeTorrentCacheCrashFixture {
    @JvmStatic fun main(args: Array<String>) {
        val cache = NativeTorrentCache.open(File(args.single()), { directory, _ ->
            File(directory, "fixture-payload").writeText("owned synthetic payload")
            object : NativeTorrentCacheManager {
                override fun hasFailedSettlement() = false
                override fun closeAfterSettlement() = Unit
            }
        })
        check(cache.isAvailable)
        println("READY")
        System.`in`.read()
        Runtime.getRuntime().halt(0)
    }
}
