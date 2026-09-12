package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises VipTvHttpGateway through its real HttpURLConnection boundary. The
 * fixture intentionally speaks the server's JSON wire shape rather than
 * mocking the gateway or its JSON helpers.
 */
class BackendGatewayWireTest {
    @Test
    fun `source polling consumes every streams array event`() = runBlocking {
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/streams" -> FixtureResponse("""{"id":"job-1"}""")
                "/api/streams/job-1?after=0" -> FixtureResponse(
                    """{"events":[
                        {"seq":1,"source":"addon:one","streams":[
                          {"id":"stream-a","name":"1080p","filename":"a.mkv","source_addon_id":"addon:one","source_fingerprint":"fp-a"},
                          {"id":"stream-b","name":"720p","filename":"b.mkv","source_addon_id":"addon:one","source_fingerprint":"fp-b"}
                        ]},
                        {"seq":2,"source":"addon:two","streams":[
                          {"id":"stream-c","name":"480p","source_addon_id":"addon:two","source_fingerprint":"fp-c"}
                        ]}
                    ],"done":true}""".trimIndent(),
                )
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val updates = mutableListOf<List<Source>>()
            val sources = VipTvHttpGateway(server.origin).sources(Media("movie-1", "movie", "Movie")) { updates += it }

            assertEquals(listOf("stream-a", "stream-b", "stream-c"), sources.map(Source::id))
            assertEquals("addon:one", sources.first().addonId)
            assertEquals("fp-c", sources.last().fingerprint)
            assertEquals(sources, updates.last())
            assertEquals("POST", server.requests[0].method)
            assertEquals("GET", server.requests[1].method)
            server.assertHealthy()
        }
    }

    @Test
    fun `playback and progress convert seconds at the HTTP boundary and retain delivery facts`() = runBlocking {
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/playback" -> FixtureResponse(
                    """{
                      "id":"session-1","url":"/media/session-1/capability/index.m3u8",
                      "format":"hls","mode":"remux","video_mode":"copy","audio_mode":"encode",
                      "position":42.5,"duration":120.25,"live":false,
                      "audio_tracks":[{"input_index":2,"codec":"aac","language":"en","language_status":"declared","title":"English","selected":true,"supported":true,"selectable":true}],
                      "subtitle_tracks":[{"input_index":4,"codec":"webvtt","language":"es","title":"Spanish","selected":false,"supported":true,"selectable":true}],
                      "subtitles_supported":true
                    }""".trimIndent(),
                )
                "/api/profiles/profile-1/progress" -> FixtureResponse("{}")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val source = Source("stream-1", "Provider", name = "1080p", addonId = "addon:one", fingerprint = "fp-one")
            val launch = gateway.playback(source, 42_500, audioTrackIndex = 2, subtitleTrackIndex = 4, subtitlesOff = true)
            gateway.updateProgress(
                "profile-1",
                Media("movie-1", "movie", "Movie", positionMillis = 42_500, durationMillis = 120_250, sourceAddonId = "addon:one", sourceFingerprint = "fp-one"),
                42_500,
            )

            val playback = JSONObject(server.requests[0].body)
            assertEquals(42.5, playback.getDouble("position"))
            assertEquals(2, playback.getInt("audio_track_index"))
            assertEquals(4, playback.getInt("subtitle_track_index"))
            assertTrue(playback.getBoolean("subtitles_off"))

            assertEquals("${server.origin}/media/session-1/capability/index.m3u8", launch.url)
            assertEquals("remux", launch.mode)
            assertEquals(42_500, launch.positionMillis)
            assertEquals(120_250, launch.durationMillis)
            assertFalse(launch.live)
            assertTrue(launch.subtitlesSupported)
            assertEquals(2, launch.audioTracks.single().inputIndex)
            assertTrue(launch.audioTracks.single().selected)
            assertEquals(4, launch.subtitleTracks.single().inputIndex)

            val progress = JSONObject(server.requests[1].body)
            assertEquals(42.5, progress.getDouble("position"))
            assertEquals(120.25, progress.getDouble("duration"))
            assertEquals("addon:one", progress.getString("source_addon_id"))
            assertEquals("fp-one", progress.getString("source_fingerprint"))
            assertNull(progress.opt("source_name").takeIf { it != JSONObject.NULL })
            server.assertHealthy()
        }
    }
}

private data class FixtureRequest(val method: String, val target: String, val body: String)
private data class FixtureResponse(val body: String, val status: Int = 200)

private class FixtureServer(
    private val expectedRequests: Int,
    private val respond: (FixtureRequest) -> FixtureResponse,
) : AutoCloseable {
    private val socket = ServerSocket(0)
    private val workerFailure = Collections.synchronizedList(mutableListOf<Throwable>())
    val requests = Collections.synchronizedList(mutableListOf<FixtureRequest>())
    val origin = "http://127.0.0.1:${socket.localPort}"
    private val worker = thread(name = "viptv-gateway-wire", isDaemon = true) {
        try {
            repeat(expectedRequests) {
                socket.accept().use(::handle)
            }
        } catch (error: Throwable) {
            if (!socket.isClosed) workerFailure += error
        }
    }

    private fun handle(connection: java.net.Socket) {
        val input = connection.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val requestLine = input.readLine() ?: error("Missing request line")
        val parts = requestLine.split(' ', limit = 3)
        require(parts.size >= 2) { "Malformed request line: $requestLine" }
        var contentLength = 0
        while (true) {
            val line = input.readLine() ?: error("Unexpected end of headers")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toInt()
            }
        }
        val body = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(body, read, contentLength - read)
            if (count < 0) error("Unexpected end of request body")
            read += count
        }
        val request = FixtureRequest(parts[0], parts[1], body.concatToString())
        requests += request
        val response = respond(request)
        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        connection.getOutputStream().buffered().use { output ->
            output.write("HTTP/1.1 ${response.status} OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
        }
    }

    fun assertHealthy() {
        assertEquals(expectedRequests, requests.size)
        assertTrue(workerFailure.isEmpty(), workerFailure.joinToString("\n") { it.stackTraceToString() })
    }

    override fun close() {
        socket.close()
        worker.join(1_000)
        assertHealthy()
    }
}
