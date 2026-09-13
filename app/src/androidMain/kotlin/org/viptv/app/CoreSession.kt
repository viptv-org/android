package org.viptv.app

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.ViewModel
import tv.viptv.core.HttpTransport
import uniffi.viptv_core.CoreBridge
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Android executes effects; Rust owns restoration, refresh, profile acceptance and revocation. */
internal class CoreSession(
    private val origin: String,
    private val store: SharedPreferences,
    scope: CoroutineScope,
    private val credentialsChanged: (String?) -> Unit,
    private val render: suspend (ViewModel) -> Unit,
) {
    private val effectsJob = Job(scope.coroutineContext[Job])
    private val effectScope = CoroutineScope(scope.coroutineContext + effectsJob)
    private val core = CoreBridge()
    private val executor = Executors.newFixedThreadPool(3)
    private val transport = HttpTransport(setOf(origin), executor, maxResponseBytes = 2 * 1024 * 1024)
    private var lastView: String? = null
    private val calls = mutableSetOf<HttpTransport.Call>()
    private var closed = false
    fun close() { closed = true; effectsJob.cancel(); calls.toList().forEach { it.cancel() }; executor.shutdownNow(); core.close() }
    fun begin() = dispatch(JSONObject().put("Begin", JSONObject().put("origin", origin).put("allowInsecurePreview", false)))
    fun retry() = dispatch("Retry")
    fun select(profileId: String) = dispatch(JSONObject().put("SelectProfile", JSONObject().put("profileId", profileId)))
    fun signOut() = dispatch("SignOut")
    fun adopt(tokens: String) = dispatch(JSONObject().put("AdoptSession", JSONObject().put("tokensJson", tokens)))
    private fun dispatch(event: Any) {
        if (closed) return
        calls.toList().forEach { it.cancel() }
        effectScope.launch {
            try { drain(core.update(if (event is String) JSONObject.quote(event) else event.toString())) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!closed) render(ViewModel(org.viptv.core.wire.Phase.ERROR, error = "Could not restore your session. Try again.")) }
        }
    }
    private suspend fun drain(output: String) {
        if (closed) return
        val effects = JSONArray(output)
        for (index in 0 until effects.length()) {
            val request = effects.getJSONObject(index)
            val id = request.getLong("id").toUInt()
            val effect = request.getJSONObject("effect")
            when {
                effect.has("Render") -> {
                    val view = core.view()
                    if (view != lastView) { lastView = view; render(CoreJson.decode<ViewModel>(view)) }
                }
                effect.has("Storage") -> {
                    val operation = effect.get("Storage")
                    val result = try {
                        val loaded = when (operation) {
                            "Load" -> savedSession()
                            "Clear" -> { check(store.edit().remove("core.session").remove("access").remove("refresh").commit()); credentialsChanged(null); null }
                            else -> {
                                val tokens = (operation as JSONObject).getString("Save")
                                val session = JSONObject(tokens)
                                check(store.edit().putString("core.session", tokens).putString("access", session.getString("accessToken")).putString("refresh", session.getString("refreshToken")).commit())
                                credentialsChanged(session.getString("accessToken")); null
                            }
                        }
                        JSONObject().put("Ok", loaded ?: JSONObject.NULL)
                    } catch (_: Exception) { JSONObject().put("Err", "Secure storage operation failed") }
                    drain(core.resolve(id, result.toString()))
                }
                effect.has("Http") -> effectScope.launch {
                    var activeCall: HttpTransport.Call? = null
                    val result = try { suspendCancellableCoroutine<JSONObject> { continuation ->
                        val call = transport.execute(effect.getJSONObject("Http")) { result ->
                            if (continuation.isActive) continuation.resume(result)
                        }
                        activeCall = call
                        calls.add(call)
                        continuation.invokeOnCancellation { call.cancel() }
                    }
                    } finally { calls.remove(activeCall) }
                    if (!closed) {
                        try { drain(core.resolve(id, result.toString())) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (!closed) render(ViewModel(org.viptv.core.wire.Phase.ERROR, error = "Could not restore your session. Try again.")) }
                    }
                }
            }
        }
    }
    private fun savedSession(): String? {
        store.getString("core.session", null)?.let { return it }
        // One-time migration retains the existing Android grant and profile-scoped history.
        val refresh = store.getString("refresh", null) ?: return null
        return JSONObject().put("sessionId", "").put("accountId", "")
            .put("accessToken", store.getString("access", "")).put("refreshToken", refresh)
            .put("profileId", JSONObject.NULL).put("expiresIn", 0).toString()
    }
}
