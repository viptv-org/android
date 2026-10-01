package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.json.JSONObject
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class V2PlaybackControlTest {
    @Test fun `gateway direct processing still uses a managed title timeline and selected subtitles`() = runTest {
        val control = V2PlaybackControl("https://backend.test") { _, _, _ -> JSONObject(v2Ready("gateway", """{"kind":"gateway","url":"https://gateway.test/base/media/viewer/cap/index.m3u8","format":"hls","mode":"direct","video_mode":"copy","audio_mode":"copy","position":42,"duration":120,"live":false,"audio_tracks":[],"subtitle_tracks":[{"input_index":2,"language":"es","selected":true}],"subtitles_supported":true}""")) }
        val launch = control.start(input())
        assertEquals("direct", launch.mode)
        assertEquals("managed", launch.timelineMode)
        assertEquals(0L, launch.nativeStartPositionMillis)
        assertEquals(42_000L, PlaybackTimelinePolicy.titleOffsetMillis(launch.timelineMode, launch.positionMillis))
        assertEquals(true, launch.subtitlesEnabled)
        assertEquals("es", launch.subtitleTracks.single().language)
    }
    private fun input() = JSONObject("""{"requestId":"request_1","streamId":"source","client":{"platform":"android","canPlayDirect":true,"maxWidth":3840,"maxHeight":2160,"videoCodecs":["h264"],"audioCodecs":["aac"]},"position":0,"forceGateway":false}""")
    private fun lease(status: String = "ready", id: String = "session") = JSONObject(v2Ready(id, """{"kind":"direct","url":"http://provider.test/video.mp4","headers":{},"format":"original","position":0,"live":false}""")).put("status", status)

    @Test fun `pending admission polls then renews and releases only backend paths`() = runTest {
        val calls = mutableListOf<String>()
        val control = V2PlaybackControl("https://backend.test") { method, path, _ ->
            calls += "$method $path"
            if (path == "/v2/playback") lease("starting") else if (method == "DELETE") JSONObject() else lease()
        }
        val playback = control.start(input())
        assertEquals("direct", playback.deliveryKind)
        assertEquals("http://provider.test/video.mp4", playback.url)
        control.renew(playback.sessionId)
        control.stop(playback.sessionId)
        assertEquals(listOf("POST /v2/playback", "GET /v2/playback/session", "POST /v2/playback/session/heartbeat", "DELETE /v2/playback/session"), calls)
        assertNull(control.remainingMillis(playback.sessionId))
    }

    @Test fun `cancellation before admission response reconciles the exact request and releases it`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val bodies = mutableListOf<String>()
        var deleted = false
        val control = V2PlaybackControl("https://backend.test") { method, path, body ->
            if (path == "/v2/playback") {
                bodies += body.toString()
                if (bodies.size == 1) { entered.complete(Unit); awaitCancellation() }
                lease()
            } else { deleted = method == "DELETE"; JSONObject() }
        }
        val job = launch { control.start(input()); fail("Cancelled admission must not reach player") }
        entered.await(); job.cancelAndJoin()
        assertTrue(deleted)
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertNull(control.remainingMillis("session"))
    }

    @Test fun `startup and cleanup deadlines stay bounded`() = runTest {
        val control = V2PlaybackControl("https://backend.test") { _, _, _ -> awaitCancellation() }
        val failure = assertFailsWith<GatewayError> { control.start(input()) }
        assertEquals("playback_start_timeout", failure.code)
        assertEquals(50_000L, testScheduler.currentTime)
    }

    @Test fun `definitive refusal does not create a reconciliation attempt`() = runTest {
        var calls = 0
        val control = V2PlaybackControl("https://backend.test") { _, _, _ -> calls++; throw GatewayError(409, "Gateway required", "gateway_required") }
        assertEquals("gateway_required", assertFailsWith<GatewayError> { control.start(input()) }.code)
        assertEquals(1, calls)
    }

    @Test fun `late renewal cannot restore a released lease`() = runTest {
        val renewed = CompletableDeferred<JSONObject>()
        val control = V2PlaybackControl("https://backend.test") { method, path, _ ->
            if (path.endsWith("/heartbeat")) renewed.await() else if (method == "DELETE") JSONObject() else lease()
        }
        control.start(input())
        val job = launch { control.renew("session") }
        runCurrent()
        control.stop("session")
        renewed.complete(lease())
        job.join()
        assertNull(control.remainingMillis("session"))
    }

    @Test fun `terminal provider failure keeps its safe reason and releases admission`() = runTest {
        var deleted = false
        val control = V2PlaybackControl("https://backend.test") { method, _, _ ->
            if (method == "DELETE") { deleted = true; JSONObject() }
            else lease("failed").put("error_code", "provider_connection_limit").put("error", "http://private.invalid/credential")
        }
        val failure = assertFailsWith<GatewayError> { control.start(input()) }
        assertEquals("provider_connection_limit", failure.code)
        assertTrue(failure.message.contains("connection limit"))
        assertFalse(failure.message.contains("private.invalid"))
        assertTrue(deleted)
    }
}
