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
        if (first != null && (page.catalogId != first.catalogId || page.generation != first.generation) || page.channels.isEmpty())
            throw GatewayError(409, "This playlist changed while you were browsing. Reload the guide.", "catalog_changed")
        val known = channels.map { it.id }.toSet()
        if (page.channels.map { it.id }.distinct().size != page.channels.size || page.channels.any { it.id in known })
            throw GatewayError(502, "The server repeated a live playlist page. Reload the guide.", "invalid_catalog_response")
        if (previous) {
            pages.addFirst(page); start = (start - page.channels.size).coerceAtLeast(0)
            if (pages.size > 3) pages.removeLast()
        } else {
            pages.addLast(page)
            if (pages.size > 3) start += pages.removeFirst().channels.size
        }
    }
}
