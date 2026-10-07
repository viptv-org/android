package org.viptv.app

import android.os.SystemClock
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.NativeTorrentState
import uniffi.viptv_core.NativeTorrentBridge
import uniffi.viptv_core.normalize

/** Suspend-aware Android facts; device wall-clock time never establishes native authority. */
internal fun interface NativePlaybackClock { fun elapsedMillis(): Long? }
internal val androidNativePlaybackClock = NativePlaybackClock { SystemClock.elapsedRealtime() }

internal sealed interface NativePlaybackStart {
    class Native(val control: NativePlaybackControl) : NativePlaybackStart
    class Legacy(val launch: PlaybackLaunch) : NativePlaybackStart
}

/** Private control lifetime survives player preparation/pause; UI receives only safe facts. */
internal class NativePlaybackControl(
    private val origin: String,
    private val scope: String,
    private val generation: Long,
    private val currentScope: () -> Boolean,
    private val transport: NativePlaybackTransport,
    private val legacy: V2PlaybackControl,
    private val jobs: CoroutineScope,
    private val clock: NativePlaybackClock = androidNativePlaybackClock,
    private val preventReads: () -> Unit,
    private val invalidated: () -> Unit,
    private val released: () -> Unit = {},
) {
    private val serial = Mutex()
    private val releaseSerial = Mutex()
    private var remoteReleased = false
    private var bridge: NativeTorrentBridge? = null
    private var sequence = 0L
    private var requestId: String? = null
    private var ownedPlaybackId: String? = null
    @Volatile private var retired = false
    @Volatile private var authorizationRefused = false
    @Volatile private var selectionRefused = false
    @Volatile private var revalidated = true
    @Volatile private var backgroundRevision = 0L
    private var anchorElapsed: Long? = null
    private var anchorWall: Long? = null
    @Volatile private var firstGrantReceipt: Long? = null
    private var heartbeat: Job? = null
    private var expiry: Job? = null
    private var heartbeatFinished: java.util.concurrent.CountDownLatch? = null
    private var expiryFinished: java.util.concurrent.CountDownLatch? = null
    private var retainedInput: NativeTorrentControlReservation? = null
    override fun toString() = "NativePlaybackControl(<redacted>)"

    /** Negotiation is repeated for every qualified start; stale origin/auth results never admit. */
    suspend fun start(input: JSONObject, qualified: Boolean, vod: Boolean, cache: NativeTorrentCache): NativePlaybackStart {
        requireCurrent()
        val request = JSONObject(input.toString())
        request.getJSONObject("client").remove("nativeTorrent")
        if (!qualified) { retireLocal(notify = false); return NativePlaybackStart.Legacy(legacy.start(request)) }
        val negotiation = JSONObject(normalize("request", JSONObject().put("operation", "playbackProtocolV2").toString(), origin))
        val protocol = try { transport.request(negotiation.getString("method"), negotiation.getString("path").removePrefix("/api"), negotiation = true) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (error is GatewayError && error.status in listOf(401, 403)) throw error
            null
        }
        val decision = protocol?.use {
            // Negotiation contains no private grant. UTF-8 decoding remains strict.
            val text = try { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(it.bytes)).toString() } catch (_: Exception) { "" }
            normalize("nativeTorrent", JSONObject().put("operation", "negotiation")
                .put("platform", request.getJSONObject("client").getString("platform"))
                .put("qualified", qualified).put("scopeMatches", currentScope())
                .put("status", it.status).put("authorizationRefused", it.status in listOf(401, 403))
                .put("body", text).toString(), "").trim('"')
        } ?: "legacy"
        if (!currentScope()) throw expired()
        when (decision) {
            "authRecovery" -> throw GatewayError(403, "Your session is no longer authorized.", "unauthorized")
            "rejectStale" -> throw expired()
            "advertise" -> Unit
            else -> { retireLocal(notify = false); return NativePlaybackStart.Legacy(legacy.start(request)) }
        }
        request.getJSONObject("client").put("nativeTorrent", JSONObject().put("version", 1).put("networkPolicy", "public_dht_tcp_v1"))
        requestId = request.getString("requestId")
        // Retained input stays accounted independently of transient response/Okio buffers.
        retainedInput = cache.reserveControl(6_291_456)
        try {
            bridge = NativeTorrentBridge(JSONObject().put("origin", origin).put("scope", scope)
                .put("generation", generation).put("qualified", qualified).put("negotiated", true)
                .put("vod", vod).put("request", request).toString())
            return withTimeout<NativePlaybackStart>(45_000) {
                var response = request("playbackV2", request = request)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val state = state()
                    when (state.status) {
                        "legacy" -> {
                            val value = response ?: throw invalid()
                            retireLocal(notify = false)
                            val adopted = legacy.start(request, JSONObject(value))
                            // V2 now owns the ordinary lease; native-local cleanup must not release it.
                            ownedPlaybackId = null
                            requestId = null
                            return@withTimeout NativePlaybackStart.Legacy(adopted)
                        }
                        "ready" -> return@withTimeout NativePlaybackStart.Native(this@NativePlaybackControl)
                        "starting" -> {
                            startEffects()
                            delay(500)
                            response = request("playbackV2Status")
                        }
                        else -> throw expired()
                    }
                }
                @Suppress("UNREACHABLE_CODE") throw invalid()
            }
        } catch (error: Exception) {
            retireLocal()
            withContext(NonCancellable) { withTimeoutOrNull(10_000) { runCatching { releaseRemote() } } }
            throw error
        }
    }

    private fun startEffects() {
        if (heartbeat != null) return
        val heartbeatCompletion = java.util.concurrent.CountDownLatch(1).also { heartbeatFinished = it }
        heartbeat = jobs.launch {
            try {
                var next = Math.addExact(elapsed(), 20_000)
                while (!retired) {
                    delay((next - elapsed()).coerceAtLeast(0))
                    next = Math.addExact(elapsed(), 20_000)
                    try { renew() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: IOException) { /* The last accepted deadline remains authoritative. */ }
                    catch (_: Exception) { retireLocal() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { retireLocal() }
        }.also { it.invokeOnCompletion { heartbeatCompletion.countDown() } }
        val expiryCompletion = java.util.concurrent.CountDownLatch(1).also { expiryFinished = it }
        expiry = jobs.launch {
            while (!retired) {
                delay(100)
                if (state().status == "ready") runCatching { authorize(requireForeground = false) }.onFailure { retireLocal() }
            }
        }.also { it.invokeOnCompletion { expiryCompletion.countDown() } }
    }

    /** Serializing control responses fences polls against successful renewal. */
    private suspend fun request(operation: String, request: JSONObject? = null): String? = serial.withLock {
        requireCurrent()
        val holder = bridge ?: throw invalid()
        val id = if (operation == "playbackV2") null else holder.playbackId() ?: throw invalid()
        val wire = JSONObject(normalize("request", JSONObject().put("operation", operation)
            .putOpt("id", id).putOpt("playback", request).toString(), origin))
        val sent = elapsed()
        transport.request(wire.getString("method"), wire.getString("path").removePrefix("/api"),
            wire.optJSONObject("body")?.toString()?.toByteArray(Charsets.UTF_8)).use { response ->
            synchronized(this) {
                requireCurrent()
                // Receipt sampling and adoption share the read guard; a concurrent read's
                // elapsed sample cannot overtake this response before Rust sees it.
                val received = elapsed()
                val rtt = Math.subtractExact(received, sent).takeIf { it >= 0 } ?: throw invalid()
                val observation = JSONObject().put("scope", scope).put("generation", generation).put("sequence", ++sequence)
                    .put("operation", when (operation) { "playbackV2" -> "start"; "playbackV2Heartbeat" -> "heartbeat"; else -> "poll" })
                    .put("receivedAtMillis", received).put("roundTripMillis", rtt)
                    .put("uncertaintyMillis", 0).put("maxUncertaintyMillis", 0)
                    .put("trustedWallUpperUnixMillis", JSONObject.NULL).put("suspendAware", true)
                try {
                    holder.acceptMeasuredBytes(response.status.toUShort(), response.bytes, observation.toString())
                } catch (_: Exception) {
                    if (response.status in listOf(401, 403)) authorizationRefused = true else selectionRefused = true
                    retireLocal()
                    if (response.status in listOf(401, 403)) throw GatewayError(response.status, "Your session is no longer authorized.", "unauthorized")
                    throw invalid()
                }
                anchorWall = holder.trustedWallUpperUnixMillis()?.toLong()
                anchorElapsed = received
                ownedPlaybackId = holder.playbackId()
                if (anchorWall != null && firstGrantReceipt == null) firstGrantReceipt = received
            }
            val status = state().status
            if (status == "ready") startEffects()
            if (status in listOf("failed", "expired", "released", "invalidated")) {
                if (status == "failed") selectionRefused = true
                retireLocal()
                throw expired()
            }
            // Private bodies are never allocated as JSONObject or generic UI events.
            if (status == "legacy") response.bytes.toString(Charsets.UTF_8) else null
        }
    }

    suspend fun renew() {
        try { request("playbackV2Heartbeat") }
        catch (error: Exception) {
            if (error is GatewayError && error.status in listOf(401, 403)) {
                authorizationRefused = true
                retireLocal()
            }
            throw error
        }
    }
    suspend fun poll() { request("playbackV2Status") }

    @Synchronized fun background() {
        revalidated = false
        backgroundRevision++
        preventReads()
    }

    suspend fun foreground() {
        val revision = backgroundRevision
        // Expiry cannot be revived by a late successful foreground heartbeat.
        authorize(requireForeground = false)
        try { renew() } catch (error: Exception) { retireLocal(); throw error }
        requireCurrent()
        synchronized(this) {
            if (revision != backgroundRevision) throw expired()
            revalidated = true
        }
        authorize()
    }

    fun state(): NativeTorrentState = try { CoreJson.decode(requireNotNull(bridge).state()) } catch (_: Exception) { throw invalid() }

    /** Every native read/resume calls this; private getters receive this same current clock. */
    @Synchronized fun authorize(requireForeground: Boolean = true): String = try {
        requireCurrent()
        val now = elapsed()
        val base = anchorElapsed ?: throw invalid()
        val advanced = Math.subtractExact(now, base).takeIf { it >= 0 } ?: throw invalid()
        val wall = Math.addExact(anchorWall ?: throw invalid(), advanced)
        val facts = JSONObject().put("scope", scope).put("generation", generation).put("nowMillis", now)
            .put("trustedWallUpperUnixMillis", wall).put("backendRevalidated", !requireForeground || revalidated)
            .put("suspendAware", true).toString()
        try { bridge!!.authorize(facts) } catch (_: Exception) {
            if (!revalidated && requireForeground) { preventReads(); throw expired() }
            retireLocal(); throw expired()
        }
        facts
    } catch (error: Exception) {
        if (revalidated || !requireForeground) retireLocal()
        throw error
    }

    fun privateBridge(): NativeTorrentBridge { authorize(); return requireNotNull(bridge) }
    fun firstGrantAcceptedAtMillis(): Long? = firstGrantReceipt
    fun hasNativeAdmission(): Boolean = firstGrantReceipt != null
    fun isLocallyRetired(): Boolean = retired
    fun authorizationWasRefused(): Boolean = authorizationRefused
    fun selectionWasRefused(): Boolean = selectionRefused
    fun playbackId(): String? = ownedPlaybackId
    fun remainingMillis(): Long? = try { authorize(); state().deadlineMillis?.minus(elapsed()) } catch (_: Exception) { null }
    fun retireLocal(notify: Boolean = true) {
        synchronized(this) {
            if (retired) {
                transport.cancelActive()
                heartbeat?.cancel()
                expiry?.cancel()
                return
            }
            retired = true
            transport.captureRetirementCredential()
            preventReads()
            bridge?.invalidate()
            transport.cancelActive()
            heartbeat?.cancel()
            expiry?.cancel()
            retainedInput?.close()
            retainedInput = null
            released()
            if (notify) invalidated()
        }
    }
    suspend fun stop() {
        retireLocal()
        withContext(NonCancellable) {
            heartbeat?.join(); expiry?.join()
            serial.withLock { }
            withTimeoutOrNull(10_000) { runCatching { releaseRemote() } }
            closeAfterSettlement()
        }
    }
    /** Independent teardown IO worker joins local authority; remote cleanup is separately bounded. */
    fun joinLocal(deadlineNanos: Long): Boolean {
        if (!retired || !transport.joinLocal(deadlineNanos)) return false
        return listOfNotNull(heartbeatFinished, expiryFinished).all {
            it.await(transport.remainingSettlementNanos(deadlineNanos), java.util.concurrent.TimeUnit.NANOSECONDS)
        } && transport.remainingSettlementNanos(deadlineNanos) > 0
    }
    @Synchronized fun closeAfterSettlement() {
        check(retired)
        bridge?.close()
        bridge = null
        transport.clearRetirementCredential()
    }
    suspend fun releaseRemote() = releaseSerial.withLock {
        if (remoteReleased) return@withLock
        val id = ownedPlaybackId
        val operation = if (id != null) "playbackV2Stop" else "playbackV2CancelRequest"
        val wire = JSONObject(normalize("request", JSONObject().put("operation", operation)
            .put(if (id != null) "id" else "requestId", id ?: requestId ?: return@withLock).toString(), origin))
        transport.retirementRequest(wire.getString("path").removePrefix("/api")).use {
            if (it.status != 200) throw invalid()
            normalize("nativeTorrent", JSONObject().put("operation", "releaseResponse")
                .put("body", Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(it.bytes)).toString()).toString(), "")
            remoteReleased = true
        }
    }
    private fun requireCurrent() { if (retired || !currentScope()) { retireLocal(); throw expired() } }
    private fun elapsed(): Long = clock.elapsedMillis()?.takeIf { it >= 0 } ?: throw invalid()
    private fun expired() = GatewayError(410, "This playback session has expired. Start playback again.", "playback_expired")
    private fun invalid() = GatewayError(502, "The server returned an invalid playback session.", "invalid_playback_response")
}
