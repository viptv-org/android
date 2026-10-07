package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackOptions
import org.viptv.video.PlaybackSource
import uniffi.viptv_core.normalize

/** Private controller effects: an admitted native delivery never becomes a generic launch. */
internal class NativePlaybackEffects(
    internal val coordinator: NativeTorrentCoordinator,
    private val jobs: CoroutineScope,
    private val stopPlayer: () -> Unit,
    private val invalidatedPlayer: () -> Unit,
) {
    internal sealed interface Prepared {
        class Native(val candidate: NativeTorrentCoordinator.Candidate) : Prepared
        class Legacy(val launch: PlaybackLaunch) : Prepared
    }
    private val controls = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<NativePlaybackControl, Boolean>())
    private val retirements = java.util.WeakHashMap<NativePlaybackControl, CompletableDeferred<Boolean>>()
    private var active: NativeTorrentCoordinator.Candidate? = null
    private var stopping = false
    private var closingScope = false
    private var replacingControl: NativePlaybackControl? = null
    private var failure: Recovery? = null
    val hasActive: Boolean get() = active != null
    fun resetRecovery() { failure = null }

    /** The control callback also runs before a handle exists; unrelated outgoing reads survive. */
    fun preventReads(control: NativePlaybackControl) {
        if (active?.control === control) stopPlayer()
        if (control.isLocallyRetired()) coordinator.cancelNative(control)
    }

    fun invalidated(control: NativePlaybackControl) {
        if (stopping || active?.control !== control || (replacingControl != null && replacingControl !== control)) return
        val receipt = Recovery(control, true, false, control.authorizationWasRefused(), control.selectionWasRefused())
        failure = receipt
        active = null
        jobs.launch { receipt.retired = retire(control) }
        invalidatedPlayer()
    }

    suspend fun prepare(control: NativePlaybackControl, request: JSONObject, generation: Long): Prepared {
        controls.add(control)
        return try {
            coordinator.ownControl(control, generation)
            when (val start = control.start(request, qualified = true, vod = true, cache = coordinator.cache)) {
                is NativePlaybackStart.Legacy -> {
                    if (!retire(control)) throw NativeTorrentCoordinatorUnavailable()
                    Prepared.Legacy(start.launch)
                }
                is NativePlaybackStart.Native -> Prepared.Native(coordinator.prepare(start.control, generation))
            }
        } catch (error: Exception) {
            recordFailure(control, error)
            throw error
        }
    }

    suspend fun accept(
        prepared: Prepared.Native,
        title: String,
        playWhenReady: Boolean,
        boundary: () -> Unit,
        open: suspend (PlaybackSource, Boolean) -> Unit,
    ): Long {
        val candidate = prepared.candidate
        replacingControl = candidate.control
        return try {
            var position = 0L
            coordinator.accept(candidate) { capability ->
                active = candidate
                boundary()
                val state = candidate.control.state()
                position = ((state.position ?: 0.0) * 1000).toLong()
                open(PlaybackSource(capability.url, headers = emptyMap(), title = title,
                    kindHint = PlaybackKind.OnDemand, startPositionMillis = position,
                    options = PlaybackOptions(preferredAudioLanguage = state.audioLanguage,
                        preferredSubtitleLanguage = state.subtitleLanguage, subtitlesEnabled = state.subtitlesEnabled)), playWhenReady)
            }
            failure = null
            position
        } catch (error: Exception) {
            if (active === candidate) active = null
            recordFailure(candidate.control, error)
            throw error
        } finally { replacingControl = null }
    }

    /** Explicit ordinary replacement retires native authority only at its own accepted open. */
    suspend fun retireOutgoing(): Boolean {
        val candidate = active ?: return true
        active = null
        val settled = retire(candidate.control)
        if (settled) failure = null
        return settled
    }

    fun beginScopeClose() { closingScope = true }

    fun stop() {
        stopping = true
        if (active != null) stopPlayer()
        active = null
        val snapshot = controls.toList()
        snapshot.forEach { it.retireLocal() }
        if (!closingScope) jobs.launch { try { snapshot.forEach { retire(it) } } finally { stopping = false } }
    }

    fun authorizeRead(): Boolean = try { active?.control?.authorize(); true } catch (_: Exception) { false }

    fun cancelPending() {
        if (closingScope) return
        controls.toList().filter { it !== active?.control }.forEach { control ->
            control.retireLocal()
            jobs.launch { retire(control) }
        }
    }

    /** Retry waits for paired cleanup and uses Rust's shared refusal/recovery decision. */
    suspend fun recoveryDecision(): String {
        val receipt = failure ?: return decision(false, true, false, false)
        receipt.retired = retire(receipt.control)
        return decision(receipt.admitted, receipt.retired, receipt.authorizationRefused, receipt.selectionRefused)
    }

    suspend fun failActive(error: Throwable) {
        val candidate = active ?: return
        active = null
        recordFailure(candidate.control, error)
    }

    private suspend fun recordFailure(control: NativePlaybackControl, error: Throwable) {
        val refusal = error as? GatewayError
        val receipt = Recovery(control, control.hasNativeAdmission(), false, control.authorizationWasRefused() || refusal?.status in listOf(401, 403),
            // Invalid/unknown selection evidence cannot authorize a gateway bypass.
            control.selectionWasRefused() || refusal?.code == "invalid_playback_response" || error is NativeTorrentCoordinatorUnavailable ||
                (error is NativeTorrentFailure && error.reason in setOf("native_metadata_invalid", "native_file_unavailable", "native_playback_failed")) ||
                (refusal != null && refusal.status in listOf(400, 404, 409, 422)))
        failure = receipt
        receipt.retired = retire(control)
    }

    private suspend fun retire(control: NativePlaybackControl): Boolean = withContext(NonCancellable) {
        retirements[control]?.let { return@withContext it.await() }
        if (closingScope) return@withContext false
        val receipt = CompletableDeferred<Boolean>()
        retirements[control] = receipt
        val settled = try { coordinator.retireControl(control) } catch (_: Exception) { false }
        if (settled) controls.remove(control)
        receipt.complete(settled)
        settled
    }

    private fun decision(admitted: Boolean, retired: Boolean, authorization: Boolean, selection: Boolean): String =
        normalize("nativeTorrent", JSONObject().put("operation", "recovery").put("facts", JSONObject()
            .put("admitted", admitted).put("authorityRetired", retired).put("authorizationRefused", authorization)
            .put("selectionRefused", selection).put("action", "retry")).toString(), "").trim('"')

    private class Recovery(val control: NativePlaybackControl, val admitted: Boolean, var retired: Boolean, val authorizationRefused: Boolean, val selectionRefused: Boolean)
    override fun toString() = "NativePlaybackEffects(<redacted>)"
}
