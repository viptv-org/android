package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.viptv.core.wire.Identity
import org.viptv.core.wire.ForegroundAuthorityDecision

sealed interface ForegroundValidationResult {
    data class Valid(val identity: Identity) : ForegroundValidationResult
    data object Revoked : ForegroundValidationResult
    data class ProfileUnavailable(val identity: Identity) : ForegroundValidationResult
    data class Failed(val message: String) : ForegroundValidationResult
}

/** One bounded foreground authorization attempt, canceled with its visible owner. */
class ForegroundValidation(
    private val scope: CoroutineScope,
    private val identity: suspend () -> Identity,
) : AutoCloseable {
    private val mutableResult = MutableStateFlow<ForegroundValidationResult?>(null)
    val result = mutableResult.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var closed = false

    fun onForeground(expected: Identity, profileId: String?) {
        if (closed || job?.isActive == true) return
        val ticket = ++generation
        mutableResult.value = null
        job = scope.launch {
            val result = try {
                val current = withTimeout(30_000) { identity() }
                foregroundIdentityResult(expected, current, profileId)
            } catch (_: TimeoutCancellationException) {
                ForegroundValidationResult.Failed("Could not reconnect to VIPTV. Try again.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: GatewayError) {
                if (error.status == 401) ForegroundValidationResult.Revoked
                else ForegroundValidationResult.Failed(playbackFailureMessage(error))
            } catch (_: Exception) {
                ForegroundValidationResult.Failed("Could not reconnect to VIPTV. Try again.")
            }
            if (!closed && generation == ticket) mutableResult.value = result
        }
    }

    fun onBackground() {
        generation++
        job?.cancel()
        job = null
        mutableResult.value = null
    }

    override fun close() { onBackground(); closed = true }
}

/** Existing normalized authority facts decide whether cached presentation is safe. */
internal fun foregroundIdentityResult(expected: Identity, current: Identity, profileId: String?): ForegroundValidationResult =
    when (CoreLifecycle.authority(expected, current, profileId)) {
        ForegroundAuthorityDecision.REVOKED -> ForegroundValidationResult.Revoked
        ForegroundAuthorityDecision.PROFILEUNAVAILABLE -> ForegroundValidationResult.ProfileUnavailable(current)
        ForegroundAuthorityDecision.VALID -> ForegroundValidationResult.Valid(current)
    }
