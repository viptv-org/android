package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.viptv.video.PlaybackErrorCode
import org.viptv.video.PlaybackFailure

/** Delivery changes never select another source or infer codec support from an extension. */
data class PlaybackDeliveryOptions(val forceGateway: Boolean = false, val forceTranscode: Boolean = false)

internal fun nextPlaybackDelivery(
    current: PlaybackDeliveryOptions,
    direct: Boolean,
    failure: PlaybackErrorCode,
): PlaybackDeliveryOptions? {
    val decoderRefusal = failure in setOf(PlaybackErrorCode.UnsupportedCodec, PlaybackErrorCode.UnsupportedContainer, PlaybackErrorCode.Decode)
    if (direct && !current.forceGateway && (decoderRefusal || failure == PlaybackErrorCode.Network))
        return PlaybackDeliveryOptions(forceGateway = true)
    if (!direct && decoderRefusal && !current.forceTranscode)
        return PlaybackDeliveryOptions(forceGateway = true, forceTranscode = true)
    return null
}

/** Application effects for one exact source. Each failed native admission is released first. */
internal suspend fun openPlaybackDelivery(
    initial: PlaybackDeliveryOptions,
    prepare: suspend (PlaybackDeliveryOptions) -> PlaybackLaunch,
    open: suspend (PlaybackLaunch) -> Unit,
    release: suspend (String) -> Unit,
    isCurrent: () -> Boolean,
): Pair<PlaybackLaunch, PlaybackDeliveryOptions> {
    var options = initial
    while (true) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("Playback was replaced")
        // Control refusals (no gateway, authorization, capacity, outage) never escalate.
        val launch = prepare(options)
        try {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Playback was replaced")
            open(launch)
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Playback was replaced")
            return launch to options
        } catch (error: Exception) {
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000) { try { release(launch.sessionId) } catch (_: Exception) { } }
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Playback was replaced")
            if (error is CancellationException) throw error
            options = (error as? PlaybackFailure)?.let {
                nextPlaybackDelivery(options, launch.deliveryKind == "direct", it.error.code)
            } ?: throw error
        }
    }
}
