package org.viptv.app

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.json.JSONObject
import org.viptv.video.PlaybackError
import org.viptv.video.PlaybackErrorCode
import org.viptv.video.PlaybackFailure
import org.viptv.video.PlaybackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Actual controller effect seam and Rust private bridge; transport acquisition is a controlled effect. */
class NativePlaybackEffectsTest {
    private fun corpus() = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())
    private fun ready() = corpus().getJSONArray("cases").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getString("body").runtimeV2Fixture()
    private fun request() = corpus().getJSONObject("context").getJSONObject("request")
    private val url = "http://127.0.0.1:1234/${"a".repeat(64)}/3/stream.mp4"
    private val protocol = """{"version":2,"native_torrent_versions":[2]}"""

    private class Harness(val startsFailAfter: Int = Int.MAX_VALUE, val settlement: Boolean = true,
        val startFailure: Exception = NativeTorrentCoordinatorUnavailable()) : AutoCloseable {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val now = AtomicLong(100_000_000_000)
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var starts = 0
        val cache = NativeTorrentCache.open(Files.createTempDirectory("native-player-effects").toFile(), { _, _ ->
            object : NativeTorrentCacheManager {
                override fun hasFailedSettlement() = false
                override fun closeAfterSettlement() { events.add("manager.close") }
            }
        }, nowNanos = now::get)
        val coordinator = NativeTorrentCoordinator(cache, now::get, { it == 7L }, { events.add("player.stop") },
            main = Dispatchers.Unconfined, beginAcquisition = { _, _, _ ->
                starts++
                if (starts > startsFailAfter) throw startFailure
                object : NativeTorrentAcquisitionEffect {
                    override fun waitReady() = object : NativeTorrentHandleEffect {
                        override fun validatedCapability() = NativeTorrentCapability.validated("http://127.0.0.1:1234/${"a".repeat(64)}/3/stream.mp4", 3u)
                        override fun stop() { events.add("bytes.stop") }
                        override fun stopAndJoin() = settlement.also { events.add("bytes.join") }
                        override fun close() { events.add("bytes.close") }
                    }
                    override fun cancel() { events.add("acquisition.cancel") }
                    override fun cancelAndJoin() = settlement
                    override fun close() { events.add("acquisition.close") }
                }
            })
        val effects = NativePlaybackEffects(coordinator, jobs, { events.add("player.stop") }, { events.add("recovery") })
        fun control(server: FixtureServer, legacy: V2PlaybackControl = V2PlaybackControl("https://fixture.invalid") { _, _, _ ->
            throw AssertionError("Unexpected legacy start")
        }, current: () -> Boolean = { true }, bearer: () -> String? = { "fixture_bearer" }): NativePlaybackControl {
            lateinit var value: NativePlaybackControl
            value = NativePlaybackControl("https://fixture.invalid", "scope_fixture", 7, current,
                NativePlaybackTransport(server.origin, bearer, current, cache), legacy, jobs,
                NativePlaybackClock { now.get() / 1_000_000 }, { effects.preventReads(value) }, { effects.invalidated(value) })
            return value
        }
        override fun close() { effects.beginScopeClose(); effects.stop(); runBlocking { coordinator.closeScope() }; jobs.cancel() }
    }

    @Test fun exactLocalCapabilityOpensWithEmptyHeadersAndAuthoritativeMediaTimeAndPreferences() = runBlocking {
        FixtureServer(3) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> protocol
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> ready()
        }) }.use { server -> Harness().use { h ->
            val prepared = h.effects.prepare(h.control(server), request(), 7) as NativePlaybackEffects.Prepared.Native
            assertFalse(h.events.contains("player.stop"))
            val position = h.effects.accept(prepared, "Exact episode", false, { h.events.add("lease.replace") }) { source, playing ->
                h.events.add("player.open")
                assertEquals(url, source.uri)
                assertEquals(emptyMap(), source.headers)
                assertEquals(PlaybackKind.OnDemand, source.kindHint)
                assertEquals("Exact episode", source.title)
                assertEquals(120_000L, source.startPositionMillis)
                assertEquals(null, source.options.preferredAudioLanguage)
                assertEquals(null, source.options.preferredSubtitleLanguage)
                assertFalse(source.options.subtitlesEnabled!!)
                assertFalse(playing)
                assertFalse(source.toString().contains(url))
            }
            assertEquals(120_000L, position)
            assertEquals(listOf("player.stop", "lease.replace", "player.open"), h.events)
            assertTrue(h.effects.hasActive)
            assertEquals("ordinaryRetry", h.effects.recoveryDecision())
        } }
    }

    @Test fun preBoundaryCandidateFailureLeavesOutgoingPlayerAndNativeGrantUsable() = runBlocking {
        FixtureServer(6) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> protocol
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> ready()
        }) }.use { server -> Harness(startsFailAfter = 1).use { h ->
            val outgoing = h.effects.prepare(h.control(server), request(), 7) as NativePlaybackEffects.Prepared.Native
            h.effects.accept(outgoing, "Exact episode", true, {}) { _, _ -> h.events.add("player.open") }
            assertFailsWith<NativeTorrentCoordinatorUnavailable> { h.effects.prepare(h.control(server), request(), 7) }
            assertTrue(h.effects.hasActive)
            assertTrue(h.coordinator.authorizeActive() === outgoing.candidate)
            assertEquals(1, h.events.count { it == "player.stop" })
            assertFalse(h.events.contains("bytes.stop"))
            assertEquals("chooseSource", h.effects.recoveryDecision())
        } }
    }

    @Test fun admittedDecoderFailureReleasesNativeAndBackendBeforeExplicitGatewayRetry() = runBlocking {
        FixtureServer(3) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> protocol
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> ready()
        }) }.use { server -> Harness().use { h ->
            val prepared = h.effects.prepare(h.control(server), request(), 7) as NativePlaybackEffects.Prepared.Native
            val failure = PlaybackFailure(PlaybackError(PlaybackErrorCode.UnsupportedCodec, "Unsupported video", true))
            assertFailsWith<PlaybackFailure> {
                h.effects.accept(prepared, "Exact episode", false, {}) { _, _ -> h.events.add("player.open"); throw failure }
            }
            assertFalse(h.effects.hasActive)
            assertEquals("ordinaryRetry", h.effects.recoveryDecision())
            h.effects.resetRecovery()
            assertEquals("ordinaryRetry", h.effects.recoveryDecision())
            assertEquals(1, server.requests.count { it.method == "POST" })
            assertEquals(1, server.requests.count { it.method == "DELETE" })
            assertTrue(h.events.indexOf("player.stop") < h.events.indexOf("bytes.stop"))
            assertTrue(h.events.indexOf("bytes.join") < h.events.indexOf("bytes.close"))
            assertEquals(0L, h.cache.reservedControlBytes)
        } }
    }

    @Test fun nativeCapacityAndInvalidSelectionKeepDistinctRecoveryAfterJoinedRetirement() = runBlocking {
        for ((failure, expected) in listOf(
            NativeTorrentFailure("native_payload_limit") to "ordinaryRetry",
            NativeTorrentFailure("native_metadata_invalid") to "chooseSource",
        )) {
            FixtureServer(3) { incoming -> FixtureResponse(when {
                incoming.target.endsWith("torrent-runtime-protocol") -> protocol
                incoming.method == "DELETE" -> """{"ok":true}"""
                else -> ready()
            }) }.use { server -> Harness(startsFailAfter = 0, startFailure = failure).use { h ->
                assertFailsWith<NativeTorrentFailure> { h.effects.prepare(h.control(server), request(), 7) }
                assertEquals(expected, h.effects.recoveryDecision())
                assertFalse(h.effects.hasActive)
                assertEquals(0L, h.cache.reservedControlBytes)
                assertEquals(1, server.requests.count { it.method == "DELETE" })
            } }
        }
    }

    @Test fun failedNativeSettlementCannotAuthorizeRetryOrDiscardItsReservations() = runBlocking {
        FixtureServer(2) { incoming -> FixtureResponse(if (incoming.target.endsWith("torrent-runtime-protocol")) protocol else ready()) }.use { server ->
            Harness(settlement = false).use { h ->
                val prepared = h.effects.prepare(h.control(server), request(), 7) as NativePlaybackEffects.Prepared.Native
                assertFailsWith<PlaybackFailure> {
                    h.effects.accept(prepared, "Exact episode", true, {}) { _, _ ->
                        throw PlaybackFailure(PlaybackError(PlaybackErrorCode.Network, "Unavailable", true))
                    }
                }
                assertEquals("waitForRetirement", h.effects.recoveryDecision())
                assertFalse(h.cache.isAvailable)
                assertTrue(h.cache.reservedControlBytes > 0)
                assertFalse(h.events.contains("bytes.close"))
            }
        }
    }

    @Test fun authenticationRefusalCannotBeRetriedThroughGateway() = runBlocking {
        FixtureServer(1) { FixtureResponse("{}", 403) }.use { server -> Harness().use { h ->
            assertFailsWith<GatewayError> { h.effects.prepare(h.control(server), request(), 7) }
            assertEquals("authRecovery", h.effects.recoveryDecision())
            assertEquals(0, h.starts)
            assertFalse(h.events.contains("player.stop"))
        } }
    }

    @Test fun negotiatedOrdinaryDeliveryTransfersLeaseInsteadOfDeletingItDuringNativeCleanup() = runBlocking {
        val body = """{"id":"ordinary","status":"ready","delivery":{"kind":"direct","url":"https://fixture.invalid/file.mp4","headers":{"X-Fixture":"upstream"},"format":"original","position":120,"live":false},"error_code":null,"error":null,"expires_at":2000000000,"renew_after_seconds":20}"""
        FixtureServer(2) { incoming -> FixtureResponse(if (incoming.target.endsWith("torrent-runtime-protocol")) protocol else body) }.use { server ->
            Harness().use { h ->
                val legacy = V2PlaybackControl("https://fixture.invalid") { _, _, _ -> throw AssertionError("Duplicate start or premature release") }
                val prepared = h.effects.prepare(h.control(server, legacy), request().also { it.getJSONObject("client").put("canPlayDirect", true) }, 7) as NativePlaybackEffects.Prepared.Legacy
                assertEquals("ordinary", prepared.launch.sessionId)
                assertEquals(mapOf("X-Fixture" to "upstream"), prepared.launch.headers)
                assertTrue(legacy.remainingMillis("ordinary")!! > 0)
                assertEquals(0, h.starts)
                assertEquals(0L, h.cache.reservedControlBytes)
                assertFalse(h.events.contains("player.stop"))
                assertEquals(0, server.requests.count { it.method == "DELETE" })
            }
        }
    }

    @Test fun concurrentRetirementWaitsForTheSameRemoteCleanupReceipt() = runBlocking {
        val deletion = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        FixtureServer(3) { incoming -> when {
            incoming.target.endsWith("torrent-runtime-protocol") -> FixtureResponse(protocol)
            incoming.method == "DELETE" -> {
                deletion.countDown()
                check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                FixtureResponse("""{"ok":true}""")
            }
            else -> FixtureResponse(ready())
        } }.use { server -> Harness().use { h ->
            val prepared = h.effects.prepare(h.control(server), request(), 7) as NativePlaybackEffects.Prepared.Native
            h.effects.accept(prepared, "Exact episode", false, {}) { _, _ -> }
            val first = async(Dispatchers.Default) { h.coordinator.retire(prepared.candidate) }
            var second: kotlinx.coroutines.Deferred<Boolean>? = null
            try {
                assertTrue(kotlinx.coroutines.withContext(Dispatchers.IO) { deletion.await(3, java.util.concurrent.TimeUnit.SECONDS) })
                second = async(Dispatchers.Default) { h.coordinator.retire(prepared.candidate) }
                kotlinx.coroutines.delay(100)
                assertFalse(second.isCompleted, "Concurrent cleanup must not report completion before the existing release attempt")
            } finally { release.countDown(); first.await(); second?.await() }
        } }
    }

    @Test fun renewalAuthorizationRefusalRetiresReadsAndDoesNotAuthorizeGatewayBypass() = runBlocking {
        FixtureServer(4) { incoming -> when {
            incoming.target.endsWith("torrent-runtime-protocol") -> FixtureResponse(protocol)
            incoming.target.endsWith("heartbeat") -> FixtureResponse("{}", 403)
            incoming.method == "DELETE" -> FixtureResponse("""{"ok":true}""")
            else -> FixtureResponse(ready())
        } }.use { server -> Harness().use { h ->
            val control = h.control(server)
            val prepared = h.effects.prepare(control, request(), 7) as NativePlaybackEffects.Prepared.Native
            h.effects.accept(prepared, "Exact episode", false, {}) { _, _ -> h.events.add("player.open") }
            assertFailsWith<GatewayError> { control.renew() }
            assertEquals("authRecovery", h.effects.recoveryDecision())
            assertFalse(h.effects.hasActive)
            assertTrue(h.events.indexOf("player.stop") < h.events.indexOf("bytes.stop"))
            assertEquals(1, h.events.count { it == "recovery" })
            assertEquals(1, server.requests.count { it.method == "DELETE" })
        } }
    }

    @Test fun scopeChangeReleasesOnlyWithCapturedOutgoingCredentialAndCannotRenewOldAuthority() = runBlocking {
        FixtureServer(3) { incoming -> FixtureResponse(when {
            incoming.target.endsWith("torrent-runtime-protocol") -> protocol
            incoming.method == "DELETE" -> """{"ok":true}"""
            else -> ready()
        }) }.use { server -> Harness().use { h ->
            var current = true
            var token = "fixture_old_scope"
            val control = h.control(server, current = { current }, bearer = { token })
            val prepared = h.effects.prepare(control, request(), 7) as NativePlaybackEffects.Prepared.Native
            h.effects.accept(prepared, "Exact episode", true, {}) { _, _ -> h.events.add("player.open") }
            h.effects.beginScopeClose()
            h.effects.stop()
            current = false
            token = "fixture_other_principal"
            assertTrue(h.coordinator.closeScope())
            assertFailsWith<GatewayError> { control.renew() }
            val release = server.requests.single { it.method == "DELETE" }
            assertEquals("Bearer fixture_old_scope", release.headers["authorization"])
            assertFalse(h.events.contains("recovery"))
            assertEquals(1, server.requests.count { it.method == "DELETE" })
        } }
    }

    @Test fun explicitGatewayRequestUsesCanonicalCoreSeamSameSourceAndFreshRequestIdentity() = runBlocking {
        val gateway = VipTvHttpGateway("https://fixture.invalid")
        val source = Source("opaque_exact_episode", "fixture", "Title", "Body")
        val capabilities = PlaybackClientCapabilities(1920, 1080, true, false, false, true, true)
        val native = gateway.playbackRequest(source, 123_000, capabilities, preferredAudioLanguage = "fr",
            preferredSubtitleLanguage = "es", preferredSubtitlesEnabled = true)
        val retry = gateway.playbackRequest(source, 123_000, capabilities, delivery = PlaybackDeliveryOptions(forceGateway = true))
        assertEquals(source.id, retry.getString("streamId"))
        assertEquals(native.getString("streamId"), retry.getString("streamId"))
        assertEquals(123.0, retry.getDouble("position"))
        assertFalse(native.getString("requestId") == retry.getString("requestId"))
        assertTrue(retry.getBoolean("forceGateway"))
        assertEquals("fr", native.getString("preferredAudioLanguage"))
        assertEquals("es", native.getString("preferredSubtitleLanguage"))
        assertEquals(2, retry.getJSONObject("client").getJSONObject("nativeTorrent").getInt("version"))
    }
}
