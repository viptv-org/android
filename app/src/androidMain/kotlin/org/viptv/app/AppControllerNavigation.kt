package org.viptv.app

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun AppController.navigate(destination: Destination) = scope.launch {
    if (destination != Destination.Home) cancelPendingQueueContinuation()
    if (destination != Destination.Discover) {
        discoverJob?.cancel()
        discoverGeneration++
    }
    if (destination == Destination.Profile) { _state.value = _state.value.copy(route = Route.Profiles); return@launch }
    if (destination == Destination.Settings) { openSettings(); return@launch }
    if (destination == Destination.MyList) { openMyList(); return@launch }
    if (destination == Destination.Live) { openLive(); return@launch }
    if (destination == Destination.Search) { _state.value = _state.value.copy(route = Route.Search, loading = false); return@launch }
    if (destination == Destination.Discover) { openDiscover(); return@launch }
    _state.value = _state.value.copy(route = Route.Browse(destination), loading = true, message = null)
    runCatching { when (destination) { Destination.Home -> gateway.home(requireProfile()); Destination.Discover, Destination.Search -> listOf(HomeShelf("Discover", gateway.discover())); Destination.MyList -> listOf(HomeShelf("My List", gateway.discover())); Destination.Live -> listOf(HomeShelf("Live TV", gateway.discover("live"))); Destination.Settings, Destination.Profile -> emptyList() } }.onSuccess { shelves -> _state.value = _state.value.copy(shelves = shelves, catalog = shelves.flatMap(HomeShelf::items), loading = false) }.onFailure(::fail)
}

/** Invalidates an in-flight source/playback request before a user leaves its surface. */
internal fun AppController.invalidatePlaybackPreparation() { playbackGeneration++ }

internal fun AppController.back() { handleBack() }
internal fun AppController.consumesBack(state: AppState = _state.value): Boolean = BackAvailabilityPolicy.consumes(state)
/** Returns false only when Android should handle app exit at a root gate/page. */
internal fun AppController.handleBack(): Boolean {
    if (queueContinuationJob?.isActive == true) {
        cancelPendingQueueContinuation()
        return true
    }
    if (continuationRestore != null && ((_state.value.route is Route.Player && nextEpisodeJob?.isActive == true) || _state.value.route is Route.Sources)) {
        invalidatePlaybackPreparation()
        nextEpisodeJob?.cancel()
        restoreContinuation(null)
        return true
    }
    when (BackPolicy.decide(_state.value.dialog != null, _state.value.pinPrompt != null, _state.value.seekPreview != null, _state.value.playerChromeVisible, _state.value.route is Route.Player)) {
        BackDisposition.DismissDialog -> {
            if (_state.value.dialog?.kind == DialogKind.PlaybackRecovery) backFromPlaybackRecovery() else dismissDialog()
            return true
        }
        BackDisposition.CancelPin -> { cancelPin(); return true }
        BackDisposition.CancelSeek -> { _state.value = _state.value.copy(seekPreview = null); return true }
        BackDisposition.HidePlayerChrome -> { _state.value = _state.value.copy(playerChromeVisible = false); return true }
        BackDisposition.ExitPlayer, BackDisposition.Navigate -> Unit
    }
    when (val route = _state.value.route) {
        is Route.Player -> exitPlayer(route, snapshotPlaybackMedia(route))
        is Route.Sources -> {
            invalidatePlaybackPreparation()
            sourceDiscovery?.cancel()
            queueContinuationJob?.cancel()
            _state.value = _state.value.copy(route = SourceReturnPolicy.cancelRoute(route.origin, route.media))
            if (route.origin == SourceReturn.Home) requestHomeFocusRestore()
        }
        is Route.Profiles -> if (_state.value.managingProfiles) _state.value = _state.value.copy(managingProfiles = false) else if (_state.value.selectedProfile != null) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null) else return false
        is Route.Details -> {
            val destination = DetailReturnPolicy.destination(detailReturnDestination)
            detailReturnDestination = null
            _state.value = _state.value.copy(route = Route.Browse(destination), dialog = null, pinPrompt = null)
            if (destination == Destination.Home) requestHomeFocusRestore()
        }
        is Route.Guide -> {
            guideBrowseGeneration++
            guideGeneration++
            _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
            requestHomeFocusRestore()
        }
        is Route.Search, is Route.Settings, is Route.Addons, is Route.ProfileEditor -> _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
        is Route.Browse -> if (route.destination != Destination.Home) {
            if (route.destination == Destination.Discover) {
                discoverJob?.cancel()
                discoverGeneration++
            }
            _state.value = _state.value.copy(route = Route.Browse(Destination.Home))
            requestHomeFocusRestore()
        } else return false
        Route.Pairing -> return false
    }
    return true
}
/** The visible Exit control is intentionally immediate; only hardware Back hides chrome first. */
internal fun AppController.exitPlayback() {
    val route = _state.value.route as? Route.Player ?: return
    if (BackPolicy.decide(dialogOpen = false, pinOpen = false, seekPreviewOpen = false, playerChromeOpen = _state.value.playerChromeVisible, inPlayer = true, explicitExit = true) == BackDisposition.ExitPlayer) {
        exitPlayer(route, snapshotPlaybackMedia(route))
    }
}

internal fun AppController.exitPlayer(route: Route.Player, media: Media) {
    stopPlayback(media)
    _state.value = _state.value.copy(
        route = PlaybackRecoveryPolicy.returnRoute(route, media),
        dialog = null,
        message = null,
    )
}

internal fun AppController.requestDialog(kind: DialogKind, title: String, media: Media? = null, source: Source? = null) { _state.value = _state.value.copy(dialog = DialogState(kind, title, media, source)) }
internal fun AppController.dismissDialog() {
    val restoresHomeFocus = _state.value.dialog?.kind in setOf(DialogKind.QueueManage, DialogKind.QueueRemoved)
    _state.value = _state.value.copy(dialog = null)
    if (restoresHomeFocus) requestHomeFocusRestore()
}
internal fun AppController.submitPin(pin: String) = scope.launch { if (!pin.matches(Regex("\\d{4,8}"))) { update(message = "Enter a 4–8 digit parent PIN."); return@launch }; runCatching { gateway.unlockParent(pin) }.onSuccess { _state.value = _state.value.copy(pinPrompt = null, message = null); afterParentUnlock?.also { pending -> afterParentUnlock = null; pending() } }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "Incorrect PIN. Try again.") } }
internal fun AppController.cancelPin() { afterParentUnlock = null; _state.value = _state.value.copy(pinPrompt = null) }
