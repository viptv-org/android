package com.getair.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MpvHeadlessBackendTest {
    @Test
    fun mapsTracksTimelineAndSelectionThroughTheAirContract() = runBlocking {
        val client = FakeMpvClient()
        val player = DefaultVideoPlayer(
            MpvSessionBackend(clientFactory = { client }),
            Dispatchers.IO,
        )
        player.open(
            PlaybackSource(
                uri = "https://media.invalid/movie.mkv",
                headers = mapOf("Authorization" to "redacted-fixture"),
                kindHint = PlaybackKind.OnDemand,
            ),
            playWhenReady = false,
        )

        assertEquals(PlaybackKind.OnDemand, player.state.value.timeline?.kind)
        assertEquals(60_000, player.state.value.timeline?.durationMillis)
        assertEquals(2, player.audioTracks.value.size)
        assertEquals(2, player.subtitleTracks.value.size)
        assertEquals(1, player.videoTracks.value.size)
        assertTrue(player.seekTo(70_000))
        assertIs<TrackSelectionResult.Requested>(player.selectAudioTrack(player.audioTracks.value.last().id))
        assertIs<TrackSelectionResult.Requested>(player.selectSubtitleTrack(player.subtitleTracks.value.last().id))

        withTimeout(2_000) {
            while (client.commands.none { it.commandName() == "seek" }) delay(10)
            while (client.commands.count { it.commandName() == "set_property" } < 4) delay(10)
        }
        assertTrue(client.commands.toString().contains("http-header-fields"))
        assertFalse(client.commands.toString().contains("media.invalid"))
        player.close()
    }

    @Test
    fun providerLiveHintAlwaysSuppressesSeeking() = runBlocking {
        val client = FakeMpvClient()
        val player = DefaultVideoPlayer(MpvSessionBackend({ client }), Dispatchers.IO)
        player.open(
            PlaybackSource("https://media.invalid/live.m3u8", kindHint = PlaybackKind.Live),
            playWhenReady = false,
        )

        assertEquals(PlaybackKind.Live, player.state.value.timeline?.kind)
        assertFalse(player.state.value.timeline?.showSeekBar == true)
        assertFalse(player.seekTo(1_000))
        player.close()
    }

    @Test
    fun realMpvOpensCorpusTracksAndLivePlaylistWhenEnabled() = runBlocking {
        if (System.getenv("AIR_MPV_INTEGRATION") != "1") return@runBlocking
        val corpus = Path.of(checkNotNull(System.getenv("AIR_VIDEO_CORPUS_DIR")))
        val player = MpvHeadlessBackendFactory().create()
        try {
            player.open(corpus.source("h264-multitrack.mkv", PlaybackKind.OnDemand, withExternalSubtitles = true), false)
            assertTrue(player.audioTracks.value.size >= 2)
            assertTrue(player.subtitleTracks.value.size >= 5)
            assertTrue(player.subtitleTracks.value.count(SubtitleTrack::external) >= 3)
            assertTrue(player.videoTracks.value.isNotEmpty())
            player.audioTracks.value.forEach {
                assertIs<TrackSelectionResult.Requested>(player.selectAudioTrack(it.id))
            }
            player.subtitleTracks.value.forEach {
                assertIs<TrackSelectionResult.Requested>(player.selectSubtitleTrack(it.id))
            }

            listOf(
                "hevc.mkv" to "hevc",
                "av1.mkv" to "av1",
            ).forEach { (path, codec) ->
                player.open(corpus.source(path, PlaybackKind.OnDemand), false)
                assertTrue(player.videoTracks.value.any { it.codec?.contains(codec, ignoreCase = true) == true })
            }

            player.open(corpus.source("live.ts", PlaybackKind.Live), false)
            assertFalse(player.state.value.timeline?.showSeekBar == true)

            player.open(corpus.source("hls/event.m3u8", PlaybackKind.SeekableLive), false)
            assertEquals(PlaybackKind.SeekableLive, player.state.value.timeline?.kind)
            assertTrue(player.state.value.timeline?.showSeekBar == true)

            player.open(corpus.source("hls/live.m3u8", PlaybackKind.Live), false)
            assertEquals(PlaybackKind.Live, player.state.value.timeline?.kind)
            assertFalse(player.state.value.timeline?.showSeekBar == true)
        } finally {
            player.close()
        }
    }

    private class FakeMpvClient : MpvCommandClient {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val eventFlow = MutableSharedFlow<JsonObject>(replay = 16, extraBufferCapacity = 32)
        val commands = CopyOnWriteArrayList<List<JsonElement>>()
        override val events = eventFlow

        override suspend fun command(arguments: List<JsonElement>): JsonElement? {
            commands += arguments.redacted()
            return when (arguments.commandName()) {
                "loadfile" -> {
                    scope.launch { eventFlow.emit(buildJsonObject { put("event", "file-loaded") }) }
                    null
                }
                "get_property" -> property(arguments.getOrNull(1)?.jsonPrimitive?.contentOrNull)
                else -> null
            }
        }

        private fun property(name: String?): JsonElement? = when (name) {
            "pause", "paused-for-cache" -> JsonPrimitive(false)
            "time-pos" -> JsonPrimitive(0.0)
            "duration", "demuxer-cache-time" -> JsonPrimitive(60.0)
            "seekable" -> JsonPrimitive(true)
            "track-list" -> tracks
            else -> JsonNull
        }

        override fun close() = scope.cancel()

        private fun List<JsonElement>.redacted(): List<JsonElement> = mapIndexed { index, element ->
            if (commandName() == "loadfile" && index == 1) JsonPrimitive("<redacted>")
            else if (commandName() == "set_property" && getOrNull(1)?.jsonPrimitive?.contentOrNull == "http-header-fields" && index == 2) {
                JsonPrimitive("<redacted>")
            } else element
        }

        private val tracks = JsonArray(
            listOf(
                track(1, "video", "H.264", codec = "h264", selected = true),
                track(2, "audio", "English", "eng", "aac", selected = true),
                track(3, "audio", "Spanish", "spa", "aac"),
                track(4, "sub", "English SRT", "eng", "subrip", selected = true),
                track(5, "sub", "Spanish ASS", "spa", "ass"),
            ),
        )
    }
}

private fun Path.source(
    relativePath: String,
    kind: PlaybackKind,
    withExternalSubtitles: Boolean = false,
): PlaybackSource = PlaybackSource(
    uri = resolve(relativePath).toUri().toString(),
    kindHint = kind,
    externalSubtitles = if (!withExternalSubtitles) emptyList() else listOf(
        ExternalSubtitleSource("external-srt", resolve("external-en.srt").toUri().toString(), "application/x-subrip"),
        ExternalSubtitleSource("external-vtt", resolve("external-en.vtt").toUri().toString(), "text/vtt"),
        ExternalSubtitleSource("external-ass", resolve("external-es.ass").toUri().toString(), "text/x-ssa"),
    ),
)

private fun track(
    id: Int,
    type: String,
    title: String,
    language: String? = null,
    codec: String,
    selected: Boolean = false,
): JsonObject = buildJsonObject {
    put("id", id)
    put("type", type)
    put("title", title)
    language?.let { put("lang", it) }
    put("codec", codec)
    put("selected", selected)
    put("default", selected)
    if (type == "video") {
        put("demux-w", 1920)
        put("demux-h", 1080)
    }
}

private fun List<JsonElement>.commandName(): String? = firstOrNull()?.jsonPrimitive?.contentOrNull
