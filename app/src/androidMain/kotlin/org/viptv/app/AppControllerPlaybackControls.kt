package org.viptv.app

import org.viptv.video.PlaybackStatus
import kotlinx.coroutines.launch

/** The Media3 adapter exposes session-relative HLS time; map it once to title time. */
internal fun AppController.absolutePositionMillis(): Long {
    val candidate = PlaybackTimelinePolicy.absolutePositionMillis(player.state.value.positionMillis, playbackTitleOffsetMillis)
    managedPauseAnchorMillis?.let { return it }
    return if (player.state.value.status == PlaybackStatus.Error && lastTrustedTitlePositionMillis > 0L) {
        lastTrustedTitlePositionMillis
    } else {
        lastTrustedTitlePositionMillis = candidate
        candidate
    }
}
internal fun AppController.titleDurationMillis(): Long? = playbackTitleDurationMillis ?: player.state.value.timeline?.durationMillis

/** Pause captures title time before Media3's rolling window can advance. */
internal fun AppController.pausePlayback() {
    val route = _state.value.route as? Route.Player ?: return
    if (ManagedPausePolicy.usesAnchor(_state.value.playbackDeliveryMode, route.media.type == "live")) {
        managedPauseAnchorMillis = absolutePositionMillis()
    }
    player.pause()
    showPlayerChrome()
}

/** Managed paused output resumes by preparing the original title coordinate. */
internal fun AppController.resumePlayback() {
    val route = _state.value.route as? Route.Player ?: return
    val anchor = managedPauseAnchorMillis
    if (ManagedPausePolicy.requiresReplacementOnResume(_state.value.playbackDeliveryMode, anchor)) {
        val requestGeneration = ++playbackGeneration
        scope.launch {
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            val resumed = prepareAndStart(
                route.media.copy(positionMillis = checkNotNull(anchor)),
                route.source,
                explicitResume = false,
                playWhenReady = true,
                resetTrackChoices = false,
                expectedGeneration = requestGeneration,
            )
            if (!resumed && PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) {
                showPlaybackRecovery(route.copy(media = route.media.copy(positionMillis = checkNotNull(anchor))))
            }
        }
    } else {
        player.play()
    }
    showPlayerChrome()
}

internal fun AppController.saveProgress(media: Media) {
    val profileId = _state.value.selectedProfile?.id
    val position = absolutePositionMillis()
    scope.launch { persistProgress(profileId, media, position) }
}
internal fun AppController.previewSeek(deltaMillis: Long) {
    val playback = player.state.value
    val timeline = playback.timeline ?: return
    val base = _state.value.seekPreview?.targetMillis ?: absolutePositionMillis()
    val range = timeline.seekableRange
    val rangeStart = range?.startMillis?.let { PlaybackTimelinePolicy.absolutePositionMillis(it, playbackTitleOffsetMillis) }
    val rangeEnd = range?.endMillis?.let { PlaybackTimelinePolicy.absolutePositionMillis(it, playbackTitleOffsetMillis) }
    SeekPolicy.target(base, deltaMillis, titleDurationMillis(), rangeStart, rangeEnd)?.let { target ->
        _state.value = _state.value.copy(seekPreview = SeekPreview(target))
        showPlayerChrome()
    }
}
internal fun AppController.commitSeek() {
    val target = _state.value.seekPreview?.targetMillis ?: return
    val route = _state.value.route as? Route.Player ?: return
    if (!SeekCommitPolicy.usesManagedReplacement(_state.value.playbackDeliveryMode)) {
        if (player.seekTo(PlaybackTimelinePolicy.segmentPositionMillis(target, playbackTitleOffsetMillis))) {
            _state.value = _state.value.copy(seekPreview = null)
            showPlayerChrome()
        }
        return
    }
    val wasPlaying = player.state.value.isPlaying
    managedRecoveryKey = null
    val requestGeneration = ++playbackGeneration
    _state.value = _state.value.copy(seekPreview = null)
    scope.launch {
        if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
        val requestedRoute = route.copy(media = route.media.copy(positionMillis = target))
        val replaced = prepareAndStart(
            requestedRoute.media,
            requestedRoute.source,
            explicitResume = false,
            playWhenReady = wasPlaying,
            resetTrackChoices = false,
            expectedGeneration = requestGeneration,
        )
        if (!replaced && PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) showPlaybackRecovery(requestedRoute)
    }
}
internal fun AppController.cancelSeek() { _state.value = _state.value.copy(seekPreview = null) }

/** Server-managed selection replaces the playback session; native track IDs are output-local. */
internal fun AppController.selectAudioTrack(track: PlaybackTrack) {
    if (!track.selectable || !track.supported) return
    val route = _state.value.route as? Route.Player ?: return
    val priorAudio = selectedAudioTrackIndex
    selectedAudioTrackIndex = track.inputIndex
    replaceForManualTrackChoice(route) { selectedAudioTrackIndex = priorAudio }
}

internal fun AppController.selectSubtitleTrack(track: PlaybackTrack?) {
    val route = _state.value.route as? Route.Player ?: return
    if (track != null && (!track.selectable || !track.supported)) return
    val priorSubtitle = selectedSubtitleTrackIndex
    val priorSubtitlesOff = subtitlesOff
    selectedSubtitleTrackIndex = track?.inputIndex
    subtitlesOff = track == null
    replaceForManualTrackChoice(route) {
        selectedSubtitleTrackIndex = priorSubtitle
        subtitlesOff = priorSubtitlesOff
    }
}

private fun AppController.replaceForManualTrackChoice(route: Route.Player, rollback: () -> Unit) {
    managedRecoveryKey = null
    val requestGeneration = ++playbackGeneration
    val position = absolutePositionMillis()
    val wasPlaying = player.state.value.isPlaying
    scope.launch {
        if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
        val replaced = prepareAndStart(
            route.media.copy(positionMillis = position),
            route.source,
            explicitResume = false,
            playWhenReady = wasPlaying,
            resetTrackChoices = false,
            expectedGeneration = requestGeneration,
        )
        if (!replaced && PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) {
            rollback()
            showPlaybackRecovery(route.copy(media = route.media.copy(positionMillis = position)))
        }
    }
}
/** Any player input restores controls and restarts the seven-second visibility timer. */
internal fun AppController.showPlayerChrome() {
    if (_state.value.route !is Route.Player) return
    _state.value = _state.value.copy(playerChromeVisible = true)
    schedulePlayerChromeDismissal()
}
internal fun AppController.hidePlayerChrome() {
    playerChromeJob?.cancel()
    if (_state.value.route is Route.Player && !playerMenuOpen) _state.value = _state.value.copy(playerChromeVisible = false)
}
/** Local track dialogs report ownership so the seven-second timer cannot hide their context. */
internal fun AppController.setPlayerMenuOpen(open: Boolean) {
    playerMenuOpen = open
    if (open) {
        playerChromeJob?.cancel()
        if (_state.value.route is Route.Player) _state.value = _state.value.copy(playerChromeVisible = true)
    } else {
        schedulePlayerChromeDismissal()
    }
}
