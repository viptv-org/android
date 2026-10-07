package org.viptv.app

import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackEvent
import org.viptv.video.PlaybackErrorCode
import org.viptv.video.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun AppController.start(media: Media, source: Source, explicitResume: Boolean = false, deliveryOptions: PlaybackDeliveryOptions = PlaybackDeliveryOptions(), retryIntent: NativePlaybackRetryIntent? = null) {
    cancelUpNext()
    if (_state.value.preparingSourceId != null) return
    if (retryIntent == null) {
        nativeEffects?.resetRecovery()
        nativeRetryIntent = null
        nativeStoppedMedia = null
    }
    if (_state.value.route is Route.Guide) cancelGuideWork()
    playbackStartJob?.cancel()
    val requestGeneration = ++playbackGeneration
    _state.value = _state.value.copy(preparingSourceId = source.id, loading = true, message = null)
    playbackStartJob = scope.launch {
        managedRecoveryKey = null
        managedRecoveryInFlightKey = null
        if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
        val started = prepareAndStart(media, source, explicitResume, playWhenReady = retryIntent?.playWhenReady ?: true, resetTrackChoices = retryIntent == null, expectedGeneration = requestGeneration, deliveryOptions = deliveryOptions)
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
    deliveryOptions: PlaybackDeliveryOptions = if (resetTrackChoices) PlaybackDeliveryOptions() else activePlaybackDelivery,
): Boolean = playbackPrepareMutex.withLock {
    if (!PlaybackRequestPolicy.mayPrepareAfterMutexWait(expectedGeneration, playbackGeneration)) return@withLock false
    prepareAndStartLocked(media, source, explicitResume, playWhenReady, resetTrackChoices, expectedGeneration, deliveryOptions)
}

private suspend fun AppController.prepareAndStartLocked(
    media: Media,
    source: Source,
    explicitResume: Boolean,
    playWhenReady: Boolean,
    resetTrackChoices: Boolean,
    generation: Long,
    deliveryOptions: PlaybackDeliveryOptions,
): Boolean {
    cancelUpNext()
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
    var nativeBoundaryCrossed = false
    val retryIntent = if (deliveryOptions.forceGateway) nativeRetryIntent else null
    return try {
        suspend fun ordinary(prepared: PlaybackLaunch? = null): OpenedPlayback {
            val (launch, deliveredOptions) = openPlaybackDelivery(
                initial = deliveryOptions,
                prepare = { options -> prepared ?: gateway.playback(
                    source = source,
                    positionMillis = media.positionMillis,
                    capabilities = PlaybackClientCapabilities.from(player.capabilities.value),
                    audioTrackIndex = requestedAudio,
                    subtitleTrackIndex = requestedSubtitle,
                    subtitlesOff = requestedSubtitlesOff,
                    delivery = options,
                ) },
                open = { launch ->
                    nativeBoundaryCrossed = true
                    if (nativeEffects?.retireOutgoing() == false) throw NativeTorrentCoordinatorUnavailable()
                    retirePlaybackSession()
                    player.open(
                        PlaybackSource(
                            launch.url,
                            mimeType = when (launch.format) { "hls" -> "application/x-mpegURL"; "dash" -> "application/dash+xml"; else -> null },
                            headers = launch.headers,
                            startPositionMillis = launch.nativeStartPositionMillis,
                            options = org.viptv.video.PlaybackOptions(
                                preferredAudioLanguage = retryIntent?.audioLanguage ?: launch.preferredAudioLanguage ?: launch.audioTracks.firstOrNull { it.selected }?.language ?: _state.value.preferences.audioLanguage.takeIf { it.isNotBlank() },
                                preferredSubtitleLanguage = retryIntent?.subtitleLanguage ?: launch.preferredSubtitleLanguage ?: launch.subtitleTracks.firstOrNull { it.selected }?.language ?: _state.value.preferences.subtitleLanguage.takeIf { it.isNotBlank() },
                                subtitlesEnabled = retryIntent?.subtitlesEnabled ?: launch.subtitlesEnabled ?: _state.value.preferences.subtitlesEnabled),
                            title = media.name,
                            kindHint = if (launch.live || media.type == "live") PlaybackKind.Live else PlaybackKind.OnDemand,
                        ),
                        playWhenReady = playWhenReady,
                    )
                },
                release = gateway::stopPlayback,
                isCurrent = { PlaybackRequestPolicy.isCurrent(generation, playbackGeneration) },
            )
            activePlaybackDelivery = deliveredOptions
            nativePlaybackIntent = null
            nativeEffects?.resetRecovery()
            replacePlaybackSession(launch.sessionId)
            return OpenedPlayback.ordinary(launch)
        }
        val effects = if (media.type != "live" && source.channelId == null) nativePlaybackEffects() else null
        val opened = if (effects == null) ordinary() else {
            val epoch = checkNotNull(nativePlaybackEpoch)
            val preferences = _state.value.preferences
            val request = gateway.playbackRequest(source, media.positionMillis, PlaybackClientCapabilities.from(player.capabilities.value),
                requestedAudio, requestedSubtitle, requestedSubtitlesOff, deliveryOptions,
                preferences.audioLanguage.takeIf { it.isNotBlank() }, preferences.subtitleLanguage.takeIf { it.isNotBlank() },
                preferences.subtitlesEnabled)
            lateinit var control: NativePlaybackControl
            control = gateway.nativePlaybackControl(epoch.scope, generation, epoch.coordinator.cache, scope,
                preventReads = { effects.preventReads(control) }, invalidated = { effects.invalidated(control) })
            when (val prepared = effects.prepare(control, request, generation)) {
                is NativePlaybackEffects.Prepared.Legacy -> ordinary(prepared.launch)
                is NativePlaybackEffects.Prepared.Native -> {
                    val position = effects.accept(prepared, media.name, playWhenReady,
                        boundary = {
                            nativeBoundaryCrossed = true
                            retirePlaybackSession()
                            val state = prepared.candidate.control.state()
                            nativePlaybackIntent = NativePlaybackRetryIntent(playWhenReady, state.audioLanguage, state.subtitleLanguage,
                                state.subtitlesEnabled ?: false)
                            nativeRetryIntent = nativePlaybackIntent
                        }, open = player::open)
                    activePlaybackDelivery = deliveryOptions
                    OpenedPlayback.native(position)
                }
            }
        }
            playbackTitleOffsetMillis = PlaybackTimelinePolicy.titleOffsetMillis(opened.timelineMode, opened.positionMillis)
            playbackTitleDurationMillis = opened.durationMillis ?: media.durationMillis
            lastTrustedTitlePositionMillis = opened.positionMillis
            managedPauseAnchorMillis = ManagedPausePolicy.anchorAfterOpen(opened.timelineMode, opened.live, opened.positionMillis, playWhenReady)
            selectedAudioTrackIndex = requestedAudio
            selectedSubtitleTrackIndex = requestedSubtitle
            subtitlesOff = requestedSubtitlesOff
            val playbackMedia = media.copy(
                positionMillis = opened.positionMillis,
                durationMillis = opened.durationMillis ?: media.durationMillis,
                sourceAddonId = source.addonId,
                sourceFingerprint = source.fingerprint,
            )
            val lifecycle = CoreLifecycle.upNextPlayback(
                playbackMedia, (_state.value.route as? Route.Player)?.media,
                autoNextMediaKey, explicitResumeAwaitingCompletionKey, explicitResume
            )
            autoNextMediaKey = lifecycle.attemptedKey
            explicitResumeAwaitingCompletionKey = lifecycle.resumeAwaitingKey
            _state.value = _state.value.copy(
                route = Route.Player(playbackMedia, source, returnDestination, directOrigin, sourceRoute),
                playerChromeVisible = true,
                playbackTracks = opened.tracks,
                playbackDeliveryMode = opened.timelineMode,
                dialog = null,
                loading = false,
                preparingSourceId = null,
            )
            nativeRetryIntent = null
            nativeStoppedMedia = null
            continuationRestore = null
            startProgressPersistence(playbackMedia)
            schedulePlayerChromeDismissal()
            true
    } catch (error: CancellationException) {
        if (nativeBoundaryCrossed) player.stop()
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false)
        throw error
    } catch (error: Throwable) {
        // Native replacement has crossed the outgoing lease boundary.
        if (nativeBoundaryCrossed && PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) { player.stop(); retirePlaybackSession() }
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) update(loading = false, message = playbackFailureMessage(error))
        false
    } finally {
        if (PlaybackRequestPolicy.isCurrent(generation, playbackGeneration)) _state.value = _state.value.copy(preparingSourceId = null)
    }
}
internal fun AppController.onPlayerEvent(event: PlaybackEvent) {
    if (event !is PlaybackEvent.Failed || _state.value.dialog?.kind == DialogKind.PlaybackRecovery || _state.value.preparingSourceId != null) return
    val active = _state.value.route as? Route.Player ?: return
    cancelUpNext()
    _state.value = _state.value.copy(message = event.error.message)
    val route = active.copy(media = snapshotPlaybackMedia(active))
    val playWhenReady = player.state.value.playWhenReady
    val key = "${route.media.type}:${route.media.id}:${route.source.id}"
    if (managedRecoveryInFlightKey == key) return
    val native = nativeEffects?.takeIf { it.hasActive }
    if (native != null) {
        captureNativeRetryIntent()
        scope.launch { native.failActive(org.viptv.video.PlaybackFailure(event.error)) }
        retirePlaybackSession()
        showPlaybackRecovery(route)
        return
    }
    player.stop()
    retirePlaybackSession()
    if (!ManagedRecoveryPolicy.shouldAttempt(
            serverManaged = SeekCommitPolicy.usesManagedReplacement(_state.value.playbackDeliveryMode),
            networkFailure = event.error.code == PlaybackErrorCode.Network,
            alreadyAttempted = managedRecoveryKey == key,
        )
    ) {
        showPlaybackRecovery(route)
        return
    }
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
                deliveryOptions = activePlaybackDelivery,
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
    scope.launch {
        val decision = nativeEffects?.recoveryDecision() ?: "ordinaryRetry"
        if (_state.value.dialog !== dialog) return@launch
        when (decision) {
            "waitForRetirement" -> return@launch
            "authRecovery" -> { fail(GatewayError(403, "Your session is no longer authorized.", "unauthorized")); return@launch }
            "chooseSource" -> { chooseAnotherSourceForRecovery(); return@launch }
        }
        _state.value = _state.value.copy(dialog = null, message = null)
        start(media, source, explicitResume = false, deliveryOptions = PlaybackDeliveryOptions(forceGateway = decision == "forceGatewayRetry"),
            retryIntent = nativeRetryIntent.takeIf { decision == "forceGatewayRetry" })
    }
}

internal fun AppController.chooseAnotherSourceForRecovery() {
    val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
    val media = dialog.media ?: return
    when (val route = _state.value.route) {
        is Route.Player -> {
            stopPlayback(media)
            _state.value = _state.value.copy(dialog = null, message = null)
            chooseSources(media, resume = false, origin = route.sourceRoute?.origin ?: SourceReturn.Details,
                queueEpisodeReturn = route.sourceRoute?.queueEpisodeReturn == true)
        }
        is Route.Sources -> { _state.value = _state.value.copy(dialog = null, message = null); chooseSources(media, origin = route.origin, queueEpisodeReturn = route.queueEpisodeReturn) }
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
