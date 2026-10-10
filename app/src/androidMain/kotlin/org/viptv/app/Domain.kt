package org.viptv.app

import org.json.JSONObject

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
    /** Server continuation state for a Continue Watching display row. */
    val queueStatus: String? = null,
    /** When a cached next replaces the row, management still addresses this prior episode. */
    val previousEpisode: Media? = null,
    val backdrop: String? = null,
    val thumbnail: String? = null,
    val year: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val credits: String? = null,
    val resumeActive: Boolean? = null,
    val completionOnly: Boolean? = null,
    val watchDateKnown: Boolean? = null,
    val watched: Boolean = false,
    val imdbRating: String? = null,
    val posterShape: String? = null,
    val updatedAtMillis: Long? = null,
    val releasedAtMillis: Long? = null,
    internal val coreItem: org.viptv.core.wire.MediaItem? = null,
)

/** Rust enriches display metadata while retaining this occurrence's progress and identity. */
internal fun Media.withArtworkFrom(other: Media): Media = CoreModels.enrich(this, other)

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
    /** Direct live playback target; never serialized as a discovered stream identifier. */
    val channelId: String? = null,
    /** True when shared Rust has projected the safe display fields. */
    val displayResolved: Boolean = false,
    val providerKey: String = "",
    val providerLabel: String = "",
)

/** Safe Title display facts; source selection remains an explicit picker action. */
data class SourceSummary(val key: String, val best: Source?, val count: Int, val done: Boolean, val failed: Boolean = false)

/** Safe, Rust-normalized discovery event facts for a configured producer. */
data class SourceProducerOutcome(
    val sourceId: String,
    val label: String = sourceId,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    /** The same generated display projection supplies keys for rows and empty producers. */
    val providerKey: String get() = SourceDisplayPolicy.providerKey(Source("", "", addonId = sourceId))
}

/** Join observed producer IDs to account-configured labels; never create a choice from settings alone. */
internal fun namedSourceProducers(observed: List<SourceProducerOutcome>, addons: List<Addon>, sources: List<Source>): List<SourceProducerOutcome> =
    SharedPresentation.namedProducers(observed, addons, sources)

object SourceDisplayPolicy {
    private fun display(source: Source): JSONObject = CorePolicy.value("sourceDisplay", JSONObject().put("name", source.name).put("description", source.description).put("provider", source.provider).putOpt("sourceAddonId", source.addonId)) as JSONObject
    fun title(source: Source): String = if (source.displayResolved) source.name else display(source).getString("title")
    fun body(source: Source): String = if (source.displayResolved) source.description else display(source).getString("body")
    fun providerKey(source: Source): String = source.providerKey.ifBlank { display(source).getString("providerKey") }
    fun providerLabel(source: Source): String = source.providerLabel.ifBlank { display(source).getString("providerLabel") }
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
data class DeviceCode(val code: String, val userCode: String, val verificationUri: String, val verificationUriComplete: String?, val qrUri: String?, val intervalSeconds: Long) { override fun toString() = "DeviceCode(<redacted>)" }
data class DeviceSession(val accessToken: String, val refreshToken: String, val profileId: String?, val coreJson: String = "") { override fun toString() = "DeviceSession(<redacted>)" }
/**
 * Home rows carry their role separately from server-provided display copy.
 * Queue controls follow this flag even when the row has just become empty.
 */
/** [contentType]/[catalogName] identify a catalog shelf so phones can head it by content type (AND-042). */
data class HomeShelf(val title: String, val items: List<Media>, val isQueueShelf: Boolean = false, val id: String = title, val contentType: String? = null, val catalogName: String? = null)

data class NextResult(val status: String, val item: Media? = null)
data class Addon(val id: String, val name: String, val manifestUrl: String, val enabled: Boolean)
/** Deliberately minimal, account-safe facts for the informational Settings section. */
data class ServerAbout(val mediaServiceAvailable: Boolean)
/** A labelled source section retained when another search source fails. */
data class SearchSection(val source: String, val items: List<Media>, val id: String = source, val type: String? = null)
/** Partial failures are explicit so the UI can retain results without lying about coverage. */
data class SearchResults(val sections: List<SearchSection>, val partialFailure: Boolean, val coverage: String = "", val hasMore: Boolean = false)

data class PlaybackPreferences(
    val audioLanguage: String = "en", val subtitleLanguage: String = "en", val subtitlesEnabled: Boolean = false,
    val subtitleSize: String = "normal", val subtitleStyle: String = "system", val autoplay: Boolean = true,
)
/** Safe server-owned track facts only; URLs and request headers never enter UI state. */
data class PlaybackTrackChoices(
    val audio: List<PlaybackTrack> = emptyList(),
    val subtitles: List<PlaybackTrack> = emptyList(),
    val subtitlesSupported: Boolean = false,
)
data class GuideProgramme(
    val title: String, val startMillis: Long, val endMillis: Long, val description: String? = null,
    val displayTime: String? = null, val timezone: String? = null,
    val timelineLabels: Map<Long, String> = emptyMap(),
)
data class LiveChannel(val id: String, val name: String, val logo: String? = null, val category: String? = null)

enum class DialogKind { QueueManage, QueueRemoved, MyListManage, EpisodeManage, SourceDetails, LiveManage, DeleteProfile, SignOut, NextUnavailable, PlaybackRecovery }
data class DialogState(val kind: DialogKind, val title: String, val media: Media? = null, val source: Source? = null, val profile: Profile? = null, val detail: String? = null)
data class PinPrompt(val title: String)
data class SeekPreview(val targetMillis: Long)

data class AppState(
    val foregroundError: String? = null,
    val sessionRestoring: Boolean = true,
    val route: Route = Route.Pairing,
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    val profilePage: Int = 0,
    val managingProfiles: Boolean = false,
    val shelves: List<HomeShelf> = emptyList(),
    /** Identity and restore epoch are product state, so refreshes cannot steal D-pad focus. */
    val homeFocus: HomeFocusSnapshot = HomeFocusSnapshot(),
    val catalog: List<Media> = emptyList(),
    val sources: List<Source> = emptyList(),
    val sourceSummary: SourceSummary? = null,
    val sourceProducers: List<SourceProducerOutcome> = emptyList(),
    val favorites: List<Media> = emptyList(),
    val queue: List<Media> = emptyList(),
    val libraryQueue: Boolean = false,
    /** Queued Next is cancellable from Home before it has a source route. */
    val queueContinuationPending: Boolean = false,
    /** Query-owned state survives focus moves between keyboard and source-labelled rows. */
    val searchQuery: String = "",
    /** Increments only for explicit Search navigation, never for a details return. */
    val searchEntryEpoch: Int = 0,
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
    /** Measured decoder facts keep source recommendations reactive to the async probe. */
    val sourceCapabilities: PlaybackClientCapabilities? = null,
    val playbackTracks: PlaybackTrackChoices = PlaybackTrackChoices(),
    /** Server delivery category; safe UI state used to choose native versus managed seek. */
    val playbackDeliveryMode: String = "direct",
    val dialog: DialogState? = null,
    val pinPrompt: PinPrompt? = null,
    val seekPreview: SeekPreview? = null,
    val upNext: UpNextPrompt? = null,
    val deviceCode: DeviceCode? = null,
    /** Player controls begin visible and dismiss after seven seconds of inactivity. */
    val playerChromeVisible: Boolean = true,
    val loading: Boolean = false,
    val homeLoading: Boolean = false,
    val sourceLoading: Boolean = false,
    val preparingSourceId: String? = null,
    val playbackPreparationStage: String? = null,
    val pairingRequested: Boolean = false,
    val message: String? = null,
)
