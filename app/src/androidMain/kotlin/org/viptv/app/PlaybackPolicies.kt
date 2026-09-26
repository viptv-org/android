package org.viptv.app

import org.json.JSONObject

sealed interface PlaybackIntent {
    data class Open(val source: Source, val positionMillis: Long) : PlaybackIntent
    data object ChooseSource : PlaybackIntent
}

enum class MediaCardAction { OpenDetails, ResumeExactSource, PlayQueuedNext }
object MediaCardPolicy {
    /** Only Continue Watching has a Resume primary action; discovery always opens details. */
    fun primary(resumeSurface: Boolean, media: Media): MediaCardAction =
        when {
            resumeSurface && QueuePolicy.hasResolvedNext(media) -> MediaCardAction.PlayQueuedNext
            resumeSurface && media.positionMillis > 0 && media.type != "live" -> MediaCardAction.ResumeExactSource
            else -> MediaCardAction.OpenDetails
        }
}

/** Product policy from the design contract. The player adapter does not choose sources. */
object PlaybackPolicy {
    fun forResume(expectedIdentity: String?, discovered: List<Source>, positionMillis: Long = 0): PlaybackIntent {
        val id = CorePolicy.value("resume", JSONObject().putOpt("expectedIdentity", expectedIdentity).put("sources", CorePolicy.sources(discovered))) as? String
        return discovered.firstOrNull { it.id == id }?.let { PlaybackIntent.Open(it, positionMillis) } ?: PlaybackIntent.ChooseSource
    }
    fun canAutoNext(media: Media, positionMillis: Long, durationMillis: Long?, playing: Boolean, seeking: Boolean, nextAvailable: Boolean, autoplay: Boolean = true): Boolean =
        CorePolicy.value("autoNext", JSONObject().put("type", media.type).put("position", positionMillis / 1000.0).putOpt("duration", durationMillis?.let { it / 1000.0 }).put("playing", playing).put("seeking", seeking).put("nextAvailable", nextAvailable).put("autoplay", autoplay)) == true

}

object ResumeIdentity {
    fun storageKey(profileId: String, media: Media): String = "source.$profileId.${media.type}.${media.id}"
    /** Stream job IDs and display names are unstable; both server-owned fields are required. */
    fun sourceIdentity(addonId: String?, fingerprint: String?): String? =
        CorePolicy.value("sourceIdentity", JSONObject().putOpt("addonId", addonId).putOpt("fingerprint", fingerprint)) as? String
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
        val id = CorePolicy.value("continuationSource", JSONObject().putOpt("outgoingAddonId", outgoing?.addonId).putOpt("nextAddonId", next.sourceAddonId).put("sources", CorePolicy.sources(candidates))) as? String
        return candidates.firstOrNull { it.id == id }
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

enum class PlaybackReturn { Details, Sources }

object PlaybackReturnPolicy {
    /** Explicit Resume returns to title detail; an ordinary source picker remains its return surface. */
    fun afterSourceStart(resume: Boolean): PlaybackReturn = if (resume) PlaybackReturn.Details else PlaybackReturn.Sources
}

/** Playback failures retain a title coordinate so every recovery action is explicit and deterministic. */
object PlaybackRecoveryPolicy {
    fun returnRoute(player: Route.Player, media: Media): Route = player.directOrigin ?: when (player.returnDestination) {
        PlaybackReturn.Sources -> player.sourceRoute?.copy(media = media) ?: Route.Sources(media)
        PlaybackReturn.Details -> (player.sourceRoute?.backRoute as? Route.Details) ?: Route.Details(media)
    }

    fun snapshot(media: Media, positionMillis: Long, durationMillis: Long?): Media = media.copy(
        positionMillis = positionMillis.coerceAtLeast(0),
        durationMillis = durationMillis ?: media.durationMillis,
    )
    fun returnRoute(returnDestination: PlaybackReturn, media: Media): Route = when (returnDestination) {
        PlaybackReturn.Details -> Route.Details(media)
        PlaybackReturn.Sources -> Route.Sources(media)
    }
}
