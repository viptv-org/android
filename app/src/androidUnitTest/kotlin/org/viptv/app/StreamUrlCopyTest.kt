package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamUrlCopyTest {
    private val caps = PlaybackClientCapabilities(1920, 1080, true, false, false, true, true)

    @Test fun `copy resolves each exact source and retires its lease without altering the URL`() = runBlocking {
        var sequence = 0
        FixtureServer(4) { request ->
            val index = sequence++ / 2
            if (request.method == "POST") {
                assertEquals("/api/playback", request.target)
                val body = JSONObject(request.body)
                assertEquals("source-$index", body.getString("stream_id"))
                assertEquals(0.0, body.getDouble("position"))
                assertTrue(body.getJSONObject("capabilities").getBoolean("direct_urls"))
                FixtureResponse("""{"id":"copy-$index","mode":"direct","format":"file","url":"https://provider.test/video-$index.mkv?token=synthetic%2Bvalue&x=1","authorization":{"headers":{"Authorization":"synthetic-header"}}}""")
            } else {
                assertEquals("DELETE", request.method)
                assertEquals("/api/playback/copy-$index", request.target)
                FixtureResponse("{}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            repeat(2) { index ->
                assertEquals("https://provider.test/video-$index.mkv?token=synthetic%2Bvalue&x=1",
                    resolveStreamUrlForCopy(gateway, Source("source-$index", "Fixture"), caps))
            }
        }
    }

    @Test fun `cancelled copy still retires late lease and never delivers URL`() = runBlocking {
        val requested = CompletableDeferred<Unit>()
        FixtureServer(2) { request ->
            if (request.method == "POST") {
                requested.complete(Unit)
                Thread.sleep(150)
                FixtureResponse("""{"id":"late","mode":"direct","url":"https://provider.test/video.mkv"}""")
            } else {
                assertEquals("DELETE", request.method)
                assertEquals("/api/playback/late", request.target)
                FixtureResponse("{}")
            }
        }.use { server ->
            var delivered = false
            val job = launch {
                resolveStreamUrlForCopy(VipTvHttpGateway(server.origin), Source("one", "Fixture"), caps)
                delivered = true
            }
            requested.await()
            job.cancel()
            job.join()
            assertFalse(delivered)
        }
    }

    @Test fun `session-bound URL cannot be copied and lease is still retired`() = runBlocking {
        FixtureServer(2) { request ->
            if (request.method == "POST") FixtureResponse("""{"id":"managed","mode":"direct","url":"/media/managed/capability/index.m3u8"}""")
            else {
                assertEquals("DELETE", request.method)
                assertEquals("/api/playback/managed", request.target)
                FixtureResponse("{}")
            }
        }.use { server ->
            val failure = assertFailsWith<IllegalStateException> {
                resolveStreamUrlForCopy(VipTvHttpGateway(server.origin), Source("one", "Fixture"), caps)
            }
            assertEquals("A direct stream URL is unavailable", failure.message)
        }
    }

    @Test fun `failed resolution does not fabricate a URL or a lease`() = runBlocking {
        FixtureServer(1) { FixtureResponse("""{"error":"expired"}""", 404) }.use { server ->
            assertFailsWith<GatewayError> {
                resolveStreamUrlForCopy(VipTvHttpGateway(server.origin), Source("expired", "Fixture"), caps)
            }
            Unit
        }
    }
}
