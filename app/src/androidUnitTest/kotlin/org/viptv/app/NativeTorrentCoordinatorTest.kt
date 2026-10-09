package org.viptv.app

import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import java.io.File

/** Exercises the actual owned-work orchestration; native lifecycle remains a separate JNI gate. */
class NativeTorrentCoordinatorTest {
    private val url = "http://127.0.0.1:1234/${"a".repeat(64)}/3/stream.mp4"
    private class Handle(val events: MutableList<String>, private val joinBarrier: CountDownLatch? = null) : NativeTorrentHandleEffect {
        var settled = true
        override fun validatedCapability() = NativeTorrentCapability.validated("http://127.0.0.1:1234/${"a".repeat(64)}/3/stream.mp4", 3u)
        override fun stop() { events.add("handle.stop") }
        override fun stopAndJoin(): Boolean { events.add("handle.join"); joinBarrier?.countDown(); joinBarrier?.await(1, TimeUnit.SECONDS); return settled }
        override fun close() { events.add("handle.close") }
    }
    private class Acquisition(val handle: Handle, val events: MutableList<String>,
        private val readyGate: CountDownLatch? = null, private val joinBarrier: CountDownLatch? = null,
    ) : NativeTorrentAcquisitionEffect {
        var settled = true
        override fun waitReady(): NativeTorrentHandleEffect { readyGate?.await(2, TimeUnit.SECONDS); return handle }
        override fun cancel() { events.add("acquisition.cancel") }
        override fun cancelAndJoin(): Boolean { events.add("acquisition.join"); joinBarrier?.countDown(); joinBarrier?.await(1, TimeUnit.SECONDS); return settled }
        override fun close() { events.add("acquisition.close") }
    }
    private fun events(): MutableList<String> = Collections.synchronizedList(mutableListOf())
    private fun cache(events: MutableList<String>, nowNanos: () -> Long = System::nanoTime) = NativeTorrentCache.open(Files.createTempDirectory("native-coordinator").toFile(), { _, _ ->
        object : NativeTorrentCacheManager {
            override fun hasFailedSettlement() = false
            override fun closeAfterSettlement() { events.add("manager.close") }
        }
    }, nowNanos = nowNanos)

    @Test fun metadataRefusalSurvivesItsOwnAuthorityInvalidation() = runBlocking {
        lateinit var work: NativeTorrentOwnedWork
        val handle = object : NativeTorrentHandleEffect {
            override fun validatedCapability(): NativeTorrentCapability {
                // Shared metadata refusal invalidates authority; expiry stops local reads.
                work.cancel()
                throw NativeTorrentFailure("native_file_unavailable")
            }
            override fun stop() = Unit
            override fun stopAndJoin() = true
            override fun close() = Unit
        }
        val acquisition = object : NativeTorrentAcquisitionEffect {
            override fun waitReady() = handle
            override fun cancel() = Unit
            override fun cancelAndJoin() = true
            override fun close() = Unit
        }
        work = NativeTorrentOwnedWork({ acquisition }, System::nanoTime)
        work.start()
        val failure = assertFailsWith<NativeTorrentFailure> { work.ready.await() }
        assertEquals("native_file_unavailable", failure.reason)
        assertTrue(work.join(work.settlementDeadlineNanos()))
        work.closeAfterSettlement()
    }

    @Test fun cancellationDuringValidationCannotPublishALateCapability() = runBlocking {
        lateinit var work: NativeTorrentOwnedWork
        val handle = object : NativeTorrentHandleEffect {
            override fun validatedCapability(): NativeTorrentCapability {
                work.cancel()
                return NativeTorrentCapability.validated(url, 3u)
            }
            override fun stop() = Unit
            override fun stopAndJoin() = true
            override fun close() = Unit
        }
        val acquisition = object : NativeTorrentAcquisitionEffect {
            override fun waitReady() = handle
            override fun cancel() = Unit
            override fun cancelAndJoin() = true
            override fun close() = Unit
        }
        work = NativeTorrentOwnedWork({ acquisition }, System::nanoTime)
        work.start()
        assertFailsWith<NativeTorrentCoordinatorUnavailable> { work.ready.await() }
        assertTrue(work.join(work.settlementDeadlineNanos()))
        work.closeAfterSettlement()
    }

