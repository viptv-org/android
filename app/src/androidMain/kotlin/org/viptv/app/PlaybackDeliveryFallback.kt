package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Delivery changes never select another source or infer codec support from an extension. */
data class PlaybackDeliveryOptions(val forceGateway: Boolean = false, val forceTranscode: Boolean = false)

/** Ordinary delivery effects for one exact source. Each failed player open releases its lease. */
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

/** Safe player facts shared by the controller after an ordinary or private native open. */
internal class OpenedPlayback(
    val positionMillis: Long,
    val durationMillis: Long?,
    val timelineMode: String,
    val live: Boolean,
    val tracks: PlaybackTrackChoices,
) {
    companion object {
        fun ordinary(launch: PlaybackLaunch) = OpenedPlayback(launch.positionMillis, launch.durationMillis,
            launch.timelineMode, launch.live, PlaybackTrackChoices(launch.audioTracks, launch.subtitleTracks, launch.subtitlesSupported))
        fun native(positionMillis: Long) = OpenedPlayback(positionMillis, null, "direct", false, PlaybackTrackChoices())
    }
}
