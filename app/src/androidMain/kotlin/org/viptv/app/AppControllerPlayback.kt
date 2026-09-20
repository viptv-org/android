package org.viptv.app

import com.getair.video.PlaybackKind
import com.getair.video.PlaybackEvent
import com.getair.video.PlaybackErrorCode
import com.getair.video.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun AppController.start(media: Media, source: Source, explicitResume: Boolean = false) {
    val requestGeneration = ++playbackGeneration
    scope.launch {
        managedRecoveryKey = null
        managedRecoveryInFlightKey = null
        sourceDiscovery?.cancel()
        if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
        val started = prepareAndStart(media, source, explicitResume, playWhenReady = true, resetTrackChoices = true, expectedGeneration = requestGeneration)
        if (!started && PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) showPlaybackRecovery(media, source)
    }
}

/**
 * Opens a replacement session only after the server has accepted the exact
 * source, position, and manual track request. A failed replacement leaves
 * the outgoing session playable and avoids stopping its server lease.
 */
internal suspend fun AppController.prepareAndStart(
    media: Media,
    source: Source,
    explicitResume: Boolean,
    playWhenReady: Boolean,
    resetTrackChoices: Boolean,
    /** Captured before this request can queue on the preparation mutex. */
    expectedGeneration: Long,
): Boolean = playbackPrepareMutex.withLock {
    if (!PlaybackRequestPolicy.mayPrepareAfterMutexWait(expectedGeneration, playbackGeneration)) return@withLock false
    prepareAndStartLocked(media, source, explicitResume, playWhenReady, resetTrackChoices, expectedGeneration)
}

private suspend fun AppController.prepareAndStartLocked(
    media: Media,
    source: Source,
    explicitResume: Boolean,
    playWhenReady: Boolean,
    resetTrackChoices: Boolean,
    generation: Long,
): Boolean {
    val current = _state.value.route
    val directOrigin = if (current is Route.Player) current.directOrigin else current.takeIf { source.channelId != null }
    val returnDestination = when (current) {
        is Route.Sources -> SourceReturnPolicy.playbackReturn(current.origin, current.resume)
        is Route.Player -> current.returnDestination
        else -> PlaybackReturn.Details
    }
    val requestedAudio = if (resetTrackChoices) null else selectedAudioTrackIndex
    val requestedSubtitle = if (resetTrackChoices) null else selectedSubtitleTrackIndex
    val requestedSubtitlesOff = if (resetTrackChoices) false else subtitlesOff
    update(loading = true, message = null)
    return try {
        val launch = gateway.playback(
            source = source,
            positionMillis = media.positionMillis,
            capabilities = PlaybackClientCapabilities.from(player.capabilities.value),
            audioTrackIndex = requestedAudio,
            subtitleTrackIndex = requestedSubtitle,
            subtitlesOff = requestedSubtitlesOff,
        )
        if (!PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) {
            runCatching { gateway.stopPlayback(launch.sessionId) }
            return false
        }
        if (launch.url.isBlank()) {
            if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false, message = "The selected source could not be prepared.")
            false
        } else {
            try {
                player.open(
                    PlaybackSource(
                        launch.url,
                        headers = launch.headers,
                        title = media.name,
                        kindHint = if (launch.live || media.type == "live") PlaybackKind.Live else PlaybackKind.OnDemand,
                    ),
                    playWhenReady = playWhenReady,
                )
            } catch (error: Throwable) {
                runCatching { gateway.stopPlayback(launch.sessionId) }
                // Opening crossed the native replacement boundary; the outgoing
                // lease can no longer be assumed healthy.
                if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) retirePlaybackSession()
                throw error
            }
            if (!PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) {
                player.stop()
                runCatching { gateway.stopPlayback(launch.sessionId) }
                return false
            }
            // Direct delivery uses the title clock locally. Server-managed
            // remux/transcode delivery starts a new segment at launch.position.
            if (!launch.live && !SeekCommitPolicy.usesManagedReplacement(launch.mode) && launch.positionMillis > 0L && !player.seekTo(launch.positionMillis)) {
                player.stop()
                runCatching { gateway.stopPlayback(launch.sessionId) }
                // Direct seek failure occurs after native replacement, unlike a
                // gateway rejection above; retire the displaced lease too.
                if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) {
                    retirePlaybackSession()
                    update(loading = false, message = "This source cannot resume at the requested position. Choose another source.")
                }
                return false
            }
            playbackTitleOffsetMillis = PlaybackTimelinePolicy.titleOffsetMillis(launch.mode, launch.positionMillis)
            playbackTitleDurationMillis = launch.durationMillis ?: media.durationMillis
            lastTrustedTitlePositionMillis = launch.positionMillis
            managedPauseAnchorMillis = ManagedPausePolicy.anchorAfterOpen(launch.mode, launch.live, launch.positionMillis, playWhenReady)
            replacePlaybackSession(launch.sessionId)
            selectedAudioTrackIndex = requestedAudio
            selectedSubtitleTrackIndex = requestedSubtitle
            subtitlesOff = requestedSubtitlesOff
            val playbackMedia = media.copy(
                positionMillis = launch.positionMillis,
                durationMillis = launch.durationMillis ?: media.durationMillis,
                sourceAddonId = source.addonId,
                sourceFingerprint = source.fingerprint,
            )
            val key = "${playbackMedia.type}.${playbackMedia.id}"
            val previousKey = (_state.value.route as? Route.Player)?.media?.let { "${it.type}.${it.id}" }
            if (previousKey != key) autoNextMediaKey = null
            if (explicitResume && playbackMedia.durationMillis != null && playbackMedia.positionMillis >= playbackMedia.durationMillis - 10_000) {
                explicitResumeAwaitingCompletionKey = key
            }
            _state.value = _state.value.copy(
                route = Route.Player(playbackMedia, source, returnDestination, directOrigin),
                playerChromeVisible = true,
                playbackTracks = PlaybackTrackChoices(launch.audioTracks, launch.subtitleTracks, launch.subtitlesSupported),
                playbackDeliveryMode = launch.mode,
                dialog = null,
                loading = false,
            )
            continuationRestore = null
            startProgressPersistence(playbackMedia)
            schedulePlayerChromeDismissal()
            true
        }
    } catch (error: CancellationException) {
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false)
        throw error
    } catch (error: Throwable) {
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) fail(error)
        false
    }
}
internal fun AppController.onPlayerEvent(event: PlaybackEvent) {
    if (event !is PlaybackEvent.Failed || _state.value.dialog?.kind == DialogKind.PlaybackRecovery) return
    val active = _state.value.route as? Route.Player ?: return
    val route = active.copy(media = snapshotPlaybackMedia(active))
    retirePlaybackSession()
    val key = "${route.media.type}:${route.media.id}:${route.source.id}"
    if (managedRecoveryInFlightKey == key) return
    if (!ManagedRecoveryPolicy.shouldAttempt(
            serverManaged = SeekCommitPolicy.usesManagedReplacement(_state.value.playbackDeliveryMode),
            networkFailure = event.error.code == PlaybackErrorCode.Network,
            alreadyAttempted = managedRecoveryKey == key,
        )
    ) {
        showPlaybackRecovery(route)
        return
    }
    val playWhenReady = player.state.value.playWhenReady
    val requestGeneration = playbackGeneration
    managedRecoveryKey = key
    managedRecoveryInFlightKey = key
    scope.launch {
        try {
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            _state.value = _state.value.copy(message = "Reconnecting at your previous position…", loading = false)
            val restored = prepareAndStart(
                route.media,
                route.source,
                explicitResume = false,
                playWhenReady = playWhenReady,
                resetTrackChoices = false,
                expectedGeneration = requestGeneration,
            )
            if (restored) managedRecoveryKey = null
            else if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) showPlaybackRecovery(route)
        } finally {
            if (managedRecoveryInFlightKey == key) managedRecoveryInFlightKey = null
        }
    }
}

