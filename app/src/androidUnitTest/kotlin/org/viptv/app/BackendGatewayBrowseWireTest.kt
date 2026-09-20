package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises VipTvHttpGateway browse surfaces through its real OkHttp boundary.
 * The fixture intentionally speaks the server's JSON wire shape rather than
 * mocking the gateway or its JSON helpers.
 */
class BackendGatewayBrowseWireTest {

    @Test
    fun `queue next keeps the prior episode as the management and exact resume target`() = runBlocking {
        FixtureServer(2) { request ->
            assertEquals("GET", request.method)
            if (request.target == "/api/meta/series/episode-2") return@FixtureServer FixtureResponse("""{"meta":{"id":"episode-2","type":"series","name":"Fixture Show","background":"https://images.example/series.jpg"}}""")
            assertEquals("/api/profiles/profile/continue/page?limit=40", request.target)
            FixtureResponse(
                """{"items":[{
                    "id":"episode-2","type":"series","name":"Fixture Show","queue_status":"next",
                    "previous_episode":{"id":"episode-1","type":"series","name":"Fixture Show","position":995,"duration":1000,"source_addon_id":"iptv","source_fingerprint":"fingerprint"}
                }]}""",
            )
        }.use { server ->
            val item = VipTvHttpGateway(server.origin).queue("profile").single()
            assertTrue(QueuePolicy.hasResolvedNext(item))
            assertEquals("episode-1", QueuePolicy.manageTarget(item).id)
            assertEquals(MediaCardAction.PlayQueuedNext, MediaCardPolicy.primary(resumeSurface = true, media = item))
            assertTrue(QueuePolicy.canResume(item))
            server.assertHealthy()
        }
    }

