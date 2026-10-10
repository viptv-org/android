package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeEnrichmentBudgetTest {
    @Test fun largeSeriesEnrichmentKeepsEpisodeJoinWithinNativeInputBudget() {
        val original = Media("show:1:3000", "episode", "Show", season = 1, episode = 3000, positionMillis = 90000)
        val metadata = Media("show", "series", "Show", description = "Series description", episodes = (1..6000).map {
            Media("show:1:$it", "episode", "Episode $it", season = 1, episode = it,
                episodeTitle = "Title $it", description = "x".repeat(600))
        })
        val enriched = CoreModels.enrich(original, metadata)
        assertEquals("Title 3000", enriched.episodeTitle)
        assertEquals("Series description", enriched.description)
        assertEquals(90000L, enriched.positionMillis)
    }
}
