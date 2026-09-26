package org.viptv.app

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Dispatch the core's card intent; Kotlin owns effects, not selection policy. */
internal fun AppController.activateCard(media: Media, queue: Boolean = false, origin: SourceReturn = sourceOrigin()) {
    when (CoreModels.card(media, queue).primaryAction) {
        "play" -> start(media, Source(media.id, "Live TV", media.name, channelId = media.id))
        "resume" -> chooseSources(media, true, origin)
        "next" -> playQueuedNext(media)
        "sources" -> chooseSources(media, origin = origin)
        "episodes", "details" -> open(media)
        else -> fail(IllegalStateException("This card action is unavailable."))
    }
}

internal fun AppController.open(media: Media) {
    if (media.type == "live") { activateCard(media); return }
    detailJob?.cancel()
    val generation = ++detailGeneration
    val backRoute = _state.value.route
    val profile = _state.value.selectedProfile?.id
    detailJob = scope.launch {
    val origin = (backRoute as? Route.Browse)?.destination
    update(loading = true)
    runCatching {
        val details = gateway.metadata(media)
        if (details.type != "series") details else {
            val progress = try { gateway.seriesProgress(requireProfile(), details.id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            details.copy(episodes = details.episodes.map { episode ->
                val record = progress.firstOrNull { it.id == episode.id }
                    ?: progress.firstOrNull { it.seriesId == details.id && it.season == episode.season && it.episode == episode.episode }
                if (record == null) episode else episode.copy(
                    positionMillis = record.positionMillis, durationMillis = record.durationMillis ?: episode.durationMillis,
                    watched = record.watched, updatedAtMillis = record.updatedAtMillis,
                    sourceAddonId = record.sourceAddonId, sourceFingerprint = record.sourceFingerprint,
                )
            })
        }
    }.onSuccess { metadata ->
        if (generation != detailGeneration || _state.value.selectedProfile?.id != profile) return@onSuccess
        // Catalog/history carries artwork and progress that sparse metadata
        // responses may omit. Metadata may enrich it, never erase it.
        val detail = metadata.copy(
            poster = metadata.poster ?: media.poster,
            season = media.season ?: metadata.season,
            episode = media.episode ?: metadata.episode,
            seriesId = media.seriesId ?: metadata.seriesId,
            backdrop = metadata.backdrop ?: media.backdrop,
            thumbnail = metadata.thumbnail ?: media.thumbnail,
            year = metadata.year ?: media.year,
            imdbRating = metadata.imdbRating ?: media.imdbRating,
            runtime = metadata.runtime ?: media.runtime,
            genres = metadata.genres.ifEmpty { media.genres },
            credits = metadata.credits ?: media.credits,
            description = metadata.description ?: media.description,
            positionMillis = metadata.positionMillis.takeIf { it > 0 } ?: media.positionMillis,
            durationMillis = metadata.durationMillis ?: media.durationMillis,
            sourceAddonId = metadata.sourceAddonId ?: media.sourceAddonId,
            sourceFingerprint = metadata.sourceFingerprint ?: media.sourceFingerprint,
        )
        detailReturnDestination = origin
        detailReturnRoute = backRoute
        _state.value = _state.value.copy(route = Route.Details(detail), loading = false,
            shelves = _state.value.shelves.map { shelf -> shelf.copy(items = shelf.items.map { item ->
                if ((item.seriesId ?: item.id) == (detail.seriesId ?: detail.id)) item.withArtworkFrom(detail) else item
            }) },
        )
    }.onFailure { if (it !is CancellationException && generation == detailGeneration) fail(it) }
    }
}
internal fun AppController.chooseSources(media: Media, resume: Boolean = false, origin: SourceReturn = sourceOrigin()) {
    val previous = _state.value.route
    val returnRoute = (previous as? Route.Sources)?.backRoute ?: previous.takeUnless { it is Route.Player }
    val route = Route.Sources(media, resume, origin, returnRoute)
    sourceDiscovery?.cancel()
    sourceDiscovery = scope.launch {
        if (origin == SourceReturn.Home) detailReturnDestination = Destination.Home
        _state.value = _state.value.copy(route = route, sources = emptyList(), loading = true, sourceLoading = true, message = null)
        runCatching { gateway.sources(media) { arriving ->
            val route = _state.value.route
            if (route is Route.Sources && route.media.type == media.type && route.media.id == media.id) _state.value = _state.value.copy(sources = arriving)
        } }.onSuccess { discovered ->
        _state.value = _state.value.copy(sourceLoading = false)
        val savedIdentity = ResumeIdentity.sourceIdentity(media.sourceAddonId, media.sourceFingerprint)
        val exact = if (resume) discovered.firstOrNull { ResumeIdentity.sourceIdentity(it) == savedIdentity } else null
        if (exact != null) start(media, exact, explicitResume = true) else {
            _state.value = _state.value.copy(
                route = route, sources = discovered, loading = false,
                message = when { discovered.isEmpty() -> "No sources found. Choose another title or try again."; resume && savedIdentity == null -> "Choose a source to resume. Your prior source cannot be verified."; resume -> "Your previous source is unavailable. Choose a source."; else -> null },
            )
        }
    }.onFailure { error -> if (error !is CancellationException) { _state.value = _state.value.copy(sourceLoading = false); fail(error) } }
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
internal fun AppController.toggleMyList(media: Media) = scope.launch { guarded("Enter parent PIN") { val saved = gateway.toggleFavorite(requireProfile(), media); _state.value = _state.value.copy(message = if (saved) "Added to My List." else "Removed from My List.") } }

internal fun AppController.correctEpisode(media: Media, watched: Boolean) = scope.launch { guarded("Enter parent PIN") { gateway.correctProgress(requireProfile(), media, if (watched) "watched" else "unwatched"); _state.value = _state.value.copy(message = if (watched) "Marked watched." else "Marked unwatched.") } }
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
