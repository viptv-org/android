package org.viptv.app

import android.content.Context
import org.viptv.video.AndroidMedia3BackendFactory
import org.viptv.video.AndroidMedia3VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AppController(context: Context, private val origin: String) {
    private val television = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    internal val gateway = VipTvHttpGateway(origin, store.getString("access", null), ::refreshAccessToken, television)
    internal val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val coreSession = CoreSession(origin, store, scope, gateway::setAccessToken, ::renderSession)
    private val sessionRefreshMutex = Mutex()
    private var pendingCoreAction: (() -> Unit)? = null
    private var sessionRenderGeneration = 0L
    private var keepProfilesOnIdentityRefresh = false
    private var quietSessionAdoption = false
    private var authenticationGeneration = 0L
    private var expiredSessionNotice: String? = null
    private var verifiedIdentity: org.viptv.core.wire.Identity? = null
    private var sessionRefreshJob: Deferred<Result<String?>>? = null
    private var pendingProfileAfterRefresh: Profile? = null
    private var pendingProfileRefreshWait: Job? = null
    internal val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val foregroundValidation = ForegroundValidation(scope, gateway::foregroundIdentity)
    internal val backendFactory = AndroidMedia3BackendFactory(context)
    private val playerDelegate = lazy {
        backendFactory.createAndroidPlayer().also { instance ->
            scope.launch { instance.events.collect(::onPlayerEvent) }
        }
    }
    val player: AndroidMedia3VideoPlayer get() = playerDelegate.value
    internal var homeJob: Job? = null
    internal var homeContentFocused = false
    private var homeRevisionJob: Job? = null
    private var homeWatcherKey: String? = null
    private var homeForeground = true
    private var renderedCatalogRevision: String? = null
    private var revisionOwner: String? = null
    private var pairingPoll: Job? = null
    private var loginJob: Job? = null
    internal var playbackStartJob: Job? = null
    internal var sourceDiscovery: Job? = null
    internal var probedCapabilities: PlaybackClientCapabilities? = null
    internal var capabilityProbe: Job? = null
    internal fun rankCapabilities(): PlaybackClientCapabilities? =
        if (playerDelegate.isInitialized()) PlaybackClientCapabilities.from(player.capabilities.value) else probedCapabilities
    internal val sourcePreview = TitleSourcePreview(scope, gateway::sources, { preview ->
        _state.value = _state.value.copy(sourceSummary = preview?.let {
            SourceSummary(it.key, SourceRankPolicy.order(it.sources, rankCapabilities(), _state.value.preferences.audioLanguage).firstOrNull(),
                it.sources.size, it.done, it.error != null)
        })
    })
    internal var queueContinuationJob: Job? = null
    /** Last queue/Home refresh wins over any earlier response racing Undo. */
    internal var homeRefreshGeneration = 0L
    private val homeMetadataGate = Semaphore(3)
    private val homeMetadataRequested = mutableSetOf<String>()
    internal var libraryRevision = 0L
    internal var discoverJob: Job? = null
    internal var searchJob: Job? = null
    internal var nextEpisodeJob: Job? = null
    internal var upNextJob: Job? = null
    internal var playerChromeJob: Job? = null
    internal var playerMenuOpen = false
    private var heartbeatJob: Job? = null
    private var progressJob: Job? = null
    private var playbackSessionId: String? = null
    /** Nonzero only when a managed delivery segment begins at title time. */
    internal var playbackTitleOffsetMillis = 0L
    internal var playbackTitleDurationMillis: Long? = null
    internal var lastTrustedTitlePositionMillis = 0L
    /** Absolute title time frozen by a viewer pause on rolling managed HLS. */
    internal var managedPauseAnchorMillis: Long? = null
    internal var afterParentUnlock: (suspend () -> Unit)? = null
    internal var selectedAudioTrackIndex: Int? = null
    internal var selectedSubtitleTrackIndex: Int? = null
    internal var subtitlesOff = false
    internal var continuationRestore: Route.Player? = null
    internal var continuationWasPlaying = false
    internal var detailReturnDestination: Destination? = null
    internal var detailReturnRoute: Route? = null
    internal var detailJob: Job? = null
    internal var detailGeneration = 0L
    internal var guideGeneration = 0L
    internal var guideBrowseGeneration = 0L
    internal var guideBrowseJob: Job? = null
    internal var guideRowsJob: Job? = null
    internal var guidePageJob: Job? = null
    internal val guidePages = LivePageWindow()
    internal val guideCategories by lazy {
        GuideCategoryPager(scope, gateway::liveCategoriesV2, {
            val state = _state.value
            state.selectedProfile?.id?.takeIf { state.route is Route.Guide && !state.loading && state.preparingSourceId == null }
                ?.let { GuideCategoryScope(it, state.guideUi.catalogId, state.guideUi.generation) }
        }, { page ->
            _state.value = _state.value.copy(guideUi = _state.value.guideUi.copy(categories = page.items, categoryPage = page))
        }, ::fail)
    }
    internal var discoverGeneration = 0L
    internal var managedRecoveryKey: String? = null
    /** Suppresses duplicate Media3 failure events while the one permitted same-source recovery is awaiting the server. */
    internal var managedRecoveryInFlightKey: String? = null
    internal var activePlaybackDelivery = PlaybackDeliveryOptions()
    internal val playbackPrepareMutex = Mutex()
    internal var playbackGeneration = 0L
    internal var playbackInteractionVersion = 0L
    internal val guideScheduleCache = mutableMapOf<String, GuideScheduleCache>()
    internal var explicitResumeAwaitingCompletionKey: String? = null
    internal var autoNextMediaKey: String? = null

    init {
        scope.launch { foregroundValidation.result.collect(::acceptForegroundResult) }
        scope.launch { state.collect {
            updateHomeRevisionWatcher()
            sourcePreview.key?.let { key -> if (!SourcePreviewPolicy.keep(key, it.selectedProfile?.id, it.route)) sourcePreview.cancel() }
        } }
        coreSession.begin()
    }
    private fun acceptForegroundResult(result: ForegroundValidationResult?) {
            when (result) {
                is ForegroundValidationResult.Failed -> _state.value = _state.value.copy(foregroundError = result.message)
                is ForegroundValidationResult.Valid -> {
                    verifiedIdentity = result.identity
                    val profiles = result.identity.profiles.map(CoreModels::profileNormalized)
                    _state.value = _state.value.copy(foregroundError = null, profiles = profiles,
                        selectedProfile = _state.value.selectedProfile?.let { selected -> profiles.firstOrNull { it.id == selected.id } })
                }
                null -> _state.value = _state.value.copy(foregroundError = null)
                ForegroundValidationResult.Revoked -> {
                    cancelForegroundValidation()
                    authenticationGeneration++
                    verifiedIdentity = null; quietSessionAdoption = false
                    stopPlayback((_state.value.route as? Route.Player)?.media)
                    cancelAuthenticatedWork()
                    wipeCredentialsForOriginChange()
                    expiredSessionNotice = "Your session expired. Sign in again."
                    _state.value = AppState(sessionRestoring = false, message = expiredSessionNotice)
                    coreSession.begin()
                }
                is ForegroundValidationResult.ProfileUnavailable -> {
                    verifiedIdentity = result.identity
                    cancelForegroundValidation()
                    stopPlayback((_state.value.route as? Route.Player)?.media)
                    cancelAuthenticatedWork()
                    _state.value = AppState(sessionRestoring = false, route = Route.Profiles, profiles = result.identity.profiles.map(CoreModels::profileNormalized),
                        message = "This profile is no longer available. Choose a profile.")
                }
            }
        }
    fun onForeground() {
        homeForeground = true
        updateHomeRevisionWatcher()
        if (_state.value.route == Route.Pairing || _state.value.sessionRestoring) return
        val identity = verifiedIdentity ?: return
        foregroundValidation.onForeground(identity, _state.value.selectedProfile?.id)
        validatePlaybackOnForeground()
    }
    fun retryForegroundValidation() = onForeground()
    internal fun cancelForegroundValidation() {
        foregroundValidation.onBackground()
        pendingProfileAfterRefresh = null; pendingProfileRefreshWait?.cancel(); pendingProfileRefreshWait = null
        _state.value = _state.value.copy(foregroundError = null)
    }
    internal fun onBackground() {
        homeForeground = false
        homeRevisionJob?.cancel(); homeRevisionJob = null
        homeWatcherKey = null
        cancelForegroundValidation()
    }
    private fun updateHomeRevisionWatcher() {
        val state = _state.value
        val visible = homeForeground && state.route == Route.Browse(Destination.Home) &&
            state.selectedProfile != null && verifiedIdentity != null
        if (!visible) { homeRevisionJob?.cancel(); homeRevisionJob = null; homeWatcherKey = null; return }
        val profile = state.selectedProfile ?: return
        val account = verifiedIdentity?.account?.id ?: return
        val owner = "$origin\u0000$account\u0000${profile.id}"
        val watcherKey = "$owner\u0000$sessionRenderGeneration"
        if (homeRevisionJob?.isActive == true && homeWatcherKey == watcherKey) return
        homeRevisionJob?.cancel()
        homeWatcherKey = watcherKey
        if (revisionOwner != owner) { revisionOwner = owner; renderedCatalogRevision = null }
        val generation = sessionRenderGeneration
        homeRevisionJob = scope.launch {
            val watcherJob = currentCoroutineContext()[Job]
            val polling = HomeRevisionPolling(
                revision = gateway::catalogRevision,
                renderedRevision = { renderedCatalogRevision },
                activeLoad = { homeJob?.takeIf { it.isActive && it != watcherJob } },
                valid = { homeForeground && _state.value.route == Route.Browse(Destination.Home) &&
                    _state.value.selectedProfile?.id == profile.id &&
                    verifiedIdentity?.account?.id == account && generation == sessionRenderGeneration },
                refresh = { async { loadHome(profile, generation, atomicRefresh = true) }.await() },
            )
            var first = true
            var immediate = false
            while (isActive) {
                if (!first && !immediate) delay(15_000)
                first = false
                immediate = false
                try {
                    when (polling.check()) {
                        HomeRevisionCheck.Refreshed -> immediate = renderedCatalogRevision != null
                        HomeRevisionCheck.Unsupported, HomeRevisionCheck.ScopeLost -> break
                        HomeRevisionCheck.Unchanged, HomeRevisionCheck.RetryLater -> Unit
                    }
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                }
                catch (_: Exception) { /* Keep the current Home; the next interval retries. */ }
            }
        }
    }
    private fun cancelAuthenticatedWork() {
        sourcePreview.cancel()
        homeRevisionJob?.cancel(); homeRevisionJob = null
        homeWatcherKey = null
        renderedCatalogRevision = null; revisionOwner = null
        gateway.clearProfileCache()
        cancelGuideWork(); cancelPendingQueueContinuation(); cancelUpNext()
        homeRefreshGeneration++; homeJob?.cancel()
        detailGeneration++; detailJob?.cancel()
        discoverGeneration++; discoverJob?.cancel(); searchJob?.cancel(); sourceDiscovery?.cancel()
        nextEpisodeJob?.cancel(); queueContinuationJob?.cancel()
    }
    fun beginPairing() = scope.launch {
        loginJob?.cancel(); pairingPoll?.cancel()
        _state.value = _state.value.copy(pairingRequested = true, deviceCode = null, loading = true, message = expiredSessionNotice)
        runCatching { gateway.startDevicePairing(if (television) "VIPTV Android TV" else "VIPTV Android") }.onSuccess { code ->
            _state.value = _state.value.copy(route = Route.Pairing, deviceCode = code, loading = false)
            pairingPoll?.cancel(); pairingPoll = launch { pollPairing(code) }
        }.onFailure { fail(it) }
    }
    fun usePasswordSignIn() {
        pairingPoll?.cancel(); loginJob?.cancel()
        _state.value = _state.value.copy(pairingRequested = false, deviceCode = null, loading = false, message = null)
    }
    fun signIn(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) { update(message = "Enter your username and password."); return }
        cancelForegroundValidation(); authenticationGeneration++; sessionRefreshJob?.cancel()
        quietSessionAdoption = false
        loginJob?.cancel(); pairingPoll?.cancel()
        update(loading = true, message = null)
        loginJob = scope.launch {
            try {
                val session = gateway.signIn(username, password, if (television) "VIPTV Android TV" else "VIPTV Android")
                coreSession.adopt(session.coreJson)
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) {
                update(loading = false, message = when ((error as? GatewayError)?.status) {
                    401 -> "The username or password is incorrect."
                    429 -> "Too many attempts. Please try again shortly."
                    404 -> "This server needs the native sign-in update. You can use a device code for now."
                    else -> "Could not sign in. Check the server and your connection, then try again."
                })
            }
        }
    }
    private suspend fun pollPairing(code: DeviceCode) {
        var intervalSeconds = code.intervalSeconds.coerceAtLeast(1)
        repeat(120) {
            delay(intervalSeconds * 1_000)
            try {
                when (val result = gateway.exchangeDeviceCode(code.code)) {
                    is DevicePollResult.Authorized -> {
                        coreSession.adopt(result.session.coreJson)
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
    fun retryAuthentication() { if (_state.value.route == Route.Pairing && !_state.value.sessionRestoring) { if (television || _state.value.pairingRequested) beginPairing() else usePasswordSignIn() } else coreSession.retry() }
    private suspend fun renderSession(view: org.viptv.core.wire.ViewModel) {
        val generation = if (quietSessionAdoption) sessionRenderGeneration else ++sessionRenderGeneration
        val phase = view.phase.name
        _state.value = _state.value.copy(sessionRestoring = phase !in setOf("PAIRING", "READY", "PROFILES") && _state.value.route == Route.Pairing)
        val profiles = view.identity?.profiles?.map(CoreModels::profileNormalized) ?: _state.value.profiles
        when (phase) {
            "READY" -> {
                expiredSessionNotice = null
                pendingCoreAction = null
                val selected = view.selectedProfileId
                val chosen = profiles.firstOrNull { it.id == selected }
                if (quietSessionAdoption) {
                    quietSessionAdoption = false
                    view.identity?.let { identity -> verifiedIdentity?.let { prior ->
                        acceptForegroundResult(foregroundIdentityResult(prior, identity, _state.value.selectedProfile?.id))
                    } }
                    val requested = pendingProfileAfterRefresh
                    pendingProfileAfterRefresh = null
                    if (requested != null && _state.value.route == Route.Profiles) chooseProfile(requested)
                    return
                }
                quietSessionAdoption = false
                verifiedIdentity = view.identity
                if (keepProfilesOnIdentityRefresh) {
                    keepProfilesOnIdentityRefresh = false
                    _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, selectedProfile = chosen, loading = false)
                    return
                }
                val changed = chosen?.id != _state.value.selectedProfile?.id
                val enter = changed || _state.value.route == Route.Pairing || _state.value.route == Route.Profiles
                if (changed) _state.value = _state.value.copy(shelves = emptyList(), favorites = emptyList(), queue = emptyList(), discoverUi = DiscoverUiState(), searchQuery = "", searchResults = emptyList(), searchSections = emptyList(), guideUi = GuideUiState(), homeFocus = HomeFocusSnapshot())
                _state.value = _state.value.copy(profiles = profiles, selectedProfile = chosen, loading = false, message = null)
                chosen?.let { profile -> scope.launch { loadHome(profile, generation, enter) } }
            }
            "PROFILES" -> {
                verifiedIdentity = view.identity
                val lostProfile = _state.value.selectedProfile != null
                if (lostProfile) {
                    cancelForegroundValidation()
                    stopPlayback((_state.value.route as? Route.Player)?.media)
                    cancelAuthenticatedWork()
                }
                quietSessionAdoption = false; keepProfilesOnIdentityRefresh = false
                _state.value = AppState(sessionRestoring = false, route = Route.Profiles, profiles = profiles,
                    message = if (lostProfile) "This profile is no longer available. Choose a profile." else null)
            }
            "PAIRING" -> { gateway.clearProfileCache(); quietSessionAdoption = false; _state.value = AppState(route = Route.Pairing, sessionRestoring = false, message = expiredSessionNotice); if (television) beginPairing() }
            "ERROR" -> {
                if (quietSessionAdoption) {
                    quietSessionAdoption = false
                    if (pendingProfileAfterRefresh != null) {
                        pendingProfileAfterRefresh = null
                        _state.value = _state.value.copy(foregroundError = "Could not reconnect to VIPTV. Try again.")
                    }
                    return
                }
                val message = view.error ?: "Could not restore your session. Try again."
                _state.value = _state.value.copy(loading = false, profiles = if (keepProfilesOnIdentityRefresh) _state.value.profiles else profiles, message = message)
                if (view.errorStatus == 403) {
                    afterParentUnlock = { pendingCoreAction?.invoke() ?: coreSession.retry() }
                    _state.value = _state.value.copy(pinPrompt = PinPrompt("Enter parent PIN"))
                }
            }
            else -> if (!quietSessionAdoption) _state.value = _state.value.copy(loading = true, message = null)
        }
    }
    internal suspend fun loadHome(profile: Profile, generation: Long = sessionRenderGeneration, enter: Boolean = false, atomicRefresh: Boolean = false): Boolean {
        val job = currentCoroutineContext()[Job]
        if (homeJob !== job) homeJob?.cancel()
        homeJob = job
        val refresh = ++homeRefreshGeneration
        homeMetadataRequested.clear()
        val library = libraryRevision
        if (!atomicRefresh) _state.value = _state.value.copy(homeLoading = true)
        if (enter) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), selectedProfile = profile, loading = false)
        // This must precede /catalogs, including on the first Home load.
        val loadRevision = try { gateway.catalogRevision() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        var staged: List<HomeShelf>? = null
        val partial = java.util.concurrent.atomic.AtomicBoolean(false)
        val result = runCatching { (if (atomicRefresh) gateway.refreshHome(profile.id, _state.value.shelves, { partial.set(true) }) { shelves ->
            if (generation == sessionRenderGeneration && refresh == homeRefreshGeneration && _state.value.selectedProfile?.id == profile.id) staged = shelves
        } else gateway.home(profile.id) { shelves ->
            if (generation == sessionRenderGeneration && refresh == homeRefreshGeneration && _state.value.selectedProfile?.id == profile.id) {
                _state.value = _state.value.copy(
                    shelves = if (library == libraryRevision) shelves else shelves.map { if (it.id == "My List") it.copy(items = _state.value.favorites) else it },
                    queue = shelves.firstOrNull { it.isQueueShelf }?.items.orEmpty(),
                    favorites = if (library == libraryRevision) shelves.firstOrNull { it.id == "My List" }?.items.orEmpty() else _state.value.favorites,
                    loading = if (_state.value.route == Route.Browse(Destination.Home)) false else _state.value.loading,
                )
            }
        }) }
        if (atomicRefresh && result.isSuccess && generation == sessionRenderGeneration && refresh == homeRefreshGeneration &&
            _state.value.selectedProfile?.id == profile.id && homeForeground && _state.value.route == Route.Browse(Destination.Home)) {
            val shelves = staged ?: result.getOrThrow()
            val previous = _state.value
            val presented = if (library == libraryRevision) shelves else shelves.map { if (it.id == "My List") it.copy(items = previous.favorites) else it }
            _state.value = previous.copy(shelves = presented,
                queue = shelves.firstOrNull { it.isQueueShelf }?.items.orEmpty(),
                favorites = if (library == libraryRevision) shelves.firstOrNull { it.id == "My List" }?.items.orEmpty() else previous.favorites,
                homeFocus = HomeFocusPolicy.reconcile(previous.homeFocus, previous.shelves, presented,
                    restoreFocusedCard = homeContentFocused && previous.dialog == null && previous.pinPrompt == null))
        }
        result.onFailure { if (!atomicRefresh && generation == sessionRenderGeneration && refresh == homeRefreshGeneration && it !is CancellationException) fail(it) }
        if (!atomicRefresh && generation == sessionRenderGeneration && refresh == homeRefreshGeneration) _state.value = _state.value.copy(homeLoading = false)
        val accepted = result.isSuccess && generation == sessionRenderGeneration && refresh == homeRefreshGeneration &&
            _state.value.selectedProfile?.id == profile.id &&
            (!atomicRefresh || (homeForeground && _state.value.route == Route.Browse(Destination.Home)))
        if (accepted && !partial.get() && loadRevision != null && verifiedIdentity?.account?.id != null) {
            val owner = "$origin\u0000${verifiedIdentity?.account?.id}\u0000${profile.id}"
            if (revisionOwner == owner) renderedCatalogRevision = loadRevision
        }
        return accepted && !partial.get()
    }
    internal fun refreshProfileIdentity() { keepProfilesOnIdentityRefresh = true; coreSession.retry() }
    internal fun enrichVisibleHomeItem(media: Media) {
        val current = _state.value
        if (media.type == "live" || current.homeLoading || current.route != Route.Browse(Destination.Home)) return
        val profile = current.selectedProfile?.id ?: return
        val generation = homeRefreshGeneration
        val key = media.type + ":" + media.id
        if (!homeMetadataRequested.add(key)) return
        scope.launch {
            homeMetadataGate.withPermit {
                if (generation != homeRefreshGeneration || _state.value.selectedProfile?.id != profile) return@withPermit
                val rich = try { gateway.metadata(media) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return@withPermit }
                if (generation != homeRefreshGeneration || _state.value.selectedProfile?.id != profile) return@withPermit
                val state = _state.value
                val shelves = state.shelves.map { shelf -> shelf.copy(items = shelf.items.map { item ->
                    if (item.type == media.type && item.id == media.id) CoreModels.enrich(item, rich) else item
                }) }
                _state.value = state.copy(shelves = shelves, queue = shelves.firstOrNull { it.isQueueShelf }?.items.orEmpty())
            }
        }
    }
    fun chooseProfile(profile: Profile) {
        homeRevisionJob?.cancel(); homeRevisionJob = null
        renderedCatalogRevision = null; revisionOwner = null
        cancelForegroundValidation()
        gateway.clearProfileCache()
        val refresh = sessionRefreshJob?.takeIf { it.isActive }
        if (refresh != null || quietSessionAdoption) {
            pendingProfileAfterRefresh = profile
            val generation = authenticationGeneration
            if (refresh != null) pendingProfileRefreshWait = scope.launch {
                val error = refresh.await().exceptionOrNull()
                if (error != null && authenticationGeneration == generation &&
                    pendingProfileAfterRefresh?.id == profile.id && _state.value.route == Route.Profiles) {
                    pendingProfileAfterRefresh = null
                    acceptForegroundResult(if (error is GatewayError && error.status == 401) ForegroundValidationResult.Revoked
                        else ForegroundValidationResult.Failed("Could not reconnect to VIPTV. Try again."))
                }
            }
            return
        }
        quietSessionAdoption = false
        keepProfilesOnIdentityRefresh = false
        cancelUpNext()
        homeJob?.cancel()
        homeRefreshGeneration++
        detailGeneration++; detailJob?.cancel()
        searchJob?.cancel(); discoverJob?.cancel(); sourceDiscovery?.cancel()
        cancelPendingQueueContinuation()
        cancelGuideWork()
        pendingCoreAction = { coreSession.select(profile.id) }
        coreSession.select(profile.id)
    }
    fun setProfilePage(page: Int) { _state.value = _state.value.copy(profilePage = page.coerceIn(0, ((_state.value.profiles.size - 1).coerceAtLeast(0)) / 5)) }
    fun openProfileManagement() { _state.value = _state.value.copy(managingProfiles = true); navigate(Destination.Profile) }
    fun toggleProfileManagement() { _state.value = _state.value.copy(managingProfiles = !_state.value.managingProfiles) }

    fun signOut() = scope.launch { cancelForegroundValidation(); guarded("Enter parent PIN to sign out") { homeRevisionJob?.cancel(); homeRevisionJob = null; renderedCatalogRevision = null; revisionOwner = null; quietSessionAdoption = false; authenticationGeneration++; stopPlayback((_state.value.route as? Route.Player)?.media); pendingCoreAction = coreSession::signOut; coreSession.signOut() } }
    fun close() {
        sourcePreview.cancel(); capabilityProbe?.cancel()
        homeRevisionJob?.cancel(); homeRevisionJob = null
        foregroundValidation.close(); authenticationGeneration++; sessionRefreshJob?.cancel(); pendingProfileRefreshWait?.cancel()
        cancelGuideWork()
        loginJob?.cancel(); playbackStartJob?.cancel(); coreSession.close(); homeJob?.cancel(); detailJob?.cancel(); pairingPoll?.cancel(); sourceDiscovery?.cancel()
        queueContinuationJob?.cancel(); discoverJob?.cancel(); searchJob?.cancel(); nextEpisodeJob?.cancel(); playerChromeJob?.cancel()
        if (playerDelegate.isInitialized()) { stopPlayback((_state.value.route as? Route.Player)?.media); player.close() }
        scope.launch { delay(5000); scope.cancel() }
    }

    /**
     * The stored device grant belongs to the previous origin; an origin change
     * wipes it so the replacement controller starts device pairing instead of
     * replaying credentials against a different server.
     */
    fun wipeCredentialsForOriginChange() {
        gateway.clearProfileCache()
        store.edit().remove("access").remove("refresh").remove("core.session").remove("token").remove("profile").commit()
        gateway.setAccessToken(null)
    }
    internal data class GuideScheduleCache(val entries: List<GuideProgramme>, val expiresAtMillis: Long)

    internal fun requireProfile() = checkNotNull(_state.value.selectedProfile).id
    internal fun schedulePlayerChromeDismissal() {
        playerChromeJob?.cancel()
        playerChromeJob = scope.launch {
            delay(7_000)
            if (PlayerChromePolicy.shouldAutoHide(_state.value.route is Route.Player, player.state.value.isPlaying, playerMenuOpen, _state.value.seekPreview != null)) _state.value = _state.value.copy(playerChromeVisible = false)
        }
    }
    /** The native player has errored/replaced; stop heartbeat before exposing recovery actions. */
    internal fun retirePlaybackSession() {
        heartbeatJob?.cancel()
        progressJob?.cancel()
        val prior = playbackSessionId
        playbackSessionId = null
        prior?.let { id -> scope.launch { runCatching { gateway.stopPlayback(id) } } }
    }

    internal fun replacePlaybackSession(sessionId: String) {
        val prior = playbackSessionId
        heartbeatJob?.cancel()
        playbackSessionId = sessionId
        heartbeatJob = scope.launch {
            if (gateway.playbackRemainingMillis(sessionId) != null) {
                maintainPlaybackLease(
                    remaining = { gateway.playbackRemainingMillis(sessionId) },
                    interval = { gateway.playbackRenewAfterMillis(sessionId) },
                    renew = { gateway.heartbeat(sessionId) },
                    failed = { error -> rejectPlaybackLease(sessionId, error) },
                )
                return@launch
            }
            while (isActive) {
                delay(15_000)
                runCatching { gateway.heartbeat(sessionId) }
            }
        }
        if (prior != null && prior != sessionId) scope.launch { runCatching { gateway.stopPlayback(prior) } }
    }
    private suspend fun rejectPlaybackLease(sessionId: String, error: Throwable) {
        if (playbackSessionId != sessionId) return
        val route = (_state.value.route as? Route.Player)?.let { it.copy(media = snapshotPlaybackMedia(it)) }
        runCatching { player.stop() }
        if (playbackSessionId != sessionId) return
        retirePlaybackSession()
        fail(error)
        route?.let(::showPlaybackRecovery)
    }
    fun validatePlaybackOnForeground() {
        val id = playbackSessionId ?: return
        if (gateway.playbackRemainingMillis(id) == null) return
        val generation = playbackGeneration
        scope.launch {
            val wasPlaying = player.state.value.isPlaying
            try {
                if (wasPlaying) pausePlayback()
                val interaction = playbackInteractionVersion
                kotlinx.coroutines.withTimeout((gateway.playbackRemainingMillis(id) ?: 0).coerceAtLeast(1)) { gateway.heartbeat(id) }
                if (playbackSessionId == id && playbackGeneration == generation && wasPlaying && playbackInteractionVersion == interaction) resumePlayback()
            } catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) rejectPlaybackLease(id, GatewayError(410, "Playback authorization expired. Start playback again.", "playback_expired"))
                else throw cancelled
            } catch (error: Exception) { rejectPlaybackLease(id, error) }
        }
    }
    internal fun startProgressPersistence(media: Media) {
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
    internal suspend fun persistProgress(profileId: String?, media: Media, positionMillis: Long) {
        if (media.type != "live" && positionMillis > 0) {
            profileId?.let { id -> runCatching { gateway.updateProgress(id, media, positionMillis) } }
        }
    }
    internal fun stopPlayback(media: Media? = null): Job? {
        cancelUpNext()
        invalidatePlaybackPreparation()
        if (!playerDelegate.isInitialized()) return null
        val position = absolutePositionMillis()
        val profileId = _state.value.selectedProfile?.id
        progressJob?.cancel()
        val finalSave = media?.let { item -> scope.launch { persistProgress(profileId, item, position) } }
        player.stop()
        player.detachSurface()
        playbackTitleOffsetMillis = 0L
        playbackTitleDurationMillis = null
        lastTrustedTitlePositionMillis = 0L
        managedPauseAnchorMillis = null
        playerMenuOpen = false
        retirePlaybackSession()
        return finalSave
    }
    internal suspend fun guarded(pinTitle: String, action: suspend () -> Unit) {
        try { action() } catch (error: GatewayError) {
            if (error.status == 403 && (error.message.contains("PIN", true) || error.message.contains("Parent", true))) {
                afterParentUnlock = action
                _state.value = _state.value.copy(pinPrompt = PinPrompt(pinTitle), loading = false)
            } else fail(error)
        } catch (error: Throwable) { fail(error) }
    }
    /**
     * One coalesced imperative refresh for any authenticated 401: the mutex
     * collapses concurrent expirations into a single rotation, the rotated
     * tokens are persisted exactly as the core session's storage effect
     * writes them, and the Rust session model adopts the new grant.
     */
    internal suspend fun refreshAccessToken(rejectedToken: String?): String? {
        val refresh = sessionRefreshMutex.withLock {
            store.getString("access", null)?.takeIf { it != rejectedToken }?.let { return it }
            sessionRefreshJob?.takeIf { it.isActive } ?: run {
                val refreshToken = store.getString("refresh", null) ?: return null
                val saved = store.getString("core.session", null)?.let { org.json.JSONObject(it) } ?: return null
                val generation = authenticationGeneration
                val accountId = saved.getString("accountId")
                val sessionId = saved.getString("sessionId")
                // The grant owns this rotation. Canceling a route's await must not
                // discard a token already committed by the server.
                scope.async {
                    runCatching { withTimeout(30_000) {
                        val session = gateway.refresh(refreshToken)
                        currentCoroutineContext().ensureActive()
                        val current = store.getString("core.session", null)?.let { org.json.JSONObject(it) }
                        if (generation != authenticationGeneration || current?.optString("sessionId") != sessionId)
                            throw CancellationException("Session was replaced")
                        if (org.json.JSONObject(session.coreJson).getString("accountId") != accountId)
                            throw GatewayError(401, "Your session expired. Sign in again.")
                        check(store.edit().putString("core.session", session.coreJson).putString("access", session.accessToken)
                            .putString("refresh", session.refreshToken).commit())
                        gateway.setAccessToken(session.accessToken)
                        quietSessionAdoption = _state.value.route != Route.Pairing
                        coreSession.adopt(session.coreJson)
                        session.accessToken
                    } }
                }.also { sessionRefreshJob = it }
            }
        }
        return refresh.await().getOrThrow()
    }
    internal fun update(loading: Boolean = _state.value.loading, message: String? = _state.value.message) { _state.value = _state.value.copy(loading = loading, message = message) }
    internal fun fail(error: Throwable) {
        val message = (error as? GatewayError)?.message ?: "Could not complete that request. Check your connection and try again."
        _state.value = _state.value.copy(loading = false, message = message)
    }
}
