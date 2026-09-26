package org.viptv.app

import org.viptv.video.PlayerCapabilities
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises VipTvHttpGateway through its real OkHttp boundary. The
 * fixture intentionally speaks the server's JSON wire shape rather than
 * mocking the gateway or its JSON helpers.
 */
class BackendGatewayWireTest {

    @Test
    fun `direct live playback sends a channel target without a stream id`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/playback", request.target)
            val body = JSONObject(request.body)
            assertEquals("station-1", body.getString("channel_id"))
            assertFalse(body.has("stream_id"))
            FixtureResponse("""{"id":"live-session","url":"/media/live-session/capability/index.m3u8","format":"hls","mode":"direct","position":0,"live":true}""")
        }.use { server ->
            val result = VipTvHttpGateway(server.origin).playback(
                source = Source("station-1", "Live TV", "News", channelId = "station-1"),
                positionMillis = 0,
                capabilities = PlaybackClientCapabilities(maxWidth = 1920, maxHeight = 1080, h264 = true, hevc = false, hevcSdr = false, aac = true, directPlay = true),
            )
            assertTrue(result.live)
            server.assertHealthy()
        }
    }

    @Test
    fun `device poll distinguishes pending rate limit and unexpected failures`() = runBlocking {
        val attempt = AtomicInteger()
        FixtureServer(3) { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/auth/device/token", request.target)
            assertEquals("device-code", JSONObject(request.body).getString("device_code"))
            when (attempt.getAndIncrement()) {
                0 -> FixtureResponse("""{"error":"authorization_pending","code":"authorization_pending"}""", 400)
                1 -> FixtureResponse("""{"error":"Please try again shortly","code":"rate_limited"}""", 429)
                else -> FixtureResponse("""{"error":"Invalid device request","code":"invalid_request"}""", 400)
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)

            assertEquals(DevicePollResult.Pending, gateway.exchangeDeviceCode("device-code"))
            assertEquals(DevicePollResult.RateLimited, gateway.exchangeDeviceCode("device-code"))
            val error = try {
                gateway.exchangeDeviceCode("device-code")
                error("Expected the invalid device request to fail")
            } catch (error: GatewayError) {
                error
            }
            assertEquals(400, error.status)
            server.assertHealthy()
        }
    }

    @Test
    fun `playback client capabilities retain only measured decoder facts`() {
        val measured = PlaybackClientCapabilities.from(
            PlayerCapabilities(
                containers = setOf("mp4"),
                videoCodecs = setOf("h264", "hevc"),
                audioCodecs = setOf("aac"),
                maxVideoWidth = 3840,
                maxVideoHeight = 2160,
                supportsHevcSdr = true,
            ),
        )
        val unknownSize = PlaybackClientCapabilities.from(
            PlayerCapabilities(
                adaptiveProtocols = setOf("hls"),
                videoCodecs = setOf("h264", "hevc"),
                audioCodecs = setOf("aac"),
                supportsHevcSdr = true,
            ),
        )

        assertEquals(3840, measured.maxWidth)
        assertEquals(2160, measured.maxHeight)
        assertTrue(measured.hevc)
        assertTrue(measured.hevcSdr)
        assertTrue(measured.directPlay)
        assertEquals(0, unknownSize.maxWidth)
        assertEquals(0, unknownSize.maxHeight)
        assertFalse(unknownSize.directPlay)
    }

    @Test
    fun `source polling consumes every streams array event`() = runBlocking {
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/streams" -> FixtureResponse("""{"id":"job-1"}""")
                "/api/streams/job-1?after=0" -> FixtureResponse(
                    """{"events":[
                        {"seq":1,"source":"iptv:4","streams":[
                          {"id":"stream-a","provider":"iptv:4","source_name":"Evening News","title":"HD broadcast","filename":"evening-news.mkv","source_quality":"1080p","source_audio":"English 5.1","headers":{"Authorization":"private-token"},"source_addon_id":"addon:one","source_fingerprint":"fp-a"},
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
            assertEquals("iptv:4", sources.first().provider)
            assertEquals("Evening News", sources.first().name)
            assertEquals("HD broadcast\nevening-news.mkv", sources.first().description)
            assertEquals("Evening News", SourceDisplayPolicy.title(sources.first()))
            assertEquals("HD broadcast\nevening-news.mkv", SourceDisplayPolicy.body(sources.first()))
            assertEquals("1080p", sources.first().quality)
            assertEquals("English 5.1", sources.first().audio)
            assertEquals("addon:one", sources.first().addonId)
            assertEquals("fp-c", sources.last().fingerprint)
            assertFalse(sources.toString().contains("private-token"))
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
            val launch = gateway.playback(
                source = source,
                positionMillis = 42_500,
                capabilities = PlaybackClientCapabilities(
                    maxWidth = 3840,
                    maxHeight = 2160,
                    h264 = true,
                    hevc = true,
                    hevcSdr = true,
                    aac = true,
                    directPlay = true,
                ),
                audioTrackIndex = 2,
                subtitleTrackIndex = 4,
                subtitlesOff = true,
            )
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
            val capabilities = playback.getJSONObject("capabilities")
            assertEquals(3840, capabilities.getInt("max_width"))
            assertEquals(2160, capabilities.getInt("max_height"))
            assertTrue(capabilities.getBoolean("h264"))
            assertTrue(capabilities.getBoolean("hevc"))
            assertTrue(capabilities.getBoolean("hevc_sdr"))
            assertTrue(capabilities.getBoolean("aac"))
            assertTrue(capabilities.getBoolean("direct_play"))

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

    @Test
    fun `profile mutations send avatar choice not server seed and honor setup complete rules`() = runBlocking {
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/profiles" -> FixtureResponse(
                    """{"id":"7","name":"New","avatar_style":"pixel-art","avatar_choice":3,"setup_complete":true}""",
                )
                "/api/profiles/7" -> FixtureResponse(
                    """{"id":"7","name":"Renamed","avatar_style":"moods","avatar_choice":9,"setup_complete":true}""",
                )
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val created = gateway.createProfile("New", "pixel-art", avatarChoice = 3)
            val updated = gateway.updateProfile(created.copy(setupComplete = false), "Renamed", "moods", avatarChoice = 9)

            val create = JSONObject(server.requests[0].body)
            assertEquals("POST", server.requests[0].method)
            assertEquals("New", create.getString("name"))
            assertEquals("pixel-art", create.getString("avatar_style"))
            assertEquals(3, create.getInt("avatar_choice"))
            assertFalse(create.has("avatar_seed"))
            assertFalse(create.has("setup_complete"))

            val update = JSONObject(server.requests[1].body)
            assertEquals("PATCH", server.requests[1].method)
            assertEquals("Renamed", update.getString("name"))
            assertEquals("moods", update.getString("avatar_style"))
            assertEquals(9, update.getInt("avatar_choice"))
            assertTrue(update.getBoolean("setup_complete"))
            assertFalse(update.has("avatar_seed"))
            assertEquals(9, updated.avatarChoice)
            assertTrue(updated.setupComplete)
            server.assertHealthy()
        }
    }

    @Test
    fun `series progress retains resume identity and recency from profile endpoint`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("/api/profiles/profile-1/progress/series?series_id=show-1", request.target)
            FixtureResponse("""[{"id":"show-1:1:2","type":"series","series_id":"show-1","season":1,"episode":2,"position":120,"duration":1800,"updated_at":1700000000,"source_addon_id":"addon-1","source_fingerprint":"fp-1"}]""")
        }.use { server ->
            val progress = VipTvHttpGateway(server.origin).seriesProgress("profile-1", "show-1").single()
            assertEquals(120_000L, progress.positionMillis)
            assertEquals(1_700_000_000_000L, progress.updatedAtMillis)
            assertEquals("fp-1", progress.sourceFingerprint)
            assertEquals(2, progress.episode)
            server.assertHealthy()
        }
    }

    @Test
    fun `metadata preserves episode title while inheriting series context`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/meta/series/show-1", request.target)
            FixtureResponse(
                """{"meta":{"id":"show-1","type":"series","name":"Fixture Show","videos":[{"id":"show-1:1:2","name":"Fixture Show","episode_title":"The Signal","season":1,"episode":2}]}}""",
            )
        }.use { server ->
            val show = VipTvHttpGateway(server.origin).metadata(Media("show-1", "series", "Fixture Show"))
            val episode = show.episodes.single()

            assertEquals("Fixture Show", episode.name)
            assertEquals("The Signal", episode.episodeTitle)
            assertEquals("show-1", episode.seriesId)
            assertEquals("series", episode.type)
            server.assertHealthy()
        }
    }

    @Test
    fun `add-on installation and server facts use bounded public wire fields`() = runBlocking {
        FixtureServer(3) { request ->
            when (request.target) {
                "/api/addons" -> if (request.method == "GET") FixtureResponse("""[{"id":2,"name":"Fixture","manifest_url":"https://example.test/manifest.json","enabled":false}]""")
                    else FixtureResponse("""{"id":4,"name":"Installed","manifest_url":"https://addons.example.test/manifest.json","enabled":true}""")
                "/api/status" -> FixtureResponse("""{"providers":3,"addons":4,"profiles":8,"active_sessions":2,"ffmpeg_available":true,"video_acceleration":{"backend":"vaapi"}}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val addon = gateway.addAddon("https://addons.example.test/manifest.json")
            val about = gateway.serverAbout()
            val addons = gateway.addons()

            assertEquals("POST", server.requests[0].method)
            assertEquals("https://addons.example.test/manifest.json", JSONObject(server.requests[0].body).getString("manifest_url"))
            assertEquals(Addon("4", "Installed", "https://addons.example.test/manifest.json", true), addon)
            assertTrue(about.mediaServiceAvailable)
            assertEquals(listOf(Addon("2", "Fixture", "https://example.test/manifest.json", false)), addons)
            server.assertHealthy()
        }
    }
}
