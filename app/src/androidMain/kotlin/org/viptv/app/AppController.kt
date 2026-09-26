package org.viptv.app

import android.content.Context
import org.viptv.video.AndroidMedia3BackendFactory
import org.viptv.video.AndroidMedia3VideoPlayer
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppController(context: Context, private val origin: String) {
    private val television = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    internal val gateway = VipTvHttpGateway(origin, store.getString("access", null), ::refreshAccessToken)
    internal val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val coreSession = CoreSession(origin, store, scope, gateway::setAccessToken, ::renderSession)
    private val sessionRefreshMutex = Mutex()
    private var pendingCoreAction: (() -> Unit)? = null
    private var sessionRenderGeneration = 0L
    private var keepProfilesOnIdentityRefresh = false
    internal val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val playerDelegate = lazy {
        AndroidMedia3BackendFactory(context).createAndroidPlayer().also { instance ->
            scope.launch { instance.events.collect(::onPlayerEvent) }
        }
    }
    val player: AndroidMedia3VideoPlayer get() = playerDelegate.value
    internal var homeJob: Job? = null
    private var pairingPoll: Job? = null
    private var loginJob: Job? = null
    internal var playbackStartJob: Job? = null
    internal var sourceDiscovery: Job? = null
    internal var queueContinuationJob: Job? = null
    /** Last queue/Home refresh wins over any earlier response racing Undo. */
    internal var homeRefreshGeneration = 0L
    internal var libraryRevision = 0L
    internal var discoverJob: Job? = null
    internal var searchJob: Job? = null
    internal var nextEpisodeJob: Job? = null
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
    internal var discoverGeneration = 0L
    internal var managedRecoveryKey: String? = null
    /** Suppresses duplicate Media3 failure events while the one permitted same-source recovery is awaiting the server. */
    internal var managedRecoveryInFlightKey: String? = null
    internal val playbackPrepareMutex = Mutex()
    internal var playbackGeneration = 0L
    internal val guideScheduleCache = mutableMapOf<String, GuideScheduleCache>()
    internal var explicitResumeAwaitingCompletionKey: String? = null
    internal var autoNextMediaKey: String? = null

    init {
        coreSession.begin()
    }
    fun beginPairing() = scope.launch {
        loginJob?.cancel(); pairingPoll?.cancel()
        _state.value = _state.value.copy(pairingRequested = true, deviceCode = null, loading = true, message = null)
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
        val generation = ++sessionRenderGeneration
        val phase = view.phase.name
        _state.value = _state.value.copy(sessionRestoring = phase != "PAIRING" && _state.value.route == Route.Pairing)
        val profiles = view.identity?.profiles?.map(CoreModels::profileNormalized) ?: _state.value.profiles
        when (phase) {
            "READY" -> {
                pendingCoreAction = null
                val selected = view.selectedProfileId
                val chosen = profiles.firstOrNull { it.id == selected }
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
            "PROFILES" -> { keepProfilesOnIdentityRefresh = false; _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, selectedProfile = null, loading = false, message = null) }
            "PAIRING" -> { _state.value = AppState(route = Route.Pairing, sessionRestoring = false); if (television) beginPairing() }
            "ERROR" -> {
                val message = view.error ?: "Could not restore your session. Try again."
                _state.value = _state.value.copy(loading = false, profiles = if (keepProfilesOnIdentityRefresh) _state.value.profiles else profiles, message = message)
                if (view.errorStatus == 403) {
                    afterParentUnlock = { pendingCoreAction?.invoke() ?: coreSession.retry() }
                    _state.value = _state.value.copy(pinPrompt = PinPrompt("Enter parent PIN"))
                }
            }
            else -> _state.value = _state.value.copy(loading = true, message = null)
        }
    }
    internal suspend fun loadHome(profile: Profile, generation: Long = sessionRenderGeneration, enter: Boolean = false) {
        val job = currentCoroutineContext()[Job]
        if (homeJob !== job) homeJob?.cancel()
        homeJob = job
        val refresh = ++homeRefreshGeneration
        val library = libraryRevision
        _state.value = _state.value.copy(homeLoading = true)
        if (enter) _state.value = _state.value.copy(route = Route.Browse(Destination.Home), selectedProfile = profile, loading = false)
        runCatching { gateway.home(profile.id) { shelves ->
            if (generation == sessionRenderGeneration && refresh == homeRefreshGeneration && _state.value.selectedProfile?.id == profile.id) {
                _state.value = _state.value.copy(
                    shelves = if (library == libraryRevision) shelves else shelves.map { if (it.id == "My List") it.copy(items = _state.value.favorites) else it },
                    queue = shelves.firstOrNull { it.isQueueShelf }?.items.orEmpty(),
                    favorites = if (library == libraryRevision) shelves.firstOrNull { it.id == "My List" }?.items.orEmpty() else _state.value.favorites,
                    loading = if (_state.value.route == Route.Browse(Destination.Home)) false else _state.value.loading,
                )
            }
        } }.onFailure { if (generation == sessionRenderGeneration && refresh == homeRefreshGeneration && it !is CancellationException) fail(it) }
        if (generation == sessionRenderGeneration && refresh == homeRefreshGeneration) _state.value = _state.value.copy(homeLoading = false)
    }
    internal fun refreshProfileIdentity() { keepProfilesOnIdentityRefresh = true; coreSession.retry() }
    fun chooseProfile(profile: Profile) {
        keepProfilesOnIdentityRefresh = false
        homeJob?.cancel()
        homeRefreshGeneration++
        detailGeneration++; detailJob?.cancel()
        searchJob?.cancel(); discoverJob?.cancel(); sourceDiscovery?.cancel()
        cancelPendingQueueContinuation()
        guideBrowseGeneration++
        guideGeneration++
        pendingCoreAction = { coreSession.select(profile.id) }
        coreSession.select(profile.id)
    }
    fun setProfilePage(page: Int) { _state.value = _state.value.copy(profilePage = page.coerceIn(0, ((_state.value.profiles.size - 1).coerceAtLeast(0)) / 5)) }
    fun openProfileManagement() { _state.value = _state.value.copy(managingProfiles = true); navigate(Destination.Profile) }
    fun toggleProfileManagement() { _state.value = _state.value.copy(managingProfiles = !_state.value.managingProfiles) }

    fun signOut() = scope.launch { guarded("Enter parent PIN to sign out") { stopPlayback((_state.value.route as? Route.Player)?.media); pendingCoreAction = coreSession::signOut; coreSession.signOut() } }
    fun close() {
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
        store.edit().remove("access").remove("refresh").remove("core.session").remove("token").remove("profile").commit()
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
        val prior = playbackSessionId
        playbackSessionId = null
        prior?.let { id -> scope.launch { runCatching { gateway.stopPlayback(id) } } }
    }

    internal fun replacePlaybackSession(sessionId: String) {
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
    internal fun stopPlayback(media: Media? = null) {
        invalidatePlaybackPreparation()
        if (!playerDelegate.isInitialized()) return
        val position = absolutePositionMillis()
        val profileId = _state.value.selectedProfile?.id
        progressJob?.cancel()
        media?.let { item -> scope.launch { persistProgress(profileId, item, position) } }
        player.stop()
        player.detachSurface()
        playbackTitleOffsetMillis = 0L
        playbackTitleDurationMillis = null
        lastTrustedTitlePositionMillis = 0L
        managedPauseAnchorMillis = null
        playerMenuOpen = false
        retirePlaybackSession()
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
    internal suspend fun refreshAccessToken(rejectedToken: String?): String? = sessionRefreshMutex.withLock {
        store.getString("access", null)?.takeIf { it != rejectedToken }?.let { return@withLock it }
        val refreshToken = store.getString("refresh", null) ?: return@withLock null
        try {
            val session = gateway.refresh(refreshToken)
            store.edit()
                .putString("core.session", session.coreJson)
                .putString("access", session.accessToken)
                .putString("refresh", session.refreshToken)
                .commit()
            coreSession.adopt(session.coreJson)
            session.accessToken
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    internal fun update(loading: Boolean = _state.value.loading, message: String? = _state.value.message) { _state.value = _state.value.copy(loading = loading, message = message) }
    internal fun fail(error: Throwable) {
        val message = (error as? GatewayError)?.message ?: "Could not complete that request. Check your connection and try again."
        _state.value = _state.value.copy(loading = false, message = message)
    }
}
