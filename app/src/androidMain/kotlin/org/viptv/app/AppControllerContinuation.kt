package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.SystemClock
import org.viptv.video.PlaybackStatus

/** Controlled continuation is the only non-Resume automatic source path. */
internal fun AppController.nextEpisode(outgoing: Media, resolved: NextResult? = null, playWhenReady: Boolean? = null) {
    cancelUpNext()
    nextEpisodeJob?.cancel()
    val outgoingRoute = _state.value.route as? Route.Player ?: return
    val requestGeneration = ++playbackGeneration
    continuationRestore = outgoingRoute
    continuationWasPlaying = player.state.value.isPlaying
    val nextShouldPlay = playWhenReady ?: continuationWasPlaying
    nextEpisodeJob = scope.launch {
        player.pause()
        _state.value = _state.value.copy(message = "LOADING", loading = false)
        try {
            val result = resolved ?: gateway.nextEpisode(requireProfile(), outgoing)
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            when (result.status) {
                "next" -> {
                    val next = result.item ?: run {
                        if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) {
                            restoreContinuation("Episode information is unavailable. Open the series to choose an episode.")
                        }
                        return@launch
                    }
                    val candidates = gateway.sources(next)
                    if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
                    val selected = ContinuationSourcePolicy.select(next, outgoingRoute.source, candidates)
                    if (selected == null) {
                        _state.value = _state.value.copy(
                            route = Route.Sources(next),
                            sources = candidates,
                            loading = false,
                            message = "Choose a source for the next episode.",
                        )
                    } else {
                        val started = prepareAndStart(next, selected, explicitResume = false, playWhenReady = nextShouldPlay, resetTrackChoices = true, expectedGeneration = requestGeneration)
                        if (!started && PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation("Could not prepare the next episode.")
                    }
                }
                "caught_up" -> if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation("You're caught up. No next episode is listed yet.")
                "upcoming" -> if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation("The next episode hasn't been released yet.")
                else -> if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation("Episode information is unavailable. Open the series to choose an episode.")
            }
        } catch (error: CancellationException) {
            if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation(null)
            throw error
        } catch (_: Throwable) {
            if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) restoreContinuation("Could not prepare the next episode.")
        }
    }
}

/** Back/cancel returns to the still-live outgoing session and its play intent. */
internal fun AppController.restoreContinuation(message: String?) {
    continuationRestore?.let { _state.value = _state.value.copy(route = it, sources = emptyList(), loading = false, message = message) }
    if (continuationWasPlaying) player.play() else player.pause()
    continuationRestore = null
}

