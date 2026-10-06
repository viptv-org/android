package org.viptv.app

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.PlaybackDeliveryKind
import org.viptv.core.wire.PlaybackLease
import org.viptv.core.wire.PlaybackLeaseDecision
import java.util.concurrent.ConcurrentHashMap
import uniffi.viptv_core.normalize

/** Control requests stay on the backend. Delivery URLs/headers are never logged. */
internal class V2PlaybackControl(
    private val origin: String,
    private val send: suspend (String, String, JSONObject?) -> JSONObject,
) {
    private class Entry(val lease: PlaybackLease, val received: Long = System.nanoTime())
    private val leases = ConcurrentHashMap<String, Entry>()

    fun remainingMillis(id: String): Long? = leases[id]?.let {
        CorePlaybackPolicy.authority(it.lease.expiresAt.toLong(), System.currentTimeMillis(),
            (System.nanoTime() - it.received) / 1_000_000, observationCapMillis = 60_000L).remainingMillis
    }
    fun renewAfterMillis(id: String): Long? = leases[id]?.lease?.renewAfterSeconds?.times(1000)

    private suspend fun control(operation: String, id: String? = null, request: JSONObject? = null): JSONObject {
        val input = JSONObject().put("operation", operation).putOpt("id", id).putOpt("playback", request)
        val wire = JSONObject(normalize("request", input.toString(), origin))
        return send(wire.getString("method"), wire.getString("path").removePrefix("/api"), wire.optJSONObject("body"))
    }
    private fun identity(value: JSONObject): String {
        val id = value.opt("id") as? String ?: throw invalid()
        try { normalize("request", JSONObject().put("operation", "playbackV2Stop").put("id", id).toString(), origin) }
        catch (_: Exception) { throw invalid() }
        return id
    }
    private fun decode(value: JSONObject): PlaybackLease {
        val lease = try { CoreJson.decode<PlaybackLease>(normalize("playbackV2", value.toString(), origin)) }
        catch (_: Exception) { throw invalid() }
        return lease
    }
    private fun ready(lease: PlaybackLease, id: String, heartbeat: Boolean = false, previous: PlaybackLease? = null): Boolean =
        when (CorePlaybackPolicy.lease(lease, id, System.currentTimeMillis(), heartbeat, previous)) {
            PlaybackLeaseDecision.INVALID -> throw invalid()
            PlaybackLeaseDecision.TERMINAL -> throw GatewayError(409, lease.error ?: "This playback session is no longer available. Start playback again.", lease.errorCode ?: "playback_expired")
            PlaybackLeaseDecision.EXPIRED -> throw GatewayError(410, "This playback session has expired. Start playback again.", "playback_expired")
            PlaybackLeaseDecision.PENDING -> false
            PlaybackLeaseDecision.READY -> true
        }
    suspend fun start(input: JSONObject): PlaybackLaunch {
        val request = JSONObject(input.toString())
        try { normalize("request", JSONObject().put("operation", "playbackV2").put("playback", request).toString(), origin) }
        catch (_: Exception) { throw invalid() }
        currentCoroutineContext().ensureActive()
        var id: String? = null
        try {
            return withTimeout(45_000) {
                val response = control("playbackV2", request = request)
                val owned = identity(response).also { id = it }
                currentCoroutineContext().ensureActive()
                var lease = decode(response)
                while (!ready(lease, owned)) {
                    delay(500)
                    val status = control("playbackV2Status", owned)
                    currentCoroutineContext().ensureActive()
                    lease = decode(status)
                }
                val session = lease.session!!
                if (!CorePlaybackPolicy.deliveryCompatible(session.deliveryKind == PlaybackDeliveryKind.DIRECT,
                    request.getJSONObject("client").getBoolean("canPlayDirect"), request.optBoolean("forceGateway"),
                    request.optString("conversion", "auto") == "auto")) throw invalid()
                leases[lease.id] = Entry(lease)
                CoreModels.playbackNormalized(session)
            }
        } catch (error: Exception) {
            if (CorePlaybackPolicy.failure(error, hasLeaseId = id != null).reconcileAndRelease) withContext(NonCancellable) {
                withTimeoutOrNull(5_000) {
                    try {
                        val owned = id ?: identity(control("playbackV2", request = request))
                        control("playbackV2Stop", owned)
                    } catch (_: Exception) { /* Remote lease expiry is the final bound. */ }
                }
            }
            id?.let(leases::remove)
            currentCoroutineContext().ensureActive()
            if (error is TimeoutCancellationException) {
                throw GatewayError(408, "Playback preparation timed out. Try again or choose another source.", "playback_start_timeout")
            }
            throw error
        }
    }
    suspend fun renew(id: String) {
        val previous = leases[id]
        val next = decode(control("playbackV2Heartbeat", id))
        if (!ready(next, id, heartbeat = true, previous = previous?.lease)) throw invalid()
        if (previous != null) {
            leases.replace(id, previous, Entry(next))
        }
    }
    suspend fun stop(id: String) {
        try { control("playbackV2Stop", id) }
        finally { leases.remove(id) }
    }
    private fun invalid() = GatewayError(502, "The server returned an invalid playback session.", "invalid_playback_response")
}
