package org.viptv.app

data class Media(
    val id: String,
    val type: String,
    val name: String = "",
    val poster: String? = null,
    val description: String? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
    val seriesId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val sourceAddonId: String? = null,
    val sourceFingerprint: String? = null,
    val episodes: List<Media> = emptyList(),
    /** Episode-specific title when upstream video `name` repeats the series title. */
    val episodeTitle: String? = null,
)

data class Source(
    val id: String,
    val provider: String,
    val name: String = provider,
    val description: String = "",
    /** Source discovery must never expose upstream delivery credentials to UI state. */
    val addonId: String? = null,
    val fingerprint: String? = null,
    /** Safe server display facts; provider identity is kept separately for ranking. */
    val quality: String? = null,
    val audio: String? = null,
)

object SourceDisplayPolicy {
    private val opaqueProviderId = Regex("^[A-Za-z0-9._-]+:[0-9]+$")
    /** Provider worker IDs are not meaningful metadata; prefer the server filename/title. */
    fun title(source: Source): String = when {
        source.name.isNotBlank() && !opaqueProviderId.matches(source.name) -> source.name
        source.description.isNotBlank() -> source.description.lineSequence().first().take(180)
        source.provider.isNotBlank() && !opaqueProviderId.matches(source.provider) -> source.provider
        else -> "Source"
    }
    fun body(source: Source): String = source.description.ifBlank { source.provider.takeUnless(opaqueProviderId::matches).orEmpty() }
}

sealed interface PlaybackIntent {
    data class Open(val source: Source, val positionMillis: Long) : PlaybackIntent
    data object ChooseSource : PlaybackIntent
}

enum class MediaCardAction { OpenDetails, ResumeExactSource }
object MediaCardPolicy {
    /** Only Continue Watching has a Resume primary action; discovery always opens details. */
    fun primary(resumeSurface: Boolean, media: Media): MediaCardAction =
        if (resumeSurface && media.positionMillis > 0 && media.type != "live") MediaCardAction.ResumeExactSource else MediaCardAction.OpenDetails
    fun supportsChooseSourceHold(resumeSurface: Boolean, media: Media): Boolean =
        primary(resumeSurface, media) == MediaCardAction.ResumeExactSource
}

/** Product policy from the design contract. The player adapter does not choose sources. */
object PlaybackPolicy {
    fun forResume(expectedIdentity: String?, discovered: List<Source>, positionMillis: Long = 0): PlaybackIntent =
        expectedIdentity?.let { expected -> discovered.firstOrNull { ResumeIdentity.sourceIdentity(it) == expected } }
            ?.let { PlaybackIntent.Open(it, positionMillis) } ?: PlaybackIntent.ChooseSource

    fun canAutoNext(
        media: Media,
        positionMillis: Long,
        durationMillis: Long?,
        playing: Boolean,
        seeking: Boolean,
        nextAvailable: Boolean,
        autoplay: Boolean = true,
    ): Boolean = media.type == "series" && durationMillis != null && durationMillis > 10_000 && autoplay && playing && !seeking && nextAvailable &&
        positionMillis >= durationMillis - 10_000
}

object ResumeIdentity {
    fun storageKey(profileId: String, media: Media): String = "source.$profileId.${media.type}.${media.id}"
    /** Stream job IDs and display names are unstable; both server-owned fields are required. */
    fun sourceIdentity(addonId: String?, fingerprint: String?): String? =
        addonId?.takeIf(String::isNotBlank)?.let { addon -> fingerprint?.takeIf(String::isNotBlank)?.let { "$addon\u0000$it" } }
    fun sourceIdentity(source: Source): String? = sourceIdentity(source.addonId, source.fingerprint)
}

/**
 * Durable device grants are only discarded when the server has conclusively
 * rejected them.  A timeout, rate limit, or server fault must leave the saved
 * refresh token available for an explicit retry instead of forcing a new TV
 * pairing flow.
 */
object AuthSessionPolicy {
    fun discardStoredGrant(httpStatus: Int?): Boolean = httpStatus == 401
}

object DevicePollPolicy {
    fun nextIntervalSeconds(issuedSeconds: Long, currentSeconds: Long, rateLimited: Boolean): Long =
        if (rateLimited) (currentSeconds.coerceAtLeast(issuedSeconds.coerceAtLeast(1)) * 2).coerceAtMost(30)
        else issuedSeconds.coerceAtLeast(1)
}

