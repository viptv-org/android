package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimklGatewayTest {
    @Test fun `advanced search uses one request per SIMKL category and reports limited coverage`() = runBlocking {
        val paths=mutableListOf<String>()
        FixtureServer(4) { request ->
            paths.add(request.target)
            if(request.target.startsWith("/api/v2/iptv/live/channels")) return@FixtureServer FixtureResponse("""{"catalog_id":1,"generation":1,"items":[],"next_cursor":null,"previous_cursor":null}""")
            assertTrue(request.target.startsWith("/api/discover?"))
            FixtureResponse("""{"metas":[{"id":"simkl:movies:42","type":"movie","name":"Fixture movie"}],"full_search":false,"has_more":false}""")
        }.use { server ->
            val result=VipTvHttpGateway(server.origin).advancedSearch("fixture",mapOf("year_min" to "2020","rank_max" to "100"))
            assertEquals(3,result.sections.size)
            assertTrue(result.coverage.contains("link SIMKL"))
            assertTrue(paths.none {it.contains("catalogs") || it.contains("stremio")})
            server.assertHealthy()
        }
    }
    @Test fun `player events keep SIMKL identifiers and seconds in a profile scoped request`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("/api/profiles/7/integrations/simkl/playback",request.target)
            val body=JSONObject(request.body)
            assertEquals("pause",body.getString("action"));assertEquals(30.0,body.getDouble("position"));assertEquals(100.0,body.getDouble("duration"))
            assertEquals(42,body.getJSONObject("item").getJSONObject("simkl_ids").getInt("simkl"))
            FixtureResponse("""{"action":"pause"}""")
        }.use { server ->
            val media=CoreModels.media(JSONObject("""{"id":"simkl:movies:42","type":"movie","name":"Fixture movie","simkl_ids":{"simkl":42},"simkl_category":"movie"}"""))
            VipTvHttpGateway(server.origin).simklPlayback("7",media,"session-1","pause",30000,100000)
            server.assertHealthy()
        }
    }
    @Test fun `premium only HTTP 200 becomes a useful custom list message`() = runBlocking {
        FixtureServer(1) { FixtureResponse("""{"error":"premium_only"}""") }.use { server ->
            val result=runCatching { VipTvHttpGateway(server.origin).simklLists("7") }
            assertTrue(result.exceptionOrNull()?.message?.contains("PRO or VIP")==true)
            server.assertHealthy()
        }
    }
    @Test fun `home requests curated feeds without syncing or hydrating every queue item`() = runBlocking {
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val catalogs = org.json.JSONArray()
        for (type in listOf("movie", "series", "anime")) {
            for (id in listOf("today", "week", "month", "new-episodes", "premieres", "calendar")) {
                if (type == "movie" && id == "new-episodes") continue
                catalogs.put(JSONObject().put("addon_id", 0).put("id", if(type == "anime") "anime-$id" else id)
                    .put("type", if(type == "anime") "series" else type).put("name", id).put("supports_skip", true).put("extra", org.json.JSONArray()))
            }
        }
        FixtureServer(9) { request ->
            paths.add(request.target)
            when {
                request.target == "/api/catalogs" -> FixtureResponse(catalogs.toString())
                request.target.contains("/continue/page") -> FixtureResponse("""{"items":[{"id":"simkl:tv:7:1:1","type":"episode","name":"Fixture episode","position":30,"duration":100}]}""")
                request.target.contains("/favorites/page") -> FixtureResponse("""{"items":[]}""")
                request.target.startsWith("/api/discover?") -> FixtureResponse("""{"metas":[{"id":"simkl:movies:42","type":"movie","name":"Fixture movie"}],"has_more":false}""")
                else -> error("Unexpected home request: " + request.target)
            }
        }.use { server ->
            val shelves = VipTvHttpGateway(server.origin, television = true).home("1") {}
            assertEquals(6, paths.count { it.startsWith("/api/discover?") })
            assertTrue(paths.none { it.contains("/sync") || it.contains("/meta/") })
            assertEquals(30000, shelves.first { it.isQueueShelf }.items.single().positionMillis)
            server.assertHealthy()
        }
    }
}
