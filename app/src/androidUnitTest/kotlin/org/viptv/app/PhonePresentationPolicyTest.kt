package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AND-042 phone presentation and track-menu rules. */
class PhonePresentationPolicyTest {
    @Test fun catalogShelfHeadingNamesContentTypeNotAddon() {
        val shelf = HomeShelf("AIOMetadata · AniList Trending", emptyList(), id = "c1", contentType = "series", catalogName = "AniList Trending")
        assertEquals("Series · AniList Trending", PhonePresentationPolicy.shelfHeading(shelf))
        assertEquals("Movies · Popular", PhonePresentationPolicy.shelfHeading(shelf.copy(contentType = "movie", catalogName = "Popular")))
        assertEquals("Anime · Top", PhonePresentationPolicy.shelfHeading(shelf.copy(contentType = "anime.series", catalogName = "Top")))
        assertEquals("Tv Shows · Picks", PhonePresentationPolicy.shelfHeading(shelf.copy(contentType = "tv_shows", catalogName = "Picks")))
        assertEquals("Series", PhonePresentationPolicy.shelfHeading(shelf.copy(catalogName = " ")))
    }

    @Test fun fixedShelvesKeepTheirNames() {
        assertEquals("Live now", PhonePresentationPolicy.shelfHeading(HomeShelf("Live now", emptyList())))
        assertEquals("My List", PhonePresentationPolicy.shelfHeading(HomeShelf("My List", emptyList())))
        assertEquals("Continue watching", PhonePresentationPolicy.shelfHeading(HomeShelf("Queue", emptyList(), isQueueShelf = true, contentType = "movie")))
    }

    @Test fun cardContextIsEpisodeNumberOrYearOnly() {
        assertEquals("S1 E2", PhonePresentationPolicy.cardContext(Media("e", "episode", "Pilot", season = 1, episode = 2, year = "2024")))
        assertEquals("2026", PhonePresentationPolicy.cardContext(Media("m", "movie", "Mayday", year = "2026")))
        assertEquals("", PhonePresentationPolicy.cardContext(Media("m", "movie", "Unknown")))
    }

    @Test fun trackMenuMarksCurrentAndUnavailableRows() {
        val english = PlaybackTrack(0, language = "en", title = "English", selected = true, supported = true, selectable = true)
        val pgs = PlaybackTrack(1, language = "pt", title = "Portuguese (PGS)", supported = false, selectable = true)
        val untitled = PlaybackTrack(2, language = "es", supported = true, selectable = true)
        val entries = listOf(null, english, pgs, untitled)
        assertTrue(TrackMenuPolicy.current(english, entries))
        assertFalse(TrackMenuPolicy.current(null, entries))
        assertTrue(TrackMenuPolicy.current(null, listOf(null, english.copy(selected = false))))
        assertEquals("Off", TrackMenuPolicy.text(null, tv = false))
        assertEquals("Portuguese (PGS) (unavailable)", TrackMenuPolicy.text(pgs, tv = false))
        assertEquals("Portuguese (PGS) · unavailable", TrackMenuPolicy.text(pgs, tv = true))
        assertEquals("es", TrackMenuPolicy.label(untitled))
        assertTrue(TrackMenuPolicy.unavailable(pgs))
        assertFalse(TrackMenuPolicy.unavailable(null))
    }
}
