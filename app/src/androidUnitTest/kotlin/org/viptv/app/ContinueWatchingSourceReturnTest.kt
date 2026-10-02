package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContinueWatchingSourceReturnTest {
    @Test fun episodeSourceCancellationTargetsItsParentAndOriginalEpisode() {
        val episode = Media("series-42:2:7", "episode", "The Show", seriesId = "series-42", season = 2, episode = 7)

        val parent = SourceReturnPolicy.parentSeries(episode)

        assertEquals("series-42", parent?.id)
        assertEquals("series", parent?.type)
        assertEquals(2, parent?.season)
        assertEquals(7, parent?.episode)
    }

    @Test fun nonEpisodeAndEpisodeWithoutParentKeepTheirExistingReturn() {
        assertNull(SourceReturnPolicy.parentSeries(Media("movie-1", "movie", "Movie")))
        assertNull(SourceReturnPolicy.parentSeries(Media("orphan-episode", "episode", "Episode")))
    }

    @Test fun queueSeriesWithEpisodeCoordinatesReturnsToItsShow() {
        val queueItem = Media("series-42", "series", "The Show", season = 2, episode = 9)

        val parent = SourceReturnPolicy.parentSeries(queueItem)

        assertEquals("series-42", parent?.id)
        assertEquals(2, parent?.season)
        assertEquals(9, parent?.episode)
    }
}
