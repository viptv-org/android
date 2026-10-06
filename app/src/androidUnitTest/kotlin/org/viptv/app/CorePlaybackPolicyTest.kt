package org.viptv.app

import java.io.IOException
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.LivePageValidationDecision
import org.viptv.core.wire.PlaybackLease
import org.viptv.core.wire.PlaybackLeaseDecision
import uniffi.viptv_core.normalize
import kotlin.test.*

/** These adapters exercise the real native normalizer, not a Kotlin policy substitute. */
class CorePlaybackPolicyTest {
    @Test fun `title projection keeps gateway direct processing managed and trusts anchored error coordinates`() {
        val managed = CorePlaybackPolicy.timeline(deliveryMode = "managed", launchPositionMillis = 42_000,
            segmentPositionMillis = 3_000, titleOffsetMillis = 42_000, titlePositionMillis = 47_000,
            nativeDurationMillis = 78_000, titleDurationMillis = 120_000)
        assertEquals(42_000L, managed.launchOffsetMillis)
        assertEquals(45_000L, managed.positionMillis)
        assertEquals(5_000L, managed.segmentPositionMillis)
        assertEquals(120_000L, managed.durationMillis)
        assertEquals(78_000L, CorePlaybackPolicy.timeline(deliveryMode = "direct", nativeDurationMillis = 78_000, titleDurationMillis = 120_000).durationMillis)
        val error = CorePlaybackPolicy.timeline(segmentPositionMillis = 10_000, titleOffsetMillis = 42_000,
            playerError = true, trustedPositionMillis = 44_000)
        assertEquals(44_000L, error.positionMillis)
        assertFalse(error.updateTrustedPosition)
        assertEquals(43_000L, CorePlaybackPolicy.timeline(segmentPositionMillis = 10_000, titleOffsetMillis = 42_000,
            playerError = true, trustedPositionMillis = 44_000, pauseAnchorMillis = 43_000).positionMillis)
    }

    @Test fun `paused managed seek anchors replacement and live never acquires vod pause anchor`() {
        val decision = CorePlaybackPolicy.pause("managed", anchor = 42_000, launch = 55_000, playWhenReady = false)
        assertTrue(decision.usesAnchor)
        assertTrue(decision.replaceOnResume)
        assertEquals(55_000L, decision.anchorAfterOpenMillis)
        assertFalse(CorePlaybackPolicy.pause("direct", anchor = 42_000).replaceOnResume)
        assertFalse(CorePlaybackPolicy.pause("managed", live = true).usesAnchor)
        assertFalse(CorePlaybackPolicy.recovery(true, true, true))
        assertTrue(CorePlaybackPolicy.recovery(true, true, false))
    }

    @Test fun `seek preview clamps dvr and suppresses noops without committing`() {
        assertEquals(10_000L, CorePlaybackPolicy.seekPreview(12_000, -15_000, null, 10_000, 20_000))
        assertEquals(20_000L, CorePlaybackPolicy.seekPreview(12_000, 50_000, null, 10_000, 20_000))
        assertNull(CorePlaybackPolicy.seekPreview(12_000, 499, null, 10_000, 20_000))
        assertNull(CorePlaybackPolicy.seekPreview(12_000, 5_000, null, null, null))
        assertFalse(CorePlaybackPolicy.managedReplacement("DIRECT"))
        assertTrue(CorePlaybackPolicy.managedReplacement("managed"))
    }

    @Test fun `delivery compatibility preserves force gateway and conversion authority`() {
        assertTrue(CorePlaybackPolicy.deliveryCompatible(true, true, false, true))
        assertFalse(CorePlaybackPolicy.deliveryCompatible(true, false, false, true))
        assertFalse(CorePlaybackPolicy.deliveryCompatible(true, true, true, true))
        assertFalse(CorePlaybackPolicy.deliveryCompatible(true, true, false, false))
        assertTrue(CorePlaybackPolicy.deliveryCompatible(false, false, true, false))
    }

    @Test fun `authority and retry classification do not invent expiry or escalate delivery`() {
        val budget = CorePlaybackPolicy.authority(100_000, 20_000, 55_000, 60_000, 20_000)
        assertEquals(5_000L, budget.remainingMillis)
        assertEquals(5_000L, budget.delayMillis)
        assertEquals(500L, CorePlaybackPolicy.renewalDelay(500, 20_000))
        assertFalse(CorePlaybackPolicy.failure(GatewayError(409, "Refused")).retryRenewal)
        assertFalse(CorePlaybackPolicy.failure(GatewayError(409, "Refused")).reconcileAndRelease)
        assertTrue(CorePlaybackPolicy.failure(GatewayError(409, "Refused"), hasLeaseId = true).reconcileAndRelease)
        assertTrue(CorePlaybackPolicy.failure(GatewayError(408, "Timeout")).retryRenewal)
        assertTrue(CorePlaybackPolicy.failure(IOException()).retryRenewal)
        assertFalse(CorePlaybackPolicy.failure(GatewayError(502, "Invalid", "invalid_playback_response")).retryRenewal)
    }

    @Test fun `lease id and heartbeat transport equality are checked without serializing urls into policy facts`() {
        val raw = JSONObject(v2Ready("session", """{"kind":"direct","url":"http://provider.test/video.mp4","headers":{},"format":"original","position":0,"live":false}"""))
        val lease = CoreJson.decode<PlaybackLease>(normalize("playbackV2", raw.toString(), "https://backend.test"))
        assertEquals(PlaybackLeaseDecision.READY, CorePlaybackPolicy.lease(lease, "session", 0))
        assertEquals(PlaybackLeaseDecision.INVALID, CorePlaybackPolicy.lease(lease, "another", 0))
        assertEquals(PlaybackLeaseDecision.EXPIRED, CorePlaybackPolicy.lease(lease, "session", lease.expiresAt.toLong()))
        val altered = JSONObject(raw.toString())
        altered.getJSONObject("delivery").put("url", "http://provider.test/other.mp4")
        val changed = CoreJson.decode<PlaybackLease>(normalize("playbackV2", altered.toString(), "https://backend.test"))
        assertEquals(PlaybackLeaseDecision.INVALID, CorePlaybackPolicy.lease(changed, "session", 0, heartbeat = true, previous = lease))
    }

    @Test fun `live validation retains scope cursor order overlap and empty initial page semantics`() {
        fun page(ids: List<String> = listOf("z", "a"), next: String? = "opaque_next", previous: Boolean = false,
            cursor: String? = null, known: List<String> = emptyList(), snapshot: String? = "0") =
            CorePlaybackPolicy.livePage("2", "0", ids, ids.map { "Name" }, next, "opaque_previous",
                checkSnapshot = true, snapshotCatalogId = "2", snapshotGeneration = snapshot,
                knownIds = known, cursor = cursor, previous = previous)
        assertEquals(LivePageValidationDecision.VALID, page())
        assertEquals(LivePageValidationDecision.INVALID, page(listOf("a", "a")))
        assertEquals(LivePageValidationDecision.CATALOG_CHANGED, page(snapshot = "1"))
        assertEquals(LivePageValidationDecision.INVALID, page(cursor = "opaque_request", known = listOf("a")))
        assertEquals(LivePageValidationDecision.INVALID, page(cursor = "opaque_next"))
        assertEquals(LivePageValidationDecision.VALID, page(cursor = "opaque_next", previous = true))
        assertEquals(LivePageValidationDecision.VALID, CorePlaybackPolicy.livePage(null, null, emptyList(), emptyList(), null, null))
    }
}
