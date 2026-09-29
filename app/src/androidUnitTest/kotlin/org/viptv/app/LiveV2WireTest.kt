package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveV2WireTest {
    @Test fun `default live page is lazy and its cursor does not become an explicit catalog override`() = runBlocking {
        FixtureServer(2) { request ->
            assertEquals("GET",request.method)
            assertTrue(request.target.startsWith("/api/v2/iptv/live/channels?limit=2"))
            FixtureResponse("""{"catalog_id":1,"generation":7,"items":[{"id":"iptv:1:9","name":"Zulu","logo":"http://images.example/logo.png"},{"id":"iptv:1:1","name":"Alpha"}],"next_cursor":"next_page"}""")
        }.use { server ->
            val api=VipTvHttpGateway(server.origin)
            val first=api.liveV2(LiveCatalogQuery(limit=2))
            assertEquals(listOf("iptv:1:9","iptv:1:1"),first.items.map { it.id })
            assertEquals("http://images.example/logo.png",first.items[0].poster)
            assertEquals("1",first.catalogId)
            assertEquals(1,server.requests.size)
            api.liveV2(LiveCatalogQuery(limit=2,cursor=first.nextCursor))
            assertEquals("/api/v2/iptv/live/channels?limit=2&cursor=next_page",server.requests.last().target)
            server.assertHealthy()
        }
    }

    @Test fun `personal subset and category pages use native core contracts without counts`() = runBlocking {
        FixtureServer(2) { request ->
            if (request.target.contains("channels")) {
                assertEquals("/api/v2/iptv/live/channels?limit=40&collection=favorites&catalog_id=2&search=News",request.target)
                FixtureResponse("""{"catalog_id":2,"generation":7,"items":[],"next_cursor":null}""")
            } else FixtureResponse("""{"catalog_id":2,"generation":7,"items":[{"id":"news","name":"News","count":999}],"next_cursor":null}""")
        }.use { server ->
            val api=VipTvHttpGateway(server.origin)
            api.liveV2(LiveCatalogQuery(catalogId="2",collection="favorites",search=" News ",limit=40))
            val categories=api.liveCategoriesV2(LiveCatalogQuery(catalogId="2",limit=40))
            assertEquals("News",categories.items.single().name)
            assertNull(categories.nextCursor)
            server.assertHealthy()
        }
    }

    @Test fun `exact live source and guide routes keep raw media authority outside source cards`() = runBlocking {
        FixtureServer(2) { request -> when(request.target) {
            "/api/v2/iptv/live/iptv%3A1%3A7/source" -> {
                assertEquals("POST",request.method)
                FixtureResponse("""{"source":{"id":"opaque_live","source":"iptv:1","source_addon_id":"iptv:1","name":"Provider","title":"News","source_fingerprint":"stable","integration_key":"private-key"}}""")
            }
            "/api/v2/iptv/guide/iptv%3A1%3A7" -> FixtureResponse("""{"timezone":"UTC","programs":[{"title":"News","start":10,"end":20}]}""")
            else -> error("Unexpected fixture route")
        } }.use { server ->
            val api=VipTvHttpGateway(server.origin)
            val source=api.liveSourceV2("iptv:1:7")
            assertEquals("opaque_live",source.id)
            assertEquals("iptv:1",source.addonId)
            assertEquals("stable",source.fingerprint)
            val guide=api.guideV2("iptv:1:7")
            assertEquals(10_000L,guide.single().startMillis)
            server.assertHealthy()
        }
    }

    @Test fun `malformed and changed catalogs do not fall back to old APIs or silently switch playlists`() = runBlocking {
        FixtureServer(2) { request ->
            assertTrue(request.target.startsWith("/api/v2/iptv/live/channels"))
            if (request.target.contains("cursor=")) FixtureResponse("""{"error_code":"catalog_changed","error":"https://private.example/token"}""",409)
            else FixtureResponse("""{"channels":[],"total":0}""")
        }.use { server ->
            val api=VipTvHttpGateway(server.origin)
            assertEquals("invalid_catalog_response",assertFailsWith<GatewayError> { api.liveV2() }.code)
            assertEquals("catalog_changed",assertFailsWith<GatewayError> { api.liveV2(LiveCatalogQuery(cursor="old_cursor")) }.code)
            server.assertHealthy()
        }
    }

    @Test fun `invalid limits are rejected by core before a network call`() = runBlocking {
        assertEquals("invalid_catalog_query",assertFailsWith<GatewayError> { VipTvHttpGateway("https://fixture.invalid").liveV2(LiveCatalogQuery(limit=201)) }.code)
    }
}
