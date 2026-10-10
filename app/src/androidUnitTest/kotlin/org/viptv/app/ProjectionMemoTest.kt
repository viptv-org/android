package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ProjectionMemoTest {
    @Test fun `immutable media reuse caches but progress copies recompute`() {
        val memo = ProjectionMemo<Any>(2)
        val item = Media("simkl:movies:42", "movie", "Fixture")
        var calls = 0
        fun project(media: Media) = memo.get(media) { calls++; Any() }
        val first = project(item)
        assertSame(first, project(item))
        assertNotSame(first, project(item.copy(positionMillis = 10000)))
        assertEquals(2, calls)
        memo.clear()
        assertNotSame(first, project(item))
    }
}
