package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Delivery changes never select another source or infer codec support from an extension. */
data class PlaybackDeliveryOptions(val forceGateway: Boolean = false, val forceTranscode: Boolean = false)

/** Application effects for one exact source. Each failed native admission is released first. */
internal suspend fun openPlaybackDelivery(
    initial: PlaybackDeliveryOptions,
    prepare: suspend (PlaybackDeliveryOptions) -> PlaybackLaunch,
    open: suspend (PlaybackLaunch) -> Unit,
    release: suspend (String) -> Unit,
    isCurrent: () -> Boolean,
): Pair<PlaybackLaunch, PlaybackDeliveryOptions> {
    currentCoroutineContext().ensureActive()
    if (!isCurrent()) throw CancellationException("Playback was replaced")
    val launch = prepare(initial)
    try {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Playback was replaced")
        open(launch)
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Playback was replaced")
        return launch to initial
    } catch (error: Exception) {
        withContext(NonCancellable) {
            withTimeoutOrNull(5_000) { try { release(launch.sessionId) } catch (_: Exception) { } }
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Playback was replaced")
        throw error
    }
}
