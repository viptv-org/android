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
)

data class Source(
    val id: String,
    val provider: String,
    val name: String = provider,
    val description: String = "",
    val headers: Map<String, String> = emptyMap(),
    val addonId: String? = null,
    val fingerprint: String? = null,
)

sealed interface PlaybackIntent {
    data class Open(val source: Source, val positionMillis: Long) : PlaybackIntent
    data object ChooseSource : PlaybackIntent
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

/** Back always resolves transient state before leaving its route. */
enum class BackDisposition { DismissDialog, CancelPin, CancelSeek, HidePlayerChrome, ExitPlayer, Navigate }
object BackPolicy {
    fun decide(dialogOpen: Boolean, pinOpen: Boolean, seekPreviewOpen: Boolean, playerChromeOpen: Boolean, inPlayer: Boolean): BackDisposition = when {
        dialogOpen -> BackDisposition.DismissDialog
        pinOpen -> BackDisposition.CancelPin
        seekPreviewOpen -> BackDisposition.CancelSeek
        inPlayer && playerChromeOpen -> BackDisposition.HidePlayerChrome
        inPlayer -> BackDisposition.ExitPlayer
        else -> BackDisposition.Navigate
    }
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

enum class Destination(val label: String) {
    Profile("Profile"), Home("Home"), Discover("Discover"), Live("Live TV"), MyList("My List"), Search("Search"), Settings("Settings")
}

sealed interface Route {
    data object Pairing : Route
    data object Profiles : Route
    data class Browse(val destination: Destination) : Route
    data class Details(val media: Media) : Route
    data class Sources(val media: Media, val resume: Boolean = false) : Route
    data class Player(val media: Media, val source: Source) : Route
    data object Search : Route
    data object Settings : Route
    data object Addons : Route
    data class ProfileEditor(val profile: Profile? = null) : Route
    data class Guide(val channel: LiveChannel) : Route
}

data class Profile(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val kids: Boolean = false,
    val primary: Boolean = false,
    val avatarStyle: String = "critters",
    val avatarSeed: String? = null,
)
data class DeviceCode(val code: String, val userCode: String, val verificationUri: String, val verificationUriComplete: String?, val qrUri: String?, val intervalSeconds: Long)
data class DeviceSession(val accessToken: String, val refreshToken: String, val profileId: String?)
data class HomeShelf(val title: String, val items: List<Media>)
data class NextResult(val status: String, val item: Media? = null)
data class Addon(val id: String, val name: String, val manifestUrl: String, val enabled: Boolean)
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
enum class DialogKind { QueueManage, MyListManage, EpisodeManage, SourceDetails, LiveManage, DeleteProfile, SignOut, NextUnavailable }
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
    val searchResults: List<Media> = emptyList(),
    val liveChannels: List<LiveChannel> = emptyList(),
    val guide: List<GuideProgramme> = emptyList(),
    val addons: List<Addon> = emptyList(),
    val preferences: PlaybackPreferences = PlaybackPreferences(),
    val playbackTracks: PlaybackTrackChoices = PlaybackTrackChoices(),
    val dialog: DialogState? = null,
    val pinPrompt: PinPrompt? = null,
    val seekPreview: SeekPreview? = null,
    val deviceCode: DeviceCode? = null,
    /** Player controls begin visible and dismiss after seven seconds of inactivity. */
    val playerChromeVisible: Boolean = true,
    val loading: Boolean = false,
    val message: String? = null,
)
