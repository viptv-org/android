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
}
