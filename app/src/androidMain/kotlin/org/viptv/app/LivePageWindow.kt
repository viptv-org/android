package org.viptv.app

/** Three server pages maximum. Reverse cursors retrieve evicted rows. */
internal class LivePageWindow {
    private val pages = ArrayDeque<LiveBrowsePage>()
    var start: Int = 0
        private set
    val channels: List<LiveChannel> get() = pages.flatMap { it.channels }
    val next: String? get() = pages.lastOrNull()?.nextCursor
    val previous: String? get() = pages.firstOrNull()?.previousCursor
    fun replace(page: LiveBrowsePage, offset: Int = 0) {
        pages.clear(); pages.add(page); start = offset
    }
    fun add(page: LiveBrowsePage, previous: Boolean) {
        val first = pages.firstOrNull()
        CorePlaybackPolicy.requireLivePage(CorePlaybackPolicy.livePage(
            page.catalogId, page.generation, page.channels.map { it.id }, page.channels.map { it.name },
            page.nextCursor, page.previousCursor, categories = false, checkSnapshot = first != null,
            snapshotCatalogId = first?.catalogId, snapshotGeneration = first?.generation,
            knownIds = channels.map { it.id }, extendingWindow = true,
        ))
        if (previous) {
            pages.addFirst(page); start = (start - page.channels.size).coerceAtLeast(0)
            if (pages.size > 3) pages.removeLast()
        } else {
            pages.addLast(page)
            if (pages.size > 3) start += pages.removeFirst().channels.size
        }
    }
}