/** Product-only transition policy; the backend remains the authority on the actual next source. */
sealed interface ContinuationDecision {
    data object PrepareNext : ContinuationDecision
    data object KeepOutgoing : ContinuationDecision
    data object ShowCaughtUp : ContinuationDecision
    data object ShowUpcoming : ContinuationDecision
}

object ContinuationPolicy {
    fun decide(
        media: Media,
        positionMillis: Long,
        durationMillis: Long?,
        playing: Boolean,
        seeking: Boolean,
        nextStatus: String?,
    ): ContinuationDecision {
        if (!PlaybackPolicy.canAutoNext(media, positionMillis, durationMillis, playing, seeking, true)) return ContinuationDecision.KeepOutgoing
        return when (nextStatus) {
            "next" -> ContinuationDecision.PrepareNext
            "caught_up" -> ContinuationDecision.ShowCaughtUp
            "upcoming" -> ContinuationDecision.ShowUpcoming
            else -> ContinuationDecision.KeepOutgoing
        }
    }
}

/**
 * A controlled continuation may stay with the active IPTV add-on or use the
 * add-on ranked by the server for the returned episode. It never substitutes
 * an arbitrary source merely to keep autoplay moving.
 */
object ContinuationSourcePolicy {
    fun select(next: Media, outgoing: Source?, candidates: List<Source>): Source? {
        val outgoingAddon = outgoing?.addonId?.takeIf(String::isNotBlank)
        val rankedAddon = next.sourceAddonId?.takeIf(String::isNotBlank)
        return outgoingAddon?.let { wanted -> candidates.firstOrNull { it.addonId == wanted } }
            ?: rankedAddon?.let { wanted -> candidates.firstOrNull { it.addonId == wanted } }
    }
}

enum class RemoteAction { Activate, Hold }
object HoldPolicy {
    const val thresholdMillis = 700L
    fun release(heldMillis: Long): RemoteAction = if (heldMillis >= thresholdMillis) RemoteAction.Hold else RemoteAction.Activate
}

/** A release belongs only to the focus owner that observed the initial non-repeat press. */
object HoldPressPolicy {
    fun begins(downAtMillis: Long, repeatCount: Int): Boolean = downAtMillis == 0L && repeatCount == 0
    fun activatesOnRelease(downAtMillis: Long, held: Boolean): Boolean = downAtMillis != 0L && !held
}

/** Back always resolves transient state before leaving its route. */
enum class BackDisposition { DismissDialog, CancelPin, CancelSeek, HidePlayerChrome, ExitPlayer, Navigate }
object BackPolicy {
    fun decide(dialogOpen: Boolean, pinOpen: Boolean, seekPreviewOpen: Boolean, playerChromeOpen: Boolean, inPlayer: Boolean, explicitExit: Boolean = false): BackDisposition = when {
        explicitExit && inPlayer -> BackDisposition.ExitPlayer
        dialogOpen -> BackDisposition.DismissDialog
        pinOpen -> BackDisposition.CancelPin
        seekPreviewOpen -> BackDisposition.CancelSeek
        inPlayer && playerChromeOpen -> BackDisposition.HidePlayerChrome
        inPlayer -> BackDisposition.ExitPlayer
        else -> BackDisposition.Navigate
    }
}

/** Back registration is derived from observed UI state so Compose re-registers it as routes change. */
object BackAvailabilityPolicy {
    fun consumes(state: AppState): Boolean = when (state.route) {
        is Route.Player, is Route.Sources, is Route.Details, Route.Search, Route.Settings, Route.Addons, is Route.ProfileEditor, is Route.Guide -> true
        is Route.Profiles -> state.managingProfiles || state.selectedProfile != null
        is Route.Browse -> state.route.destination != Destination.Home
        Route.Pairing -> false
    } || state.dialog != null || state.pinPrompt != null || state.seekPreview != null
}

object SeekPolicy {
    /** A preview never commits a request and clamps to known VOD duration or DVR window. */
    fun target(currentMillis: Long, deltaMillis: Long, durationMillis: Long?, rangeStart: Long? = null, rangeEnd: Long? = null): Long? {
        val raw = currentMillis + deltaMillis
        val target = when {
            durationMillis != null -> raw.coerceIn(0, durationMillis)
            rangeStart != null && rangeEnd != null -> raw.coerceIn(rangeStart, rangeEnd)
            else -> return null
        }
        return target.takeIf { kotlin.math.abs(it - currentMillis) >= 500 }
    }
}