    @Test fun cancellationIsAddressableBeforeBeginReturnsAndLateHandleCannotPublish() {
        val events = events()
        val beginEntered = CountDownLatch(1)
        val beginReturn = CountDownLatch(1)
        val acquisition = Acquisition(Handle(events), events)
        val work = NativeTorrentOwnedWork({ beginEntered.countDown(); beginReturn.await(); acquisition }, System::nanoTime)
        val owner = cache(events)
        owner.register(work, owner.reserveMetainfo(4_194_304))
        work.start()
        assertTrue(beginEntered.await(1, TimeUnit.SECONDS))
        work.cancel()
        assertTrue(work.ready.isCancelled)
        beginReturn.countDown()
        assertTrue(owner.retire(work))
        assertTrue(events.indexOf("acquisition.cancel") < events.indexOf("acquisition.close"))
        assertTrue(events.indexOf("handle.stop") < events.indexOf("handle.close"))
        assertEquals(1, events.count { it == "handle.close" })
        assertTrue(owner.retire(work))
        assertEquals(0L, owner.reservedControlBytes)
        assertTrue(owner.closeScope())
    }

    @Test fun acquisitionAndHandleJoinsStartConcurrentlyAgainstOneDeadline() = runBlocking {
        val events = events()
        val barrier = CountDownLatch(2)
        val acquisition = Acquisition(Handle(events, barrier), events, joinBarrier = barrier)
        val work = NativeTorrentOwnedWork({ acquisition }, System::nanoTime)
        val owner = cache(events)
        owner.register(work, owner.reserveMetainfo(4_194_304))
        work.start(); work.ready.await()
        val start = System.nanoTime()
        assertTrue(owner.retire(work))
        assertTrue(System.nanoTime() - start < 800_000_000)
        assertEquals(0L, barrier.count)
        assertTrue(owner.closeScope())
    }

    @Test fun commonScopeDeadlineCancelsEveryWorkBeforeJoining() = runBlocking {
        val events = events()
        val barrier = CountDownLatch(4)
        val owner = cache(events)
        repeat(2) {
            val acquisition = Acquisition(Handle(events, barrier), events, joinBarrier = barrier)
            val work = NativeTorrentOwnedWork({ acquisition }, System::nanoTime)
            owner.register(work, owner.reserveMetainfo(4_194_304))
            work.start(); work.ready.await()
        }
        val start = System.nanoTime()
        assertTrue(owner.closeScope())
        assertTrue(System.nanoTime() - start < 800_000_000)
        assertEquals(0L, barrier.count)
        assertEquals(2, events.count { it == "handle.close" })
        assertEquals("manager.close", events.last())
    }

    @Test fun failedSettlementRetainsAcquisitionHandleAndAllCharges() = runBlocking {
        val events = events()
        val handle = Handle(events).apply { settled = false }
        val acquisition = Acquisition(handle, events)
        val work = NativeTorrentOwnedWork({ acquisition }, System::nanoTime)
        val owner = cache(events)
        owner.register(work, owner.reserveMetainfo(4_194_304))
        work.start(); work.ready.await()
        assertFalse(owner.retire(work))
        assertFalse(owner.isAvailable)
        assertEquals(4_194_304L, owner.reservedControlBytes)
        assertEquals(2_147_483_648L, owner.heldPayloadCapacityBytes)
        assertFalse(events.any { it.endsWith(".close") })
        assertFalse(owner.closeScope())
    }

