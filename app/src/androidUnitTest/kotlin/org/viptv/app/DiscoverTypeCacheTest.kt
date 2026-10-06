package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DiscoverTypeCacheTest {
    @Test fun repeatedTypeReusesOneImmutableRustProjection() {
        var projections = 0
        val cache = DiscoverTypeCache { type -> projections++; SharedPresentation.discover(type) }
        val first = cache.get("anime.series")
        repeat(100) { assertSame(first, cache.get("anime.series")) }
        assertEquals(1, projections)
        assertEquals("anime", first.group)
        assertEquals("Anime", first.groupLabel)
    }

    @Test fun sixtyFourEntryBoundEvictsLeastRecentlyUsedType() {
        var projections = 0
        val cache = DiscoverTypeCache { type -> projections++; SharedPresentation.discover(type) }
        repeat(64) { cache.get("custom.$it") }
        val recent = cache.get("custom.0")
        cache.get("custom.64")
        assertEquals(65, projections)
        assertSame(recent, cache.get("custom.0"))
        cache.get("custom.1")
        assertEquals(66, projections)
        assertEquals("other", cache.get("custom.64").group)
        assertEquals(66, projections)
    }

    @Test fun cachedGroupingAndLabelsEqualUncachedRustResults() {
        val cache = DiscoverTypeCache { SharedPresentation.discover(it) }
        for (type in listOf("movie", "series", "anime", "anime.series", "anime.movie", "live", "custom_namespace")) {
            val expected = SharedPresentation.discover(type)
            assertEquals(expected, cache.get(type))
            assertEquals(expected, cache.get(type))
            assertEquals(expected.group, DiscoverPolicy.typeGroup(type))
            assertEquals(expected.groupLabel, DiscoverPolicy.groupLabel(type))
        }
    }
}
