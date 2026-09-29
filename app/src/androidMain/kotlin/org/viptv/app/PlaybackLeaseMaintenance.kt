package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Renewal does not extend authority until a validated response updates the lease. */
internal suspend fun maintainPlaybackLease(
    remaining: () -> Long?,
    interval: () -> Long?,
    renew: suspend () -> Unit,
    failed: suspend (Throwable) -> Unit,
) {
    var waitMillis = interval() ?: 20_000L
    while (currentCoroutineContext().isActive) {
        val before = remaining() ?: return
        if (before <= 0) { failed(expiredPlaybackLease()); return }
        delay(minOf(waitMillis, before))
        val budget = remaining() ?: return
        if (budget <= 0) { failed(expiredPlaybackLease()); return }
        try {
            withTimeout(budget) { renew() }
            waitMillis = interval() ?: 20_000L
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            failed(expiredPlaybackLease()); return
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if ((error is GatewayError && ((error.status in 1..499 && error.status != 408) || error.code == "invalid_playback_response")) || (error !is GatewayError && error !is IOException)) {
                failed(error); return
            }
            waitMillis = 2_000L
        }
    }
}

private fun expiredPlaybackLease() = GatewayError(410, "Playback could not be renewed before its session expired. Start playback again.", "playback_expired")
