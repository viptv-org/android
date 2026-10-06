package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.viptv.core.wire.LiveCatalogCategories

/** The original query stays implicit when the guide uses the account default. */
internal data class GuideCategoryScope(val profileId: String, val catalogId: String?, val generation: String?,
    val query: LiveCatalogQuery = LiveCatalogQuery(limit = 200))

data class GuideCategoryPageState(
    val items: List<LiveCategory> = emptyList(),
    val cursor: String? = null,
    val nextCursor: String? = null,
    val previousCursor: String? = null,
    /** Captured by rendered-list callbacks; old callbacks cannot move a new page. */
    val revision: Long = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val focusIndex: Int = 0,
    val visibleFirst: Int = 0,
    val visibleLast: Int = 0,
    val scrollOffset: Int = 0,
    val awaitingAnchor: Boolean = false,
    /** App-internal exact LazyRow position, including the fixed controls. */
    val rowFirstKey: String? = null,
    val rowFirstIndex: Int = 0,
    val rowScrollOffset: Int = 0,
    val rowFocusKey: String? = null,
)

/** One 200-category replacement page. It never fetches channels, EPG or media. */
internal class GuideCategoryPager(
    private val scope: CoroutineScope,
    private val fetch: suspend (LiveCatalogQuery) -> LiveCatalogCategories,
    private val currentScope: () -> GuideCategoryScope?,
    private val publish: (GuideCategoryPageState) -> Unit,
    private val onError: (GatewayError) -> Unit,
) {
    private var owner: GuideCategoryScope? = null
    private var generation = 0L
    private var job: Job? = null
    private var page = GuideCategoryPageState()
    private var retry: Pair<String?, Boolean>? = null

    fun cancel() {
        job?.cancel(); job = null
        page = page.copy(revision = ++generation, loading = false)
        if (owner != null && owner == currentScope()) publish(page)
    }

    fun ensure() {
        val active = currentScope() ?: return
        if (owner != active) {
            job?.cancel(); owner = active; retry = null
            page = GuideCategoryPageState(revision = ++generation)
        }
        if (page.loaded || page.loading) publish(page) else load(null, false, active)
    }

    fun retry() {
        val active = currentScope() ?: return
        if (active != owner || page.loading) return
        val request = retry ?: return
        load(request.first, request.second, active)
    }

    fun move(delta: Int, renderedRevision: Long) {
        val active = currentScope() ?: return
        if (active != owner || renderedRevision != page.revision || page.loading || page.error != null || delta == 0) return
        val previous = delta < 0
        val cursor = (if (previous) page.previousCursor else page.nextCursor) ?: return
        load(cursor, previous, active)
    }

    fun rowViewport(renderedRevision: Long, firstKey: String, firstIndex: Int, offset: Int, focusKey: String?) {
        if (currentScope() != owner || renderedRevision != page.revision || page.loading) return
        if (guideCategoryRowKeyIndex(page.items, firstKey) != firstIndex ||
            focusKey != null && guideCategoryRowKeyIndex(page.items, focusKey) == null) return
        val position = offset.coerceAtLeast(0)
        if (page.rowFirstKey == firstKey && page.rowFirstIndex == firstIndex && page.rowScrollOffset == position && page.rowFocusKey == focusKey) return
        page = page.copy(rowFirstKey = firstKey, rowFirstIndex = firstIndex, rowScrollOffset = position, rowFocusKey = focusKey)
        publish(page)
    }

    /** Category IDs only, not the fixed All/My/Recent/Search tab indices. */
    fun viewport(renderedRevision: Long, firstId: String, lastId: String, offset: Int, allowPaging: Boolean = true) {
        if (currentScope() != owner || renderedRevision != page.revision || page.loading) return
        val first = page.items.indexOfFirst { it.id == firstId }
        val last = page.items.indexOfFirst { it.id == lastId }
        if (first < 0 || last < first) return
        val restored = page.awaitingAnchor
        if (restored && page.focusIndex !in first..last) return
        if (!restored && first == page.visibleFirst && last == page.visibleLast && offset.coerceAtLeast(0) == page.scrollOffset) return
        val forward = first > page.visibleFirst || last > page.visibleLast || offset > page.scrollOffset
        val backward = first < page.visibleFirst || last < page.visibleLast || offset < page.scrollOffset
        page = page.copy(visibleFirst = first, visibleLast = last, scrollOffset = offset.coerceAtLeast(0), awaitingAnchor = false)
        publish(page)
        // The first viewport after restoration is an acknowledgement, not input.
        if (restored || page.error != null || !allowPaging) return
        if (forward && last == page.items.lastIndex) move(1, renderedRevision)
        else if (backward && first == 0) move(-1, renderedRevision)
    }

    private fun load(cursor: String?, previous: Boolean, active: GuideCategoryScope) {
        job?.cancel()
        val ticket = ++generation
        val before = page
        page = page.copy(revision = ticket, loading = true, error = null, errorCode = null)
        publish(page)
        job = scope.launch {
            try {
                val response = withTimeout(15_000) { fetch(active.query.copy(cursor = cursor, limit = 200)) }
                if (ticket != generation || currentScope() != active) return@launch
                CorePlaybackPolicy.requireLivePage(CorePlaybackPolicy.livePage(
                    response.catalogId, response.generation, response.items.map { it.id }, response.items.map { it.name },
                    response.nextCursor, response.previousCursor, checkSnapshot = true,
                    snapshotCatalogId = active.catalogId, snapshotGeneration = active.generation,
                    knownIds = before.items.map { it.id }, cursor = cursor, previous = previous,
                ))
                page = GuideCategoryPageState(items = response.items.map { LiveCategory(it.id, it.name) }, cursor = cursor,
                    nextCursor = response.nextCursor, previousCursor = response.previousCursor, revision = ticket, loaded = true,
                    focusIndex = if (previous) (response.items.size - 1).coerceAtLeast(0) else 0, awaitingAnchor = response.items.isNotEmpty())
                retry = null; publish(page)
            } catch (error: Throwable) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (ticket != generation || currentScope() != active) return@launch
                val safe = error as? GatewayError ?: if (error is TimeoutCancellationException)
                    GatewayError(504, "Category loading timed out. Try again.", "request_timeout")
                    else GatewayError(502, "Could not load categories. Try again.", "request_failed")
                retry = cursor to previous
                page = before.copy(revision = ticket, loading = false, error = safe.message, errorCode = safe.code)
                publish(page); onError(safe)
            } finally {
                if (ticket == generation && page.loading) {
                    page = page.copy(loading = false)
                    if (currentScope() == active) publish(page)
                }
            }
        }
    }
}
