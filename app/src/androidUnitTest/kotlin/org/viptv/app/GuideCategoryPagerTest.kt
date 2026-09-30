package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.viptv.core.wire.LiveCatalogCategories
import org.viptv.core.wire.LiveCatalogCategory
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GuideCategoryPagerTest {
    @Test fun `row keys retain fixed controls through cancel and reject mismatched stale scope samples`() = runTest {
        var active: GuideCategoryScope? = owner
        var state = GuideCategoryPageState()
        val pager = GuideCategoryPager(this, { query -> page(if (query.cursor == null) 0 else 1) },
            { active }, { state = it }, { fail(it.message) })
        pager.ensure(); runCurrent()
        pager.rowViewport(state.revision,"fixed:all",0,4,"fixed:all")
        assertTrue(state.awaitingAnchor); assertEquals(0,state.rowFirstIndex)
        pager.viewport(state.revision,"cat-0","cat-6",0,allowPaging=false)
        pager.rowViewport(state.revision,"fixed:search",203,12,"fixed:search")
        pager.cancel(); pager.ensure()
        assertEquals("fixed:search",state.rowFirstKey); assertEquals(203,state.rowFirstIndex)
        assertEquals(12,state.rowScrollOffset); assertEquals("fixed:search",state.rowFocusKey)
        val before = state
        pager.rowViewport(state.revision,"fixed:all",203,0,"fixed:all")
        pager.rowViewport(state.revision-1,"fixed:all",0,0,"fixed:all")
        assertEquals(before,state)
        active = owner.copy(profileId="other")
        pager.rowViewport(state.revision,"fixed:all",0,0,"fixed:all")
        assertEquals(before,state)
        pager.ensure(); runCurrent()
        assertNull(state.rowFirstKey); assertNull(state.rowFocusKey); assertEquals(0,state.rowScrollOffset)
    }
    @Test fun `replacement resets row anchors while an error keeps the old exact row`() = runTest {
        var state = GuideCategoryPageState(); var reject = true
        val pager = GuideCategoryPager(this, { query -> if(query.cursor!=null && reject) throw GatewayError(503,"Synthetic failure") else page(if(query.cursor==null) 0 else 1) },
            { owner }, { state = it }, {})
        pager.ensure(); runCurrent(); pager.viewport(state.revision,"cat-0","cat-6",0,allowPaging=false)
        pager.rowViewport(state.revision,"fixed:search",203,9,"fixed:search")
        pager.move(1,state.revision); runCurrent()
        assertEquals("fixed:search",state.rowFirstKey); assertEquals(9,state.rowScrollOffset)
        reject = false; pager.retry(); runCurrent()
        assertNull(state.rowFirstKey); assertNull(state.rowFocusKey); assertEquals(0,state.rowFirstIndex)
    }
    @Test fun `observation-only viewport saves edges without stealing fixed-control navigation`() = runTest {
        var state = GuideCategoryPageState(); var requests = 0
        val pager = GuideCategoryPager(this, { query -> requests++; page(if (query.cursor == null) 0 else 1) },
            { owner }, { state = it }, { fail(it.message) })
        pager.ensure(); runCurrent()
        pager.viewport(state.revision,"cat-0","cat-6",0,allowPaging=false)
        pager.viewport(state.revision,"cat-194","cat-199",13,allowPaging=false); runCurrent()
        assertEquals(1,requests); assertEquals(194,state.visibleFirst); assertEquals(13,state.scrollOffset)
        pager.move(1,state.revision); runCurrent(); assertEquals(2,requests)
        val revision = state.revision
        pager.viewport(revision,"cat-394","cat-399",0,allowPaging=false)
        assertTrue(state.awaitingAnchor)
        pager.viewport(revision,"cat-200","cat-206",0,allowPaging=false)
        assertFalse(state.awaitingAnchor)
        pager.viewport(revision,"cat-200","cat-206",3,allowPaging=false); runCurrent()
        assertEquals(2,requests)
        pager.viewport(revision - 1,"cat-394","cat-399",0,allowPaging=false)
        assertEquals(0,state.visibleFirst)
    }
    private val owner = GuideCategoryScope("profile", "1", "7")
    private fun page(index: Int, count: Int = 200) = LiveCatalogCategories("1", "7",
        List(count) { LiveCatalogCategory("cat-${index * 200 + it}", "Category") },
        if (index < 4) "next_${index + 1}" else null, if (index > 0) "previous_${index - 1}" else null)

    @Test fun `forward and backward page replacement stays bounded and preserves the implicit query`() = runTest {
        var state = GuideCategoryPageState()
        val requests = mutableListOf<LiveCatalogQuery>()
        val pager = GuideCategoryPager(this, { query ->
            requests.add(query)
            page(query.cursor?.substringAfter('_')?.toInt() ?: 0)
        }, { owner }, { state = it }, { fail(it.message) })
        pager.ensure(); runCurrent()
        for (index in 1..4) {
            pager.move(1, state.revision); runCurrent()
            assertEquals(200, state.items.size)
            assertEquals("cat-${index * 200}", state.items.first().id)
            assertEquals(0, state.focusIndex)
        }
        assertNull(state.nextCursor)
        for (index in 3 downTo 0) {
            pager.move(-1, state.revision); runCurrent()
            assertEquals(200, state.items.size)
            assertEquals("cat-${index * 200}", state.items.first().id)
            assertEquals(199, state.focusIndex)
        }
        assertNull(state.previousCursor)
        assertEquals(9, requests.size)
        assertTrue(requests.all { it.limit == 200 && it.catalogId == null && it.categoryId == null })
    }

    @Test fun `category work changes only categories and preserves existing channel schedule and timeline state`() = runTest {
        val channel = LiveChannel("channel", "Channel")
        val programme = GuideProgramme("Programme", 1, 2)
        val initial = GuideUiState(channels = listOf(channel), selectedChannelId = channel.id,
            schedulesByChannelId = mapOf(channel.id to listOf(programme)), windowStartMillis = 1234,
            visibleFirst = 3, visibleScrollOffset = 42, channelFilter = LiveChannelFilter.Recent)
        var guide = initial
        var state = GuideCategoryPageState()
        val pager = GuideCategoryPager(this, { page(0) }, { owner }, {
            state = it; guide = guide.copy(categories = it.items, categoryPage = it)
        }, { fail(it.message) })
        pager.ensure(); runCurrent()
        assertEquals(initial, guide.copy(categories = emptyList(), categoryPage = GuideCategoryPageState()))
        assertEquals(200, state.items.size)
        pager.ensure(); runCurrent() // Same scope after a channel-filter load is reused.
        assertTrue(state.loaded)
    }

    @Test fun `failed next page retains content and explicit retry uses the exact same cursor`() = runTest {
        var state = GuideCategoryPageState(); var failure: GatewayError? = null
        val requests = mutableListOf<LiveCatalogQuery>(); var attempts = 0
        val pager = GuideCategoryPager(this, { query ->
            requests.add(query)
            if (query.cursor == null) page(0) else if (attempts++ == 0)
                throw GatewayError(503, "Provider unavailable", "provider_unavailable") else page(1)
        }, { owner }, { state = it }, { failure = it })
        pager.ensure(); runCurrent(); val before = state.items
        pager.move(1, state.revision); runCurrent()
        assertEquals(before, state.items); assertFalse(state.loading)
        assertEquals("provider_unavailable", failure?.code)
        pager.move(1, state.revision); runCurrent(); assertEquals(2, requests.size)
        pager.retry(); runCurrent()
        assertEquals(requests[1], requests[2]); assertEquals("cat-200", state.items.first().id)
        assertNull(state.error)
    }

    @Test fun `metadata mismatches duplicates empty continuation and repeated pages fail before replacement`() = runTest {
        for (bad in listOf(page(1).copy(catalogId = "2"), page(1).copy(generation = "8"), page(1, 0), page(0),
            page(1).copy(items = listOf(LiveCatalogCategory("dup", "One"), LiveCatalogCategory("dup", "Two"))),
            page(1).copy(nextCursor = "next_1"))) {
            var state = GuideCategoryPageState()
            val pager = GuideCategoryPager(this, { if (it.cursor == null) page(0) else bad }, { owner }, { state = it }, {})
            pager.ensure(); runCurrent(); val before = state.items
            pager.move(1, state.revision); runCurrent()
            assertEquals(before, state.items); assertNotNull(state.error); assertFalse(state.loading)
            pager.cancel()
        }
    }

    @Test fun `late category response cannot publish after profile or snapshot replacement`() = runTest {
        for (replacement in listOf(owner.copy(profileId = "other"), owner.copy(generation = "8"), null)) {
            var active: GuideCategoryScope? = owner
            var state = GuideCategoryPageState(); val late = CompletableDeferred<LiveCatalogCategories>()
            var publications = 0
            val pager = GuideCategoryPager(this, { withContext(NonCancellable) { late.await() } }, { active }, { state = it; publications++ }, {})
            pager.ensure(); runCurrent()
            active = replacement; val before = publications
            pager.cancel(); late.complete(page(0)); runCurrent()
            assertEquals(before, publications); assertTrue(state.items.isEmpty())
        }
    }

    @Test fun `stale viewport callbacks and unrestored new anchors cannot cause paging loops`() = runTest {
        var state = GuideCategoryPageState(); var requests = 0
        val pager = GuideCategoryPager(this, { query -> requests++; page(query.cursor?.substringAfter('_')?.toInt() ?: 0) },
            { owner }, { state = it }, { fail(it.message) })
        pager.ensure(); runCurrent(); val old = state.revision
        pager.viewport(old, "cat-0", "cat-6", 0)
        pager.viewport(old, "cat-194", "cat-199", 20); runCurrent()
        assertEquals(2, requests); assertTrue(state.awaitingAnchor)
        pager.viewport(old, "cat-394", "cat-399", 20)
        pager.viewport(state.revision, "cat-394", "cat-399", 20); runCurrent()
        assertEquals(2, requests)
        pager.viewport(state.revision, "cat-200", "cat-206", 0); runCurrent()
        assertFalse(state.awaitingAnchor); assertEquals(2, requests)
        pager.viewport(state.revision, "cat-200", "cat-206", 0); runCurrent()
        assertEquals(2, requests) // No automatic backward request on a fresh forward page.
    }

    @Test fun `a stalled category read clears busy state at its deadline and does not echo raw diagnostics`() = runTest {
        var state = GuideCategoryPageState(); var failure: GatewayError? = null
        val pager = GuideCategoryPager(this, { awaitCancellation() }, { owner }, { state = it }, { failure = it })
        pager.ensure(); runCurrent(); assertTrue(state.loading)
        advanceTimeBy(15_000); runCurrent()
        assertFalse(state.loading); assertEquals("request_timeout", failure?.code)
    }

    @Test fun `explicit override remains explicit and an empty terminal catalog is valid`() = runTest {
        var state = GuideCategoryPageState(); var query: LiveCatalogQuery? = null
        val explicit = owner.copy(query = LiveCatalogQuery(catalogId = "1", limit = 200))
        val pager = GuideCategoryPager(this, { query = it; page(0, 0).copy(nextCursor = null) },
            { explicit }, { state = it }, { fail(it.message) })
        pager.ensure(); runCurrent()
        assertTrue(state.loaded); assertFalse(state.loading); assertTrue(state.items.isEmpty())
        assertEquals("1", query?.catalogId)
    }
    @Test fun `new profile resets the retained page before any new response and discards old diagnostics`() = runTest {
        var active = owner; var state = GuideCategoryPageState(); var failed = false
        val pager = GuideCategoryPager(this, {
            if (failed) throw java.io.IOException("https://private.invalid/password") else page(0)
        }, { active }, { state = it }, {})
        pager.ensure(); runCurrent(); assertEquals(200, state.items.size)
        active = owner.copy(profileId = "other"); failed = true
        pager.ensure(); assertTrue(state.items.isEmpty()); runCurrent()
        assertTrue(state.items.isEmpty()); assertEquals("request_failed", state.errorCode)
        assertFalse(state.error.orEmpty().contains("private.invalid"))
    }
}
