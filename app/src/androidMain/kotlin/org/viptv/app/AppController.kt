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
import kotlinx.coroutines.sync.Mutex

class AppController(context: Context, private val origin: String = "https://viptv.syek.tech") {
    private val store = context.getSharedPreferences("viptv.auth", Context.MODE_PRIVATE)
    internal val gateway = VipTvHttpGateway(origin, store.getString("access", null))
    internal val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private val coreSession = CoreSession(origin, store, scope, gateway::setAccessToken, ::renderSession)
    private var pendingCoreAction: (() -> Unit)? = null
    private var sessionRenderGeneration = 0L
    private var keepProfilesOnIdentityRefresh = false
    internal val _state = MutableStateFlow(AppState(loading = true))
    val state: StateFlow<AppState> = _state.asStateFlow()
    val player: AndroidMedia3VideoPlayer = AndroidMedia3BackendFactory(context).createAndroidPlayer()
    private var pairingPoll: Job? = null
    internal var sourceDiscovery: Job? = null
    internal var queueContinuationJob: Job? = null
    /** Last queue/Home refresh wins over any earlier response racing Undo. */
    internal var homeRefreshGeneration = 0L
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
        scope.launch { player.events.collect(::onPlayerEvent) }
        coreSession.begin()
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
    fun retryAuthentication() { if (_state.value.route == Route.Pairing && !_state.value.sessionRestoring) beginPairing() else coreSession.retry() }
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
                _state.value = _state.value.copy(profiles = profiles, selectedProfile = chosen, loading = chosen != null, message = null)
                chosen?.let { profile -> scope.launch { loadHome(profile, generation) } }
            }
            "PROFILES" -> { keepProfilesOnIdentityRefresh = false; _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, selectedProfile = null, loading = false, message = null) }
            "PAIRING" -> { _state.value = AppState(route = Route.Pairing, sessionRestoring = false); beginPairing() }
            "ERROR" -> {
                val message = view.error ?: "Could not restore your session. Try again."
                _state.value = _state.value.copy(loading = false, profiles = profiles, message = message)
                if (view.errorStatus == 403) {
                    afterParentUnlock = { pendingCoreAction?.invoke() ?: coreSession.retry() }
                    _state.value = _state.value.copy(pinPrompt = PinPrompt("Enter parent PIN"))
                }
            }
            else -> _state.value = _state.value.copy(loading = true, message = null)
        }
    }
    private suspend fun loadHome(profile: Profile, generation: Long = sessionRenderGeneration) {
        runCatching { gateway.home(profile.id) }.onSuccess { shelves ->
            if (generation != sessionRenderGeneration) return@onSuccess
            _state.value = _state.value.copy(
                route = Route.Browse(Destination.Home),
                selectedProfile = profile,
                shelves = shelves,
                loading = false,
            )
        }.onFailure { if (generation == sessionRenderGeneration) fail(it) }
    }
    internal fun refreshProfileIdentity() { keepProfilesOnIdentityRefresh = true; coreSession.retry() }
    fun chooseProfile(profile: Profile) {
        guideBrowseGeneration++
        guideGeneration++
        pendingCoreAction = { coreSession.select(profile.id) }
        coreSession.select(profile.id)
    }
    fun setProfilePage(page: Int) { _state.value = _state.value.copy(profilePage = page.coerceIn(0, ((_state.value.profiles.size - 1).coerceAtLeast(0)) / 5)) }
    fun openProfileManagement() { _state.value = _state.value.copy(managingProfiles = true); navigate(Destination.Profile) }
    fun toggleProfileManagement() { _state.value = _state.value.copy(managingProfiles = !_state.value.managingProfiles) }

    fun signOut() = scope.launch { guarded("Enter parent PIN to sign out") { stopPlayback((_state.value.route as? Route.Player)?.media); pendingCoreAction = coreSession::signOut; coreSession.signOut() } }
    fun close() { coreSession.close(); pairingPoll?.cancel(); sourceDiscovery?.cancel(); queueContinuationJob?.cancel(); discoverJob?.cancel(); searchJob?.cancel(); nextEpisodeJob?.cancel(); playerChromeJob?.cancel(); stopPlayback((_state.value.route as? Route.Player)?.media); player.close() }
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
        val position = absolutePositionMillis()
        val profileId = _state.value.selectedProfile?.id
        progressJob?.cancel()
        media?.let { item -> scope.launch { persistProgress(profileId, item, position) } }
        player.stop()
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
    internal fun update(loading: Boolean = _state.value.loading, message: String? = _state.value.message) { _state.value = _state.value.copy(loading = loading, message = message) }
    internal fun fail(error: Throwable) {
        val message = (error as? GatewayError)?.message ?: "Could not complete that request. Check your connection and try again."
        _state.value = _state.value.copy(loading = false, message = message)
    }
}