    @Test
    fun `catalogs retain source qualified identities and declared filters`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/catalogs", request.target)
            FixtureResponse(
                """[
                  {"addon_id":1,"id":"top","type":"movie","name":"First Top","supports_search":true,"supports_skip":true,"extra":[
                    {"name":"search","is_required":true,"options":[],"default":null},
                    {"name":"genre","is_required":false,"options":["Drama","Science Fiction"],"default":"Drama","options_limit":32},
                    {"name":"year","is_required":true,"options":["2024","2025"],"default":"2025","options_limit":2},
                    {"name":"query","is_required":false,"options":[],"default":null},
                    {"name":"skip","is_required":false,"options":[]}
                  ]},
                  {"addon_id":2,"id":"top","type":"movie","name":"Second Top","supports_search":false,"supports_skip":false,"extra":[]}
                ]""".trimIndent(),
            )
        }.use { server ->
            val catalogs = VipTvHttpGateway(server.origin).catalogs()

            assertEquals(2, catalogs.size)
            assertEquals("1\u0000movie\u0000top", catalogs[0].key.stableId)
            assertEquals("2\u0000movie\u0000top", catalogs[1].key.stableId)
            assertTrue(catalogs[0].key != catalogs[1].key)
            assertTrue(catalogs[0].supportsSkip)
            assertEquals(CatalogFilterKind.Search, catalogs[0].filters.single { it.name == "search" }.kind)
            assertEquals(CatalogFilterKind.Genre, catalogs[0].filters.single { it.name == "genre" }.kind)
            assertEquals("Drama", catalogs[0].filters.single { it.name == "genre" }.defaultValue)
            assertEquals(CatalogFilterKind.Choice, catalogs[0].filters.single { it.name == "year" }.kind)
            assertTrue(catalogs[0].filters.single { it.name == "year" }.required)
            assertEquals(CatalogFilterKind.FreeText, catalogs[0].filters.single { it.name == "query" }.kind)
            assertFalse(catalogs[0].filters.any { it.name == "skip" })
            server.assertHealthy()
        }
    }

    @Test
    fun `catalog discover applies qualified filters and returns the forward cursor`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            val query = queryParameters(request.target)
            assertEquals("movie", query["type"])
            assertEquals("top", query["catalog"])
            assertEquals("2", query["addon_id"])
            assertEquals("40", query["skip"])
            assertEquals("space opera", query["search"])
            assertEquals("Science Fiction", query["genre"])
            assertEquals("en", JSONObject(query.getValue("extras")).getString("language"))
            assertEquals("2025", JSONObject(query.getValue("extras")).getString("year"))
            FixtureResponse("""{"metas":[{"id":"movie-41","type":"movie","name":"Page item"}],"has_more":true,"next_skip":64}""")
        }.use { server ->
            val catalog = DiscoverCatalog(
                key = CatalogKey(addonId = "2", type = "movie", id = "top"),
                name = "Second Top",
                supportsSearch = true,
                supportsSkip = true,
                filters = emptyList(),
            )
            val page = VipTvHttpGateway(server.origin).discover(
                CatalogDiscoverRequest(
                    catalog = catalog,
                    skip = 40,
                    search = "space opera",
                    genre = "Science Fiction",
                    extras = mapOf("year" to "2025", "language" to "en"),
                ),
            )

            assertEquals(catalog.key, page.catalog)
            assertEquals(40, page.requestedSkip)
            assertTrue(page.hasMore)
            assertEquals(64, page.nextSkip)
            assertEquals(listOf("movie-41"), page.items.map(Media::id))
            server.assertHealthy()
        }
    }

    @Test
    fun `search keeps successful labelled catalogs and live results when one catalog fails`() = runBlocking {
        FixtureServer(4) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse("""[
                    {"id":"movies","name":"Movie catalog","type":"movie","addon_id":2,"supports_search":true},
                    {"id":"series","name":"Broken catalog","type":"series","addon_id":3,"supports_search":true},
                    {"id":"hidden","name":"Hidden","type":"movie","addon_id":4,"supports_search":false}
                ]""".trimIndent())
                request.target.startsWith("/api/discover?type=movie") -> FixtureResponse("""{"metas":[
                    {"id":"movie-1","type":"movie","name":"Movie one"},
                    {"id":"movie-1","type":"movie","name":"Duplicate"},
                    {"id":"movie-2","type":"movie","name":"Movie two"}
                ]}""")
                request.target.startsWith("/api/discover?type=series") -> FixtureResponse("""{"error":"provider failed"}""", 502)
                request.target.startsWith("/api/live?") -> FixtureResponse("""{"channels":[{"id":"live-1","name":"Live one"}],"total":1}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val results = VipTvHttpGateway(server.origin).search("space opera")

            assertTrue(results.partialFailure)
            assertEquals(listOf("Movie catalog", "Live TV"), results.sections.map(SearchSection::source))
            assertEquals(listOf("movie-1", "movie-2"), results.sections[0].items.map(Media::id))
            assertEquals(listOf("live-1"), results.sections[1].items.map(Media::id))
            assertEquals(4, server.requests.size)
            server.assertHealthy()
        }
    }

    @Test
    fun `catalog lookup failure retains live results and signals partial search`() = runBlocking {
        FixtureServer(2) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse("""{"error":"catalog index unavailable"}""", 503)
                request.target.startsWith("/api/live?") -> FixtureResponse("""{"channels":[{"id":"live-2","name":"Still live"}],"total":1}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val results = VipTvHttpGateway(server.origin).search("news")

            assertTrue(results.partialFailure)
            assertEquals(listOf("Live TV"), results.sections.map(SearchSection::source))
            assertEquals("live-2", results.sections.single().items.single().id)
            server.assertHealthy()
        }
    }

    @Test
    fun `live guide browse uses canonical US filters categories and forty channel pages`() = runBlocking {
        FixtureServer(6) { request ->
            val query = queryParameters(request.target)
            when {
                request.target.startsWith("/api/live/categories?") -> {
                    assertEquals("us", query["view"])
                    FixtureResponse("""{"total":2,"categories":[
                        {"id":"section:News","name":"News","count":18},
                        {"id":"section:Sports","name":"Sports","count":9}
                    ]}""")
                }
                request.target.startsWith("/api/live?") -> {
                    assertEquals("us", query["view"])
                    assertEquals("40", query["limit"])
                    when {
                        query["collection"] == "favorites" -> assertEquals("40", query["offset"])
                        query["collection"] == "recent" -> assertEquals("0", query["offset"])
                        query["category"] == "section:News" -> assertEquals("0", query["offset"])
                        query["search"] == "morning news" -> assertEquals("0", query["offset"])
                        else -> {
                            assertNull(query["collection"])
                            assertNull(query["category"])
                            assertNull(query["search"])
                            assertEquals("0", query["offset"])
                        }
                    }
                    FixtureResponse("""{
                        "channels":[{"id":"channel-1","name":"Fixture Channel","logo":"/logo.png","section":"News"}],
                        "total":81,
                        "search_scope":"US channels, sections and currently airing programmes with available guide data"
                    }""")
                }
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val all = gateway.livePage(LiveBrowseRequest())
            val mine = gateway.livePage(LiveBrowseRequest(LiveChannelFilter.MyChannels, offset = 40))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Recent))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Category("section:News")))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Search("morning news")))
            val categories = gateway.liveCategories()

            assertEquals("News", all.channels.single().category)
            assertEquals("/logo.png", all.channels.single().logo)
            assertEquals(81, all.total)
            assertEquals(1, all.nextOffset)
            assertEquals(41, mine.nextOffset)
            assertEquals(
                listOf(LiveCategory("section:News", "News", 18), LiveCategory("section:Sports", "Sports", 9)),
                categories,
            )
            server.assertHealthy()
        }
    }

private fun queryParameters(target: String): Map<String, String> = target
    .substringAfter('?', "")
    .split('&')
    .filter(String::isNotBlank)
    .associate { pair ->
        val (name, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }
}
