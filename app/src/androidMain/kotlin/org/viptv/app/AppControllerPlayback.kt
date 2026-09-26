package org.viptv.app

import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackEvent
import org.viptv.video.PlaybackErrorCode
import org.viptv.video.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun AppController.start(media: Media, source: Source, explicitResume: Boolean = false) {
    if (_state.value.preparingSourceId != null) return
    playbackStartJob?.cancel()
    val requestGeneration = ++playbackGeneration
    _state.value = _state.value.copy(preparingSourceId = source.id, loading = true, message = null)
    playbackStartJob = scope.launch {
        managedRecoveryKey = null
        managedRecoveryInFlightKey = null
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
    val sourceRoute = when (current) { is Route.Sources -> current; is Route.Player -> current.sourceRoute; else -> null }
    val directOrigin = when {
        current is Route.Player -> current.directOrigin
        current is Route.Sources && media.type == "live" -> current.backRoute ?: Route.Browse(Destination.Home)
        source.channelId != null -> current
        else -> null
    }
    val returnDestination = when (current) {
        is Route.Sources -> SourceReturnPolicy.playbackReturn(current.origin, current.resume)
        is Route.Player -> current.returnDestination
        else -> PlaybackReturn.Details
    }
    val requestedAudio = if (resetTrackChoices) null else selectedAudioTrackIndex
    val requestedSubtitle = if (resetTrackChoices) null else selectedSubtitleTrackIndex
    val requestedSubtitlesOff = if (resetTrackChoices) false else subtitlesOff
    _state.value = _state.value.copy(preparingSourceId = source.id, loading = true, message = null)
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
            discardPreparedLease(launch.sessionId)
            return false
        }
        if (launch.mode != "direct") {
            discardPreparedLease(launch.sessionId)
            throw GatewayError(409, "This server did not honor native direct playback. Update the server and try again.")
        }
        if (launch.url.isBlank()) {
            if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false, message = "The selected source could not be prepared.")
            false
        } else {
            try {
                player.open(
                    PlaybackSource(
                        launch.url,
                        mimeType = when (launch.format) { "hls" -> "application/x-mpegURL"; "dash" -> "application/dash+xml"; else -> null },
                        headers = launch.headers,
                        startPositionMillis = if (launch.live) 0 else launch.positionMillis,
                        options = org.viptv.video.PlaybackOptions(
                            preferredAudioLanguage = _state.value.preferences.audioLanguage.takeIf { it.isNotBlank() },
                            preferredSubtitleLanguage = _state.value.preferences.subtitleLanguage.takeIf { it.isNotBlank() },
                            subtitlesEnabled = _state.value.preferences.subtitlesEnabled),
                        title = media.name,
                        kindHint = if (launch.live || media.type == "live") PlaybackKind.Live else PlaybackKind.OnDemand,
                    ),
                    playWhenReady = playWhenReady,
                )
            } catch (error: Throwable) {
                discardPreparedLease(launch.sessionId)
                // Opening crossed the native replacement boundary; the outgoing
                // lease can no longer be assumed healthy.
                if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) { player.stop(); retirePlaybackSession() }
                throw error
            }
            if (!PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) {
                player.stop()
                discardPreparedLease(launch.sessionId)
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
                route = Route.Player(playbackMedia, source, returnDestination, directOrigin, sourceRoute),
                playerChromeVisible = true,
                playbackTracks = PlaybackTrackChoices(launch.audioTracks, launch.subtitleTracks, launch.subtitlesSupported),
                playbackDeliveryMode = launch.mode,
                dialog = null,
                loading = false,
                preparingSourceId = null,
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
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false, message = playbackFailureMessage(error))
        false
    } finally {
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) _state.value = _state.value.copy(preparingSourceId = null)
    }
}
internal fun AppController.onPlayerEvent(event: PlaybackEvent) {
    if (event !is PlaybackEvent.Failed || _state.value.dialog?.kind == DialogKind.PlaybackRecovery) return
    val active = _state.value.route as? Route.Player ?: return
    _state.value = _state.value.copy(message = event.error.message)
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
            detail = _state.value.message ?: "The selected source could not be opened on this device.",
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
        is Route.Sources -> { _state.value = _state.value.copy(dialog = null, message = null); chooseSources(media, origin = route.origin) }
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

internal fun playbackFailureMessage(error: Throwable): String = when (error) {
    is org.viptv.video.PlaybackFailure -> error.error.message
    is GatewayError -> {
        val safe = error.message.take(240).takeUnless { it.contains(Regex("(?i)https?://|bearer |authorization|cookie[=:]|password[=:]")) }
        (safe ?: "The server could not prepare this source.") + " (HTTP " + error.status + ")"
    }
    is java.io.IOException -> "Could not reach the source or server. Check the connection and try another source."
    else -> "The selected source could not start on this device. Try another source."
}

/** Cleanup outlives a cancelled opening coroutine, but remains bounded. */
private fun AppController.discardPreparedLease(sessionId: String) {
    scope.launch { kotlinx.coroutines.withTimeoutOrNull(5000) { runCatching { gateway.stopPlayback(sessionId) } } }
}
