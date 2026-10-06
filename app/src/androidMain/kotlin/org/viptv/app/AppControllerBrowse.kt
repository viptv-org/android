package org.viptv.app

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Dispatch the core's card intent; Kotlin owns effects, not selection policy. */
internal fun AppController.activateCard(media: Media, queue: Boolean = false, origin: SourceReturn = sourceOrigin()) {
    when (CoreModels.card(media, queue).primaryAction) {
        "play" -> start(media, Source(media.id, "Live TV", media.name, channelId = media.id))
        "resume" -> chooseSources(media, true, origin, queueEpisodeReturn = queue && SourceReturnPolicy.parentSeries(media) != null)
        "next" -> playQueuedNext(media)
        "sources" -> chooseSources(media, origin = origin, queueEpisodeReturn = queue && SourceReturnPolicy.parentSeries(media) != null)
        "episodes", "details" -> open(media)
        else -> fail(IllegalStateException("This card action is unavailable."))
    }
}

/** Opening, correction and playback return share the canonical episode/history join. */
internal fun mergeSeriesProgress(details: Media, progress: List<Media>): Media = CoreModels.mergeEpisodeProgress(details, progress)

/** One final save, then one authoritative read; callers guard the active route before applying. */
internal suspend fun refreshEpisodeReturn(
    route: Route, episode: Media, save: suspend () -> Unit, readProgress: suspend () -> List<Media>,
): Route {
    val details = when (route) {
        is Route.Details -> route
        is Route.Sources -> route.backRoute as? Route.Details
        else -> null
    } ?: return route
    if (details.media.id != SourceReturnPolicy.parentSeries(episode)?.id) return route
    save()
    val updated = details.copy(media = mergeSeriesProgress(details.media, readProgress()))
    return if (route is Route.Sources) route.copy(backRoute = updated) else updated
}

/** A quick Back may expose the same retained Details while the final progress read is pending. */
internal fun applyRefreshedEpisodeReturn(
    original: Route, refreshed: Route, current: Route, profileStillSelected: Boolean, detailGenerationUnchanged: Boolean,
): Route {
    if (!profileStillSelected) return current
    if (current === original && detailGenerationUnchanged) return refreshed
    val originalDetails = (original as? Route.Sources)?.backRoute as? Route.Details
    val refreshedDetails = (refreshed as? Route.Sources)?.backRoute as? Route.Details
    return if (current === originalDetails && refreshedDetails != null) refreshedDetails else current
}

