package org.viptv.app

import kotlin.test.*

class GuideCategoryRowPolicyTest {
    @Test fun `only actual first or last provider focus can confirm its pending anchor`() {
        val items = listOf(LiveCategory("first","First"), LiveCategory("last","Last"))
        assertFalse(guideCategoryAnchorFocused(items,0,null))
        assertFalse(guideCategoryAnchorFocused(items,0,"fixed:all"))
        assertFalse(guideCategoryAnchorFocused(items,0,"fixed:search"))
        assertFalse(guideCategoryAnchorFocused(items,0,"category:last"))
        assertFalse(guideCategoryAnchorFocused(items,0,"category:old-scope"))
        assertTrue(guideCategoryAnchorFocused(items,0,"category:first"))
        assertTrue(guideCategoryAnchorFocused(items,1,"category:last"))
    }
    @Test fun `fixed Search and Recent focus moves are not category paging`() {
        assertEquals(0, guideCategoryTerminalDirection(null, 1))
        assertEquals(0, guideCategoryTerminalDirection(null, -1))
        assertEquals(0, guideCategoryTerminalDirection("search", -1))
        assertEquals(0, guideCategoryTerminalDirection("all", 1))
        assertEquals(1, guideCategoryTerminalDirection("search", 1))
        assertEquals(-1, guideCategoryTerminalDirection("all", -1))
    }
    @Test fun `touch paging requires unconsumed motion beyond the true row terminal`() {
        assertEquals(0, guideCategoryOverscrollDirection(-10f, true, true))
        assertEquals(0, guideCategoryOverscrollDirection(10f, true, false))
        assertEquals(0, guideCategoryOverscrollDirection(0f, false, false))
        assertEquals(1, guideCategoryOverscrollDirection(-10f, true, false))
        assertEquals(-1, guideCategoryOverscrollDirection(10f, false, true))
    }
    @Test fun `viewport reports category IDs rather than fixed tab indices`() {
        val items = listOf(LiveCategory("one", "One"), LiveCategory("two", "Two"))
        assertEquals(Triple("one", "two", 0), guideCategoryRowViewport(items,
            listOf(Triple(0,"fixed:all",0),Triple(2,"fixed:recent",100),Triple(3,"category:one",200),Triple(4,"category:two",260),Triple(5,"fixed:search",320))))
        assertEquals(Triple("one", "two", 7), guideCategoryRowViewport(items,
            listOf(Triple(3,"category:one",-7),Triple(4,"category:two",53))))
    }
    @Test fun `old layout keys and fixed-only viewports cannot acknowledge a new page`() {
        val items = listOf(LiveCategory("new", "New"))
        assertNull(guideCategoryRowViewport(items,listOf(Triple(3,"category:old",0))))
        assertNull(guideCategoryRowViewport(items,listOf(Triple(0,"fixed:all",0),Triple(4,"fixed:search",100))))
    }
}
