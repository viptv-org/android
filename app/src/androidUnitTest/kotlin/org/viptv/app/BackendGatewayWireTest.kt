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
    @Test fun `same source gateway options use shared mapping without changing title position or tracks`() = runBlocking {
        val ids = mutableSetOf<String>()
        FixtureServer(2) { request ->
            val body = JSONObject(request.body)
            ids.add(body.getString("request_id"))
            assertEquals("source-one", body.getString("stream_id"))
            assertEquals(42.0, body.getDouble("position"))
            assertEquals(2, body.getInt("audio_track"))
            assertTrue(body.getBoolean("subtitles_off"))
            assertTrue(body.getBoolean("force_gateway"))
            assertEquals(2160, body.getJSONObject("client").getInt("max_height"))
            assertEquals(if (ids.size == 1) "auto" else "audio_video", body.getString("conversion"))
            FixtureResponse(v2Ready("gateway-${ids.size}", """{"kind":"gateway","url":"https://gateway.example/media/viewer/cap/index.m3u8","format":"hls","mode":"remux","video_mode":"copy","audio_mode":"copy","position":42,"duration":120,"live":false,"audio_tracks":[],"subtitle_tracks":[],"subtitles_supported":false}"""))
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            for (transcode in listOf(false, true)) gateway.playback(Source("source-one", "Fixture"), 42_000,
                PlaybackClientCapabilities(3840, 2160, true, true, true, true, true),
                audioTrackIndex = 2, subtitlesOff = true, delivery = PlaybackDeliveryOptions(true, transcode))
            assertEquals(2, ids.size)
            server.assertHealthy()
        }
    }
    @Test fun `v2 conversion intent maps through native core into generated request types`() {
        val input = """{"requestId":"native","platform":"android_tv","preferences":{"audioLanguage":"en","quality":"1080p"},"playback":{"streamId":"source","capabilities":{"maxWidth":3840,"maxHeight":2160,"h264":true,"aac":true,"directUrls":true},"forceTranscode":true,"conversionReason":"audio-codec"}}"""
        val mapped = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.PlaybackV2Request>(
            uniffi.viptv_core.normalize("playbackV2Intent", input, "https://backend.example"))
        assertEquals(org.viptv.core.wire.PlaybackConversion.AUDIO, mapped.conversion)
        assertEquals(2160L, mapped.client.maxHeight)
        assertEquals("en", mapped.preferredAudioLanguage)
        assertTrue(mapped.forceGateway)
    }

    @Test fun `v2 playback leases decode through native core and generated Kotlin types`() {
        val wire = """{"id":"pb2_fixture","status":"ready","expires_at":1800000060,"renew_after_seconds":20,"delivery":{"kind":"direct","url":"http://provider.example/movie.mp4","format":"original","headers":{"User-Agent":"Native fixture"},"position":12,"live":false}}"""
        val normalized = uniffi.viptv_core.normalize("playbackV2", wire, "https://backend.example")
        val lease = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.PlaybackLease>(normalized)
        assertEquals(org.viptv.core.wire.PlaybackLeaseStatus.READY, lease.status)
        assertEquals(1800000060000L, lease.expiresAt.toLong())
        assertEquals(org.viptv.core.wire.PlaybackDeliveryKind.DIRECT, lease.session?.deliveryKind)
        assertEquals("http://provider.example/movie.mp4", lease.session?.url)
        assertEquals("Native fixture", lease.session?.authorization?.userAgent)
        val expired = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.PlaybackLease>(
            uniffi.viptv_core.normalize("playbackV2", wire.replace("\"ready\"", "\"expired\""), "https://backend.example"))
        assertEquals(null, expired.session)
        assertEquals("playback_expired", expired.errorCode)
    }

    @Test fun `v2 source failures stay actionable without leaking upstream URLs`() = runBlocking {
        FixtureServer(2) { request -> when(request.target) {
            "/api/v2/streams" -> FixtureResponse("""{"id":"job"}""")
            "/api/v2/streams/job?after=0" -> FixtureResponse("""{"events":[{"seq":1,"source":"iptv:1","streams":[],"error_code":"provider_connection_limit","error":"https://provider.invalid/private-token"}],"done":true}""")
            else -> error("Unexpected fixture route")
        } }.use { server ->
            val failure=kotlin.test.assertFailsWith<GatewayError> { VipTvHttpGateway(server.origin).sources(Media("tt123", "movie", "Movie")) }
            assertEquals("provider_connection_limit",failure.code)
            assertTrue(failure.message.contains("Stop another stream"))
            assertFalse(failure.message.contains("private-token"))
            server.assertHealthy()
        }
    }

    @Test fun `connection limits remain actionable through HTTP and native normalization`() = runBlocking {
        FixtureServer(1) { FixtureResponse("""{"error":"Provider connection limit reached","error_code":"provider_connection_limit"}""", 429) }.use { server ->
            val failure = kotlin.test.assertFailsWith<GatewayError> {
                VipTvHttpGateway(server.origin).playback(Source("one", "Fixture"), 0,
                    PlaybackClientCapabilities(1920, 1080, true, false, false, true, true))
            }
            assertTrue(failure.message.contains("Stop another stream"))
            assertEquals(429, failure.status)
        }
    }

    @Test fun `native password sign in uses one endpoint and keeps secrets out of descriptions`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("/api/auth/device/login", request.target)
            val body = JSONObject(request.body)
            assertEquals("viewer", body.getString("username"))
            assertEquals("test-password", body.getString("password"))
            assertEquals("VIPTV Android", body.getString("device_name"))
            FixtureResponse("""{"session_id":"native","account_id":"1","access_token":"test-access","refresh_token":"test-refresh","profile_id":null,"expires_in":900}""")
        }.use { server ->
            val session = VipTvHttpGateway(server.origin).signIn(" viewer ", "test-password", "VIPTV Android")
            assertEquals("test-access", session.accessToken)
            assertNull(session.profileId)
            assertFalse(session.toString().contains("test-access"))
            server.assertHealthy()
        }
    }

    @Test fun `native playback requests the original URL and preserves required source headers`() = runBlocking {
        FixtureServer(1) { request ->
            val body = JSONObject(request.body)
            assertEquals("/api/v2/playback", request.target)
            assertTrue(body.getJSONObject("client").getBoolean("can_play_direct"))
            assertEquals("android", body.getJSONObject("client").getString("platform"))
            assertEquals(125.0, body.getDouble("position"))
            FixtureResponse(v2Ready("native-media", """{"kind":"direct","url":"http://provider.test/video.mkv","format":"original","position":125,"live":false,"headers":{"Cookie":"fixture-cookie","User-Agent":"Native Fixture","Referer":"https://provider.test/watch"}}"""))
        }.use { server ->
            val launch = VipTvHttpGateway(server.origin).playback(Source("movie", "IPTV"), 125_000,
                PlaybackClientCapabilities(1920, 1080, true, false, false, true, true))
            assertEquals("http://provider.test/video.mkv", launch.url)
            assertEquals("fixture-cookie", launch.headers["Cookie"])
            assertEquals("Native Fixture", launch.headers["User-Agent"])
            assertEquals("https://provider.test/watch", launch.headers["Referer"])
            assertFalse(launch.toString().contains("fixture-cookie"))
            server.assertHealthy()
        }
    }

    @Test fun `empty provider IDs retain independent addon and IPTV groups`() {
        val first = CoreModels.source(JSONObject("""{"id":"a","source_name":"IPTV One","source_addon_id":"iptv:1"}"""))
        val second = CoreModels.source(JSONObject("""{"id":"b","source_name":"Stremio Addon","source_addon_id":"addon:4"}"""))
        assertFalse(SourceDisplayPolicy.providerKey(first) == SourceDisplayPolicy.providerKey(second))
        assertEquals("IPTV One", SourceDisplayPolicy.providerLabel(first))
        assertEquals("Stremio Addon", SourceDisplayPolicy.providerLabel(second))
    }

    @Test
    fun `live playback resolves exactly one channel then admits its opaque source through v2`() = runBlocking {
        FixtureServer(2) { request ->
            assertEquals("POST", request.method)
            if (request.target == "/api/v2/iptv/live/station-1/source")
                return@FixtureServer FixtureResponse("""{"source":{"id":"opaque-live","source":"iptv:1","source_addon_id":"iptv:1","name":"Fixture"}}""")
            assertEquals("/api/v2/playback", request.target)
            val body = JSONObject(request.body)
            assertEquals("opaque-live", body.getString("stream_id"))
            assertFalse(body.has("channel_id"))
            assertEquals(0.0, body.getDouble("position"))
            FixtureResponse(v2Ready("live-session", """{"kind":"direct","url":"http://provider.example/live.ts","format":"original","headers":{},"position":0,"live":true}"""))
        }.use { server ->
            val result = VipTvHttpGateway(server.origin).playback(
                source = Source("station-1", "Live TV", "News", channelId = "station-1"),
                positionMillis = 18_000,
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
    fun `source polling keeps healthy streams when another provider fails`() = runBlocking {
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/v2/streams" -> FixtureResponse("""{"id":"job-1"}""")
                "/api/v2/streams/job-1?after=0" -> FixtureResponse(
                    """{"events":[
                        {"seq":1,"source":"iptv:4","streams":[
                          {"id":"stream-a","provider":"iptv:4","source_name":"Evening News","title":"HD broadcast","filename":"evening-news.mkv","source_quality":"1080p","source_audio":"English 5.1","headers":{"Authorization":"private-token"},"source_addon_id":"addon:one","source_fingerprint":"fp-a"},
                          {"id":"stream-b","name":"720p","filename":"b.mkv","source_addon_id":"addon:one","source_fingerprint":"fp-b"}
                        ]},
                        {"seq":2,"source":"addon:two","streams":[
                          {"id":"stream-c","name":"480p","source_addon_id":"addon:two","source_fingerprint":"fp-c"}
                        ]},
                        {"seq":3,"source":"iptv:9","streams":[],"error_code":"provider_rate_limited","error":"http://private.invalid/secret"}
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
                "/api/v2/playback" -> FixtureResponse(v2Ready("session-1",
                    """{
                      "kind":"gateway","url":"https://gateway.test/base/media/session-1/capability/index.m3u8",
                      "format":"hls","mode":"remux","video_mode":"copy","audio_mode":"encode",
                      "position":42.5,"duration":120.25,"live":false,
                      "audio_tracks":[{"input_index":2,"codec":"aac","language":"en","language_status":"declared","title":"English","selected":true,"supported":true,"selectable":true}],
                      "subtitle_tracks":[{"input_index":4,"codec":"webvtt","language":"es","title":"Spanish","selected":false,"supported":true,"selectable":true}],
                      "subtitles_supported":true
                    }""".trimIndent(),
                ))
                "/api/profiles/profile-1/progress" -> FixtureResponse("{}")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin, television = true)
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
            assertEquals(2, playback.getInt("audio_track"))
            assertTrue(playback.isNull("subtitle_track"))
            assertTrue(playback.getBoolean("subtitles_off"))
            val capabilities = playback.getJSONObject("client")
            assertEquals(3840, capabilities.getInt("max_width"))
            assertEquals(2160, capabilities.getInt("max_height"))
            assertEquals("android_tv", capabilities.getString("platform"))
            assertEquals("[\"h264\",\"hevc\"]", capabilities.getJSONArray("video_codecs").toString())
            assertTrue(capabilities.getBoolean("can_play_direct"))

            assertEquals("https://gateway.test/base/media/session-1/capability/index.m3u8", launch.url)
            assertEquals("managed", launch.timelineMode)
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
