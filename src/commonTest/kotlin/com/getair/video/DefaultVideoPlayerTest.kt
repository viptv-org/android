package com.getair.video

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultVideoPlayerTest {
    @Test
    fun livePlaybackRejectsSeekAtTheCommonBoundary() = runTest {
        val backend = FakeBackend(OpenedMedia(PlaybackTimeline(PlaybackKind.Live)))
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/live.m3u8"))

        assertFalse(player.state.value.timeline?.showSeekBar == true)
        assertFalse(player.seekTo(10_000))
        assertEquals(0, backend.seekCalls)
        player.close()
    }

    @Test
    fun vodClampsSeekAndSelectsEveryTrackType() = runTest {
        val opened = OpenedMedia(
            timeline = PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = 60_000),
            audioTracks = listOf(AudioTrack("a1", "English"), AudioTrack("a2", "Spanish")),
            subtitleTracks = listOf(SubtitleTrack("s1", "English")),
            videoTracks = listOf(VideoTrack("v1", "1080p"), VideoTrack("v2", "4K")),
        )
        val backend = FakeBackend(opened)
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mkv"))

        assertTrue(player.seekTo(90_000))
        assertEquals(60_000, backend.lastSeek)
        assertIs<TrackSelectionResult.Selected>(player.selectAudioTrack("a2"))
        assertIs<TrackSelectionResult.Selected>(player.selectSubtitleTrack("s1"))
        assertIs<TrackSelectionResult.Selected>(player.selectVideoTrack("v2"))
        assertEquals("a2", player.state.value.selectedAudioTrackId)
        assertEquals("s1", player.state.value.selectedSubtitleTrackId)
        assertEquals("v2", player.state.value.selectedVideoTrackId)
        assertIs<TrackSelectionResult.NotFound>(player.selectAudioTrack("missing"))
        player.close()
    }

    @Test
    fun unsupportedSelectionAndBackendFailuresAreTyped() = runTest {
        val backend = FakeBackend(
            opened = OpenedMedia(PlaybackTimeline(PlaybackKind.OnDemand, 1_000)),
            capabilities = PlayerCapabilities(),
        )
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mp4"))

        assertEquals(TrackSelectionResult.NotSupported, player.selectAudioTrack(null))
        backend.eventsFlow.emit(
            BackendEvent.Failed(
                checkNotNull(backend.lastSessionId),
                PlaybackError(PlaybackErrorCode.Decode, "Decoder failed", recoverable = true),
            ),
        )
        testScheduler.runCurrent()
        assertEquals(PlaybackStatus.Error, player.state.value.status)
        assertEquals(PlaybackErrorCode.Decode, player.state.value.error?.code)
        player.close()
    }

    @Test
    fun ignoresLateEventsFromAReplacedPlaybackSession() = runTest {
        val backend = FakeBackend(OpenedMedia(PlaybackTimeline(PlaybackKind.OnDemand, 10_000)))
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/first.mkv"))
        val firstSession = checkNotNull(backend.lastSessionId)
        player.open(PlaybackSource("https://example.invalid/second.mkv"))
        val secondSession = checkNotNull(backend.lastSessionId)

        backend.eventsFlow.emit(BackendEvent.PositionChanged(firstSession, 9_000))
        backend.eventsFlow.emit(BackendEvent.PlaybackEnded(firstSession))
        backend.eventsFlow.emit(BackendEvent.PositionChanged(secondSession, 2_000))
        testScheduler.runCurrent()

        assertEquals(PlaybackStatus.Ready, player.state.value.status)
        assertEquals(2_000, player.state.value.positionMillis)
        player.close()
    }

    private class FakeBackend(
        private val opened: OpenedMedia,
        override val capabilities: PlayerCapabilities = PlayerCapabilities(
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            supportsVideoTrackSelection = true,
        ),
    ) : VideoBackend {
        val eventsFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 8)
        override val events: Flow<BackendEvent> = eventsFlow
        var seekCalls = 0
        var lastSeek: Long? = null
        var lastSessionId: PlaybackSessionId? = null

        override suspend fun open(
            sessionId: PlaybackSessionId,
            source: PlaybackSource,
            playWhenReady: Boolean,
        ): OpenedMedia {
            lastSessionId = sessionId
            return opened
        }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMillis: Long) { seekCalls += 1; lastSeek = positionMillis }
        override fun selectAudioTrack(id: String?): TrackSelectionResult = selection(id)
        override fun selectSubtitleTrack(id: String?): TrackSelectionResult = selection(id)
        override fun selectVideoTrack(id: String?): TrackSelectionResult = selection(id)
        override fun stop() = Unit
        override fun close() = Unit
        private fun selection(id: String?): TrackSelectionResult =
            id?.let(TrackSelectionResult::Selected) ?: TrackSelectionResult.Disabled
    }
}