object SeekCommitPolicy {
    fun usesManagedReplacement(deliveryMode: String): Boolean = !deliveryMode.equals("direct", ignoreCase = true)
}

/** Maps a server-managed segment clock onto the title clock used by UX and progress. */
object PlaybackTimelinePolicy {
    fun titleOffsetMillis(deliveryMode: String, launchPositionMillis: Long): Long =
        if (SeekCommitPolicy.usesManagedReplacement(deliveryMode)) launchPositionMillis.coerceAtLeast(0) else 0L
    fun absolutePositionMillis(segmentPositionMillis: Long, titleOffsetMillis: Long): Long =
        segmentPositionMillis.coerceAtLeast(0) + titleOffsetMillis.coerceAtLeast(0)
    fun segmentPositionMillis(titlePositionMillis: Long, titleOffsetMillis: Long): Long =
        (titlePositionMillis - titleOffsetMillis).coerceAtLeast(0)
}

/**
 * A managed HLS playlist can slide while decode is paused.  Pause is a viewer
 * intent on the title timeline, so its captured absolute time wins over later
 * native window coordinates until a replacement session resumes it.
 */
object ManagedPausePolicy {
    fun usesAnchor(deliveryMode: String, live: Boolean = false): Boolean =
        !live && SeekCommitPolicy.usesManagedReplacement(deliveryMode)

    fun displayPosition(anchorMillis: Long?, nativeTitlePositionMillis: Long): Long = anchorMillis ?: nativeTitlePositionMillis

    fun requiresReplacementOnResume(deliveryMode: String, anchorMillis: Long?): Boolean =
        usesAnchor(deliveryMode) && anchorMillis != null

    fun anchorAfterOpen(deliveryMode: String, live: Boolean, launchPositionMillis: Long, playWhenReady: Boolean): Long? =
        if (usesAnchor(deliveryMode, live) && !playWhenReady) launchPositionMillis else null
}

object PlayerChromePolicy {
    fun shouldAutoHide(inPlayer: Boolean, playing: Boolean, menuOpen: Boolean, seekPreviewOpen: Boolean): Boolean =
        inPlayer && playing && !menuOpen && !seekPreviewOpen
}

/** Behind-window recovery is a single managed reprepare, never a jump to live edge. */
object ManagedRecoveryPolicy {
    fun shouldAttempt(serverManaged: Boolean, networkFailure: Boolean, alreadyAttempted: Boolean): Boolean =
        serverManaged && networkFailure && !alreadyAttempted
}

/** A late preparation has no authority after Back, profile change, or a newer request. */
object PlaybackRequestPolicy {
    fun isCurrent(requestGeneration: Long, currentGeneration: Long): Boolean = requestGeneration == currentGeneration
    /** A request queued on the preparation mutex keeps its original authority. */
    fun mayPrepareAfterMutexWait(capturedGeneration: Long, currentGeneration: Long): Boolean =
        isCurrent(capturedGeneration, currentGeneration)
}

enum class Destination(val label: String) {
    Profile("Profile"), Home("Home"), Discover("Discover"), Live("Live TV"), MyList("My List"), Search("Search"), Settings("Settings")
}

sealed interface Route {
    data object Pairing : Route
    data object Profiles : Route
    data class Browse(val destination: Destination) : Route
    data class Details(val media: Media) : Route
    data class Sources(val media: Media, val resume: Boolean = false) : Route
    data class Player(val media: Media, val source: Source, val returnDestination: PlaybackReturn = PlaybackReturn.Details) : Route
    data object Search : Route
    data object Settings : Route
    data object Addons : Route
    data class ProfileEditor(val profile: Profile? = null) : Route
    data class Guide(val channel: LiveChannel) : Route
}

enum class PlaybackReturn { Details, Sources }

object PlaybackReturnPolicy {
    /** Explicit Resume returns to title detail; an ordinary source picker remains its return surface. */
    fun afterSourceStart(resume: Boolean): PlaybackReturn = if (resume) PlaybackReturn.Details else PlaybackReturn.Sources
}

