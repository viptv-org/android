package org.viptv.app

/** Queue holds are available only for non-live Continue Watching content. */
object QueuePolicy {
    fun canManage(media: Media): Boolean = SharedPresentation.home(media).canManage
    fun manageTarget(media: Media): Media = if (SharedPresentation.home(media).managePrevious) requireNotNull(media.previousEpisode) else media
    fun canResume(media: Media): Boolean = SharedPresentation.home(media).canResume
    fun hasResolvedNext(media: Media): Boolean = SharedPresentation.home(media).hasResolvedNext
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
    /** Last selection in the top shelf; lower shelf focus does not replace the hero. */
    val heroMediaKey: String? = null,
)

object HomeFocusPolicy {
    fun mediaKey(media: Media): String = "${media.type}\u0000${media.id}"
    fun record(
        current: HomeFocusSnapshot,
        shelfIndex: Int,
        shelfTitle: String,
        media: Media,
        surface: HomeFocusSurface = HomeFocusSurface.Card,
    ): HomeFocusSnapshot = current.copy(shelfIndex = shelfIndex, shelfTitle = shelfTitle, mediaKey = mediaKey(media), surface = surface,
        heroMediaKey = if (shelfIndex == 0) mediaKey(media) else current.heroMediaKey)
    fun afterDirectionalInput(current: HomeFocusSnapshot): HomeFocusSnapshot = current.copy(inputEpoch = current.inputEpoch + 1)
    fun requestRestore(current: HomeFocusSnapshot): HomeFocusSnapshot = current.copy(restoreRequest = current.restoreRequest + 1)
    fun mayRestore(snapshot: HomeFocusSnapshot, observedInputEpoch: Long): Boolean = snapshot.inputEpoch == observedInputEpoch

    fun reconcile(current: HomeFocusSnapshot, previous: List<HomeShelf>, next: List<HomeShelf>, restoreFocusedCard: Boolean = true): HomeFocusSnapshot {
        val key = current.mediaKey ?: return current
        val surviving = next.indexOfFirst { shelf -> shelf.id == current.shelfTitle && shelf.items.any { mediaKey(it) == key } }
        if (surviving >= 0) return current.copy(shelfIndex = surviving)
        val oldShelf = previous.indexOfFirst { it.id == current.shelfTitle }.takeIf { it >= 0 } ?: current.shelfIndex ?: 0
        val oldCard = previous.getOrNull(oldShelf)?.items?.indexOfFirst { mediaKey(it) == key }?.takeIf { it >= 0 } ?: 0
        val nearest = next.indices.filter { next[it].items.isNotEmpty() }.minWithOrNull(compareBy<Int> { kotlin.math.abs(it - oldShelf) }.thenBy { it })
            ?: return current.copy(shelfIndex = null, shelfTitle = null, mediaKey = null,
                restoreRequest = current.restoreRequest + if (restoreFocusedCard) 1 else 0)
        val shelf = next[nearest]
        val card = shelf.items[oldCard.coerceAtMost(shelf.items.lastIndex)]
        return current.copy(shelfIndex = nearest, shelfTitle = shelf.id, mediaKey = mediaKey(card),
            restoreRequest = current.restoreRequest + if (restoreFocusedCard) 1 else 0)
    }
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

/** AND-042 phone presentation: content-type shelf headings and minimal card context. */
object PhonePresentationPolicy {
    fun shelfHeading(shelf: HomeShelf): String = SharedPresentation.phone(shelf = shelf).shelfHeading
    fun contentTypeLabel(type: String): String = SharedPresentation.phone(contentType = type).contentTypeLabel
    fun cardContext(media: Media): String = SharedPresentation.phone(media = media).cardContext
}
