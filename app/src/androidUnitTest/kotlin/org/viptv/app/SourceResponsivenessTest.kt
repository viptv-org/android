package org.viptv.app

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceResponsivenessTest {
    @Test fun largeSourceResponseDoesNotBlockTheCallingUiDispatcher() = runBlocking {
        val streams = JSONArray().also { rows -> repeat(1_000) { index ->
            rows.put(JSONObject().put("id", "source-$index").put("source_name", "Torrentio")
                .put("source_addon_id", "addon:1").put("source_fingerprint", "fingerprint-$index")
                .put("name", "Torrentio 1080p").put("description", "Owned fixture release $index H264 AAC English"))
        } }
        val poll = JSONObject().put("events", JSONArray().put(JSONObject().put("seq", 1)
            .put("source", "addon:1").put("streams", streams))).put("done", true).toString()
        FixtureServer(2) { request -> when (request.target) {
            "/api/v2/streams" -> FixtureResponse("""{"id":"responsiveness"}""")
            "/api/v2/streams/responsiveness?after=0" -> FixtureResponse(poll)
            else -> error("Unexpected fixture request")
        } }.use { server ->
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
                var largestGapMillis = 0L
                withContext(ui) {
                    val heartbeat = launch {
                        var previous = System.nanoTime()
                        while (true) {
                            delay(5)
                            val now = System.nanoTime()
                            largestGapMillis = maxOf(largestGapMillis, (now - previous) / 1_000_000)
                            previous = now
                        }
                    }
                    try {
                        assertEquals(1_000, VipTvHttpGateway(server.origin).sources(Media("fixture", "movie", "Fixture")).size)
                        delay(10)
                    } finally { heartbeat.cancel() }
                }
                println("SOURCE_UI maximum_dispatcher_stall_ms=$largestGapMillis")
                assertTrue(largestGapMillis < 100, "Source parsing blocked UI for $largestGapMillis ms")
            }
            server.assertHealthy()
        }
    }
}
