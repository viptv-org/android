package org.viptv.app

import android.content.Context
import com.getair.video.AndroidMedia3BackendFactory
import com.getair.video.AndroidMedia3VideoPlayer
import com.getair.video.PlaybackKind
import com.getair.video.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppController(context: Context, private val origin: String = "https://viptv.app") {
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    private val gateway = VipTvHttpGateway(origin, store.getString("access", null))
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    val player: AndroidMedia3VideoPlayer = AndroidMedia3BackendFactory(context).createAndroidPlayer()
    private var pairingPoll: Job? = null
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
    fun chooseSources(media: Media, resume: Boolean = false) = scope.launch {
        update(loading = true)
        runCatching { gateway.sources(media) }.onSuccess { discovered ->
            val savedId = store.getString("source.${media.type}.${media.id}", null)
            val exact = if (resume) discovered.firstOrNull { it.id == savedId } else null
            if (exact != null) start(media, exact) else {
                _state.value = _state.value.copy(
                    route = Route.Sources(media, resume), sources = discovered, loading = false,
                    message = when { discovered.isEmpty() -> "No sources found. Choose another title or try again."; resume -> "Your previous source is unavailable. Choose a source."; else -> null },
                )
            }
        }.onFailure(::fail)
    }
    fun start(media: Media, source: Source) = scope.launch {
        update(loading = true); runCatching { gateway.playback(source, media.positionMillis) }.onSuccess { launch ->
            if (launch.url.isBlank()) { update(loading = false, message = "The selected source could not be prepared."); return@onSuccess }
            player.open(PlaybackSource(launch.url, headers = launch.headers, title = media.name, kindHint = if (media.type == "live") PlaybackKind.Live else PlaybackKind.OnDemand))
            store.edit().putString("source.${media.type}.${media.id}", source.id).apply()
            _state.value = _state.value.copy(route = Route.Player(media, source), loading = false)
        }.onFailure(::fail)
    }
    fun back() {
        when (val route = _state.value.route) {
            is Route.Player -> { player.stop(); _state.value = _state.value.copy(route = Route.Details(route.media)) }
            is Route.Sources -> _state.value = _state.value.copy(route = Route.Details(route.media))
            is Route.Details, is Route.Profiles, is Route.Search, is Route.Settings, is Route.Addons, is Route.ProfileEditor, is Route.Guide -> _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
            is Route.Browse -> if (route.destination != Destination.Home) _state.value = _state.value.copy(route = Route.Browse(Destination.Home))
            Route.Pairing -> Unit
        }
    }
    fun saveProgress(media: Media) = scope.launch { _state.value.selectedProfile?.let { gateway.updateProgress(it.id, media, player.state.value.positionMillis) } }
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
    fun saveProfile(profile: Profile?, name: String, avatarStyle: String, avatarSeed: String?) = scope.launch { guarded("Enter parent PIN to manage profiles") { val saved = if (profile == null) gateway.createProfile(name, avatarStyle, avatarSeed) else gateway.updateProfile(profile, name, avatarStyle, avatarSeed); val profiles = _state.value.profiles.filterNot { it.id == saved.id } + saved; _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, message = "Profile saved.") } }
    fun deleteProfile(profile: Profile) = scope.launch { if (profile.primary) { update(message = "The primary profile cannot be deleted."); return@launch }; guarded("Enter parent PIN to manage profiles") { gateway.deleteProfile(profile); _state.value = _state.value.copy(route = Route.Profiles, profiles = _state.value.profiles.filterNot { it.id == profile.id }, selectedProfile = _state.value.selectedProfile?.takeIf { it.id != profile.id }, message = "Profile deleted.") } }
    fun requestDialog(kind: DialogKind, title: String, media: Media? = null, source: Source? = null) { _state.value = _state.value.copy(dialog = DialogState(kind, title, media, source)) }
    fun dismissDialog() { _state.value = _state.value.copy(dialog = null) }
    fun submitPin(pin: String) = scope.launch { if (!pin.matches(Regex("\\d{4,8}"))) { update(message = "Enter a 4–8 digit parent PIN."); return@launch }; runCatching { gateway.unlockParent(pin) }.onSuccess { _state.value = _state.value.copy(pinPrompt = null, message = null); afterParentUnlock?.also { pending -> afterParentUnlock = null; pending() } }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "Incorrect PIN. Try again.") } }
    fun cancelPin() { afterParentUnlock = null; _state.value = _state.value.copy(pinPrompt = null) }
    fun signOut() = scope.launch { guarded("Enter parent PIN to sign out") { gateway.logout(); store.edit().clear().apply(); player.stop(); _state.value = AppState(route = Route.Pairing); beginPairing() } }
    fun close() { pairingPoll?.cancel(); player.close() }
    private fun requireProfile() = checkNotNull(_state.value.selectedProfile).id
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
    private fun fail(error: Throwable) { _state.value = _state.value.copy(loading = false, message = error.message ?: "Could not complete that request.") }
}
