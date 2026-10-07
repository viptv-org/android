package org.viptv.app

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Identity bytes reach Rust before JSON/UTF-8 allocation. Responses retain cache accounting. */
internal class NativePlaybackResponse(
    val status: Int,
    bytes: ByteArray,
    private val settled: () -> Unit,
) : AutoCloseable {
    @Volatile private var bodyBytes: ByteArray? = bytes
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    val bytes: ByteArray get() = checkNotNull(bodyBytes)
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        bodyBytes = null
        settled()
    }
    override fun toString() = "NativePlaybackResponse(<redacted>)"
}

internal class NativePlaybackTransport(
    private val origin: String,
    private val bearer: () -> String?,
    private val currentScope: () -> Boolean,
    private val cache: NativeTorrentCache,
    private val refresh: (suspend (String?) -> String?)? = null,
    client: OkHttpClient = OkHttpClient(),
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<HttpWork>()
    private var retirementCredential: String? = null
    private var retirementCredentialCaptured = false
    @Synchronized fun captureRetirementCredential() {
        if (retirementCredentialCaptured) return
        retirementCredential = bearer()
        retirementCredentialCaptured = true
    }
    @Synchronized fun clearRetirementCredential() { retirementCredential = null }
    /** Only generated DELETE cleanup uses the retired scope's captured credential; it never refreshes. */
    suspend fun retirementRequest(path: String): NativePlaybackResponse {
        val token = synchronized(this) {
            if (!retirementCredentialCaptured) throw invalidScope()
            retirementCredential
        }
        return withTimeout(10_000) { exchange("DELETE", path, null, token, 4_096, 10_000, enforceCurrentScope = false) }
    }
    fun cancelActive() { active.forEach { it.cancel() } }
    fun remainingSettlementNanos(deadlineNanos: Long): Long = cache.remainingSettlementNanos(deadlineNanos)
    /** Run on the independent teardown IO worker after cancellation, before scope deletion. */
    fun joinLocal(deadlineNanos: Long): Boolean = try {
        active.toList().all { work ->
            work.join(deadlineNanos) && remainingSettlementNanos(deadlineNanos) > 0 &&
                cache.retire(work, deadlineNanos).also { if (it) active.remove(work) }
        } && remainingSettlementNanos(deadlineNanos) > 0
    } catch (_: Exception) { false }
    override fun toString() = "NativePlaybackTransport(<redacted>)"

    suspend fun request(method: String, path: String, body: ByteArray? = null, negotiation: Boolean = false): NativePlaybackResponse {
        if (!currentScope()) throw invalidScope()
        if (body != null && body.size > 16_384) throw invalidResponse()
        val deadline = if (negotiation) 5_000L else 10_000L
        val limit = if (negotiation) 4_096L else 6_291_456L
        var authorizationRefused = false
        try { return withTimeout(deadline) {
            val token = bearer()
            val first = exchange(method, path, body, token, limit, deadline)
            if (first.status == 401 && refresh != null) {
                authorizationRefused = true
                first.close()
                val renewed = try { refresh.invoke(token) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw GatewayError(401, "Your session is no longer authorized.", "unauthorized") }
                if (!currentScope()) throw invalidScope()
                if (renewed != null && renewed != token) return@withTimeout exchange(method, path, body, renewed, limit, deadline)
                throw GatewayError(401, "Your session is no longer authorized.", "unauthorized")
            }
            first
        } } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            if (authorizationRefused) throw GatewayError(401, "Your session is no longer authorized.", "unauthorized")
            throw NativePlaybackNetworkFailure("native_control_timeout")
        }
    }

    private suspend fun exchange(method: String, path: String, body: ByteArray?, token: String?, limit: Long, deadline: Long, enforceCurrentScope: Boolean = true): NativePlaybackResponse {
        val request = try {
            Request.Builder().url(origin.trimEnd('/') + "/api" + path)
                .header("Accept", "application/json").header("Accept-Encoding", "identity")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .method(method, body?.toRequestBody(JSON_TYPE)
                    ?: if (method in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody(null) else null).build()
        } catch (_: Exception) { throw invalidResponse() }
        // Okio's bounded buffer and the returned byte array can overlap briefly.
        val reservation = cache.reserveControl(2 * limit + 16_384)
        val work = HttpWork(cache::remainingSettlementNanos)
        try { cache.register(work, reservation) } catch (error: Exception) { reservation.close(); throw error }
        active.add(work)
        try {
            val response = suspendCancellableCoroutine<NativePlaybackResponse> { continuation ->
                val call = client.newCall(request)
                // The coroutine owns the earlier request deadline and its typed fact.
                // Keep a later transport backstop so competing timers cannot flatten it.
                call.timeout().timeout(deadline + 1_000, TimeUnit.MILLISECONDS)
                work.attach(call)
                continuation.invokeOnCancellation { work.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        work.finished.countDown()
                        work.consumed.countDown()
                        if (continuation.isActive) continuation.resumeWithException(nativePlaybackNetworkFailure(e))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        var result: NativePlaybackResponse? = null
                        var failure: Exception? = null
                        response.use {
                            try {
                                if (it.code in 300..399 || !it.header("Content-Encoding", "identity").equals("identity", true)) throw invalidResponse()
                                val source = it.body?.source() ?: throw invalidResponse()
                                if (it.body!!.contentLength() > limit || source.request(limit + 1)) throw invalidResponse()
                                val bytes = source.readByteArray()
                                if (enforceCurrentScope && !currentScope()) throw invalidScope()
                                result = NativePlaybackResponse(it.code, bytes) { work.consumed.countDown(); retire(work) }
                            } catch (error: IOException) { failure = nativePlaybackNetworkFailure(error) }
                            catch (_: Exception) { failure = invalidResponse() }
                        }
                        work.finished.countDown()
                        if (result == null || !continuation.isActive) work.consumed.countDown()
                        if (continuation.isActive) {
                            if (result != null) continuation.resume(result!!) { _, value, _ -> value.close() }
                            else continuation.resumeWithException(failure ?: invalidResponse())
                        }
                    }
                })
            }
            return response
        } catch (error: Exception) {
            // Cancellation is not joined settlement until the OkHttp body callback closes.
            work.consumed.countDown()
            withContext(NonCancellable + Dispatchers.IO) { retire(work) }
            throw error
        }
    }

    private fun retire(work: HttpWork) {
        if (!cache.retire(work)) throw NativeTorrentCacheUnavailable()
        active.remove(work)
    }

    private class HttpWork(private val remainingNanos: (Long) -> Long) : NativeTorrentCacheWork {
        val finished = CountDownLatch(1)
        val consumed = CountDownLatch(1)
        private var call: Call? = null
        private var cancelled = false
        @Synchronized fun attach(value: Call) { call = value; if (cancelled) value.cancel() }
        override fun preventReads() = cancel()
        @Synchronized override fun cancel() { cancelled = true; call?.cancel() }
        override fun join(deadlineNanos: Long): Boolean = finished.await(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS) &&
            consumed.await(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)
        override fun closeAfterSettlement() { check(finished.count == 0L && consumed.count == 0L) }
    }
    private fun invalidScope() = GatewayError(409, "This playback request is no longer current.", "playback_expired")
    private fun invalidResponse() = GatewayError(502, "The server returned an invalid playback response.", "invalid_playback_response")
    companion object { private val JSON_TYPE = "application/json; charset=utf-8".toMediaType() }
}
