package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPresentationTest {
    @Test fun activeRewatchOverridesCachedNextInCardAndHero() {
        val media = Media("s:2:3", "series", season = 2, episode = 3, positionMillis = 25_000, durationMillis = 100_000,
            watched = true, resumeActive = true, queueStatus = "next", previousEpisode = Media("s:2:2", "episode", positionMillis = 100_000, completionOnly = true))
        val actions = SharedPresentation.home(media, true)
        assertEquals("resume", actions.cardPrimaryAction)
        assertEquals("resume", actions.heroPrimaryAction)
        assertEquals("Resume", actions.heroPrimaryActionLabel)
        assertFalse(QueuePolicy.hasResolvedNext(media))
        assertEquals(MediaCardAction.ResumeExactSource, MediaCardPolicy.primary(true, media))
        assertTrue(SharedPresentation.episode(media).watching)
        assertEquals(0.25, SharedPresentation.episode(media).progress)
    }

    @Test fun completionOnlyProgressCannotResumeOrShowWatchingMarker() {
        val media = Media("e", "episode", positionMillis = 100_000, durationMillis = 100_000,
            completionOnly = true, resumeActive = true, watched = false)
        assertFalse(QueuePolicy.canResume(media))
        assertEquals(MediaCardAction.OpenDetails, MediaCardPolicy.primary(true, media))
        assertEquals("details", SharedPresentation.home(media, true).heroPrimaryAction)
        assertFalse(SharedPresentation.home(media, true).showHeroProgress)
        assertFalse(SharedPresentation.episode(media).watching)
    }

    @Test fun heroAndShelfActionsRemainDistinctOnManualMovieAndSeriesRoot() {
        val movie = Media("m", "movie", positionMillis = 10_000)
        assertEquals("details", SharedPresentation.home(movie).cardPrimaryAction)
        assertEquals("sources", SharedPresentation.home(movie).heroPrimaryAction)
        assertEquals("Play", SharedPresentation.home(movie).heroPrimaryActionLabel)
        assertEquals("details", SharedPresentation.home(Media("s", "series")).heroPrimaryAction)
        assertEquals("Episodes", SharedPresentation.home(Media("s", "series")).heroPrimaryActionLabel)
        assertTrue(HomeHoldPolicy.opensQueueManage(true, movie))
        assertFalse(HomeHoldPolicy.opensSourcesFromHero(true, movie))
    }

    @Test fun batchSourceRanksKeepDiscoveryTiesAndDoNotInventUnknownDeviceLimits() {
        val first = Source("first", "Provider", "1080p h264 English audio")
        val equal = first.copy(id = "equal")
        val high = first.copy(id = "high", name = "2160p h264 English audio")
        val sources = listOf(first, equal, high)
        val unknown = SharedPresentation.ranks(sources, null, "en")
        assertEquals(listOf(2, 0, 1), unknown.orderedIndices.map { it.toInt() })
        assertTrue(unknown.ranks[2].best)
        val caps = PlaybackClientCapabilities(1920, 1080, true, false, false, true, true)
        assertEquals(sources, SourceRankPolicy.order(sources, caps, "en"))
        assertEquals(listOf(high, first, equal), SourceRankPolicy.order(sources, null, "en"))
    }

    @Test fun measuredDecoderSupportControlsOrderingAndRecommendations() {
        val unsupported = Source("hevc", "Provider", "2160p HEVC English audio dubbed")
        val supported = Source("avc", "Provider", "1080p h264")
        val caps = PlaybackClientCapabilities(1920, 1080, true, false, false, true, true)
        val ranks = SharedPresentation.ranks(listOf(unsupported, supported), caps, "en")
        assertEquals(listOf(1, 0), ranks.orderedIndices.map { it.toInt() })
        assertFalse(ranks.ranks[0].best)
        assertFalse(ranks.ranks[0].likely)
        assertTrue(ranks.ranks[1].likely)
        val noAvc = SharedPresentation.ranks(listOf(supported), caps.copy(h264 = false), "en")
        assertFalse(noAvc.ranks.single().likely)
        assertFalse(noAvc.ranks.single().best)
    }

    @Test fun producerUpdatesRetainFailureWhenLaterEventReturnsRows() = runBlocking {
        FixtureServer(2) { request -> when (request.target) {
            "/api/v2/streams" -> FixtureResponse("""{"id":"shared-producer-job"}""")
            "/api/v2/streams/shared-producer-job?after=0" -> FixtureResponse("""{"events":[
                {"seq":1,"source":"addon:3","streams":[],"error_code":"addon_timeout","error":"https://private.invalid/secret"},
                {"seq":2,"source":"addon:3","streams":[{"id":"returned","name":"1080p h264 English audio","source_addon_id":"addon:3"}]},
                {"seq":3,"source":"addon:4","streams":[]}
            ],"done":true}""")
            else -> error("Unexpected request ${request.target}")
        } }.use { server ->
            var producers = emptyList<SourceProducerOutcome>()
            val rows = VipTvHttpGateway(server.origin).sources(Media("m", "movie"), onProducerUpdate = { producers = it })
            assertEquals(listOf("returned"), rows.map { it.id })
            assertEquals(listOf("addon:3", "addon:4"), producers.map { it.sourceId })
            assertEquals("addon_timeout", producers.first().errorCode)
            assertFalse(producers.first().errorMessage.orEmpty().contains("secret"))
            val named = namedSourceProducers(producers, listOf(Addon("3", "Configured", "", true), Addon("9", "Catalog only", "", true)), rows)
            assertEquals(listOf("Configured", "addon:4"), named.map { it.label })
            server.assertHealthy()
        }
    }

    @Test fun phoneProjectionPreservesFixedHeadingAndExactYearCopy() {
        assertEquals("Live now", PhonePresentationPolicy.shelfHeading(HomeShelf("Live now", emptyList())))
        assertEquals("Recently watched live TV", PhonePresentationPolicy.shelfHeading(HomeShelf("Recently watched live TV", emptyList())))
        assertEquals("Anime · Trending", PhonePresentationPolicy.shelfHeading(HomeShelf("Addon", emptyList(), contentType = "anime.series", catalogName = "Trending")))
        assertEquals("2024–2026", PhonePresentationPolicy.cardContext(Media("m", "movie", year = "2024–2026")))
        assertEquals("S0 E1", PhonePresentationPolicy.cardContext(Media("e", "episode", season = 0, episode = 1, year = "2024")))
    }

    @Test fun discoverDefaultsAndGroupingUseSharedProjection() {
        val catalog = DiscoverCatalog(CatalogKey("a", "anime.series", "trending"), "Trending", false, true,
            listOf(CatalogFilter("genre", CatalogFilterKind.Genre, true, listOf("Action"), "Drama"),
                CatalogFilter("sort", CatalogFilterKind.Choice, true, listOf("Popular")),
                CatalogFilter("empty", CatalogFilterKind.FreeText, true, defaultValue = " ")))
        assertEquals("anime", DiscoverPolicy.typeGroup(catalog.key.type))
        assertEquals("Anime", DiscoverPolicy.groupLabel("anime"))
        assertEquals(mapOf("genre" to "Drama", "sort" to "Popular"), DiscoverPolicy.defaults(catalog))
        assertEquals(catalog, DiscoverPolicy.firstCatalog(listOf(catalog.copy(key = CatalogKey("a", "live", "live")), catalog), "anime.movie"))
    }
}
