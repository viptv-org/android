package org.viptv.app

import com.getair.video.PlayerCapabilities
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
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
    fun `queue next keeps the prior episode as the management and exact resume target`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/profiles/profile/continue/page?limit=40", request.target)
            FixtureResponse(
                """{"items":[{
                    "id":"episode-2","type":"series","name":"Fixture Show","queue_status":"next",
                    "previous_episode":{"id":"episode-1","type":"series","name":"Fixture Show","position":995,"duration":1000,"source_addon_id":"iptv","source_fingerprint":"fingerprint"}
                }]}""",
            )
        }.use { server ->
            val item = VipTvHttpGateway(server.origin).queue("profile").single()
            assertTrue(QueuePolicy.hasResolvedNext(item))
            assertEquals("episode-1", QueuePolicy.manageTarget(item).id)
            assertEquals(MediaCardAction.PlayQueuedNext, MediaCardPolicy.primary(resumeSurface = true, media = item))
            assertTrue(QueuePolicy.canResume(item))
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
    fun `catalogs retain source qualified identities and declared filters`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/catalogs", request.target)
            FixtureResponse(
                """[
                  {"addon_id":1,"id":"top","type":"movie","name":"First Top","supports_search":true,"supports_skip":true,"extra":[
                    {"name":"search","is_required":true,"options":[],"default":null},
                    {"name":"genre","is_required":false,"options":["Drama","Science Fiction"],"default":"Drama","options_limit":32},
                    {"name":"year","is_required":true,"options":["2024","2025"],"default":"2025","options_limit":2},
                    {"name":"query","is_required":false,"options":[],"default":null},
                    {"name":"skip","is_required":false,"options":[]}
                  ]},
                  {"addon_id":2,"id":"top","type":"movie","name":"Second Top","supports_search":false,"supports_skip":false,"extra":[]}
                ]""".trimIndent(),
            )
        }.use { server ->
            val catalogs = VipTvHttpGateway(server.origin).catalogs()

            assertEquals(2, catalogs.size)
            assertEquals("1\u0000movie\u0000top", catalogs[0].key.stableId)
            assertEquals("2\u0000movie\u0000top", catalogs[1].key.stableId)
            assertTrue(catalogs[0].key != catalogs[1].key)
            assertTrue(catalogs[0].supportsSkip)
            assertEquals(CatalogFilterKind.Search, catalogs[0].filters.single { it.name == "search" }.kind)
            assertEquals(CatalogFilterKind.Genre, catalogs[0].filters.single { it.name == "genre" }.kind)
            assertEquals("Drama", catalogs[0].filters.single { it.name == "genre" }.defaultValue)
            assertEquals(CatalogFilterKind.Choice, catalogs[0].filters.single { it.name == "year" }.kind)
            assertTrue(catalogs[0].filters.single { it.name == "year" }.required)
            assertEquals(CatalogFilterKind.FreeText, catalogs[0].filters.single { it.name == "query" }.kind)
            assertFalse(catalogs[0].filters.any { it.name == "skip" })
            server.assertHealthy()
        }
    }

    @Test
    fun `catalog discover applies qualified filters and returns the forward cursor`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            val query = queryParameters(request.target)
            assertEquals("movie", query["type"])
            assertEquals("top", query["catalog"])
            assertEquals("2", query["addon_id"])
            assertEquals("40", query["skip"])
            assertEquals("space opera", query["search"])
            assertEquals("Science Fiction", query["genre"])
            assertEquals("en", JSONObject(query.getValue("extras")).getString("language"))
            assertEquals("2025", JSONObject(query.getValue("extras")).getString("year"))
            FixtureResponse("""{"metas":[{"id":"movie-41","type":"movie","name":"Page item"}],"has_more":true,"next_skip":64}""")
        }.use { server ->
            val catalog = DiscoverCatalog(
                key = CatalogKey(addonId = "2", type = "movie", id = "top"),
                name = "Second Top",
                supportsSearch = true,
                supportsSkip = true,
                filters = emptyList(),
            )
            val page = VipTvHttpGateway(server.origin).discover(
                CatalogDiscoverRequest(
                    catalog = catalog,
                    skip = 40,
                    search = "space opera",
                    genre = "Science Fiction",
                    extras = mapOf("year" to "2025", "language" to "en"),
                ),
            )

            assertEquals(catalog.key, page.catalog)
            assertEquals(40, page.requestedSkip)
            assertTrue(page.hasMore)
            assertEquals(64, page.nextSkip)
            assertEquals(listOf("movie-41"), page.items.map(Media::id))
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
        FixtureServer(2) { request ->
            when (request.target) {
                "/api/addons" -> FixtureResponse("""{"id":4,"name":"Installed","manifest_url":"https://addons.example.test/manifest.json","enabled":true}""")
                "/api/status" -> FixtureResponse("""{"providers":3,"addons":4,"profiles":8,"active_sessions":2,"ffmpeg_available":true,"video_acceleration":{"backend":"vaapi"}}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val addon = gateway.addAddon("https://addons.example.test/manifest.json")
            val about = gateway.serverAbout()

            assertEquals("POST", server.requests[0].method)
            assertEquals("https://addons.example.test/manifest.json", JSONObject(server.requests[0].body).getString("manifest_url"))
            assertEquals(Addon("4", "Installed", "https://addons.example.test/manifest.json", true), addon)
            assertTrue(about.mediaServiceAvailable)
            server.assertHealthy()
        }
    }

    @Test
    fun `search keeps successful labelled catalogs and live results when one catalog fails`() = runBlocking {
        FixtureServer(4) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse("""[
                    {"id":"movies","name":"Movie catalog","type":"movie","addon_id":2,"supports_search":true},
                    {"id":"series","name":"Broken catalog","type":"series","addon_id":3,"supports_search":true},
                    {"id":"hidden","name":"Hidden","type":"movie","addon_id":4,"supports_search":false}
                ]""".trimIndent())
                request.target.startsWith("/api/discover?type=movie") -> FixtureResponse("""{"metas":[
                    {"id":"movie-1","type":"movie","name":"Movie one"},
                    {"id":"movie-1","type":"movie","name":"Duplicate"},
                    {"id":"movie-2","type":"movie","name":"Movie two"}
                ]}""")
                request.target.startsWith("/api/discover?type=series") -> FixtureResponse("""{"error":"provider failed"}""", 502)
                request.target.startsWith("/api/live?") -> FixtureResponse("""{"channels":[{"id":"live-1","name":"Live one"}],"total":1}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val results = VipTvHttpGateway(server.origin).search("space opera")

            assertTrue(results.partialFailure)
            assertEquals(listOf("Movie catalog", "Live TV"), results.sections.map(SearchSection::source))
            assertEquals(listOf("movie-1", "movie-2"), results.sections[0].items.map(Media::id))
            assertEquals(listOf("live-1"), results.sections[1].items.map(Media::id))
            assertEquals(4, server.requests.size)
            server.assertHealthy()
        }
    }

    @Test
    fun `catalog lookup failure retains live results and signals partial search`() = runBlocking {
        FixtureServer(2) { request ->
            when {
                request.target == "/api/catalogs" -> FixtureResponse("""{"error":"catalog index unavailable"}""", 503)
                request.target.startsWith("/api/live?") -> FixtureResponse("""{"channels":[{"id":"live-2","name":"Still live"}],"total":1}""")
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val results = VipTvHttpGateway(server.origin).search("news")

            assertTrue(results.partialFailure)
            assertEquals(listOf("Live TV"), results.sections.map(SearchSection::source))
            assertEquals("live-2", results.sections.single().items.single().id)
            server.assertHealthy()
        }
    }

    @Test
    fun `live guide browse uses canonical US filters categories and forty channel pages`() = runBlocking {
        FixtureServer(6) { request ->
            val query = queryParameters(request.target)
            when {
                request.target.startsWith("/api/live/categories?") -> {
                    assertEquals("us", query["view"])
                    FixtureResponse("""{"total":2,"categories":[
                        {"id":"section:News","name":"News","count":18},
                        {"id":"section:Sports","name":"Sports","count":9}
                    ]}""")
                }
                request.target.startsWith("/api/live?") -> {
                    assertEquals("us", query["view"])
                    assertEquals("40", query["limit"])
                    when {
                        query["collection"] == "favorites" -> assertEquals("40", query["offset"])
                        query["collection"] == "recent" -> assertEquals("0", query["offset"])
                        query["category"] == "section:News" -> assertEquals("0", query["offset"])
                        query["search"] == "morning news" -> assertEquals("0", query["offset"])
                        else -> {
                            assertNull(query["collection"])
                            assertNull(query["category"])
                            assertNull(query["search"])
                            assertEquals("0", query["offset"])
                        }
                    }
                    FixtureResponse("""{
                        "channels":[{"id":"channel-1","name":"Fixture Channel","logo":"/logo.png","section":"News"}],
                        "total":81,
                        "search_scope":"US channels, sections and currently airing programmes with available guide data"
                    }""")
                }
                else -> error("Unexpected request ${request.target}")
            }
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin)
            val all = gateway.livePage(LiveBrowseRequest())
            val mine = gateway.livePage(LiveBrowseRequest(LiveChannelFilter.MyChannels, offset = 40))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Recent))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Category("section:News")))
            gateway.livePage(LiveBrowseRequest(LiveChannelFilter.Search("morning news")))
            val categories = gateway.liveCategories()

            assertEquals("News", all.channels.single().category)
            assertEquals("/logo.png", all.channels.single().logo)
            assertEquals(81, all.total)
            assertEquals(1, all.nextOffset)
            assertEquals(41, mine.nextOffset)
            assertEquals(
                listOf(LiveCategory("section:News", "News", 18), LiveCategory("section:Sports", "Sports", 9)),
                categories,
            )
            server.assertHealthy()
        }
    }

    @Test
    fun `addons consume the backend raw array response`() = runBlocking {
        FixtureServer(1) { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/addons", request.target)
            FixtureResponse("""[{"id":2,"name":"Fixture","manifest_url":"https://example.test/manifest.json","enabled":false}]""")
        }.use { server ->
            val addons = VipTvHttpGateway(server.origin).addons()
            assertEquals(listOf(Addon("2", "Fixture", "https://example.test/manifest.json", false)), addons)
            server.assertHealthy()
        }
    }
}

private data class FixtureRequest(val method: String, val target: String, val body: String)
private data class FixtureResponse(val body: String, val status: Int = 200)

private fun queryParameters(target: String): Map<String, String> = target
    .substringAfter('?', "")
    .split('&')
    .filter(String::isNotBlank)
    .associate { pair ->
        val (name, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

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
