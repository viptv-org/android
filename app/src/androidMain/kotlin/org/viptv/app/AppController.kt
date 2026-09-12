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
    private var searchJob: Job? = null
    private var nextEpisodeJob: Job? = null
    private var playerChromeJob: Job? = null
    private var heartbeatJob: Job? = null
    private var progressJob: Job? = null
    private var playbackSessionId: String? = null
    private var afterParentUnlock: (suspend () -> Unit)? = null
    private var selectedAudioTrackIndex: Int? = null
    private var selectedSubtitleTrackIndex: Int? = null
    private var subtitlesOff = false
    private var continuationRestore: Route.Player? = null
    private var continuationWasPlaying = false

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
    fun open(media: Media) = scope.launch {
        update(loading = true)
        runCatching { gateway.metadata(media) }.onSuccess { metadata ->
            // Catalog/history carries artwork and progress that sparse metadata
            // responses may omit. Metadata may enrich it, never erase it.
            val detail = metadata.copy(
                poster = metadata.poster ?: media.poster,
                description = metadata.description ?: media.description,
                positionMillis = metadata.positionMillis.takeIf { it > 0 } ?: media.positionMillis,
                durationMillis = metadata.durationMillis ?: media.durationMillis,
                sourceAddonId = metadata.sourceAddonId ?: media.sourceAddonId,
                sourceFingerprint = metadata.sourceFingerprint ?: media.sourceFingerprint,
            )
            _state.value = _state.value.copy(route = Route.Details(detail), loading = false)
        }.onFailure(::fail)
    }
    fun chooseSources(media: Media, resume: Boolean = false) {
        sourceDiscovery?.cancel()
        sourceDiscovery = scope.launch {
            _state.value = _state.value.copy(route = Route.Sources(media, resume), sources = emptyList(), loading = false, message = null)
            runCatching { gateway.sources(media) { arriving ->
                val route = _state.value.route
                if (route is Route.Sources && route.media.type == media.type && route.media.id == media.id) _state.value = _state.value.copy(sources = arriving)
            } }.onSuccess { discovered ->
            val savedIdentity = ResumeIdentity.sourceIdentity(media.sourceAddonId, media.sourceFingerprint)
            val exact = if (resume) discovered.firstOrNull { ResumeIdentity.sourceIdentity(it) == savedIdentity } else null
            if (exact != null) start(media, exact, explicitResume = true) else {
                _state.value = _state.value.copy(
                    route = Route.Sources(media, resume), sources = discovered, loading = false,
                    message = when { discovered.isEmpty() -> "No sources found. Choose another title or try again."; resume && savedIdentity == null -> "Choose a source to resume. Your prior source cannot be verified."; resume -> "Your previous source is unavailable. Choose a source."; else -> null },
                )
            }
        }.onFailure { error -> if (error !is CancellationException) fail(error) }
        }
    }
    private var explicitResumeAwaitingCompletionKey: String? = null

    fun start(media: Media, source: Source, explicitResume: Boolean = false) = scope.launch {
        sourceDiscovery?.cancel()
        prepareAndStart(media, source, explicitResume, playWhenReady = true, resetTrackChoices = true)
    }

    /**
     * Opens a replacement session only after the server has accepted the exact
     * source, position, and manual track request. A failed replacement leaves
     * the outgoing session playable and avoids stopping its server lease.
     */
    private suspend fun prepareAndStart(
        media: Media,
        source: Source,
        explicitResume: Boolean,
        playWhenReady: Boolean,
        resetTrackChoices: Boolean,
    ): Boolean {
        val requestedAudio = if (resetTrackChoices) null else selectedAudioTrackIndex
        val requestedSubtitle = if (resetTrackChoices) null else selectedSubtitleTrackIndex
        val requestedSubtitlesOff = if (resetTrackChoices) false else subtitlesOff
        update(loading = true, message = null)
        return try {
            val launch = gateway.playback(source, media.positionMillis, requestedAudio, requestedSubtitle, requestedSubtitlesOff)
            if (launch.url.isBlank()) {
                update(loading = false, message = "The selected source could not be prepared.")
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
                    throw error
                }
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
                    route = Route.Player(playbackMedia, source),
                    playerChromeVisible = true,
                    playbackTracks = PlaybackTrackChoices(launch.audioTracks, launch.subtitleTracks, launch.subtitlesSupported),
                    playbackDeliveryMode = launch.mode,
                    loading = false,
                )
                continuationRestore = null
                startProgressPersistence(playbackMedia)
                schedulePlayerChromeDismissal()
                true
            }
        } catch (error: CancellationException) {
            update(loading = false)
            throw error
        } catch (error: Throwable) {
            fail(error)
            false
        }
    }
    /** Controlled continuation is the only non-Resume automatic source path. */
    fun nextEpisode(outgoing: Media) {
        nextEpisodeJob?.cancel()
        val outgoingRoute = _state.value.route as? Route.Player ?: return
        continuationRestore = outgoingRoute
        continuationWasPlaying = player.state.value.isPlaying
        nextEpisodeJob = scope.launch {
            player.pause()
            _state.value = _state.value.copy(message = "LOADING", loading = false)
            try {
                val result = gateway.nextEpisode(requireProfile(), outgoing)
                when (result.status) {
                    "next" -> {
                        val next = result.item ?: run {
                            restoreContinuation("Episode information is unavailable. Open the series to choose an episode.")
                            return@launch
                        }
                        val candidates = gateway.sources(next)
                        val selected = ContinuationSourcePolicy.select(next, outgoingRoute.source, candidates)
                        if (selected == null) {
                            _state.value = _state.value.copy(
                                route = Route.Sources(next),
                                sources = candidates,
                                loading = false,
                                message = "Choose a source for the next episode.",
                            )
                        } else {
                            val started = prepareAndStart(next, selected, explicitResume = false, playWhenReady = continuationWasPlaying, resetTrackChoices = true)
                            if (!started) restoreContinuation("Could not prepare the next episode.")
                        }
                    }
                    "caught_up" -> restoreContinuation("You're caught up. No next episode is listed yet.")
                    "upcoming" -> restoreContinuation("The next episode hasn't been released yet.")
                    else -> restoreContinuation("Episode information is unavailable. Open the series to choose an episode.")
                }
            } catch (error: CancellationException) {
                restoreContinuation(null)
                throw error
            } catch (_: Throwable) {
                restoreContinuation("Could not prepare the next episode.")
            }
        }
    }

    /** Back/cancel returns to the still-live outgoing session and its play intent. */
    private fun restoreContinuation(message: String?) {
        continuationRestore?.let { _state.value = _state.value.copy(route = it, sources = emptyList(), loading = false, message = message) }
        if (continuationWasPlaying) player.play() else player.pause()
        continuationRestore = null
    }
    private var autoNextMediaKey: String? = null
    /** Player state triggers a bounded request; the server decides whether a successor exists. */
    fun maybeAutoNext(media: Media, positionMillis: Long, durationMillis: Long?, playing: Boolean, ended: Boolean) {
        val key = "${media.type}.${media.id}"
        if (explicitResumeAwaitingCompletionKey == key && !ended) return
        if (explicitResumeAwaitingCompletionKey == key && ended) explicitResumeAwaitingCompletionKey = null
        val eligible = if (ended) media.type == "series" && durationMillis != null && durationMillis > 10_000 && _state.value.preferences.autoplay else PlaybackPolicy.canAutoNext(media, positionMillis, durationMillis, playing, seeking = false, nextAvailable = true, autoplay = _state.value.preferences.autoplay)
        if (eligible && autoNextMediaKey != key && nextEpisodeJob?.isActive != true) {
            autoNextMediaKey = key
            nextEpisode(media)
        }
    }
    fun back() { handleBack() }
    fun consumesBack(): Boolean = when (_state.value.route) {
        is Route.Player, is Route.Sources, is Route.Details, Route.Search, Route.Settings, Route.Addons, is Route.ProfileEditor, is Route.Guide -> true
        is Route.Profiles -> _state.value.managingProfiles || _state.value.selectedProfile != null
        is Route.Browse -> (_state.value.route as Route.Browse).destination != Destination.Home
        Route.Pairing -> false
    } || _state.value.dialog != null || _state.value.pinPrompt != null || _state.value.seekPreview != null
    /** Returns false only when Android should handle app exit at a root gate/page. */
    fun handleBack(): Boolean {
        if (continuationRestore != null && ((_state.value.route is Route.Player && nextEpisodeJob?.isActive == true) || _state.value.route is Route.Sources)) {
            nextEpisodeJob?.cancel()
            restoreContinuation(null)
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
                stopPlayback(route.media); _state.value = _state.value.copy(route = Route.Details(route.media))
            }
            is Route.Sources -> { sourceDiscovery?.cancel(); _state.value = _state.value.copy(route = Route.Details(route.media)) }
            is Route.Profiles -> if (_state.value.managingProfiles) _state.value = _state.value.copy(managingProfiles = false) else if (_state.value.selectedProfile != null) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null) else return false
            is Route.Details, is Route.Search, is Route.Settings, is Route.Addons, is Route.ProfileEditor, is Route.Guide -> _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
            is Route.Browse -> if (route.destination != Destination.Home) _state.value = _state.value.copy(route = Route.Browse(Destination.Home)) else return false
            Route.Pairing -> return false
        }
        return true
    }
    fun saveProgress(media: Media) = scope.launch { persistProgress(media, player.state.value.positionMillis) }
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
        val target = _state.value.seekPreview?.targetMillis ?: return
        val route = _state.value.route as? Route.Player ?: return
        if (!SeekCommitPolicy.usesManagedReplacement(_state.value.playbackDeliveryMode)) {
            if (player.seekTo(target)) {
                _state.value = _state.value.copy(seekPreview = null)
                showPlayerChrome()
            }
            return
        }
        val wasPlaying = player.state.value.isPlaying
        _state.value = _state.value.copy(seekPreview = null)
        scope.launch {
            prepareAndStart(
                route.media.copy(positionMillis = target),
                route.source,
                explicitResume = false,
                playWhenReady = wasPlaying,
                resetTrackChoices = false,
            )
        }
    }
    fun cancelSeek() { _state.value = _state.value.copy(seekPreview = null) }

    /** Server-managed selection replaces the playback session; native track IDs are output-local. */
    fun selectAudioTrack(track: PlaybackTrack) {
        if (!track.selectable || !track.supported) return
        val route = _state.value.route as? Route.Player ?: return
        val priorAudio = selectedAudioTrackIndex
        selectedAudioTrackIndex = track.inputIndex
        replaceForManualTrackChoice(route) { selectedAudioTrackIndex = priorAudio }
    }

    fun selectSubtitleTrack(track: PlaybackTrack?) {
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

    private fun replaceForManualTrackChoice(route: Route.Player, rollback: () -> Unit) {
        val position = player.state.value.positionMillis
        val wasPlaying = player.state.value.isPlaying
        scope.launch {
            val replaced = prepareAndStart(
                route.media.copy(positionMillis = position),
                route.source,
                explicitResume = false,
                playWhenReady = wasPlaying,
                resetTrackChoices = false,
            )
            if (!replaced) rollback()
        }
    }
    /** Any player input restores controls and restarts the seven-second visibility timer. */
    fun showPlayerChrome() {
        if (_state.value.route !is Route.Player) return
        _state.value = _state.value.copy(playerChromeVisible = true)
        schedulePlayerChromeDismissal()
    }
    /** Each edit replaces prior work; a late response cannot repopulate a cleared query. */
    fun search(query: String) {
        searchJob?.cancel()
        val normalized = query.trim()
        _state.value = _state.value.copy(
            searchResults = emptyList(),
            loading = false,
            message = if (normalized.isEmpty()) "Find your next favorite." else "Searching…",
        )
        if (normalized.isEmpty()) return
        searchJob = scope.launch {
            delay(650)
            try {
                val results = gateway.discover(search = normalized)
                if (isActive) {
                    _state.value = _state.value.copy(
                        searchResults = results,
                        loading = false,
                        message = if (results.isEmpty()) "No results. Try another title." else "${results.size} results",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isActive) _state.value = _state.value.copy(loading = false, message = "Searching…  Some sources couldn't load.")
            }
        }
    }
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
    fun saveProfile(profile: Profile?, name: String, avatarStyle: String, avatarChoice: Int?) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            update(loading = false, message = "Enter a name to continue.")
            return
        }
        scope.launch {
            update(loading = true, message = "Saving profile…")
            guarded("Enter parent PIN to manage profiles") {
                val saved = if (profile == null) {
                    gateway.createProfile(normalizedName, avatarStyle, avatarChoice)
                } else {
                    gateway.updateProfile(profile, normalizedName, avatarStyle, avatarChoice)
                }
                val profiles = _state.value.profiles.filterNot { it.id == saved.id } + saved
                _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, loading = false, message = "Profile saved.")
            }
        }
    }
    fun deleteProfile(profile: Profile) = scope.launch { if (profile.primary) { update(message = "The primary profile cannot be deleted."); return@launch }; guarded("Enter parent PIN to manage profiles") { gateway.deleteProfile(profile); _state.value = _state.value.copy(route = Route.Profiles, profiles = _state.value.profiles.filterNot { it.id == profile.id }, selectedProfile = _state.value.selectedProfile?.takeIf { it.id != profile.id }, message = "Profile deleted.") } }
    fun requestDialog(kind: DialogKind, title: String, media: Media? = null, source: Source? = null) { _state.value = _state.value.copy(dialog = DialogState(kind, title, media, source)) }
    fun dismissDialog() { _state.value = _state.value.copy(dialog = null) }
    fun submitPin(pin: String) = scope.launch { if (!pin.matches(Regex("\\d{4,8}"))) { update(message = "Enter a 4–8 digit parent PIN."); return@launch }; runCatching { gateway.unlockParent(pin) }.onSuccess { _state.value = _state.value.copy(pinPrompt = null, message = null); afterParentUnlock?.also { pending -> afterParentUnlock = null; pending() } }.onFailure { error -> _state.value = _state.value.copy(message = error.message ?: "Incorrect PIN. Try again.") } }
    fun cancelPin() { afterParentUnlock = null; _state.value = _state.value.copy(pinPrompt = null) }
    fun signOut() = scope.launch { guarded("Enter parent PIN to sign out") { stopPlayback((_state.value.route as? Route.Player)?.media); gateway.logout(); store.edit().clear().apply(); _state.value = AppState(route = Route.Pairing); beginPairing() } }
    fun close() { pairingPoll?.cancel(); sourceDiscovery?.cancel(); searchJob?.cancel(); nextEpisodeJob?.cancel(); playerChromeJob?.cancel(); stopPlayback((_state.value.route as? Route.Player)?.media); player.close() }
    private fun requireProfile() = checkNotNull(_state.value.selectedProfile).id
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
    private fun startProgressPersistence(media: Media) {
        progressJob?.cancel()
        if (media.type == "live") return
        progressJob = scope.launch {
            while (isActive) {
                delay(15_000)
                persistProgress(media, player.state.value.positionMillis)
            }
        }
    }
    private suspend fun persistProgress(media: Media, positionMillis: Long) {
        if (media.type != "live" && positionMillis > 0) {
            _state.value.selectedProfile?.let { profile -> runCatching { gateway.updateProgress(profile.id, media, positionMillis) } }
        }
    }
    private fun stopPlayback(media: Media? = null) {
        val position = player.state.value.positionMillis
        progressJob?.cancel()
        media?.let { item -> scope.launch { persistProgress(item, position) } }
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
