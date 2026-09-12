package org.viptv.app

import android.content.Context
import com.getair.video.AndroidMedia3BackendFactory
import com.getair.video.AndroidMedia3VideoPlayer
import com.getair.video.PlaybackKind
import com.getair.video.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppController(context: Context, private val origin: String = "https://viptv.syek.tech") {
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    private val gateway = VipTvHttpGateway(origin, store.getString("access", null))
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    val player: AndroidMedia3VideoPlayer = AndroidMedia3BackendFactory(context).createAndroidPlayer()
    private var pairingPoll: Job? = null
    private var sourceDiscovery: Job? = null
    private var nextEpisodeJob: Job? = null
    private var playerChromeJob: Job? = null
    private var heartbeatJob: Job? = null
    private var playbackSessionId: String? = null
    private var afterParentUnlock: (suspend () -> Unit)? = null

    init { restore() }
    fun beginPairing() = scope.launch {
        update(loading = true, message = null)
        runCatching { gateway.startDevicePairing("VIPTV Android TV") }.onSuccess { code ->
            _state.value = _state.value.copy(route = Route.Pairing, deviceCode = code, loading = false)
            pairingPoll?.cancel(); pairingPoll = launch { pollPairing(code) }
        }.onFailure { fail(it) }
    }
    private suspend fun pollPairing(code: DeviceCode) {
        repeat(120) {
            delay(code.intervalSeconds * 1_000)
            runCatching { gateway.exchangeDeviceCode(code.code) }.getOrNull()?.let { session ->
                persist(session); loadProfiles(session.profileId); return
            }
        }
        update(loading = false, message = "Pairing expired. Try again.")
    }
    private fun restore() = scope.launch {
        val refresh = store.getString("refresh", null)
        if (refresh == null) { _state.value = AppState(route = Route.Pairing); beginPairing(); return@launch }
        runCatching { gateway.refresh(refresh) }.onSuccess { persist(it); loadProfiles(it.profileId) }.onFailure { store.edit().clear().apply(); _state.value = AppState(route = Route.Pairing); beginPairing() }
    }
    private suspend fun loadProfiles(selected: String?) {
        val (profiles, current) = gateway.profiles(); val chosen = profiles.firstOrNull { it.id == (selected ?: current) }
        _state.value = _state.value.copy(route = if (chosen == null) Route.Profiles else Route.Browse(Destination.Home), profiles = profiles, selectedProfile = chosen, loading = chosen != null)
        chosen?.let { loadHome(it) }
    }
    private suspend fun loadHome(profile: Profile) {
        runCatching { gateway.home(profile.id) }.onSuccess { shelves ->
            _state.value = _state.value.copy(
                route = Route.Browse(Destination.Home),
                selectedProfile = profile,
                shelves = shelves,
                loading = false,
            )
        }.onFailure(::fail)
    }
    fun chooseProfile(profile: Profile) = scope.launch {
        update(loading = true); runCatching { gateway.selectProfile(profile.id); gateway.home(profile.id) }.onSuccess { shelves ->
            _state.value = _state.value.copy(route = Route.Browse(Destination.Home), selectedProfile = profile, shelves = shelves, loading = false)
        }.onFailure(::fail)
    }
    fun setProfilePage(page: Int) { _state.value = _state.value.copy(profilePage = page.coerceIn(0, ((_state.value.profiles.size - 1).coerceAtLeast(0)) / 5)) }
    fun toggleProfileManagement() { _state.value = _state.value.copy(managingProfiles = !_state.value.managingProfiles) }
    fun navigate(destination: Destination) = scope.launch {
        if (destination == Destination.Profile) { _state.value = _state.value.copy(route = Route.Profiles); return@launch }
        if (destination == Destination.Settings) { openSettings(); return@launch }
        if (destination == Destination.MyList) { openMyList(); return@launch }
        if (destination == Destination.Live) { openLive(); return@launch }
        if (destination == Destination.Search) { _state.value = _state.value.copy(route = Route.Search, loading = false); return@launch }
        _state.value = _state.value.copy(route = Route.Browse(destination), loading = true, message = null)
        runCatching { when (destination) { Destination.Home -> gateway.home(requireProfile()); Destination.Discover, Destination.Search -> listOf(HomeShelf("Discover", gateway.discover())); Destination.MyList -> listOf(HomeShelf("My List", gateway.discover())); Destination.Live -> listOf(HomeShelf("Live TV", gateway.discover("live"))); Destination.Settings, Destination.Profile -> emptyList() } }.onSuccess { shelves -> _state.value = _state.value.copy(shelves = shelves, catalog = shelves.flatMap(HomeShelf::items), loading = false) }.onFailure(::fail)
    }
    fun open(media: Media) = scope.launch { update(loading = true); runCatching { gateway.metadata(media) }.onSuccess { _state.value = _state.value.copy(route = Route.Details(it), loading = false) }.onFailure(::fail) }
    fun chooseSources(media: Media, resume: Boolean = false) {
        sourceDiscovery?.cancel()
        sourceDiscovery = scope.launch {
            _state.value = _state.value.copy(route = Route.Sources(media, resume), sources = emptyList(), loading = false, message = null)
            runCatching { gateway.sources(media) { arriving ->
                val route = _state.value.route
                if (route is Route.Sources && route.media.type == media.type && route.media.id == media.id) _state.value = _state.value.copy(sources = arriving)
            } }.onSuccess { discovered ->
            val savedIdentity = _state.value.selectedProfile?.let { profile -> store.getString(sourceKey(profile.id, media), null) }
            val exact = if (resume) discovered.firstOrNull { ResumeIdentity.sourceIdentity(it) == savedIdentity } else null
            if (exact != null) start(media, exact) else {
                _state.value = _state.value.copy(
                    route = Route.Sources(media, resume), sources = discovered, loading = false,
                    message = when { discovered.isEmpty() -> "No sources found. Choose another title or try again."; resume -> "Your previous source is unavailable. Choose a source."; else -> null },
                )
            }
        }.onFailure { error -> if (error !is CancellationException) fail(error) }
        }
    }
    fun start(media: Media, source: Source) = scope.launch {
        sourceDiscovery?.cancel()
        update(loading = true); runCatching { gateway.playback(source, media.positionMillis) }.onSuccess { launch ->
            if (launch.url.isBlank()) { update(loading = false, message = "The selected source could not be prepared."); return@onSuccess }
            player.open(PlaybackSource(launch.url, headers = launch.headers, title = media.name, kindHint = if (media.type == "live") PlaybackKind.Live else PlaybackKind.OnDemand))
            replacePlaybackSession(launch.sessionId)
            _state.value.selectedProfile?.let { profile -> store.edit().putString(sourceKey(profile.id, media), ResumeIdentity.sourceIdentity(source)).apply() }
            _state.value = _state.value.copy(route = Route.Player(media, source), playerChromeVisible = true, loading = false)
            schedulePlayerChromeDismissal()
        }.onFailure(::fail)
    }
    /** Controlled continuation is the only non-Resume automatic source path. */
    fun nextEpisode(outgoing: Media) {
        nextEpisodeJob?.cancel()
        nextEpisodeJob = scope.launch {
            player.pause()
            _state.value = _state.value.copy(message = "LOADING", loading = false)
            runCatching { gateway.nextEpisode(requireProfile(), outgoing) }.onSuccess { result ->
                when (result.status) {
                    "next" -> {
                        val next = result.item ?: run { player.play(); _state.value = _state.value.copy(message = "Episode information is unavailable. Open the series to choose an episode."); return@onSuccess }
                        val candidates = gateway.sources(next)
                        val selected = next.sourceAddonId?.let { addon -> candidates.firstOrNull { it.addonId == addon } }
                        if (selected == null) {
                            _state.value = _state.value.copy(route = Route.Sources(next), sources = candidates, message = "Choose a source for the next episode.")
                        } else start(next, selected)
                    }
                    "caught_up" -> { player.play(); _state.value = _state.value.copy(message = "You're caught up. No next episode is listed yet.") }
                    "upcoming" -> { player.play(); _state.value = _state.value.copy(message = "The next episode hasn't been released yet.") }
                    else -> { player.play(); _state.value = _state.value.copy(message = "Episode information is unavailable. Open the series to choose an episode.") }
                }
            }.onFailure { error -> if (error !is CancellationException) { player.play(); _state.value = _state.value.copy(message = error.message ?: "Could not prepare the next episode.") } }
        }
    }
    private var autoNextMediaKey: String? = null
    /** Player state triggers a bounded request; the server decides whether a successor exists. */
    fun maybeAutoNext(media: Media, positionMillis: Long, durationMillis: Long?, playing: Boolean) {
        val key = "${media.type}.${media.id}"
        if (PlaybackPolicy.canAutoNext(media, positionMillis, durationMillis, playing, seeking = false, nextAvailable = true) && autoNextMediaKey != key && nextEpisodeJob?.isActive != true) {
            autoNextMediaKey = key
            nextEpisode(media)
        }
    }
    fun back() { handleBack() }
    /** Returns false only when Android should handle app exit at a root gate/page. */
    fun handleBack(): Boolean {
        if (_state.value.route is Route.Player && nextEpisodeJob?.isActive == true) {
            nextEpisodeJob?.cancel()
            player.play()
            _state.value = _state.value.copy(message = null)
            return true
        }
        when (BackPolicy.decide(_state.value.dialog != null, _state.value.pinPrompt != null, _state.value.seekPreview != null, _state.value.playerChromeVisible, _state.value.route is Route.Player)) {
            BackDisposition.DismissDialog -> { dismissDialog(); return true }
            BackDisposition.CancelPin -> { cancelPin(); return true }
            BackDisposition.CancelSeek -> { _state.value = _state.value.copy(seekPreview = null); return true }
            BackDisposition.HidePlayerChrome -> { _state.value = _state.value.copy(playerChromeVisible = false); return true }
            BackDisposition.ExitPlayer, BackDisposition.Navigate -> Unit
        }
        when (val route = _state.value.route) {
            is Route.Player -> {
                stopPlayback(); _state.value = _state.value.copy(route = Route.Details(route.media))
            }
            is Route.Sources -> { sourceDiscovery?.cancel(); _state.value = _state.value.copy(route = Route.Details(route.media)) }
            is Route.Profiles -> if (_state.value.managingProfiles) _state.value = _state.value.copy(managingProfiles = false) else if (_state.value.selectedProfile != null) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null) else return false
            is Route.Details, is Route.Search, is Route.Settings, is Route.Addons, is Route.ProfileEditor, is Route.Guide -> _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
            is Route.Browse -> if (route.destination != Destination.Home) _state.value = _state.value.copy(route = Route.Browse(Destination.Home)) else return false
            Route.Pairing -> return false
        }
        return true
    }
    fun saveProgress(media: Media) = scope.launch { _state.value.selectedProfile?.let { gateway.updateProgress(it.id, media, player.state.value.positionMillis) } }
    fun previewSeek(deltaMillis: Long) {
        val playback = player.state.value
        val timeline = playback.timeline ?: return
        val base = _state.value.seekPreview?.targetMillis ?: playback.positionMillis
        SeekPolicy.target(base, deltaMillis, timeline.durationMillis, timeline.seekableRange?.startMillis, timeline.seekableRange?.endMillis)?.let { target ->
            _state.value = _state.value.copy(seekPreview = SeekPreview(target))
            showPlayerChrome()
        }
    }
    fun commitSeek() {
        _state.value.seekPreview?.let { player.seekTo(it.targetMillis) }
        _state.value = _state.value.copy(seekPreview = null)
        showPlayerChrome()
    }
    fun cancelSeek() { _state.value = _state.value.copy(seekPreview = null) }
    /** Any player input restores controls and restarts the seven-second visibility timer. */
    fun showPlayerChrome() {
        if (_state.value.route !is Route.Player) return
        _state.value = _state.value.copy(playerChromeVisible = true)
        schedulePlayerChromeDismissal()
    }
    fun search(query: String) = scope.launch { update(loading = true, message = null); runCatching { gateway.discover(search = query) }.onSuccess { _state.value = _state.value.copy(searchResults = it, loading = false) }.onFailure(::fail) }
    fun openMyList() = scope.launch { update(loading = true); runCatching { gateway.favorites(requireProfile()) }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.MyList), favorites = it, catalog = it, loading = false) }.onFailure(::fail) }
    fun openQueue() = scope.launch { update(loading = true); runCatching { gateway.queue(requireProfile()) }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.Home), queue = it, loading = false) }.onFailure(::fail) }
    fun openLive() = scope.launch { update(loading = true); runCatching { gateway.live() }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.Live), liveChannels = it, loading = false) }.onFailure(::fail) }
    fun openGuide(channel: LiveChannel) = scope.launch { update(loading = true); runCatching { gateway.guide(channel.id) }.onSuccess { _state.value = _state.value.copy(route = Route.Guide(channel), guide = it, loading = false) }.onFailure(::fail) }
    fun openSettings() = scope.launch { update(loading = true); runCatching { gateway.preferences(requireProfile()) to gateway.addons() }.onSuccess { (preferences, addons) -> _state.value = _state.value.copy(route = Route.Settings, preferences = preferences, addons = addons, loading = false) }.onFailure(::fail) }
    fun openAddons() = scope.launch { update(loading = true); runCatching { gateway.addons() }.onSuccess { addons -> _state.value = _state.value.copy(route = Route.Addons, addons = addons, loading = false) }.onFailure(::fail) }
    fun toggleMyList(media: Media) = scope.launch { guarded("Enter parent PIN") { val saved = gateway.toggleFavorite(requireProfile(), media); _state.value = _state.value.copy(message = if (saved) "Added to My List." else "Removed from My List.") } }
    fun removeFromQueue(media: Media) = scope.launch { guarded("Enter parent PIN") { gateway.setQueueVisibility(requireProfile(), media, true); _state.value = _state.value.copy(queue = _state.value.queue.filterNot { it.id == media.id }, dialog = DialogState(DialogKind.QueueManage, "Removed from Continue Watching", media)) } }
    fun undoQueueRemoval(media: Media) = scope.launch { guarded("Enter parent PIN") { gateway.setQueueVisibility(requireProfile(), media, false); openQueue(); dismissDialog() } }
    fun correctEpisode(media: Media, watched: Boolean) = scope.launch { guarded("Enter parent PIN") { gateway.correctProgress(requireProfile(), media, if (watched) "watched" else "unwatched"); _state.value = _state.value.copy(message = if (watched) "Marked watched." else "Marked unwatched.") } }
    fun setPreference(preferences: PlaybackPreferences) = scope.launch { guarded("Enter parent PIN") { gateway.savePreferences(requireProfile(), preferences); _state.value = _state.value.copy(preferences = preferences, message = "Applies to your next playback. Manual track choices take priority.") } }
    fun toggleAddon(addon: Addon) = scope.launch { guarded("Enter parent PIN") { gateway.setAddonEnabled(addon, !addon.enabled); openSettings() } }
    fun removeAddon(addon: Addon) = scope.launch { guarded("Enter parent PIN") { gateway.removeAddon(addon); openSettings() } }
    fun editProfile(profile: Profile? = null) { _state.value = _state.value.copy(route = Route.ProfileEditor(profile), message = null) }
    fun requestDeleteProfile(profile: Profile) { _state.value = _state.value.copy(dialog = DialogState(DialogKind.DeleteProfile, "Delete ${profile.name}?", profile = profile)) }
    fun saveProfile(profile: Profile?, name: String, avatarStyle: String, avatarSeed: String?) = scope.launch { guarded("Enter parent PIN to manage profiles") { val saved = if (profile == null) gateway.createProfile(name, avatarStyle, avatarSeed) else gateway.updateProfile(profile, name, avatarStyle, avatarSeed); val profiles = _state.value.profiles.filterNot { it.id == saved.id } + saved; _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, message = "Profile saved.") } }
    fun deleteProfile(profile: Profile) = scope.launch { if (profile.primary) { update(message = "The primary profile cannot be deleted."); return@launch }; guarded("Enter parent PIN to manage profiles") { gateway.deleteProfile(profile); _state.value = _state.value.copy(route = Route.Profiles, profiles = _state.value.profiles.filterNot { it.id == profile.id }, selectedProfile = _state.value.selectedProfile?.takeIf { it.id != profile.id }, message = "Profile deleted.") } }
    fun requestDialog(kind: DialogKind, title: String, media: Media? = null, source: Source? = null) { _state.value = _state.value.copy(dialog = DialogState(kind, title, media, source)) }
    fun dismissDialog() { _state.value = _state.value.copy(dialog = null) }
    fun submitPin(pin: String) = scope.launch { if (!pin.matches(Regex("\\d{4,8}"))) { update(message = "Enter a 4–8 digit parent PIN."); return@launch }; runCatching { gateway.unlockParent(pin) }.onSuccess { _state.value = _state.value.copy(pinPrompt = null, message = null); afterParentUnlock?.also { pending -> afterParentUnlock = null; pending() } }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "Incorrect PIN. Try again.") } }
    fun cancelPin() { afterParentUnlock = null; _state.value = _state.value.copy(pinPrompt = null) }
    fun signOut() = scope.launch { guarded("Enter parent PIN to sign out") { stopPlayback(); gateway.logout(); store.edit().clear().apply(); _state.value = AppState(route = Route.Pairing); beginPairing() } }
    fun close() { pairingPoll?.cancel(); sourceDiscovery?.cancel(); nextEpisodeJob?.cancel(); playerChromeJob?.cancel(); stopPlayback(); player.close() }
    private fun requireProfile() = checkNotNull(_state.value.selectedProfile).id
    private fun sourceKey(profileId: String, media: Media) = ResumeIdentity.storageKey(profileId, media)
    private fun schedulePlayerChromeDismissal() {
        playerChromeJob?.cancel()
        playerChromeJob = scope.launch {
            delay(7_000)
            if (_state.value.route is Route.Player && player.state.value.isPlaying) _state.value = _state.value.copy(playerChromeVisible = false)
        }
    }
    private fun replacePlaybackSession(sessionId: String) {
        val prior = playbackSessionId
        heartbeatJob?.cancel()
        playbackSessionId = sessionId
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000)
                runCatching { gateway.heartbeat(sessionId) }
            }
        }
        if (prior != null && prior != sessionId) scope.launch { runCatching { gateway.stopPlayback(prior) } }
    }
    private fun stopPlayback() {
        player.stop()
        heartbeatJob?.cancel()
        playbackSessionId?.let { id -> scope.launch { runCatching { gateway.stopPlayback(id) } } }
        playbackSessionId = null
    }
    private fun persist(session: DeviceSession) { store.edit().putString("access", session.accessToken).putString("refresh", session.refreshToken).apply() }
    private suspend fun guarded(pinTitle: String, action: suspend () -> Unit) {
        try { action() } catch (error: GatewayError) {
            if (error.status == 403 && (error.message.contains("PIN", true) || error.message.contains("Parent", true))) {
                afterParentUnlock = action
                _state.value = _state.value.copy(pinPrompt = PinPrompt(pinTitle), loading = false)
            } else fail(error)
        } catch (error: Throwable) { fail(error) }
    }
    private fun update(loading: Boolean = _state.value.loading, message: String? = _state.value.message) { _state.value = _state.value.copy(loading = loading, message = message) }
    private fun fail(error: Throwable) {
        val message = (error as? GatewayError)?.message ?: "Could not complete that request. Check your connection and try again."
        _state.value = _state.value.copy(loading = false, message = message)
    }
}
