package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeJumpTest {
    @Test fun matchesExactEpisodeMetadataInCurrentSeasonOrder() {
        val catalog = listOf(
            Media("special", "episode", season = 0, episode = 0),
            Media("first", "episode", season = 1, episode = 1),
            Media("tenth", "episode", season = 1, episode = 10),
            Media("duplicate", "episode", season = 1, episode = 10),
            Media("unnumbered", "episode", season = 1),
            Media("far", "episode", season = 1, episode = 1410),
        )
        val seasonOne = catalog.filter { (it.season ?: 1) == 1 }
        assertEquals(0, episodeIndexForNumber(seasonOne, 1))
        assertEquals(1, episodeIndexForNumber(seasonOne, 10))
        assertEquals(4, episodeIndexForNumber(seasonOne, 1410))
        assertEquals(-1, episodeIndexForNumber(seasonOne, 2))
        assertEquals(-1, episodeIndexForNumber(seasonOne, 0))
        assertEquals(0, episodeIndexForNumber(catalog.filter { it.season == 0 }, 0))
    }
}
