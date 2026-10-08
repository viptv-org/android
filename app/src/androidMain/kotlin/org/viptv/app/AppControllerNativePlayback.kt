package org.viptv.app

import android.os.SystemClock
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Transient native owners are separate from ordinary leases and saved application state. */
internal suspend fun AppController.nativePlaybackEffects(): NativePlaybackEffects? {
    if (!nativePlaybackAvailable()) return null
    val facts = nativeAuthorizationFacts() ?: return null
    val owner = nativeScopeOwnerOverride ?: nativeScopeOwner
    val epoch = try { owner.adopt(facts) } catch (_: NativeTorrentCoordinatorUnavailable) { null } ?: return null
    if (!epoch.coordinator.cache.isAvailable || !nativePlaybackAvailable()) return null
    if (nativePlaybackEpoch !== epoch) {
        nativePlaybackEpoch = epoch
        nativeEffects = NativePlaybackEffects(epoch.coordinator, scope, ::stopNativePlayer, {
            val route = (_state.value.route as? Route.Player)?.let { it.copy(media = nativeStoppedMedia ?: snapshotPlaybackMedia(it)) }
            retirePlaybackSession()
            route?.let(::showPlaybackRecovery)
        })
    }
    return nativeEffects
}

/** Profile/principal/device invalidation fences pending controls before cache cleanup queues. */
internal fun AppController.invalidateNativeAuthorization() {
    clearPlayerHttpRedirects()
    nativeEffects?.beginScopeClose()
    if (nativeEffects?.hasActive == true) {
        val route = _state.value.route as? Route.Player
        stopPlayback(route?.let(::snapshotPlaybackMedia))
    }
    nativeEffects?.stop()
    gateway.invalidateNativeScope()
    rotateNativeAuthorizationEpoch()
    nativePlaybackEpoch = null
    nativeEffects = null
    if (hasNativeScopeOwner()) {
        val owner = nativeScopeOwnerOverride ?: nativeScopeOwner
        scope.launch { runCatching { owner.adopt(null, revoked = true) } }
    }
}

internal fun AppController.createNativeCoordinator(cache: NativeTorrentCache) = NativeTorrentCoordinator(
    cache, SystemClock::elapsedRealtimeNanos,
    { it == playbackGeneration },
    ::stopNativePlayer,
)

/** Preserve media time and local track intent before authorization shutdown clears the player. */
internal fun AppController.stopNativePlayer() {
    (_state.value.route as? Route.Player)?.let { route ->
        nativeStoppedMedia = snapshotPlaybackMedia(route)
        captureNativeRetryIntent()
    }
    player.stop()
}

internal fun AppController.captureNativeRetryIntent() {
    val playback = player.state.value
    nativeRetryIntent = NativePlaybackRetryIntent(playback.playWhenReady,
        player.audioTracks.value.firstOrNull { it.id == playback.selectedAudioTrackId }?.language ?: nativePlaybackIntent?.audioLanguage,
        player.subtitleTracks.value.firstOrNull { it.id == playback.selectedSubtitleTrackId }?.language ?: nativePlaybackIntent?.subtitleLanguage,
        if (player.subtitleTracks.value.isEmpty()) nativePlaybackIntent?.subtitlesEnabled ?: false else playback.selectedSubtitleTrackId != null)
}

internal class NativePlaybackRetryIntent(
    val playWhenReady: Boolean,
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val subtitlesEnabled: Boolean,
)
