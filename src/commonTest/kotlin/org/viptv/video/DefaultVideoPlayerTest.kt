package org.viptv.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    fun readyKeepsObservedResumePositionAndBufferBeforeAnotherBackendTick() = runTest {
        val backend = FakeBackend(OpenedMedia(
            PlaybackTimeline(PlaybackKind.OnDemand, 300_000), positionMillis = 120_000,
            bufferedPositionMillis = 125_000, playWhenReady = true, isPlaying = true,
        ))
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mkv", startPositionMillis = 120_000))
        assertEquals(120_000L, player.state.value.positionMillis)
        assertEquals(125_000L, player.state.value.bufferedPositionMillis)
        assertTrue(player.state.value.isPlaying)
        player.close()
    }

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
    fun rollingVodClampsWithinSessionCoordinateBounds() = runTest {
        val backend = FakeBackend(
            OpenedMedia(
                PlaybackTimeline(
                    kind = PlaybackKind.OnDemand,
                    durationMillis = 102_000,
                    seekableRange = SeekableRange(70_000, 102_000),
                ),
            ),
        )
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/rolling-vod.m3u8"))

        assertTrue(player.seekTo(100_000))
        assertEquals(100_000, backend.lastSeek)
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

    @Test
    fun backendTrackRefreshUpdatesNativeSelectionsAtomically() = runTest {
        val backend = FakeBackend(
            OpenedMedia(
                timeline = PlaybackTimeline(PlaybackKind.OnDemand, 10_000),
                audioTracks = listOf(AudioTrack("a1", "English")),
                selectedAudioTrackId = "a1",
            ),
        )
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mkv"))

        backend.eventsFlow.emit(
            BackendEvent.TracksChanged(
                sessionId = checkNotNull(backend.lastSessionId),
                audio = listOf(AudioTrack("a1", "English"), AudioTrack("a2", "Spanish")),
                subtitles = listOf(SubtitleTrack("s1", "English")),
                video = listOf(VideoTrack("v1", "1080p")),
                selectedAudioTrackId = "a2",
                selectedSubtitleTrackId = "s1",
                selectedVideoTrackId = "v1",
            ),
        )
        testScheduler.runCurrent()

        assertEquals("a2", player.state.value.selectedAudioTrackId)
        assertEquals("s1", player.state.value.selectedSubtitleTrackId)
        assertEquals("v1", player.state.value.selectedVideoTrackId)
        player.close()
    }

    @Test
    fun asynchronousTrackRequestWaitsForNativeConfirmation() = runTest {
        val backend = FakeBackend(
            opened = OpenedMedia(
                timeline = PlaybackTimeline(PlaybackKind.OnDemand, 10_000),
                audioTracks = listOf(AudioTrack("a1", "English"), AudioTrack("a2", "Spanish")),
                selectedAudioTrackId = "a1",
            ),
            requestedSelections = true,
        )
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mkv"))

        assertEquals(TrackSelectionResult.Requested("a2"), player.selectAudioTrack("a2"))
        assertEquals("a1", player.state.value.selectedAudioTrackId)

        backend.eventsFlow.emit(
            BackendEvent.TracksChanged(
                checkNotNull(backend.lastSessionId),
                audio = listOf(AudioTrack("a1", "English"), AudioTrack("a2", "Spanish")),
                subtitles = emptyList(),
                video = emptyList(),
                selectedAudioTrackId = "a2",
                selectedSubtitleTrackId = null,
                selectedVideoTrackId = null,
            ),
        )
        testScheduler.runCurrent()
        assertEquals("a2", player.state.value.selectedAudioTrackId)
        player.close()
    }

    @Test
    fun runtimeStatisticsAreSessionScopedAndResetOnStop() = runTest {
        val backend = FakeBackend(OpenedMedia(PlaybackTimeline(PlaybackKind.Live)))
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/first.m3u8"))
        val firstSession = checkNotNull(backend.lastSessionId)
        player.open(PlaybackSource("https://example.invalid/second.m3u8"))
        val secondSession = checkNotNull(backend.lastSessionId)

        backend.eventsFlow.emit(
            BackendEvent.StatisticsChanged(
                firstSession,
                PlaybackStatistics(liveEdgeOffsetMillis = 99_000, bufferedAheadMillis = 99_000),
            ),
        )
        backend.eventsFlow.emit(
            BackendEvent.StatisticsChanged(
                secondSession,
                PlaybackStatistics(
                    liveEdgeOffsetMillis = 10_000,
                    bufferedAheadMillis = 7_500,
                    rebufferCount = 1,
                ),
            ),
        )
        testScheduler.runCurrent()

        assertEquals(10_000, player.statistics.value.liveEdgeOffsetMillis)
        assertEquals(7_500, player.statistics.value.bufferedAheadMillis)
        assertEquals(1, player.statistics.value.rebufferCount)

        player.stop()
        assertEquals(PlaybackStatistics(), player.statistics.value)
        player.close()
    }

    @Test
    fun cancellingAPendingOpenCancelsTheBackendAndReturnsToIdleWithoutFailure() = runTest {
        val backend = GatedBackend()
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        val failures = mutableListOf<PlaybackEvent>()
        val collector = launch { player.events.collect { failures += it } }
        val open = launch { player.open(PlaybackSource("https://example.invalid/slow.m3u8")) }
        testScheduler.runCurrent()
        assertEquals(PlaybackStatus.Opening, player.state.value.status)

        open.cancel()
        testScheduler.runCurrent()

        assertTrue(backend.openCancelled)
        assertEquals(1, backend.stopCalls)
        assertEquals(PlaybackStatus.Idle, player.state.value.status)
        assertTrue(failures.isEmpty())

        backend.gate = null
        player.open(PlaybackSource("https://example.invalid/next.m3u8"))
        assertEquals(PlaybackStatus.Ready, player.state.value.status)
        collector.cancel()
        player.close()
    }

    @Test
    fun stopEndsAPendingOpenPromptlyAndItsLateFailureIsNeverPublished() = runTest {
        val backend = GatedBackend(failWhenCancelled = true)
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        val failures = mutableListOf<PlaybackEvent>()
        val collector = launch { player.events.collect { failures += it } }
        val open = async { runCatching { player.open(PlaybackSource("https://example.invalid/slow.m3u8")) } }
        testScheduler.runCurrent()

        player.stop()
        testScheduler.runCurrent()

        assertTrue(backend.openCancelled)
        assertIs<CancellationException>(open.await().exceptionOrNull())
        assertEquals(PlaybackStatus.Idle, player.state.value.status)
        assertTrue(failures.isEmpty())
        collector.cancel()
        player.close()
    }

    @Test
    fun closeDuringOpenStaysReleased() = runTest {
        val backend = GatedBackend()
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        val open = async { runCatching { player.open(PlaybackSource("https://example.invalid/slow.m3u8")) } }
        testScheduler.runCurrent()

        player.close()
        testScheduler.runCurrent()

        assertTrue(backend.openCancelled)
        assertIs<CancellationException>(open.await().exceptionOrNull())
        assertEquals(PlaybackStatus.Released, player.state.value.status)
        assertFalse(player.seekTo(0))
        assertEquals(TrackSelectionResult.NotSupported, player.selectAudioTrack(null))
    }

    @Test
    fun subtitleCuesFollowTheActiveSessionAndClearOnOffStopAndReplace() = runTest {
        val opened = OpenedMedia(
            timeline = PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = 60_000),
            subtitleTracks = listOf(SubtitleTrack("s1", "English"), SubtitleTrack("s2", "Spanish")),
            selectedSubtitleTrackId = "s1",
        )
        val backend = FakeBackend(opened)
        val player = DefaultVideoPlayer(backend, StandardTestDispatcher(testScheduler))
        player.open(PlaybackSource("https://example.invalid/movie.mkv"))
        testScheduler.runCurrent()
        val first = checkNotNull(backend.lastSessionId)
        val cue = SubtitleCue("Hello", line = 0.9f)

        backend.eventsFlow.emit(BackendEvent.CuesChanged(first, listOf(cue)))
        testScheduler.runCurrent()
        assertEquals(listOf(cue), player.subtitleCues.value)

        // Switching track drops the old track's cue until the new one reports.
        assertIs<TrackSelectionResult.Selected>(player.selectSubtitleTrack("s2"))
        assertEquals(emptyList(), player.subtitleCues.value)
        backend.eventsFlow.emit(BackendEvent.CuesChanged(first, listOf(cue)))
        testScheduler.runCurrent()
        assertEquals(TrackSelectionResult.Disabled, player.selectSubtitleTrack(null))
        assertEquals(emptyList(), player.subtitleCues.value)

        // A native track change that deselects text also clears.
        backend.eventsFlow.emit(BackendEvent.CuesChanged(first, listOf(cue)))
        backend.eventsFlow.emit(BackendEvent.TracksChanged(first, emptyList(), opened.subtitleTracks, emptyList(), null, null, null))
        testScheduler.runCurrent()
        assertEquals(emptyList(), player.subtitleCues.value)

        // Replacing the session ignores late cues from the old one.
        backend.eventsFlow.emit(BackendEvent.CuesChanged(first, listOf(cue)))
        testScheduler.runCurrent()
        player.open(PlaybackSource("https://example.invalid/next.mkv"))
        testScheduler.runCurrent()
        assertEquals(emptyList(), player.subtitleCues.value)
        backend.eventsFlow.emit(BackendEvent.CuesChanged(first, listOf(SubtitleCue("stale"))))
        testScheduler.runCurrent()
        assertEquals(emptyList(), player.subtitleCues.value)

        val second = checkNotNull(backend.lastSessionId)
        backend.eventsFlow.emit(BackendEvent.CuesChanged(second, listOf(cue)))
        testScheduler.runCurrent()
        assertEquals(listOf(cue), player.subtitleCues.value)
        player.stop()
        assertEquals(emptyList(), player.subtitleCues.value)
        player.close()
    }

    @Test
    fun subtitleCueRejectsPositionsOutsideTheViewport() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { SubtitleCue("x", line = 1.5f) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { SubtitleCue(" ") }
    }

    private class GatedBackend(
        private val failWhenCancelled: Boolean = false,
    ) : VideoBackend {
        override val capabilities = PlayerCapabilities()
        override val events: Flow<BackendEvent> = MutableSharedFlow()
        var gate: CompletableDeferred<Unit>? = CompletableDeferred()
        var openCancelled = false
        var stopCalls = 0

        override suspend fun open(
            sessionId: PlaybackSessionId,
            source: PlaybackSource,
            playWhenReady: Boolean,
        ): OpenedMedia {
            gate?.let { pending ->
                try {
                    pending.await()
                } catch (error: CancellationException) {
                    openCancelled = true
                    if (failWhenCancelled) {
                        throw PlaybackFailure(PlaybackError(PlaybackErrorCode.Network, "late", recoverable = true))
                    }
                    throw error
                }
            }
            return OpenedMedia(PlaybackTimeline(PlaybackKind.Live))
        }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMillis: Long) = Unit
        override fun selectAudioTrack(id: String?): TrackSelectionResult = TrackSelectionResult.NotSupported
        override fun selectSubtitleTrack(id: String?): TrackSelectionResult = TrackSelectionResult.NotSupported
        override fun selectVideoTrack(id: String?): TrackSelectionResult = TrackSelectionResult.NotSupported
        override fun stop() { stopCalls += 1 }
        override fun close() = Unit
    }

    private class FakeBackend(
        private val opened: OpenedMedia,
        override val capabilities: PlayerCapabilities = PlayerCapabilities(
            supportsAudioTrackSelection = true,
            supportsSubtitleTrackSelection = true,
            supportsVideoTrackSelection = true,
        ),
        private val requestedSelections: Boolean = false,
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
            if (requestedSelections) TrackSelectionResult.Requested(id)
            else id?.let(TrackSelectionResult::Selected) ?: TrackSelectionResult.Disabled
    }
}
