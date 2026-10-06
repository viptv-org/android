package org.viptv.app

/** Shared Home hold/Info inventory; ordinary shelf cards do not invent source menus. */
object HomeHoldPolicy {
    /** Continue Watching is logical Home row zero and always opens Queue Manage. */
    fun opensQueueManage(continueWatchingRow: Boolean, media: Media): Boolean = SharedPresentation.home(media, continueWatchingRow).opensQueueManage
    /** A series root opens its episode selector; only a resolved episode or movie opens Sources. */
    fun opensSourcesFromHero(continueWatchingRow: Boolean, media: Media): Boolean =
        SharedPresentation.home(media, continueWatchingRow).opensSourcesFromHero
}

/** Hero primary differs from an ordinary card only for manual playable media. */
object HomeHeroPrimaryPolicy {
    fun choosesManualSource(action: MediaCardAction, queueShelf: Boolean, media: Media): Boolean =
        action == MediaCardAction.OpenDetails && HomeHoldPolicy.opensSourcesFromHero(queueShelf, media)
}

/**
 * Source discovery remembers the surface that opened it.  This is deliberately
 * separate from the player return route: a Home shortcut can cancel back to
 * Home, while successful non-live playback still exits through title detail.
 */
enum class SourceReturn { Details, Home }
object SourceReturnPolicy {
    /** A queue episode's title route addresses its parent, retaining the episode cursor. */
    fun parentSeries(media: Media): Media? {
        if (media.season == null || media.episode == null) return null
        val id = when (media.type) {
            "episode" -> media.seriesId
            "series" -> media.seriesId ?: media.id
            else -> null
        }?.takeIf(String::isNotBlank) ?: return null
        return Media(id, "series", media.name, poster = media.poster, description = media.description,
            season = media.season, episode = media.episode, backdrop = media.backdrop,
            thumbnail = media.thumbnail)
    }

    fun cancelRoute(origin: SourceReturn, media: Media): Route = when (origin) {
        SourceReturn.Details -> Route.Details(media)
        SourceReturn.Home -> Route.Browse(Destination.Home)
    }

    fun playbackReturn(origin: SourceReturn, explicitResume: Boolean): PlaybackReturn = when (origin) {
        SourceReturn.Home -> PlaybackReturn.Details
        SourceReturn.Details -> PlaybackReturnPolicy.afterSourceStart(explicitResume)
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
    } || state.dialog != null || state.pinPrompt != null || state.seekPreview != null || state.queueContinuationPending
}

enum class Destination(val label: String) {
    Profile("Profile"), Home("Home"), Discover("Discover"), Live("Live TV"), MyList("My List"), Search("Search"), Settings("Settings")
}

sealed interface Route {
    data object Pairing : Route
    data object Profiles : Route
    data class Browse(val destination: Destination) : Route
    data class Details(val media: Media, val entryId: Long = 0L) : Route
    data class Sources(val media: Media, val resume: Boolean = false, val origin: SourceReturn = SourceReturn.Details, val backRoute: Route? = null, val queueEpisodeReturn: Boolean = false) : Route
    data class Player(val media: Media, val source: Source, val returnDestination: PlaybackReturn = PlaybackReturn.Details, val directOrigin: Route? = null, val sourceRoute: Sources? = null) : Route
    data object Search : Route
    data object Settings : Route
    data object Addons : Route
    data class ProfileEditor(val profile: Profile? = null) : Route
    /** A valid empty server filter still owns the Guide filters and Back path. */
    data class Guide(val channel: LiveChannel? = null) : Route
}

/** Detail navigation retains its originating browse surface and its filter/page snapshot. */
object DetailReturnPolicy {
    fun destination(origin: Destination?): Destination = origin ?: Destination.Home
}