/** Metadata lookup leaves the current episode playing; only the visible countdown may advance it. */
internal fun AppController.maybeAutoNext(media: Media, positionMillis: Long, durationMillis: Long?, playing: Boolean, ended: Boolean) {
    if (_state.value.seekPreview != null || continuationRestore != null) return
    val key = "${media.type}.${media.id}"
    if (explicitResumeAwaitingCompletionKey == key && !ended) return
    if (explicitResumeAwaitingCompletionKey == key && ended) explicitResumeAwaitingCompletionKey = null
    val eligible = PlaybackPolicy.canAutoNext(media, positionMillis, durationMillis, playing || ended, seeking = false, nextAvailable = true, autoplay = _state.value.preferences.autoplay)
    if (!eligible || autoNextMediaKey == key || nextEpisodeJob?.isActive == true) return
    autoNextMediaKey = key
    val generation = playbackGeneration
    upNextJob = scope.launch {
        try {
            val result = gateway.nextEpisode(requireProfile(), media)
            if (!isActive || generation != playbackGeneration || (_state.value.route as? Route.Player)?.media?.id != media.id) return@launch
            val successor = result.item?.takeIf { result.status == "next" } ?: return@launch
            // Usually cached from Details; use the exact episode and series artwork for the card.
            val metadata = try { gateway.metadata(successor) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { null }
            if (!isActive || generation != playbackGeneration) return@launch
            val episode = metadata?.episodes?.firstOrNull { it.id == successor.id }
            val next = (episode?.let(successor::withArtworkFrom) ?: successor).let { item -> metadata?.let(item::withArtworkFrom) ?: item }
            val clock = NextEpisodeCountdown()
            _state.value = _state.value.copy(upNext = UpNextPrompt(next))
            var last = SystemClock.elapsedRealtime()
            while (isActive && generation == playbackGeneration && _state.value.upNext != null) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val playback = player.state.value
                val advancing = !playerMenuOpen && !playback.isBuffering && (playback.isPlaying || playback.status == PlaybackStatus.Ended)
                val done = clock.advance(now - last, advancing)
                last = now
                _state.value = _state.value.copy(upNext = UpNextPrompt(next, clock.remainingMillis))
                if (done) { playUpNext(); return@launch }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { _state.value = _state.value.copy(upNext = null) }
    }
}

internal fun AppController.cancelUpNext() {
    upNextJob?.cancel(); upNextJob = null
    if (_state.value.upNext != null) _state.value = _state.value.copy(upNext = null)
}
internal fun AppController.playUpNext() {
    val next = _state.value.upNext?.media ?: return
    val outgoing = (_state.value.route as? Route.Player)?.media ?: return
    nextEpisode(outgoing, NextResult("next", next), playWhenReady = true)
}

internal fun AppController.requestQueueManage(media: Media) {
    val target = QueuePolicy.manageTarget(media)
    if (!QueuePolicy.canManage(target)) return
    _state.value = _state.value.copy(dialog = DialogState(DialogKind.QueueManage, target.name, target))
}
internal fun AppController.resumeQueueItem(media: Media) {
    val target = QueuePolicy.manageTarget(media)
    dismissDialog()
    chooseSources(target, resume = true, origin = SourceReturn.Home)
}
internal fun AppController.chooseQueueSource(media: Media) {
    val target = QueuePolicy.manageTarget(media)
    dismissDialog()
    chooseSources(target, resume = false, origin = SourceReturn.Home)
}
/**
 * Queue Next is server-controlled continuation, never a guessed source.
 * The row's preserved prior episode supplies source affinity and Manage
 * actions; the server remains authoritative for the actual successor.
 */
internal fun AppController.playQueuedNext(queueItem: Media) {
    val previous = queueItem.previousEpisode ?: return
    if (!QueuePolicy.hasResolvedNext(queueItem)) return
    cancelPendingQueueContinuation()
    val requestGeneration = ++playbackGeneration
    // Publish this before launching so Back is owned even if the user
    // presses it between this call and the coroutine's first dispatch.
    _state.value = _state.value.copy(
        loading = true,
        message = "Preparing next episode…",
        queueContinuationPending = true,
    )
    queueContinuationJob = scope.launch {
        try {
            val result = gateway.nextEpisode(requireProfile(), previous)
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            val next = result.item
            if (result.status != "next" || next == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = null,
                    queueContinuationPending = false,
                    dialog = DialogState(DialogKind.NextUnavailable, queueNextUnavailableMessage(result.status), media = previous),
                )
                return@launch
            }
            val candidates = gateway.sources(next)
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            val priorSource = Source(
                id = "queue-prior",
                provider = "",
                addonId = previous.sourceAddonId,
                fingerprint = previous.sourceFingerprint,
            )
            val selected = ContinuationSourcePolicy.select(next, priorSource, candidates)
            _state.value = _state.value.copy(
                route = Route.Sources(next, origin = SourceReturn.Home),
                sources = candidates,
                loading = false,
                queueContinuationPending = false,
                message = if (selected == null) "Choose a source for the next episode." else null,
            )
            if (selected != null) start(next, selected)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = null,
                    queueContinuationPending = false,
                    dialog = DialogState(DialogKind.NextUnavailable, "Episode information is unavailable. Open the series to choose an episode.", media = previous),
                )
            }
        }
    }
}
private fun AppController.queueNextUnavailableMessage(status: String): String = when (status) {
    "caught_up" -> "You're caught up. No next episode is listed yet."
    "upcoming" -> "The next episode hasn't been released yet."
    else -> "Episode information is unavailable. Open the series to choose an episode."
}
internal fun AppController.removeFromQueue(media: Media) = scope.launch {
    guarded("Enter parent PIN") {
        val target = QueuePolicy.manageTarget(media)
        gateway.setQueueVisibility(requireProfile(), target, true)
        val displayKey = HomeFocusPolicy.mediaKey(media)
        val targetKey = HomeFocusPolicy.mediaKey(target)
        val shelves = _state.value.shelves.map { shelf ->
            // The queue role is data, not localized server display text.
            if (shelf.isQueueShelf) shelf.copy(items = shelf.items.filterNot {
                val key = HomeFocusPolicy.mediaKey(it)
                key == displayKey || key == targetKey || HomeFocusPolicy.mediaKey(QueuePolicy.manageTarget(it)) == targetKey
            }) else shelf
        }.filter { it.items.isNotEmpty() }
        _state.value = _state.value.copy(
            shelves = shelves,
            queue = _state.value.queue.filterNot {
                val key = HomeFocusPolicy.mediaKey(it)
                key == displayKey || key == targetKey || HomeFocusPolicy.mediaKey(QueuePolicy.manageTarget(it)) == targetKey
            },
            dialog = DialogState(DialogKind.QueueRemoved, "Removed from Continue Watching", target),
            message = null,
        )
        refreshHomeAfterQueueMutation()
    }
}
internal fun AppController.undoQueueRemoval(media: Media) = scope.launch {
    guarded("Enter parent PIN") {
        gateway.setQueueVisibility(requireProfile(), QueuePolicy.manageTarget(media), false)
        dismissDialog()
        refreshHomeAfterQueueMutation()
    }
}
private fun AppController.refreshHomeAfterQueueMutation() = scope.launch {
    val generation = ++homeRefreshGeneration
    val profile = _state.value.selectedProfile ?: return@launch
    runCatching { gateway.home(profile.id) }.onSuccess { shelves ->
        if (HomeRefreshPolicy.accepts(generation, homeRefreshGeneration) && _state.value.selectedProfile?.id == profile.id) {
            _state.value = _state.value.copy(shelves = shelves, queue = shelves.firstOrNull(HomeShelf::isQueueShelf)?.items.orEmpty())
        }
    }.onFailure {
        // The acknowledged mutation is already represented locally. Retain
        // that truthful state and let the next Home visit retry refresh.
        if (
            HomeRefreshPolicy.accepts(generation, homeRefreshGeneration) &&
            _state.value.route == Route.Browse(Destination.Home)
        ) {
            _state.value = _state.value.copy(message = "Continue Watching will refresh when available.")
        }
    }
}
internal fun AppController.recordHomeFocus(
    shelfIndex: Int,
    shelfTitle: String,
    media: Media,
    surface: HomeFocusSurface = HomeFocusSurface.Card,
) {
    if (_state.value.route == Route.Browse(Destination.Home)) {
        _state.value = _state.value.copy(
            homeFocus = HomeFocusPolicy.record(_state.value.homeFocus, shelfIndex, shelfTitle, media, surface),
        )
    }
}
internal fun AppController.recordHomeDirectionalInput() {
    if (_state.value.route == Route.Browse(Destination.Home)) {
        cancelPendingQueueContinuation()
        _state.value = _state.value.copy(homeFocus = HomeFocusPolicy.afterDirectionalInput(_state.value.homeFocus))
    }
}
/** UI modal dismissal uses this when its remembered Home card remains valid. */
internal fun AppController.restoreHomeFocus() {
    requestHomeFocusRestore()
}
internal fun AppController.requestHomeFocusRestore() {
    _state.value = _state.value.copy(homeFocus = HomeFocusPolicy.requestRestore(_state.value.homeFocus))
}
internal fun AppController.cancelPendingQueueContinuation() {
    queueContinuationJob?.cancel()
    queueContinuationJob = null
    invalidatePlaybackPreparation()
    if (_state.value.loading || _state.value.queueContinuationPending) _state.value = _state.value.copy(loading = false, message = null, queueContinuationPending = false)
}
