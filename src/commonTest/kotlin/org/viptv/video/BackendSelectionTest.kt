package org.viptv.video

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackendSelectionTest {
    @Test
    fun choosesFirstCompatibleRuntimeBackend() = runTest {
        val platform = FakeFactory(
            "media3",
            PlayerCapabilities(
                containers = setOf("mp4", "mkv"),
                videoCodecs = setOf("h264", "hevc"),
                audioCodecs = setOf("aac"),
                subtitleFormats = setOf("vtt"),
                adaptiveProtocols = setOf("hls", "dash"),
                hardwareAcceleratedVideoCodecs = setOf("h264", "hevc"),
                supportsLive = true,
                supportsAudioTrackSelection = true,
                supportsSubtitleTrackSelection = true,
                hardwareAcceleration = HardwareAcceleration.DecodeAndRender,
                supportsMovableSurface = true,
                supportsSurfaceReattachment = true,
                supportsCompositedOverlays = true,
            ),
        )
        val fallback = FakeFactory(
            "mpv",
            PlayerCapabilities(
                containers = setOf("mkv"),
                videoCodecs = setOf("h264", "hevc", "av1"),
                audioCodecs = setOf("aac", "dts"),
                subtitleFormats = setOf("ass", "srt", "vtt"),
                adaptiveProtocols = setOf("hls"),
                hardwareAcceleratedVideoCodecs = setOf("h264", "hevc", "av1"),
                supportsLive = true,
                supportsAudioTrackSelection = true,
                supportsSubtitleTrackSelection = true,
                supportsVideoTrackSelection = true,
                supportsExternalSubtitles = true,
                hardwareAcceleration = HardwareAcceleration.DecodeAndRender,
                supportsMovableSurface = true,
                supportsSurfaceReattachment = true,
                supportsCompositedOverlays = true,
            ),
        )
        val router = VideoBackendRouter(listOf(platform, fallback))

        val ordinary = assertIs<BackendSelection.Selected>(
            router.select(PlaybackRequirements(container = "MKV", videoCodec = "hevc", live = true)),
        )
        assertEquals("media3", ordinary.factory.id)
        assertEquals(1, platform.probeCount)

        val advanced = assertIs<BackendSelection.Selected>(
            router.select(
                PlaybackRequirements(
                    container = "mkv",
                    videoCodec = "av1",
                    audioCodec = "dts",
                    subtitleFormat = "ass",
                    videoTrackSelection = true,
                    hardwareAcceleration = true,
                    movableSurface = true,
                    compositedOverlays = true,
                ),
            ),
        )
        assertEquals("mpv", advanced.factory.id)
        assertTrue(BackendRejection.VideoCodec in advanced.candidates.first().rejections)
        assertEquals(1, platform.probeCount)
        router.select(refreshProbes = true)
        assertEquals(2, platform.probeCount)
    }

    @Test
    fun reportsProbeAndCapabilityFailuresWithoutLeakingExceptions() = runTest {
        val router = VideoBackendRouter(
            listOf(
                FakeFactory("broken", failure = IllegalStateException("credential-bearing backend detail")),
                FakeFactory("web", PlayerCapabilities(containers = setOf("mp4"))),
            ),
        )

        val result = assertIs<BackendSelection.Unavailable>(
            router.select(PlaybackRequirements(container = "mkv", live = true)),
        )
        assertEquals(setOf(BackendRejection.ProbeFailed), result.candidates[0].rejections)
        assertEquals(setOf(BackendRejection.Container, BackendRejection.Live), result.candidates[1].rejections)
        assertTrue("credential-bearing" !in result.toString())
    }

    private class FakeFactory(
        override val id: String,
        private val result: PlayerCapabilities? = null,
        private val failure: Throwable? = null,
    ) : VideoBackendFactory {
        var probeCount: Int = 0
            private set

        override suspend fun probe(): PlayerCapabilities {
            probeCount += 1
            return failure?.let { throw it } ?: checkNotNull(result)
        }
        override fun create(): VideoPlayer = object : VideoPlayer {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())
            override val events = emptyFlow<PlaybackEvent>()
            override val capabilities = kotlinx.coroutines.flow.MutableStateFlow(checkNotNull(result))
            override val audioTracks = kotlinx.coroutines.flow.MutableStateFlow(emptyList<AudioTrack>())
            override val subtitleTracks = kotlinx.coroutines.flow.MutableStateFlow(emptyList<SubtitleTrack>())
            override val videoTracks = kotlinx.coroutines.flow.MutableStateFlow(emptyList<VideoTrack>())
            override val statistics = kotlinx.coroutines.flow.MutableStateFlow(PlaybackStatistics())
            override suspend fun open(source: PlaybackSource, playWhenReady: Boolean) = Unit
            override fun play() = Unit
            override fun pause() = Unit
            override fun seekTo(positionMillis: Long) = false
            override fun selectAudioTrack(id: String?) = TrackSelectionResult.NotSupported
            override fun selectSubtitleTrack(id: String?) = TrackSelectionResult.NotSupported
            override fun selectVideoTrack(id: String?) = TrackSelectionResult.NotSupported
            override fun stop() = Unit
            override fun close() = Unit
        }
    }
}
