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
)

object SourceDisplayPolicy {
    private fun display(source: Source): JSONObject = CorePolicy.value("sourceDisplay", JSONObject().put("name", source.name).put("description", source.description).put("provider", source.provider)) as JSONObject
    fun title(source: Source): String = display(source).getString("title")
    fun body(source: Source): String = display(source).getString("body")
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
data class DeviceSession(val accessToken: String, val refreshToken: String, val profileId: String?, val coreJson: String = "")
/**
 * Home rows carry their role separately from server-provided display copy.
 * Queue controls follow this flag even when the row has just become empty.
 */
data class HomeShelf(val title: String, val items: List<Media>, val isQueueShelf: Boolean = false)

data class NextResult(val status: String, val item: Media? = null)
data class Addon(val id: String, val name: String, val manifestUrl: String, val enabled: Boolean)
/** Deliberately minimal, account-safe facts for the informational Settings section. */
data class ServerAbout(val mediaServiceAvailable: Boolean)
/** A labelled source section retained when another search source fails. */
data class SearchSection(val source: String, val items: List<Media>)
/** Partial failures are explicit so the UI can retain results without lying about coverage. */
data class SearchResults(val sections: List<SearchSection>, val partialFailure: Boolean)

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
data class GuideProgramme(
    val title: String, val startMillis: Long, val endMillis: Long, val description: String? = null,
    val displayTime: String? = null, val timezone: String? = null,
    val timelineLabels: Map<Long, String> = emptyMap(),
)
data class LiveChannel(val id: String, val name: String, val logo: String? = null, val category: String? = null)

enum class DialogKind { QueueManage, QueueRemoved, MyListManage, EpisodeManage, SourceDetails, LiveManage, DeleteProfile, SignOut, NextUnavailable, PlaybackRecovery }
data class DialogState(val kind: DialogKind, val title: String, val media: Media? = null, val source: Source? = null, val profile: Profile? = null)
data class PinPrompt(val title: String)
data class SeekPreview(val targetMillis: Long)

data class AppState(
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
    val favorites: List<Media> = emptyList(),
    val queue: List<Media> = emptyList(),
    /** Queued Next is cancellable from Home before it has a source route. */
    val queueContinuationPending: Boolean = false,
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
