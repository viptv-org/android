package org.viptv.app

import android.content.Context
import com.getair.video.AndroidMedia3BackendFactory
import com.getair.video.AndroidMedia3VideoPlayer
import com.getair.video.PlaybackKind
import com.getair.video.PlaybackStatus
import com.getair.video.PlaybackEvent
import com.getair.video.PlaybackErrorCode
import com.getair.video.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppController(context: Context, private val origin: String = "https://viptv.syek.tech") {
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    private val gateway = VipTvHttpGateway(origin, store.getString("access", null))
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    val player: AndroidMedia3VideoPlayer = AndroidMedia3BackendFactory(context).createAndroidPlayer()
    private var pairingPoll: Job? = null
    private var sourceDiscovery: Job? = null
    private var discoverJob: Job? = null
    private var searchJob: Job? = null
    private var nextEpisodeJob: Job? = null
    private var playerChromeJob: Job? = null
    private var playerMenuOpen = false
    private var heartbeatJob: Job? = null
    private var progressJob: Job? = null
    private var playbackSessionId: String? = null
    /** Nonzero only when a managed delivery segment begins at title time. */
    private var playbackTitleOffsetMillis = 0L
    private var playbackTitleDurationMillis: Long? = null
    private var lastTrustedTitlePositionMillis = 0L
    /** Absolute title time frozen by a viewer pause on rolling managed HLS. */
    private var managedPauseAnchorMillis: Long? = null
    private var afterParentUnlock: (suspend () -> Unit)? = null
    private var selectedAudioTrackIndex: Int? = null
    private var selectedSubtitleTrackIndex: Int? = null
    private var subtitlesOff = false
    private var continuationRestore: Route.Player? = null
    private var continuationWasPlaying = false
    private var detailReturnDestination: Destination? = null
    private var guideGeneration = 0L
    private var discoverGeneration = 0L
    private var managedRecoveryKey: String? = null
    /** Suppresses duplicate Media3 failure events while the one permitted same-source recovery is awaiting the server. */
    private var managedRecoveryInFlightKey: String? = null
    private val playbackPrepareMutex = Mutex()
    private var playbackGeneration = 0L
    private val guideScheduleCache = mutableMapOf<String, GuideScheduleCache>()

    init {
        scope.launch { player.events.collect(::onPlayerEvent) }
        restore()
    }
    fun beginPairing() = scope.launch {
        update(loading = true, message = null)
        runCatching { gateway.startDevicePairing("VIPTV Android TV") }.onSuccess { code ->
            _state.value = _state.value.copy(route = Route.Pairing, deviceCode = code, loading = false)
            pairingPoll?.cancel(); pairingPoll = launch { pollPairing(code) }
        }.onFailure { fail(it) }
    }
    private suspend fun pollPairing(code: DeviceCode) {
        var intervalSeconds = code.intervalSeconds.coerceAtLeast(1)
        repeat(120) {
            delay(intervalSeconds * 1_000)
            try {
                when (val result = gateway.exchangeDeviceCode(code.code)) {
                    is DevicePollResult.Authorized -> {
                        persist(result.session)
                        loadProfiles(result.session.profileId)
                        return
                    }
                    DevicePollResult.Pending -> intervalSeconds = DevicePollPolicy.nextIntervalSeconds(code.intervalSeconds, intervalSeconds, rateLimited = false)
                    DevicePollResult.RateLimited -> intervalSeconds = DevicePollPolicy.nextIntervalSeconds(code.intervalSeconds, intervalSeconds, rateLimited = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = (error as? GatewayError)?.message ?: "Could not check pairing. Waiting to retry…",
                )
            }
        }
        update(loading = false, message = "Pairing expired. Try again.")
    }
    private fun restore() = scope.launch {
        val refresh = store.getString("refresh", null)
        if (refresh == null) { _state.value = AppState(route = Route.Pairing); beginPairing(); return@launch }
        runCatching { gateway.refresh(refresh) }.onSuccess { persist(it); loadProfiles(it.profileId) }.onFailure { error ->
            if (AuthSessionPolicy.discardStoredGrant((error as? GatewayError)?.status)) {
                store.edit().clear().apply()
                _state.value = AppState(route = Route.Pairing)
                beginPairing()
            } else {
                // Keep a transiently unavailable grant intact. Pairing again
                // would create needless device codes and orphan this session.
                _state.value = AppState(
                    route = Route.Pairing,
                    message = "Could not restore your session. Check your connection and try again.",
                )
            }
        }
    }
    /** Retries a retained refresh grant; starts device pairing only when none exists. */
    fun retryAuthentication() { if (store.getString("refresh", null) == null) beginPairing() else restore() }
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

    /** Fetches declared catalogs before exposing Discover; no synthetic filters or catalog IDs. */
    fun openDiscover() {
        discoverJob?.cancel()
        val generation = ++discoverGeneration
        discoverJob = scope.launch {
            val previous = _state.value.discoverUi
            _state.value = _state.value.copy(
                route = Route.Browse(Destination.Discover),
                discoverUi = previous.copy(loading = true, error = null),
                loading = false,
                message = null,
            )
            try {
                val catalogs = gateway.catalogs()
                if (!isCurrentDiscover(generation)) return@launch
                val catalog = catalogs.firstOrNull { it.key == previous.selectedCatalogKey && it.key.type == previous.selectedType }
                    ?: DiscoverPolicy.firstCatalog(catalogs, previous.selectedType)
                if (catalog == null) {
                    _state.value = _state.value.copy(discoverUi = DiscoverUiState(catalogs = catalogs, loading = false, error = "No catalogs are available."))
                    return@launch
                }
                val filters = if (catalog.key == previous.selectedCatalogKey) previous.selectedFilters else DiscoverPolicy.defaults(catalog)
                _state.value = _state.value.copy(
                    discoverUi = previous.copy(
                        catalogs = catalogs,
                        selectedType = catalog.key.type,
                        selectedCatalogKey = catalog.key,
                        selectedFilters = filters,
                        loading = true,
                        error = null,
                    ),
                )
                requestDiscoverPage(generation, catalogs, catalog, filters, skip = 0, previousSkips = emptyList())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isCurrentDiscover(generation)) {
                    _state.value = _state.value.copy(discoverUi = previous.copy(loading = false, error = "Couldn't load catalogs. Press OK to retry."))
                }
            }
        }
    }

    fun setDiscoverType(type: String) {
        val current = _state.value.discoverUi
        val catalog = DiscoverPolicy.firstCatalog(current.catalogs, type) ?: return
        startDiscoverRequest(catalog, DiscoverPolicy.defaults(catalog), skip = 0, previousSkips = emptyList(), selectedType = type)
    }

    fun setDiscoverCatalog(key: CatalogKey) {
        val catalog = _state.value.discoverUi.catalogs.firstOrNull { it.key == key } ?: return
        startDiscoverRequest(catalog, DiscoverPolicy.defaults(catalog), skip = 0, previousSkips = emptyList(), selectedType = catalog.key.type)
    }

    /** Search, genre, and extras all reset the forward-only server cursor. */
    fun setDiscoverFilter(key: String, value: String?) {
        val current = _state.value.discoverUi
        val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
        val declared = catalog.filters.firstOrNull { it.name == key }
        if (key != "search" && declared == null) return
        if (key == "search" && !catalog.supportsSearch && declared?.kind != CatalogFilterKind.Search) return
        val normalized = value?.trim().orEmpty()
        val replacement = when {
            normalized.isNotBlank() -> normalized
            declared?.required == true -> declared.defaultValue ?: declared.options.firstOrNull().orEmpty()
            else -> ""
        }
        val filters = current.selectedFilters.toMutableMap().apply {
            if (replacement.isBlank()) remove(key) else put(key, replacement)
        }
        if (filters == current.selectedFilters) return
        startDiscoverRequest(catalog, filters, skip = 0, previousSkips = emptyList(), selectedType = current.selectedType)
    }

    fun changeDiscoverPage(delta: Int) {
        val current = _state.value.discoverUi
        val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
        when {
            delta > 0 && current.nextSkip != null -> startDiscoverRequest(
                catalog, current.selectedFilters, current.nextSkip,
                current.previousSkips + current.requestedSkip, current.selectedType,
            )
            delta < 0 && current.previousSkips.isNotEmpty() -> startDiscoverRequest(
                catalog, current.selectedFilters, current.previousSkips.last(),
                current.previousSkips.dropLast(1), current.selectedType,
            )
        }
    }

    private fun startDiscoverRequest(
        catalog: DiscoverCatalog,
        filters: Map<String, String>,
        skip: Int,
        previousSkips: List<Int>,
        selectedType: String,
    ) {
        discoverJob?.cancel()
        val generation = ++discoverGeneration
        discoverJob = scope.launch {
            val catalogs = _state.value.discoverUi.catalogs
            _state.value = _state.value.copy(
                route = Route.Browse(Destination.Discover),
                discoverUi = _state.value.discoverUi.copy(
                    catalogs = catalogs,
                    selectedType = selectedType,
                    selectedCatalogKey = catalog.key,
                    selectedFilters = filters,
                    requestedSkip = skip,
                    previousSkips = previousSkips,
                    loading = true,
                    error = null,
                ),
                loading = false,
                message = null,
            )
            requestDiscoverPage(generation, catalogs, catalog, filters, skip, previousSkips)
        }
    }

    private suspend fun requestDiscoverPage(
        generation: Long,
        catalogs: List<DiscoverCatalog>,
        catalog: DiscoverCatalog,
        filters: Map<String, String>,
        skip: Int,
        previousSkips: List<Int>,
    ) {
        try {
            val page = gateway.discover(DiscoverPolicy.request(catalog, filters, skip))
            if (!isCurrentDiscover(generation) || page.catalog != catalog.key) return
            _state.value = _state.value.copy(
                discoverUi = _state.value.discoverUi.copy(
                    catalogs = catalogs,
                    selectedCatalogKey = catalog.key,
                    selectedFilters = filters,
                    items = page.items,
                    requestedSkip = page.requestedSkip,
                    nextSkip = page.nextSkip?.takeIf { page.hasMore },
                    previousSkips = previousSkips,
                    loading = false,
                    error = null,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (isCurrentDiscover(generation)) {
                _state.value = _state.value.copy(discoverUi = _state.value.discoverUi.copy(loading = false, error = "Couldn't load this catalog. Press OK to retry."))
            }
        }
    }

    private fun isCurrentDiscover(generation: Long): Boolean =
        generation == discoverGeneration && _state.value.route == Route.Browse(Destination.Discover)
    fun open(media: Media) = scope.launch {
        val origin = (_state.value.route as? Route.Browse)?.destination
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
            detailReturnDestination = origin
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

    fun start(media: Media, source: Source, explicitResume: Boolean = false) {
        val requestGeneration = ++playbackGeneration
        scope.launch {
            managedRecoveryKey = null
            sourceDiscovery?.cancel()
            if (!PlaybackRequestPolicy.isCurrent(requestGeneration, playbackGeneration)) return@launch
            prepareAndStart(media, source, explicitResume, playWhenReady = true, resetTrackChoices = true, expectedGeneration = requestGeneration)
        }
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
        /** Captured before this request can queue on the preparation mutex. */
        expectedGeneration: Long,
    ): Boolean = playbackPrepareMutex.withLock {
        if (!PlaybackRequestPolicy.mayPrepareAfterMutexWait(expectedGeneration, playbackGeneration)) return@withLock false
        prepareAndStartLocked(media, source, explicitResume, playWhenReady, resetTrackChoices, expectedGeneration)
    }

    private suspend fun prepareAndStartLocked(
        media: Media,
        source: Source,
        explicitResume: Boolean,
        playWhenReady: Boolean,
        resetTrackChoices: Boolean,
        generation: Long,
    ): Boolean {
        val returnDestination = when (val current = _state.value.route) {
            is Route.Sources -> PlaybackReturnPolicy.afterSourceStart(current.resume)
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
                    update(loading = false, message = "This source cannot resume at the requested position. Choose another source.")
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
                    route = Route.Player(playbackMedia, source, returnDestination),
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
            update(loading = false)
            throw error
        } catch (error: Throwable) {
            fail(error)
            false
        }
    }
    private fun onPlayerEvent(event: PlaybackEvent) {
        if (event !is PlaybackEvent.Failed || _state.value.dialog?.kind == DialogKind.PlaybackRecovery) return
        val active = _state.value.route as? Route.Player ?: return
        val route = active.copy(media = snapshotPlaybackMedia(active))
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

    private fun snapshotPlaybackMedia(route: Route.Player): Media =
        PlaybackRecoveryPolicy.snapshot(route.media, absolutePositionMillis(), titleDurationMillis())

    /** Runtime failure has no automatic source fallback: users retain exact retry, source choice, or Back. */
    private fun showPlaybackRecovery(route: Route.Player) {
        _state.value = _state.value.copy(
            route = route,
            dialog = DialogState(
                kind = DialogKind.PlaybackRecovery,
                title = "Playback unavailable",
                media = route.media,
                source = route.source,
            ),
            playerChromeVisible = true,
            loading = false,
            message = null,
        )
    }

    fun retryPlaybackRecovery() {
        val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
        val media = dialog.media ?: return
        val source = dialog.source ?: return
        _state.value = _state.value.copy(dialog = null, message = null)
        start(media, source, explicitResume = false)
    }

    fun chooseAnotherSourceForRecovery() {
        val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
        val media = dialog.media ?: return
        stopPlayback(media)
        _state.value = _state.value.copy(dialog = null, message = null)
        chooseSources(media, resume = false)
    }

    fun backFromPlaybackRecovery() {
        val dialog = _state.value.dialog?.takeIf { it.kind == DialogKind.PlaybackRecovery } ?: return
        val route = _state.value.route as? Route.Player ?: return
        val media = dialog.media ?: snapshotPlaybackMedia(route)
        exitPlayer(route, media)
    }

    /** Controlled continuation is the only non-Resume automatic source path. */
    fun nextEpisode(outgoing: Media) {
        nextEpisodeJob?.cancel()
        val outgoingRoute = _state.value.route as? Route.Player ?: return
        val requestGeneration = ++playbackGeneration
        continuationRestore = outgoingRoute
        continuationWasPlaying = player.state.value.isPlaying
        nextEpisodeJob = scope.launch {
            player.pause()
            _state.value = _state.value.copy(message = "LOADING", loading = false)
            try {
                val result = gateway.nextEpisode(requireProfile(), outgoing)
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
                            val started = prepareAndStart(next, selected, explicitResume = false, playWhenReady = continuationWasPlaying, resetTrackChoices = true, expectedGeneration = requestGeneration)
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
    /** Invalidates an in-flight source/playback request before a user leaves its surface. */
    private fun invalidatePlaybackPreparation() { playbackGeneration++ }

    fun back() { handleBack() }
    fun consumesBack(state: AppState = _state.value): Boolean = BackAvailabilityPolicy.consumes(state)
    /** Returns false only when Android should handle app exit at a root gate/page. */
    fun handleBack(): Boolean {
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
            is Route.Sources -> { invalidatePlaybackPreparation(); sourceDiscovery?.cancel(); _state.value = _state.value.copy(route = Route.Details(route.media)) }
            is Route.Profiles -> if (_state.value.managingProfiles) _state.value = _state.value.copy(managingProfiles = false) else if (_state.value.selectedProfile != null) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null) else return false
            is Route.Details -> {
                val destination = DetailReturnPolicy.destination(detailReturnDestination)
                detailReturnDestination = null
                _state.value = _state.value.copy(route = Route.Browse(destination), dialog = null, pinPrompt = null)
            }
            is Route.Search, is Route.Settings, is Route.Addons, is Route.ProfileEditor, is Route.Guide -> _state.value = _state.value.copy(route = Route.Browse(Destination.Home), dialog = null, pinPrompt = null)
            is Route.Browse -> if (route.destination != Destination.Home) {
                if (route.destination == Destination.Discover) {
                    discoverJob?.cancel()
                    discoverGeneration++
                }
                _state.value = _state.value.copy(route = Route.Browse(Destination.Home))
            } else return false
            Route.Pairing -> return false
        }
        return true
    }
    private fun exitPlayer(route: Route.Player, media: Media) {
        stopPlayback(media)
        _state.value = _state.value.copy(
            route = PlaybackRecoveryPolicy.returnRoute(route.returnDestination, media),
            dialog = null,
            message = null,
        )
    }

    /** The Media3 adapter exposes session-relative HLS time; map it once to title time. */
    fun absolutePositionMillis(): Long {
        val candidate = PlaybackTimelinePolicy.absolutePositionMillis(player.state.value.positionMillis, playbackTitleOffsetMillis)
        managedPauseAnchorMillis?.let { return it }
        return if (player.state.value.status == PlaybackStatus.Error && lastTrustedTitlePositionMillis > 0L) {
            lastTrustedTitlePositionMillis
        } else {
            lastTrustedTitlePositionMillis = candidate
            candidate
        }
    }
    fun titleDurationMillis(): Long? = playbackTitleDurationMillis ?: player.state.value.timeline?.durationMillis

    /** Pause captures title time before Media3's rolling window can advance. */
    fun pausePlayback() {
        val route = _state.value.route as? Route.Player ?: return
        if (ManagedPausePolicy.usesAnchor(_state.value.playbackDeliveryMode, route.media.type == "live")) {
            managedPauseAnchorMillis = absolutePositionMillis()
        }
        player.pause()
        showPlayerChrome()
    }

    /** Managed paused output resumes by preparing the original title coordinate. */
    fun resumePlayback() {
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

    fun saveProgress(media: Media) {
        val profileId = _state.value.selectedProfile?.id
        val position = absolutePositionMillis()
        scope.launch { persistProgress(profileId, media, position) }
    }
    fun previewSeek(deltaMillis: Long) {
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
    fun commitSeek() {
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
    fun showPlayerChrome() {
        if (_state.value.route !is Route.Player) return
        _state.value = _state.value.copy(playerChromeVisible = true)
        schedulePlayerChromeDismissal()
    }
    /** Local track dialogs report ownership so the seven-second timer cannot hide their context. */
    fun setPlayerMenuOpen(open: Boolean) {
        playerMenuOpen = open
        if (open) {
            playerChromeJob?.cancel()
            if (_state.value.route is Route.Player) _state.value = _state.value.copy(playerChromeVisible = true)
        } else {
            schedulePlayerChromeDismissal()
        }
    }
    /** Each edit replaces prior work; a late response cannot repopulate a cleared query. */
    fun search(query: String) {
        searchJob?.cancel()
        val normalized = query.trim()
        _state.value = _state.value.copy(
            searchQuery = query.take(256),
            searchResults = emptyList(),
            searchSections = emptyList(),
            searchStatus = if (normalized.isEmpty()) "Find your next favorite." else "Searching…",
            loading = false,
            message = null,
        )
        if (normalized.isEmpty()) return
        searchJob = scope.launch {
            delay(650)
            try {
                val results = gateway.search(normalized)
                if (isActive) {
                    val count = results.sections.sumOf { it.items.size }
                    val baseStatus = if (count == 0) "No results. Try another title." else "$count results"
                    _state.value = _state.value.copy(
                        searchSections = results.sections,
                        searchResults = results.sections.flatMap(SearchSection::items),
                        searchStatus = if (results.partialFailure) "$baseStatus  Some sources couldn't load." else baseStatus,
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isActive) _state.value = _state.value.copy(loading = false, searchStatus = "Searching…  Some sources couldn't load.")
            }
        }
    }
    fun openMyList() = scope.launch { update(loading = true); runCatching { gateway.favorites(requireProfile()) }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.MyList), favorites = it, catalog = it, loading = false) }.onFailure(::fail) }
    fun openQueue() = scope.launch { update(loading = true); runCatching { gateway.queue(requireProfile()) }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.Home), queue = it, loading = false) }.onFailure(::fail) }
    fun openLive() = scope.launch { update(loading = true); runCatching { gateway.live() }.onSuccess { _state.value = _state.value.copy(route = Route.Browse(Destination.Live), liveChannels = it, loading = false) }.onFailure(::fail) }

    /** Opens a 40-channel Guide page, then fills the selected five rows plus two look-ahead rows. */
    fun openGuide(channel: LiveChannel) = scope.launch {
        val generation = ++guideGeneration
        val channels = runCatching { if (_state.value.liveChannels.isEmpty()) gateway.live() else _state.value.liveChannels }
            .getOrElse { error -> fail(error); return@launch }
            .let { available -> if (available.any { it.id == channel.id }) available else listOf(channel) + available }
        val page = GuidePolicy.pageFor(channels, channel.id)
        val now = System.currentTimeMillis()
        val prior = _state.value.guideUi
        val guide = prior.copy(
            channels = channels,
            selectedChannelId = channel.id,
            page = page,
            windowStartMillis = prior.windowStartMillis.takeIf { it > 0 } ?: GuidePolicy.nowWindow(now),
            followsNow = true,
        )
        _state.value = _state.value.copy(
            route = Route.Guide(channel), liveChannels = channels, guideUi = guide,
            guide = guide.schedulesByChannelId[channel.id].orEmpty(), loading = false, message = null,
        )
        refreshGuideRows(generation)
    }

    fun selectGuideChannel(channel: LiveChannel) {
        val current = _state.value.guideUi
        if (current.selectedChannelId == channel.id) return
        val generation = ++guideGeneration
        val channels = if (current.channels.any { it.id == channel.id }) current.channels else listOf(channel) + current.channels
        val guide = current.copy(channels = channels, selectedChannelId = channel.id, page = GuidePolicy.pageFor(channels, channel.id))
        _state.value = _state.value.copy(route = Route.Guide(channel), guideUi = guide, guide = guide.schedulesByChannelId[channel.id].orEmpty(), message = null)
        scope.launch { refreshGuideRows(generation) }
    }

    fun changeGuidePage(delta: Int) {
        val current = _state.value.guideUi
        if (current.channels.isEmpty()) return
        val maxPage = ((current.channels.size - 1).coerceAtLeast(0) / GuidePolicy.PAGE_SIZE)
        val page = (current.page + delta).coerceIn(0, maxPage)
        if (page == current.page) return
        val first = current.channels[page * GuidePolicy.PAGE_SIZE]
        val generation = ++guideGeneration
        val guide = current.copy(page = page, selectedChannelId = first.id)
        _state.value = _state.value.copy(route = Route.Guide(first), guideUi = guide, guide = guide.schedulesByChannelId[first.id].orEmpty())
        scope.launch { refreshGuideRows(generation) }
    }

    fun shiftGuideWindow(hours: Int) {
        val current = _state.value.guideUi
        if (current.windowStartMillis == 0L) return
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(guideUi = current.copy(windowStartMillis = GuidePolicy.shiftedWindow(current.windowStartMillis, hours, now), followsNow = false))
    }

    fun followGuideNow() {
        val current = _state.value.guideUi
        _state.value = _state.value.copy(guideUi = current.copy(windowStartMillis = GuidePolicy.nowWindow(System.currentTimeMillis()), followsNow = true))
    }

    fun watchGuideChannel(channel: LiveChannel) = chooseSources(Media(channel.id, "live", channel.name))

    private suspend fun refreshGuideRows(generation: Long) {
        val before = _state.value.guideUi
        val ids = GuidePolicy.visibleRows(before).plus(before.channels.drop(before.page * GuidePolicy.PAGE_SIZE + GuidePolicy.VISIBLE_ROWS).take(2))
            .map(LiveChannel::id).distinct()
        val now = System.currentTimeMillis()
        val valid = ids.filter { id -> guideScheduleCache[id]?.let { now < it.expiresAtMillis } == true }
        val missing = ids - valid.toSet()
        if (missing.isEmpty()) {
            publishGuideCache(generation)
            return
        }
        _state.value = _state.value.copy(guideUi = before.copy(loadingChannelIds = before.loadingChannelIds + missing))
        coroutineScope {
            missing.chunked(3).forEach { batch ->
                batch.map { id -> async { id to runCatching { gateway.guide(id) } } }.awaitAll().forEach { (id, result) ->
                    val fetchedAt = System.currentTimeMillis()
                    guideScheduleCache[id] = result.fold(
                        onSuccess = { GuideScheduleCache(it, fetchedAt + 300_000L) },
                        onFailure = { GuideScheduleCache(emptyList(), fetchedAt + 60_000L) },
                    )
                }
            }
        }
        publishGuideCache(generation)
    }

    private fun publishGuideCache(generation: Long) {
        if (generation != guideGeneration || _state.value.route !is Route.Guide) return
        val current = _state.value.guideUi
        val schedules = current.schedulesByChannelId + guideScheduleCache.mapValues { it.value.entries }
        val selected = current.selectedChannelId
        _state.value = _state.value.copy(guideUi = current.copy(schedulesByChannelId = schedules, loadingChannelIds = emptySet()), guide = selected?.let { schedules[it] }.orEmpty(), loading = false)
    }

    /** Settings remains usable when the optional server-status endpoint is unavailable. */
    fun openSettings() = scope.launch {
        _state.value = _state.value.copy(route = Route.Settings, loading = false, message = null)
        val profileId = runCatching(::requireProfile).getOrElse { return@launch }
        coroutineScope {
            val preferences = async { runCatching { gateway.preferences(profileId) } }
            val addons = async { runCatching { gateway.addons() } }
            val about = async { runCatching { gateway.serverAbout() } }
            val prefResult = preferences.await()
            val addonResult = addons.await()
            val aboutResult = about.await()
            if (prefResult.isFailure && addonResult.isFailure) {
                fail(prefResult.exceptionOrNull() ?: addonResult.exceptionOrNull()!!)
                return@coroutineScope
            }
            _state.value = _state.value.copy(
                route = Route.Settings,
                preferences = prefResult.getOrElse { _state.value.preferences },
                addons = addonResult.getOrElse { _state.value.addons },
                serverAbout = aboutResult.getOrNull(),
                loading = false,
                message = if (aboutResult.isFailure) "Server information is unavailable." else null,
            )
        }
    }
    fun installAddon(manifestUrl: String) = scope.launch {
        guarded("Enter parent PIN") {
            gateway.addAddon(manifestUrl)
            openSettings()
        }
    }
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
    fun close() { pairingPoll?.cancel(); sourceDiscovery?.cancel(); discoverJob?.cancel(); searchJob?.cancel(); nextEpisodeJob?.cancel(); playerChromeJob?.cancel(); stopPlayback((_state.value.route as? Route.Player)?.media); player.close() }
    private data class GuideScheduleCache(val entries: List<GuideProgramme>, val expiresAtMillis: Long)

    private fun requireProfile() = checkNotNull(_state.value.selectedProfile).id
    private fun schedulePlayerChromeDismissal() {
        playerChromeJob?.cancel()
        playerChromeJob = scope.launch {
            delay(7_000)
            if (PlayerChromePolicy.shouldAutoHide(_state.value.route is Route.Player, player.state.value.isPlaying, playerMenuOpen, _state.value.seekPreview != null)) _state.value = _state.value.copy(playerChromeVisible = false)
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
        val profileId = _state.value.selectedProfile?.id
        progressJob = scope.launch {
            while (isActive) {
                delay(15_000)
                persistProgress(profileId, media, absolutePositionMillis())
            }
        }
    }
    private suspend fun persistProgress(profileId: String?, media: Media, positionMillis: Long) {
        if (media.type != "live" && positionMillis > 0) {
            profileId?.let { id -> runCatching { gateway.updateProgress(id, media, positionMillis) } }
        }
    }
    private fun stopPlayback(media: Media? = null) {
        invalidatePlaybackPreparation()
        val position = absolutePositionMillis()
        val profileId = _state.value.selectedProfile?.id
        progressJob?.cancel()
        media?.let { item -> scope.launch { persistProgress(profileId, item, position) } }
        player.stop()
        playbackTitleOffsetMillis = 0L
        playbackTitleDurationMillis = null
        lastTrustedTitlePositionMillis = 0L
        managedPauseAnchorMillis = null
        heartbeatJob?.cancel()
        playerMenuOpen = false
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
