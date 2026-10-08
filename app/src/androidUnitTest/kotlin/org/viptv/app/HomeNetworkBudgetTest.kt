package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeNetworkBudgetTest {
    @Test fun homePublishesCatalogAndSavedRowsWithoutFetchingUnfocusedMetadata() = runBlocking {
        FixtureServer(null) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse("[]")
                request.target.contains("/continue/page") -> FixtureResponse(items("queue"))
                request.target.contains("/favorites/page") -> FixtureResponse(items("saved"))
                request.target.startsWith("/api/v2/iptv/live/channels") -> FixtureResponse(
                    """{"catalog_id":1,"generation":1,"items":[],"next_cursor":null,"previous_cursor":null}""")
                request.target.startsWith("/api/meta/") -> FixtureResponse(
                    JSONObject().put("meta", JSONObject().put("id", request.target.substringAfterLast('/'))
                        .put("type", "movie").put("name", "Metadata")).toString())
                else -> error("Unexpected request: ${request.target}")
            }
        }.use { server ->
            val shelves = VipTvHttpGateway(server.origin).home("profile") {}
            assertEquals(2, shelves.size)
            assertEquals(40, shelves.first().items.size)
            assertEquals(0, server.requests.count { it.target.startsWith("/api/meta/") },
                "Opening Home must not request full metadata for unfocused cards")
            server.assertHealthy()
        }
    }

    private fun items(prefix: String): String = JSONObject().put("items", JSONArray().also { array ->
        repeat(40) { index -> array.put(JSONObject().put("id", "$prefix-$index")
            .put("type", "movie").put("name", "Item $index")) }
    }).toString()
}
