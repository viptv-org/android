package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search sources, Home order and metadata lookups follow the shared core plans. */
class SharedCatalogPlanWireTest {
    @Test fun searchCoversLiveCatalogsAndRequestsEightyChannelsShownLast() = runBlocking {
        FixtureServer(null) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse(
                    """[{"id":"top","type":"movie","name":"Popular","addon_id":1,"addon_name":"Cinemeta","supports_search":true},
                       {"id":"chan","type":"live","name":"Channels","addon_id":2,"addon_name":"IPTV","supports_search":true},
                       {"id":"plain","type":"movie","name":"Plain","addon_id":3}]""")
                request.target.startsWith("/api/discover") -> FixtureResponse(
                    """{"metas":[{"id":"hit","type":"movie","name":"Hit"}]}""")
                request.target.startsWith("/api/v2/iptv/live/channels?") -> FixtureResponse(JSONObject()
                    .put("catalog_id", 1).put("generation", 1).put("next_cursor", JSONObject.NULL).put("previous_cursor", JSONObject.NULL)
                    .put("items", JSONArray().also { rows -> repeat(30) { rows.put(JSONObject().put("id", "live-$it").put("name", "Channel $it")) } })
                    .toString())
                else -> error("Unexpected request: ${request.target}")
            }
        }.use { server ->
            val result = VipTvHttpGateway(server.origin).search("  bebop ")
            assertEquals(listOf("Cinemeta · Popular", "IPTV · Channels", "Live TV"), result.sections.map { it.source })
            assertEquals(24, result.sections.last().items.size, "Live TV shows the shared section limit")
            val live = server.requests.single { it.target.startsWith("/api/v2/iptv/live/channels?") }.target
            assertTrue("limit=80" in live && "search=bebop" in live, live)
            assertEquals(2, server.requests.count { it.target.startsWith("/api/discover") },
                "Only search-capable catalogs, including live namespaces, are searched")
            server.assertHealthy()
        }
    }

    @Test fun homeShelvesFollowTheSharedLayoutOrder() = runBlocking {
        FixtureServer(null) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse(
                    """[{"id":"top","type":"movie","name":"Popular","addon_id":1,"addon_name":"Cinemeta"},
                       {"id":"chan","type":"live","name":"Channels","addon_id":2}]""")
                request.target.startsWith("/api/discover") -> FixtureResponse("""{"metas":[{"id":"cat","type":"movie","name":"Cat"}]}""")
                request.target.contains("/continue/page") -> FixtureResponse(items("queue"))
                request.target.contains("/favorites/page") -> FixtureResponse(items("saved"))
                request.target.startsWith("/api/v2/iptv/live/channels") -> FixtureResponse(
                    """{"catalog_id":1,"generation":1,"items":[{"id":"live-1","name":"Live one"}],"next_cursor":null,"previous_cursor":null}""")
                request.target == "/api/live" -> FixtureResponse("""{"channels":[{"id":"now-1","name":"Now"}]}""")
                else -> error("Unexpected request: ${request.target}")
            }
        }.use { server ->
            val shelves = VipTvHttpGateway(server.origin).home("profile") {}
            assertEquals(listOf("Continue watching", "Recently watched live TV", "Cinemeta · Popular", "My List", "Live now"),
                shelves.map { it.title })
            assertTrue(shelves.first().isQueueShelf)
            assertTrue(server.requests.any { it.target.contains("/continue/page?limit=40") })
            server.assertHealthy()
        }
    }

    @Test fun episodeMetadataWithoutSeriesIdAsksForTheSeries() = runBlocking {
        FixtureServer(null) { request ->
            when {
                request.target.startsWith("/api/meta/series/") -> FixtureResponse(
                    """{"meta":{"id":"tt1","type":"series","name":"Series","poster":"https://images.example/p.jpg"}}""")
                else -> error("Unexpected request: ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val result = gateway.metadata(Media("tt1:1:2", "episode", "Episode"))
            assertEquals("Series", result.name)
            // The shared target keys the cache, so a second lookup issues no request.
            gateway.metadata(Media("tt1:1:2", "episode", "Episode"))
            assertEquals(listOf("/api/meta/series/tt1%3A1%3A2"), server.requests.map { it.target })
            // Live channels have no metadata title; the item is returned unchanged.
            val live = Media("chan", "live", "Channel")
            assertEquals(live, gateway.metadata(live))
            assertEquals(1, server.requests.size)
            server.assertHealthy()
        }
    }

    private fun items(prefix: String): String = JSONObject().put("items", JSONArray().also { array ->
        repeat(3) { index -> array.put(JSONObject().put("id", "$prefix-$index").put("type", "movie").put("name", "Item $index")) }
    }).toString()
}
