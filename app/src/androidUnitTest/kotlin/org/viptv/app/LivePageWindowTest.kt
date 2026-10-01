package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LivePageWindowTest {
    @Test fun `suspending during an adjacent request clears transient work but preserves viewport and authority`() {
        val before = GuideUiState(channels = listOf(LiveChannel("one", "One")), selectedChannelId = "one",
            nextCursor = "next", previousCursor = "previous", paging = true, loadingChannelIds = setOf("one"),
            visibleFirst = 5, visibleScrollOffset = 42, pagingFailed = true)
        val after = GuidePolicy.suspended(before)
        assertFalse(after.paging)
        assertTrue(after.loadingChannelIds.isEmpty())
        assertEquals(before.copy(paging = false, loadingChannelIds = emptySet()), after)
    }
    @Test fun `failed paging only retries after visible first or pixel motion not effect reruns or end relayout`() {
        val before = GuideUiState(visibleFirst = 20, visibleEnd = 25, visibleScrollOffset = 4, pagingFailed = true)
        assertFalse(GuidePolicy.movedSinceFailure(before, 20, 4))
        assertFalse(GuidePolicy.movedSinceFailure(before.copy(visibleEnd = 30), 20, 4))
        assertFalse(GuidePolicy.movedSinceFailure(before, 20, 27))
        assertTrue(GuidePolicy.movedSinceFailure(before, 21, 4))
        assertTrue(GuidePolicy.movedSinceFailure(before, 20, 28))
    }
    @Test fun `returning viewport resumes missing schedules once and never during playback preparation`() {
        assertTrue(GuidePolicy.mayResumeSchedules(false, false, true))
        assertFalse(GuidePolicy.mayResumeSchedules(false, true, true))
        assertFalse(GuidePolicy.mayResumeSchedules(true, false, true))
        assertFalse(GuidePolicy.mayResumeSchedules(false, false, false))
    }
    @Test fun `schedule lookahead stays bounded to actual nearby rows`() {
        val state = GuideUiState(channels = List(120) { LiveChannel("$it", "Channel") }, visibleFirst = 55, visibleEnd = 60)
        assertEquals((53..61).map(Int::toString), GuidePolicy.scheduleRows(state).map { it.id })
        assertEquals(20, GuidePolicy.scheduleRows(state.copy(visibleEnd = 120)).size)
    }
    private fun page(index: Int, count: Int = 40) = LiveBrowsePage(
        List(count) { LiveChannel("channel-${index * 40 + it}", "Channel") }, LiveBrowseRequest(),
        "1", "7", if (index < 9) "page_${index + 1}" else null, if (index > 0) "page_${index - 1}" else null)

    @Test fun `whole playlist forward and reverse traversal never retains more than three pages`() {
        val window = LivePageWindow()
        window.replace(page(0))
        for (index in 1..9) {
            assertEquals("page_$index", window.next)
            window.add(page(index), false)
            assertTrue(window.channels.size <= 120)
            assertEquals((index - 2).coerceAtLeast(0) * 40, window.start)
        }
        assertNull(window.next)
        for (index in 6 downTo 0) {
            assertEquals("page_$index", window.previous)
            window.add(page(index), true)
            assertEquals(120, window.channels.size)
            assertEquals("channel-${index * 40}", window.channels.first().id)
            assertEquals(index * 40, window.start)
        }
        assertNull(window.previous)
        assertEquals("page_3", window.next)
    }
    @Test fun `changed snapshots empty continuations and repeats fail before retained data mutates`() {
        val window = LivePageWindow()
        window.replace(page(0))
        for (invalid in listOf(page(1).copy(catalogId = "2"), page(1).copy(generation = "8"), page(1, 0), page(0))) {
            assertFailsWith<GatewayError> { window.add(invalid, false) }
            assertEquals(40, window.channels.size)
            assertEquals("page_1", window.next)
        }
    }
    @Test fun `new scope replaces the moving window and its cursor authority`() {
        val window = LivePageWindow()
        window.replace(page(0))
        for (index in 1..4) window.add(page(index), false)
        window.replace(page(0, 3).copy(catalogId = "2", nextCursor = null))
        assertEquals(0, window.start)
        assertEquals(3, window.channels.size)
        assertNull(window.next)
        assertNull(window.previous)
    }
}