data class Profile(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val kids: Boolean = false,
    val primary: Boolean = false,
    val avatarStyle: String = "critters",
    /** Server-selected public avatar ordinal. `avatar_seed` is never exposed or sent by clients. */
    val avatarChoice: Int? = null,
    /** The server only accepts this mutation as true, and never on create. */
    val setupComplete: Boolean = false,
)
data class DeviceCode(val code: String, val userCode: String, val verificationUri: String, val verificationUriComplete: String?, val qrUri: String?, val intervalSeconds: Long)
data class DeviceSession(val accessToken: String, val refreshToken: String, val profileId: String?)
data class HomeShelf(val title: String, val items: List<Media>)
data class NextResult(val status: String, val item: Media? = null)
data class Addon(val id: String, val name: String, val manifestUrl: String, val enabled: Boolean)
/** Deliberately minimal, account-safe facts for the informational Settings section. */
data class ServerAbout(val mediaServiceAvailable: Boolean)
/** A labelled source section retained when another search source fails. */
data class SearchSection(val source: String, val items: List<Media>)
/** Partial failures are explicit so the UI can retain results without lying about coverage. */
data class SearchResults(val sections: List<SearchSection>, val partialFailure: Boolean)

/** Server-declared catalog choices and cursor history for the Discover surface. */
data class DiscoverUiState(
    val catalogs: List<DiscoverCatalog> = emptyList(),
    val selectedType: String = "movie",
    val selectedCatalogKey: CatalogKey? = null,
    val selectedFilters: Map<String, String> = emptyMap(),
    val items: List<Media> = emptyList(),
    val requestedSkip: Int = 0,
    val nextSkip: Int? = null,
    val previousSkips: List<Int> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

object DiscoverPolicy {
    fun firstCatalog(catalogs: List<DiscoverCatalog>, type: String): DiscoverCatalog? =
        catalogs.firstOrNull { it.key.type == type } ?: catalogs.firstOrNull()

    /** Required declared filters use their server default or first allowed choice. */
    fun defaults(catalog: DiscoverCatalog): Map<String, String> = buildMap {
        catalog.filters.forEach { filter ->
            val value = filter.defaultValue ?: filter.options.firstOrNull()
            if (filter.required && !value.isNullOrBlank()) put(filter.name, value)
        }
    }

    fun request(catalog: DiscoverCatalog, filters: Map<String, String>, skip: Int): CatalogDiscoverRequest {
        val normalized = filters.mapValues { it.value.trim() }.filterValues(String::isNotBlank)
        return CatalogDiscoverRequest(
            catalog = catalog,
            skip = skip,
            search = normalized["search"].takeIf {
                catalog.supportsSearch || catalog.filters.any { filter -> filter.kind == CatalogFilterKind.Search }
            },
            genre = normalized["genre"],
            extras = normalized.filterKeys { it != "search" && it != "genre" },
        )
    }
}

/** Detail navigation retains its originating browse surface and its filter/page snapshot. */
object DetailReturnPolicy {
    fun destination(origin: Destination?): Destination = origin ?: Destination.Home
}
data class PlaybackPreferences(
    val audioLanguage: String = "en", val subtitleLanguage: String = "en", val subtitlesEnabled: Boolean = false,
    val subtitleSize: String = "normal", val subtitleStyle: String = "system", val quality: String = "auto", val autoplay: Boolean = true,
)
/** Safe server-owned track facts only; URLs and request headers never enter UI state. */
data class PlaybackTrackChoices(
    val audio: List<PlaybackTrack> = emptyList(),
    val subtitles: List<PlaybackTrack> = emptyList(),
    val subtitlesSupported: Boolean = false,
)
data class GuideProgramme(val title: String, val startMillis: Long, val endMillis: Long, val description: String? = null)
data class LiveChannel(val id: String, val name: String, val logo: String? = null, val category: String? = null)

/**
 * Server schedules are cached per channel while the Guide owns window, page and
 * selection.  It stays UI-neutral so Compose can render its five-row grid
 * without making network decisions.
 */
data class GuideUiState(
    /** Exactly the server-selected 40-channel page, never a locally-filtered full catalogue. */
    val channels: List<LiveChannel> = emptyList(),
    val schedulesByChannelId: Map<String, List<GuideProgramme>> = emptyMap(),
    val selectedChannelId: String? = null,
    val page: Int = 0,
    val channelOffset: Int = 0,
    val channelTotal: Int = 0,
    val channelFilter: LiveChannelFilter = LiveChannelFilter.AllUs,
    val categories: List<LiveCategory> = emptyList(),
    val searchScope: String? = null,
    val windowStartMillis: Long = 0,
    val followsNow: Boolean = true,
    val loadingChannelIds: Set<String> = emptySet(),
)

object LiveEntryPolicy {
    fun initialChannel(channels: List<LiveChannel>, rememberedId: String?): LiveChannel? =
        channels.firstOrNull { it.id == rememberedId } ?: channels.firstOrNull()
}

object GuidePolicy {
    const val PAGE_SIZE = 40
    const val VISIBLE_ROWS = 5
    const val WINDOW_MILLIS = 2 * 60 * 60 * 1_000L
    private const val HALF_HOUR_MILLIS = 30 * 60 * 1_000L
    private const val MAX_AHEAD_MILLIS = 24 * 60 * 60 * 1_000L

    fun nowWindow(nowMillis: Long): Long = nowMillis / HALF_HOUR_MILLIS * HALF_HOUR_MILLIS
    fun pageFor(channels: List<LiveChannel>, channelId: String): Int =
        (channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0) / PAGE_SIZE)
    /** `channels` is already the active server page; page is display metadata only. */
    fun visibleRows(state: GuideUiState): List<LiveChannel> {
        val selected = state.channels.indexOfFirst { it.id == state.selectedChannelId }.coerceAtLeast(0)
        return state.channels.drop((selected - (VISIBLE_ROWS - 1)).coerceAtLeast(0)).take(VISIBLE_ROWS)
    }
    fun visibleAndLookAhead(state: GuideUiState): List<LiveChannel> {
        val selected = state.channels.indexOfFirst { it.id == state.selectedChannelId }.coerceAtLeast(0)
        val start = (selected - (VISIBLE_ROWS - 1)).coerceAtLeast(0)
        return state.channels.drop(start).take(VISIBLE_ROWS + 2)
    }
    fun shiftedWindow(windowStartMillis: Long, hours: Int, nowMillis: Long): Long =
        (windowStartMillis + hours * 60 * 60 * 1_000L).coerceIn(nowWindow(nowMillis), nowWindow(nowMillis) + MAX_AHEAD_MILLIS)
}
/** Playback failures retain a title coordinate so every recovery action is explicit and deterministic. */
object PlaybackRecoveryPolicy {
    fun snapshot(media: Media, positionMillis: Long, durationMillis: Long?): Media = media.copy(
        positionMillis = positionMillis.coerceAtLeast(0),
        durationMillis = durationMillis ?: media.durationMillis,
    )
    fun returnRoute(returnDestination: PlaybackReturn, media: Media): Route = when (returnDestination) {
        PlaybackReturn.Details -> Route.Details(media)
        PlaybackReturn.Sources -> Route.Sources(media)
    }
}