    @Test fun delayedCoroutineJoinCannotRestartTheFirstCancellationDeadline() = runBlocking {
        val events = events()
        val now = AtomicLong(1_000)
        val work = NativeTorrentOwnedWork({ Acquisition(Handle(events), events) }, now::get)
        val owner = cache(events, now::get)
        owner.register(work, owner.reserveMetainfo(4_194_304))
        work.start(); work.ready.await()
        work.cancel()
        val original = work.settlementDeadlineNanos()
        now.set(original + 1)
        assertFalse(owner.retire(work))
        assertEquals(original, work.settlementDeadlineNanos())
        assertFalse(owner.isAvailable)
        assertEquals(4_194_304L, owner.reservedControlBytes)
        assertFalse(events.any { it.endsWith(".close") })
    }

    @Test fun loopbackCapabilityAcceptsOnlyStrictLiteralExactFileWithoutCredentials() {
        assertEquals(url, NativeTorrentCapability.validated(url, 3u).url)
        for (bad in listOf(url.replace("127.0.0.1", "localhost"), url.replace("127.0.0.1", "192.168.1.1"),
            url.replace("http:", "https:"), "$url?token=secret", "$url#fragment", url.replace("/3/", "/4/"),
            url.replace("127.0.0.1", "user:password@127.0.0.1"), url.replace("a".repeat(64), "a".repeat(16)))) {
            assertFailsWith<NativeTorrentCoordinatorUnavailable> { NativeTorrentCapability.validated(bad, 3u) }
        }
        assertFalse(NativeTorrentCapability.validated(url, 3u).toString().contains(url))
    }