internal fun AppController.snapshotPlaybackMedia(route: Route.Player): Media =
    PlaybackRecoveryPolicy.snapshot(route.media, absolutePositionMillis(), titleDurationMillis())

/** Runtime failure has no automatic source fallback: users retain exact retry, source choice, or Back. */
internal fun AppController.showPlaybackRecovery(route: Route.Player) {
    _state.value = _state.value.copy(route = route)
    showPlaybackRecovery(route.media, route.source)
}

/** Source-picker failures retain its loaded rows; no native player session is retired until an actual player error. */
private fun AppController.showPlaybackRecovery(media: Media, source: Source) {
    _state.value = _state.value.copy(
        dialog = DialogState(
            kind = DialogKind.PlaybackRecovery,
            title = "Playback unavailable",
            media = media,
            source = source,
        ),
        playerChromeVisible = true,
        loading = false,
        message = null,
    )
}

internal fun AppController.retryPlaybackRecovery() {
    val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
    val media = dialog.media ?: return
    val source = dialog.source ?: return
    _state.value = _state.value.copy(dialog = null, message = null)
    start(media, source, explicitResume = false)
}

internal fun AppController.chooseAnotherSourceForRecovery() {
    val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
    val media = dialog.media ?: return
    when (val route = _state.value.route) {
        is Route.Player -> {
            stopPlayback(media)
            _state.value = _state.value.copy(dialog = null, message = null)
            chooseSources(media, resume = false)
        }
        is Route.Sources -> _state.value = _state.value.copy(dialog = null, message = null)
        else -> Unit
    }
}

internal fun AppController.backFromPlaybackRecovery() {
    val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
    when (val route = _state.value.route) {
        is Route.Player -> exitPlayer(route, dialog.media ?: snapshotPlaybackMedia(route))
        is Route.Sources -> _state.value = _state.value.copy(dialog = null, message = null)
        else -> _state.value = _state.value.copy(dialog = null, message = null)
    }
}
