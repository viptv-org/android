package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedEpisodeProgressTest {
    @Test fun historyMergeKeepsActiveRewatchAndCompletionFacts() {
        val episode = Media("e", "episode", seriesId = "s", season = 1, episode = 2)
        val history = episode.copy(positionMillis = 42_000, durationMillis = 100_000,
            watched = true, resumeActive = true, completionOnly = false,
            watchDateKnown = false, updatedAtMillis = 2_000)
        val merged = mergeSeriesProgress(Media("s", "series", episodes = listOf(episode)), listOf(history)).episodes.single()
        assertTrue(merged.watched)
        assertEquals(true, merged.resumeActive)
        assertEquals(false, merged.completionOnly)
        assertEquals(false, merged.watchDateKnown)
        assertEquals(2_000L, merged.updatedAtMillis)
        assertEquals("e", CoreModels.initialEpisode(Media("s", "series", episodes = listOf(merged)))?.id)
    }

    @Test fun historyMergeUsesExactIdentityThenScopedCoordinatesAndPreservesUnmatchedRows() {
        val episode = Media("metadata-e", "episode", seriesId = "s", season = 2, episode = 7, durationMillis = 100_000)
        val other = Media("other", "episode", seriesId = "s", season = 2, episode = 8)
        val history = Media("history-e", "episode", seriesId = "s", season = 2, episode = 7,
            watched = true, completionOnly = true, watchDateKnown = false)
        val merged = mergeSeriesProgress(Media("s", "series", episodes = listOf(episode, other)), listOf(history)).episodes
        assertTrue(merged[0].watched)
        assertEquals(true, merged[0].completionOnly)
        assertEquals(100_000L, merged[0].durationMillis)
        assertFalse(merged[1].watched)
        assertEquals(other.id, merged[1].id)
    }

    @Test fun repeatedHistoryRefreshClearsUnknownAuthorityButRetainsMetadataDuration() {
        val episode = Media("e", "episode", seriesId = "s", season = 2, episode = 7, durationMillis = 100_000)
        val series = Media("s", "series", episodes = listOf(episode))
        val first = mergeSeriesProgress(series, listOf(episode.copy(positionMillis = 42_000, watched = true,
            resumeActive = true, completionOnly = false, watchDateKnown = true,
            sourceAddonId = "addon", sourceFingerprint = "prior", updatedAtMillis = 2_000)))
        val refreshed = mergeSeriesProgress(first, listOf(episode.copy(durationMillis = null))).episodes.single()
        assertEquals(null, refreshed.sourceAddonId)
        assertEquals(null, refreshed.sourceFingerprint)
        assertEquals(null, refreshed.updatedAtMillis)
        assertEquals(null, refreshed.resumeActive)
        assertEquals(null, refreshed.completionOnly)
        assertEquals(null, refreshed.watchDateKnown)
        assertEquals(0L, refreshed.positionMillis)
        assertFalse(refreshed.watched)
        assertEquals(100_000L, refreshed.durationMillis)
    }

    @Test fun copiedEpisodeFactsReachRustWithoutStaleBackingDto() {
        val original = CoreModels.media(org.json.JSONObject("""{"id":"e","type":"episode","season":1,"episode":1,"updated_at":1,"released":"2020-01-01T00:00:00Z"}"""))
        val updated = original.copy(updatedAtMillis = 8_000, releasedAtMillis = 9_000)
        val json = org.json.JSONObject(updated.normalizedJson())
        assertEquals(8_000L, json.getLong("updatedAtMillis"))
        assertEquals(9_000L, json.getLong("releasedAtMillis"))
    }
}
