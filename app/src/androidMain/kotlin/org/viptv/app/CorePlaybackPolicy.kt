package org.viptv.app

import org.json.JSONArray
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.LivePageValidationDecision
import org.viptv.core.wire.PlaybackAuthorityBudget
import org.viptv.core.wire.PlaybackFailureDecision
import org.viptv.core.wire.PlaybackLease
import org.viptv.core.wire.PlaybackLeaseDecision
import org.viptv.core.wire.PlaybackPauseDecision
import org.viptv.core.wire.PlaybackTimelineProjection
import uniffi.viptv_core.normalize
import java.io.IOException

/** Transport-free facts only. Native code retains clocks, session fences, effects and cleanup. */
internal object CorePlaybackPolicy {
    private fun input(operation: String) = JSONObject().put("operation", operation)
    private fun wire(input: JSONObject): String = normalize("playbackControl", input.toString(), "")

    // Multiple UI/progress consumers can project the same observation without repeated JNI calls.
    // The bounded cache holds only coordinate/duration facts, never session transport or credentials.
    private val timelines = object : LinkedHashMap<String, PlaybackTimelineProjection>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlaybackTimelineProjection>): Boolean = size > 16
    }

    @Synchronized
    fun timeline(
        deliveryMode: String = "managed",
        launchPositionMillis: Long = 0,
        segmentPositionMillis: Long = 0,
        titleOffsetMillis: Long = 0,
        titlePositionMillis: Long = 0,
        nativeDurationMillis: Long? = null,
        titleDurationMillis: Long? = null,
        pauseAnchorMillis: Long? = null,
        playerError: Boolean = false,
        trustedPositionMillis: Long = 0,
    ): PlaybackTimelineProjection {
        val facts = input("timeline").put("deliveryMode", deliveryMode).put("launchPositionMillis", launchPositionMillis)
            .put("segmentPositionMillis", segmentPositionMillis).put("titleOffsetMillis", titleOffsetMillis)
            .put("titlePositionMillis", titlePositionMillis).putOpt("nativeDurationMillis", nativeDurationMillis)
            .putOpt("titleDurationMillis", titleDurationMillis).putOpt("pauseAnchorMillis", pauseAnchorMillis)
            .put("playerError", playerError).put("trustedPositionMillis", if (playerError) trustedPositionMillis else 0)
        val key = facts.toString()
        return timelines.getOrPut(key) { CoreJson.decode(wire(facts)) }
    }

    fun seekPreview(current: Long, delta: Long, duration: Long?, start: Long?, end: Long?): Long? {
        val result = CorePolicy.value("playbackControl", input("seekPreview").put("currentMillis", current).put("deltaMillis", delta)
            .putOpt("durationMillis", duration).putOpt("rangeStartMillis", start).putOpt("rangeEndMillis", end))
        return (result as? Number)?.toLong()
    }

    fun managedReplacement(deliveryMode: String): Boolean =
        CorePolicy.value("playbackControl", input("seekCommit").put("deliveryMode", deliveryMode)) == true

    fun pause(deliveryMode: String, live: Boolean = false, anchor: Long? = null, launch: Long = 0, playWhenReady: Boolean = true): PlaybackPauseDecision =
        CoreJson.decode(wire(input("pause").put("deliveryMode", deliveryMode).put("live", live).putOpt("anchorMillis", anchor)
            .put("launchPositionMillis", launch).put("playWhenReady", playWhenReady)))

    fun recovery(serverManaged: Boolean, networkFailure: Boolean, alreadyAttempted: Boolean): Boolean =
        CorePolicy.value("playbackControl", input("recovery").put("serverManaged", serverManaged).put("networkFailure", networkFailure)
            .put("alreadyAttempted", alreadyAttempted)) == true

    fun deliveryCompatible(direct: Boolean, canPlayDirect: Boolean, forceGateway: Boolean, automaticConversion: Boolean): Boolean =
        CorePolicy.value("playbackControl", input("delivery").put("directDelivery", direct).put("canPlayDirect", canPlayDirect)
            .put("forceGateway", forceGateway).put("automaticConversion", automaticConversion)) == true

    fun lease(lease: PlaybackLease, expectedId: String, nowMillis: Long, heartbeat: Boolean = false, previous: PlaybackLease? = null): PlaybackLeaseDecision =
        CoreJson.decode(wire(input("lease").put("expectedId", expectedId).put("actualId", lease.id)
            .put("status", lease.status.name.lowercase()).put("hasSession", lease.session != null)
            .put("expiresAtMillis", lease.expiresAt).put("nowMillis", nowMillis).put("heartbeat", heartbeat)
            .put("sameDeliveryUrl", previous == null || previous.session?.url == lease.session?.url)
            .put("sameDeliveryKind", previous == null || previous.session?.deliveryKind == lease.session?.deliveryKind)))

    fun authority(expiresAtMillis: Long, nowMillis: Long, elapsedMillis: Long, observationCapMillis: Long, waitMillis: Long = 0): PlaybackAuthorityBudget =
        CoreJson.decode(wire(input("authority").put("expiresAtMillis", expiresAtMillis).put("nowMillis", nowMillis)
            .put("elapsedMillis", elapsedMillis).put("observationCapMillis", observationCapMillis).put("waitMillis", waitMillis)))

    fun renewalDelay(remainingMillis: Long, waitMillis: Long): Long =
        authority(remainingMillis, 0, 0, remainingMillis, waitMillis).delayMillis

    fun failure(error: Exception, hasLeaseId: Boolean = false): PlaybackFailureDecision =
        CoreJson.decode(wire(input("failure").put("gatewayError", error is GatewayError).put("status", (error as? GatewayError)?.status ?: 0)
            .put("invalidResponse", (error as? GatewayError)?.code == "invalid_playback_response")
            .put("ioError", error is IOException).put("hasLeaseId", hasLeaseId)))

    fun livePage(
        catalogId: String?, generation: String?, ids: List<String>, names: List<String>, nextCursor: String?, previousCursor: String?,
        requestedCatalogId: String? = null, limit: Int = 200, categories: Boolean = true, checkSnapshot: Boolean = false,
        snapshotCatalogId: String? = null, snapshotGeneration: String? = null, knownIds: List<String> = emptyList(),
        cursor: String? = null, previous: Boolean = false, extendingWindow: Boolean = false,
    ): LivePageValidationDecision = CoreJson.decode(wire(input("livePage").putOpt("catalogId", catalogId).putOpt("generation", generation)
        .put("ids", JSONArray(ids)).put("names", JSONArray(names)).putOpt("nextCursor", nextCursor).putOpt("previousCursor", previousCursor)
        .putOpt("requestedCatalogId", requestedCatalogId).put("limit", limit).put("categories", categories).put("checkSnapshot", checkSnapshot)
        .putOpt("snapshotCatalogId", snapshotCatalogId).putOpt("snapshotGeneration", snapshotGeneration).put("knownIds", JSONArray(knownIds))
        .putOpt("cursor", cursor).put("previous", previous).put("extendingWindow", extendingWindow)))

    fun requireLivePage(decision: LivePageValidationDecision) {
        when (decision) {
            LivePageValidationDecision.VALID -> Unit
            LivePageValidationDecision.CATALOG_CHANGED -> throw GatewayError(409, "This playlist changed while you were browsing. Reload the guide.", "catalog_changed")
            LivePageValidationDecision.INVALID -> throw GatewayError(502, "The server repeated or interrupted a live playlist page. Reload the guide.", "invalid_catalog_response")
        }
    }
}
