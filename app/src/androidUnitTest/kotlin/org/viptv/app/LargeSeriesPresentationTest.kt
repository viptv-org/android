package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject

/** Exercises the native input boundary reached when opening a long series. */
class LargeSeriesPresentationTest {
    @Test fun openingLongSeriesRetainsEveryEpisodeAndRequestedStartingPoint() {
        val videos = JSONArray()
        repeat(1_050) { index ->
            videos.put(JSONObject()
                .put("id", "series:1:${index + 1}")
                .put("season", 1)
                .put("episode", index + 1)
                .put("title", "Episode ${index + 1}")
                .put("overview", "Episode synopsis. ".repeat(45)))
        }
        val series = CoreModels.media(JSONObject()
            .put("id", "series")
            .put("type", "series")
            .put("name", "Long Series")
            .put("season", 1)
            .put("episode", 734)
            .put("videos", videos))

        assertEquals(1_050, series.episodes.size)
        assertTrue(series.normalizedJson().length > 1_000_000)
        assertEquals("Long Series", CoreModels.presentation(series).title)
        assertEquals("details", CoreModels.card(series).primaryAction)
        val startingEpisode = CoreModels.initialEpisode(series)
        assertEquals("series:1:734", startingEpisode?.id)
        assertEquals("series:1:734", CoreModels.itemRequest(startingEpisode!!).getString("id"))
        assertEquals(1_050, series.episodes.size)
    }
}
