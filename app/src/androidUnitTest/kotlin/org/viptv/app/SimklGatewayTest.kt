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
}