    @Test fun failedCandidatePreservesOutgoingAndReceivesOnlyRemainingStartupBudget() = runBlocking {
        val events = events()
        val now = AtomicLong(100_000_000_000)
        val owner = cache(events, now::get)
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val corpus = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())
        val ready = corpus.getJSONArray("cases").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getString("body").runtimeV2Fixture()
        val request = corpus.getJSONObject("context").getJSONObject("request")
        val budgets = mutableListOf<Long>()
        var starts = 0
        val coordinator = NativeTorrentCoordinator(owner, now::get, { it == 7L }, { events.add("player.reads.off") },
            main = Dispatchers.Unconfined, io = Dispatchers.IO, beginAcquisition = { _, _, budget ->
                synchronized(budgets) { budgets.add(budget()) }
                starts++
                if (starts == 2) throw RuntimeException("Synthetic candidate failure")
                Acquisition(Handle(events), events)
            })
        FixtureServer(6) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> """{"version":2,"native_torrent_versions":[2]}"""
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> ready
        }) }.use { server ->
            fun control() = NativePlaybackControl("https://fixture.invalid", "scope_fixture", 7, { true },
                NativePlaybackTransport(server.origin, { "fixture_bearer" }, { true }, owner),
                V2PlaybackControl("https://fixture.invalid") { _, _, _ -> throw AssertionError("Unexpected legacy admission") },
                jobs, NativePlaybackClock { now.get() / 1_000_000 }, { events.add("authority.reads.off") }, {})
            try {
                val outgoingControl = control()
                outgoingControl.start(request, true, true, owner)
                val outgoing = coordinator.prepare(outgoingControl, 7)
                assertFalse(events.contains("player.reads.off"))
                coordinator.accept(outgoing) { events.add("player.open") }
                val candidateControl = control()
                candidateControl.start(request, true, true, owner)
                now.set(129_000_000_000)
                assertFailsWith<NativeTorrentCoordinatorUnavailable> { coordinator.prepare(candidateControl, 7) }
                assertEquals(listOf(120_000L, 91_000L), budgets)
                assertEquals(1, events.count { it == "player.reads.off" })
                assertFalse(events.contains("handle.stop"))
                assertTrue(coordinator.authorizeActive() === outgoing)
                assertTrue(coordinator.closeScope())
                assertTrue(events.indexOf("player.reads.off") < events.indexOf("handle.stop"))
                assertEquals(1, events.count { it == "handle.close" })
            } finally { jobs.cancel() }
        }
    }

    @Test fun lateGenerationOrTotalDeadlineRejectReadyBeforeAnyPlayerEffect() = runBlocking {
        for (lateGeneration in listOf(true, false)) {
            val events = events()
            val now = AtomicLong(100_000_000_000)
            val generation = AtomicLong(7)
            val owner = cache(events, now::get)
            val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val corpus = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())
            val body = corpus.getJSONArray("cases").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getString("body").runtimeV2Fixture()
            FixtureServer(3) { incoming -> FixtureResponse(when {
                incoming.target.endsWith("torrent-runtime-protocol") -> """{"version":2,"native_torrent_versions":[2]}"""
                incoming.method == "DELETE" -> """{"ok":true}"""
                else -> body
            }) }.use { server ->
                val control = NativePlaybackControl("https://fixture.invalid", "scope_fixture", 7, { true },
                    NativePlaybackTransport(server.origin, { "fixture_bearer" }, { true }, owner),
                    V2PlaybackControl("https://fixture.invalid") { _, _, _ -> throw AssertionError("Unexpected legacy admission") },
                    jobs, NativePlaybackClock { now.get() / 1_000_000 }, {}, {})
                val coordinator = NativeTorrentCoordinator(owner, now::get, { it == generation.get() }, { events.add("player.reads.off") },
                    main = Dispatchers.Unconfined, io = Dispatchers.IO, beginAcquisition = { _, _, budget ->
                        assertEquals(120_000L, budget())
                        if (lateGeneration) generation.set(8) else now.set(221_000_000_000)
                        Acquisition(Handle(events), events)
                    })
                try {
                    control.start(corpus.getJSONObject("context").getJSONObject("request"), true, true, owner)
                    assertFailsWith<NativeTorrentCoordinatorUnavailable> { coordinator.prepare(control, 7) }
                    assertFalse(events.contains("player.reads.off"))
                    assertEquals(1, events.count { it == "handle.stop" })
                    assertEquals(1, events.count { it == "handle.close" })
                    assertEquals(0L, owner.reservedControlBytes)
                    assertTrue(owner.closeScope())
                } finally { jobs.cancel() }
            }
        }
    }

    @Test fun scopeCloseOwnsAcceptedBackendControlBeforeAnyNativeHandleExists() = runBlocking {
        val events = events()
        val now = AtomicLong(100_000_000_000)
        val owner = cache(events, now::get)
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val corpus = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())
        val body = corpus.getJSONArray("cases").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getString("body").runtimeV2Fixture()
        val coordinator = NativeTorrentCoordinator(owner, now::get, { it == 7L }, { events.add("player.reads.off") },
            main = Dispatchers.Unconfined, io = Dispatchers.IO, beginAcquisition = { _, _, _ -> throw AssertionError("Unexpected native IO") })
        FixtureServer(3) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> """{"version":2,"native_torrent_versions":[2]}"""
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> body
        }) }.use { server ->
            val control = NativePlaybackControl("https://fixture.invalid", "scope_fixture", 7, { true },
                NativePlaybackTransport(server.origin, { "fixture_bearer" }, { true }, owner),
                V2PlaybackControl("https://fixture.invalid") { _, _, _ -> throw AssertionError("Unexpected legacy admission") },
                jobs, NativePlaybackClock { now.get() / 1_000_000 }, {}, {})
            try {
                coordinator.ownControl(control, 7)
                control.start(corpus.getJSONObject("context").getJSONObject("request"), true, true, owner)
                assertTrue(owner.reservedControlBytes > 0)
                assertTrue(coordinator.closeScope())
                assertEquals(0L, owner.reservedControlBytes)
                assertEquals(listOf("player.reads.off", "manager.close"), events)
            } finally { jobs.cancel() }
        }
    }
}