internal fun AppController.open(media: Media, returnRoute: Route? = null, showWhileLoading: Boolean = false) {
    if (media.type == "live") { activateCard(media); return }
    detailJob?.cancel()
    val generation = ++detailGeneration
    val backRoute = returnRoute ?: _state.value.route
    val profile = _state.value.selectedProfile?.id
    if (showWhileLoading) {
        detailReturnRoute = backRoute
        detailReturnDestination = (backRoute as? Route.Browse)?.destination
        _state.value = _state.value.copy(route = Route.Details(media, generation), message = null)
    }
    detailJob = scope.launch {
    val origin = (backRoute as? Route.Browse)?.destination
    update(loading = true)
    runCatching {
        val details = gateway.metadata(media)
        if (details.type != "series") details else {
            val progress = try { gateway.seriesProgress(requireProfile(), details.id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            mergeSeriesProgress(details, progress)
        }
    }.onSuccess { metadata ->
        if (generation != detailGeneration || _state.value.selectedProfile?.id != profile) return@onSuccess
        val detail = CoreModels.enrichDetail(media, metadata)
        detailReturnDestination = origin
        detailReturnRoute = backRoute
        _state.value = _state.value.copy(route = Route.Details(detail, generation), loading = false,
            shelves = _state.value.shelves.map { shelf -> shelf.copy(items = shelf.items.map { item ->
                if ((item.seriesId ?: item.id) == (detail.seriesId ?: detail.id)) item.withArtworkFrom(detail) else item
            }) },
        )
    }.onFailure { if (it !is CancellationException && generation == detailGeneration) fail(it) }
    }
}
internal fun AppController.chooseSources(media: Media, resume: Boolean = false, origin: SourceReturn = sourceOrigin(), queueEpisodeReturn: Boolean = false) {
    val previous = _state.value.route
    if (previous is Route.Guide) cancelGuideWork()
    val returnRoute = when (previous) {
        is Route.Sources -> previous.backRoute
        is Route.Player -> previous.sourceRoute?.backRoute
        else -> previous
    }
    val route = Route.Sources(media, resume, origin, returnRoute, queueEpisodeReturn)
    val profile = _state.value.selectedProfile?.id
    val beforeStart = playbackGeneration
    fun ownsResults(): Boolean {
        val active = _state.value.route
        return _state.value.selectedProfile?.id == profile &&
            (active === route || (active is Route.Player && active.sourceRoute === route))
    }
    sourceDiscovery?.cancel()
    sourceDiscovery = scope.launch {
        if (origin == SourceReturn.Home) detailReturnDestination = Destination.Home
        _state.value = _state.value.copy(route = route, sources = emptyList(), sourceProducers = emptyList(), loading = true, sourceLoading = true, message = null)
        var observed = emptyList<SourceProducerOutcome>()
        var configuredAddons = emptyList<Addon>()
        fun publishProducers() {
            if (ownsResults()) _state.value = _state.value.copy(
                sourceProducers = namedSourceProducers(observed, configuredAddons, _state.value.sources))
        }
        launch {
            configuredAddons = try { gateway.addons() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            publishProducers()
        }
        try {
            val discovered = discoverSourcesFor(media, onSources = { arriving ->
                if (ownsResults()) { _state.value = _state.value.copy(sources = arriving); publishProducers() }
            }, onProducers = { outcomes -> observed = outcomes; publishProducers() })
            if (!ownsResults()) return@launch
            _state.value = _state.value.copy(sources = discovered, sourceLoading = false,
                loading = _state.value.preparingSourceId != null)
            if (_state.value.route !is Route.Sources || playbackGeneration != beforeStart) return@launch
            val savedIdentity = ResumeIdentity.sourceIdentity(media.sourceAddonId, media.sourceFingerprint)
            val intent = if (resume) PlaybackPolicy.forResume(savedIdentity, discovered, media.positionMillis) else PlaybackIntent.ChooseSource
            if (intent is PlaybackIntent.Open) start(media, intent.source, explicitResume = true)
            else if (discovered.isEmpty() || resume) update(message = when {
                discovered.isEmpty() -> "No sources found. Choose another title or try again."
                savedIdentity == null -> "Choose a source to resume. Your prior source cannot be verified."
                else -> "Your previous source is unavailable. Choose a source."
            })
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (ownsResults()) {
                _state.value = _state.value.copy(sourceLoading = false)
                if (_state.value.route is Route.Sources && _state.value.preparingSourceId == null) fail(error)
            }
        }
    }
}
private fun AppController.sourceOrigin(): SourceReturn = when (_state.value.route) {
    is Route.Browse -> if ((_state.value.route as Route.Browse).destination == Destination.Home) SourceReturn.Home else SourceReturn.Details
    else -> SourceReturn.Details
}

internal fun AppController.openMyList() = scope.launch {
    val profile = requireProfile()
    val route = Route.Browse(Destination.MyList)
    _state.value = _state.value.copy(route = route, libraryQueue = false, loading = true)
    runCatching { gateway.favorites(profile) }.onSuccess {
        if (_state.value.selectedProfile?.id == profile && _state.value.route == route && !_state.value.libraryQueue)
            _state.value = _state.value.copy(favorites = it, catalog = it, loading = false)
    }.onFailure { if (it !is CancellationException && _state.value.route == route && _state.value.selectedProfile?.id == profile) fail(it) }
}
internal fun AppController.openContinueWatching() {
    val profile = _state.value.selectedProfile?.id ?: return
    _state.value = _state.value.copy(route = Route.Browse(Destination.MyList), libraryQueue = true, loading = false)
    scope.launch {
        runCatching { gateway.queue(profile) }.onSuccess {
            if (_state.value.selectedProfile?.id == profile) _state.value = _state.value.copy(queue = it)
        }.onFailure { if (it !is CancellationException) fail(it) }
    }
}
/** The Live rail enters the Guide directly; the list surface is reserved for a truthful empty state. */
internal fun AppController.openLive() {
    val prior = _state.value.guideUi
    loadGuidePage(prior.channelFilter, offset = 0, preferredChannelId = prior.selectedChannelId)
}

/** Settings remains usable when the optional server-status endpoint is unavailable. */
internal fun AppController.openSettings() = scope.launch {
    _state.value = _state.value.copy(route = Route.Settings, loading = false, message = null)
    val profileId = runCatching(::requireProfile).getOrElse { return@launch }
    coroutineScope {
        val preferences = async { runCatching { gateway.preferences(profileId) } }
        val addons = async { runCatching { gateway.addons() } }
        val prefResult = preferences.await()
        val addonResult = addons.await()
        if (prefResult.isFailure && addonResult.isFailure) {
            fail(prefResult.exceptionOrNull() ?: addonResult.exceptionOrNull()!!)
            return@coroutineScope
        }
        if (_state.value.route != Route.Settings || _state.value.selectedProfile?.id != profileId) return@coroutineScope
        _state.value = _state.value.copy(
            route = Route.Settings,
            preferences = prefResult.getOrElse { _state.value.preferences },
            addons = addonResult.getOrElse { _state.value.addons },
            loading = false,
            message = null,
        )
    }
}
internal fun AppController.installAddon(manifestUrl: String) = scope.launch {
    guarded("Enter parent PIN") {
        gateway.addAddon(manifestUrl)
        openSettings()
    }
}
internal fun AppController.toggleMyList(media: Media) = scope.launch {
    val profile = requireProfile()
    guarded("Enter parent PIN") {
        val saved = gateway.toggleFavorite(profile, media)
        if (_state.value.selectedProfile?.id == profile) {
            libraryRevision++
            val favorites = _state.value.favorites.filterNot { it.id == media.id && it.type == media.type }.let { if (saved) it + media else it }
            _state.value = _state.value.copy(favorites = favorites,
                shelves = _state.value.shelves.map { if (it.id == "My List") it.copy(items = favorites) else it },
                message = if (saved) "Added to My List." else "Removed from My List.")
        }
    }
}

/** Apply a successful correction only to the exact episode currently open in this profile. */
internal fun correctedDetailsRoute(route: Route, episode: Media, watched: Boolean): Route =
    if (route is Route.Details && route.media.id == SourceReturnPolicy.parentSeries(episode)?.id)
        route.copy(media = route.media.copy(episodes = route.media.episodes.map { item ->
            if (item.id == episode.id && item.season == episode.season && item.episode == episode.episode)
                item.copy(watched = watched)
            else item
        }))
    else route

internal fun AppController.correctEpisode(media: Media, watched: Boolean) = scope.launch {
    guarded("Enter parent PIN") {
        val profile = requireProfile()
        val detail = _state.value.route as? Route.Details
        val seriesId = SourceReturnPolicy.parentSeries(media)?.id
        gateway.correctProgress(profile, media, if (watched) "watched" else "unwatched")
        if (_state.value.selectedProfile?.id == profile) {
            val current = _state.value
            val stillOnDetail = detail != null && current.route === detail && detail.media.id == seriesId
            val accepted = if (stillOnDetail) correctedDetailsRoute(checkNotNull(detail), media, watched) else current.route
            _state.value = current.copy(route = accepted,
                message = if (watched) "Marked watched." else "Marked unwatched.")
            val records = if (stillOnDetail) try { gateway.seriesProgress(profile, checkNotNull(seriesId)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            else null
            val latest = _state.value
            if (latest.selectedProfile?.id == profile && latest.route === accepted && records != null && accepted is Route.Details) {
                _state.value = latest.copy(route = accepted.copy(media = mergeSeriesProgress(accepted.media, records)))
            }
        }
    }
}
internal fun AppController.setPreference(preferences: PlaybackPreferences) = scope.launch { guarded("Enter parent PIN") { gateway.savePreferences(requireProfile(), preferences); _state.value = _state.value.copy(preferences = preferences, message = "Applies to your next playback. Manual track choices take priority.") } }
internal fun AppController.toggleAddon(addon: Addon) = scope.launch { guarded("Enter parent PIN") { gateway.setAddonEnabled(addon, !addon.enabled); openSettings() } }
internal fun AppController.removeAddon(addon: Addon) = scope.launch { guarded("Enter parent PIN") { gateway.removeAddon(addon); openSettings() } }
internal fun AppController.editProfile(profile: Profile? = null) { _state.value = _state.value.copy(route = Route.ProfileEditor(profile), message = null) }
internal fun AppController.requestDeleteProfile(profile: Profile) { _state.value = _state.value.copy(dialog = DialogState(DialogKind.DeleteProfile, "Delete ${profile.name}?", profile = profile)) }
internal fun AppController.saveProfile(profile: Profile?, name: String, avatarStyle: String, avatarChoice: Int?) {
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
            val profiles = if (_state.value.profiles.any { it.id == saved.id }) _state.value.profiles.map { if (it.id == saved.id) saved else it } else _state.value.profiles + saved
            _state.value = _state.value.copy(route = Route.Profiles, profiles = profiles, loading = false, message = "Profile saved.")
            refreshProfileIdentity()
        }
    }
}
internal fun AppController.deleteProfile(profile: Profile) = scope.launch { if (profile.primary) { update(message = "The primary profile cannot be deleted."); return@launch }; guarded("Enter parent PIN to manage profiles") { gateway.deleteProfile(profile); _state.value = _state.value.copy(route = Route.Profiles, profiles = _state.value.profiles.filterNot { it.id == profile.id }, selectedProfile = _state.value.selectedProfile?.takeIf { it.id != profile.id }, message = "Profile deleted."); refreshProfileIdentity() } }
