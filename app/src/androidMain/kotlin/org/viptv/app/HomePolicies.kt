package org.viptv.app

/** Queue holds are available only for non-live Continue Watching content. */
object QueuePolicy {
    fun canManage(media: Media): Boolean = media.type != "live"
    fun manageTarget(media: Media): Media = media.previousEpisode ?: media
    fun canResume(media: Media): Boolean = manageTarget(media).type != "live" && manageTarget(media).positionMillis > 0
    /** This status is emitted only by the server continuation cache. */
    fun hasResolvedNext(media: Media): Boolean = media.queueStatus == "next" && media.previousEpisode != null
}

enum class HomeFocusSurface { Hero, Card }

data class HomeFocusSnapshot(
    val shelfIndex: Int? = null,
    val shelfTitle: String? = null,
    val mediaKey: String? = null,
    val surface: HomeFocusSurface = HomeFocusSurface.Card,
    /** Incremented when a route explicitly returns to Home and asks Compose to restore. */
    val restoreRequest: Long = 0L,
    /** A directional event invalidates a queued focus restoration immediately. */
    val inputEpoch: Long = 0L,
)

object HomeFocusPolicy {
    fun mediaKey(media: Media): String = "${media.type}\u0000${media.id}"
    fun record(
        current: HomeFocusSnapshot,
        shelfIndex: Int,
        shelfTitle: String,
        media: Media,
        surface: HomeFocusSurface = HomeFocusSurface.Card,
    ): HomeFocusSnapshot = current.copy(shelfIndex = shelfIndex, shelfTitle = shelfTitle, mediaKey = mediaKey(media), surface = surface)
    fun afterDirectionalInput(current: HomeFocusSnapshot): HomeFocusSnapshot = current.copy(inputEpoch = current.inputEpoch + 1)
    fun requestRestore(current: HomeFocusSnapshot): HomeFocusSnapshot = current.copy(restoreRequest = current.restoreRequest + 1)
    fun mayRestore(snapshot: HomeFocusSnapshot, observedInputEpoch: Long): Boolean = snapshot.inputEpoch == observedInputEpoch
}

/** An older queue refresh must never overwrite a later Undo response. */
object HomeRefreshPolicy {
    fun accepts(responseGeneration: Long, currentGeneration: Long): Boolean = responseGeneration == currentGeneration
}

object DevicePollPolicy {
    fun nextIntervalSeconds(issuedSeconds: Long, currentSeconds: Long, rateLimited: Boolean): Long =
        if (rateLimited) (currentSeconds.coerceAtLeast(issuedSeconds.coerceAtLeast(1)) * 2).coerceAtMost(30)
        else issuedSeconds.coerceAtLeast(1)
}

data class HomeShelfFocusTarget(val shelfIndex: Int, val shelfTitle: String, val media: Media)

/** Keeps a vertical remote move in the same card column, clamping only at a row edge. */
object HomeShelfFocusPolicy {
    fun move(shelves: List<HomeShelf>, current: HomeFocusSnapshot, delta: Int): HomeShelfFocusTarget? {
        if (delta !in setOf(-1, 1)) return null
        val fromShelfIndex = current.shelfIndex ?: return null
        val fromShelf = shelves.getOrNull(fromShelfIndex) ?: return null
        val fromMediaIndex = fromShelf.items.indexOfFirst { HomeFocusPolicy.mediaKey(it) == current.mediaKey }
        if (fromMediaIndex < 0) return null
        val targetShelfIndex = fromShelfIndex + delta
        val targetShelf = shelves.getOrNull(targetShelfIndex)?.takeIf { it.items.isNotEmpty() } ?: return null
        return HomeShelfFocusTarget(
            shelfIndex = targetShelfIndex,
            shelfTitle = targetShelf.title,
            media = targetShelf.items[fromMediaIndex.coerceAtMost(targetShelf.items.lastIndex)],
        )
    }
}