enum class DialogKind { QueueManage, MyListManage, EpisodeManage, SourceDetails, LiveManage, DeleteProfile, SignOut, NextUnavailable, PlaybackRecovery }
data class DialogState(val kind: DialogKind, val title: String, val media: Media? = null, val source: Source? = null, val profile: Profile? = null)
data class PinPrompt(val title: String)
data class SeekPreview(val targetMillis: Long)

data class AppState(
    val route: Route = Route.Pairing,
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    val profilePage: Int = 0,
    val managingProfiles: Boolean = false,
    val shelves: List<HomeShelf> = emptyList(),
    val catalog: List<Media> = emptyList(),
    val sources: List<Source> = emptyList(),
    val favorites: List<Media> = emptyList(),
    val queue: List<Media> = emptyList(),
    /** Query-owned state survives focus moves between keyboard and source-labelled rows. */
    val searchQuery: String = "",
    val searchStatus: String = "Find your next favorite.",
    val searchSections: List<SearchSection> = emptyList(),
    val searchResults: List<Media> = emptyList(), // Compatibility projection for older surfaces.
    val discoverUi: DiscoverUiState = DiscoverUiState(),
    val liveChannels: List<LiveChannel> = emptyList(),
    val guide: List<GuideProgramme> = emptyList(),
    val guideUi: GuideUiState = GuideUiState(),
    val addons: List<Addon> = emptyList(),
    val serverAbout: ServerAbout? = null,
    val preferences: PlaybackPreferences = PlaybackPreferences(),
    val playbackTracks: PlaybackTrackChoices = PlaybackTrackChoices(),
    /** Server delivery category; safe UI state used to choose native versus managed seek. */
    val playbackDeliveryMode: String = "direct",
    val dialog: DialogState? = null,
    val pinPrompt: PinPrompt? = null,
    val seekPreview: SeekPreview? = null,
    val deviceCode: DeviceCode? = null,
    /** Player controls begin visible and dismiss after seven seconds of inactivity. */
    val playerChromeVisible: Boolean = true,
    val loading: Boolean = false,
    val message: String? = null,
)
