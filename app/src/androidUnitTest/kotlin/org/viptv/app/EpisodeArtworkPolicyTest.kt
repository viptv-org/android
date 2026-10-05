package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeArtworkPolicyTest {
    @Test fun parentArtworkEnrichmentRetainsTheExactEpisodeAndProgress() {
        val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059, episodeTitle = "The Future", thumbnail = "https://images.example/child.jpg",
            positionMillis = 42_000, durationMillis = 120_000, sourceAddonId = "addon-one",
            sourceFingerprint = "fingerprint-one")
        val parent = Media("show", "series", name = "Fixture Show", backdrop = "https://images.example/show.jpg")

        val enriched = episode.withArtworkFrom(parent)

        assertEquals(episode.id, enriched.id)
        assertEquals(episode.seriesId, enriched.seriesId)
        assertEquals(episode.season, enriched.season)
        assertEquals(episode.episode, enriched.episode)
        assertEquals(episode.episodeTitle, enriched.episodeTitle)
        assertEquals(episode.positionMillis, enriched.positionMillis)
        assertEquals(episode.durationMillis, enriched.durationMillis)
        assertEquals(episode.sourceAddonId, enriched.sourceAddonId)
        assertEquals(episode.sourceFingerprint, enriched.sourceFingerprint)
    }
    @Test fun normalizedRewatchFactsSurviveViewCopiesAndArtworkEnrichment() {
        val episode = CoreModels.media(org.json.JSONObject().put("id", "show:1:1").put("type", "episode")
            .put("watched", true).put("position", 42).put("duration", 120)
            .put("resume_active", true).put("completion_only", false).put("watch_date_known", true))
        assertEquals(true, episode.resumeActive)
        assertEquals(false, episode.completionOnly)
        assertEquals(true, episode.watchDateKnown)
        val enriched = episode.withArtworkFrom(Media("show", "series", name = "Show"))
        val roundTrip = CoreModels.media(org.json.JSONObject(enriched.copy(positionMillis = 43_000).normalizedJson()))
        assertEquals(true, roundTrip.watched)
        assertEquals(true, roundTrip.resumeActive)
        assertEquals(false, roundTrip.completionOnly)
        assertEquals(true, roundTrip.watchDateKnown)
        assertEquals(43_000, roundTrip.positionMillis)
    }
}
