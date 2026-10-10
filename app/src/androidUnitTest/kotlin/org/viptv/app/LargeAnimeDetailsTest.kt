package org.viptv.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LargeAnimeDetailsTest {
    @Test fun `long anime survives normalization progress merge detail enrichment and target selection`() {
        val episodes = JSONArray()
        repeat(1250) { index -> episodes.put(JSONObject().put("id", "simkl:anime:42:1:${index+1}").put("type", "series").put("series_id", "simkl:anime:42")
            .put("name", "English show").put("season", 1).put("episode", index+1).put("episode_title", "Episode ${index+1}")
            .put("duration", 1500).put("description", "Episode synopsis ".repeat(20)).put("released", "2020-01-01T00:00:00Z")
            .put("simkl_ids", JSONObject().put("simkl", 42).put("imdb", "tt42"))) }
        val details = CoreModels.media(JSONObject().put("id", "simkl:anime:42").put("type", "series").put("name", "English show").put("videos", episodes))
        val history = details.episodes.take(1000).mapIndexed { index, episode -> episode.copy(positionMillis = 1500000, watched = true, resumeActive = false, completionOnly = false, updatedAtMillis = (index+1L)*1000, sourceAddonId = "fixture", sourceFingerprint = "fixture-$index") }
        val merged = CoreModels.mergeEpisodeProgress(details, history)
        val enriched = CoreModels.enrichDetail(Media("simkl:anime:42", "series", "Before"), merged)
        assertEquals(1250, enriched.episodes.size)
        assertTrue(enriched.episodes[999].watched)
        assertEquals("fixture-999", enriched.episodes[999].sourceFingerprint)
        assertEquals(1001, CoreModels.initialEpisode(enriched)?.episode)
    }
}
