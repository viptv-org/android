package org.viptv.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmInline

data class OpenedMedia(
    val timeline: PlaybackTimeline,
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val videoTracks: List<VideoTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val selectedVideoTrackId: String? = null,
    val playWhenReady: Boolean? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val statistics: PlaybackStatistics = PlaybackStatistics(),
    val positionMillis: Long = 0,
    val bufferedPositionMillis: Long? = null,
)

@JvmInline
value class PlaybackSessionId(val value: Long)

sealed interface BackendEvent {
    val sessionId: PlaybackSessionId

    data class FirstFrame(override val sessionId: PlaybackSessionId) : BackendEvent

    data class PlaybackChanged(
        override val sessionId: PlaybackSessionId,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
    ) : BackendEvent
    data class BufferingChanged(
        override val sessionId: PlaybackSessionId,
        val isBuffering: Boolean,
        val bufferedPositionMillis: Long? = null,
    ) : BackendEvent
    data class PositionChanged(
        override val sessionId: PlaybackSessionId,
        val positionMillis: Long,
    ) : BackendEvent
    data class TimelineChanged(
        override val sessionId: PlaybackSessionId,
        val timeline: PlaybackTimeline,
    ) : BackendEvent
    data class TracksChanged(
        override val sessionId: PlaybackSessionId,
        val audio: List<AudioTrack>,
        val subtitles: List<SubtitleTrack>,
        val video: List<VideoTrack>,
        val selectedAudioTrackId: String?,
        val selectedSubtitleTrackId: String?,
        val selectedVideoTrackId: String?,
    ) : BackendEvent
    data class StatisticsChanged(
        override val sessionId: PlaybackSessionId,
        val statistics: PlaybackStatistics,
    ) : BackendEvent
    /** The complete set of text cues active now for the selected subtitle track. */
    data class CuesChanged(
        override val sessionId: PlaybackSessionId,
        val cues: List<SubtitleCue>,
    ) : BackendEvent
    data class SeekFinished(
        override val sessionId: PlaybackSessionId,
        val positionMillis: Long,
    ) : BackendEvent
    data class PlaybackEnded(override val sessionId: PlaybackSessionId) : BackendEvent
    data class Failed(
        override val sessionId: PlaybackSessionId,
        val error: PlaybackError,
    ) : BackendEvent
}

interface VideoBackend : AutoCloseable {
    val capabilities: PlayerCapabilities
    val events: Flow<BackendEvent>

    suspend fun open(
        sessionId: PlaybackSessionId,
        source: PlaybackSource,
        playWhenReady: Boolean,
    ): OpenedMedia
    fun play()
    fun pause()
    fun seekTo(positionMillis: Long)
    fun selectAudioTrack(id: String?): TrackSelectionResult
    fun selectSubtitleTrack(id: String?): TrackSelectionResult
    fun selectVideoTrack(id: String?): TrackSelectionResult
    fun stop()
    override fun close()
}

class DefaultVideoPlayer(
    private val backend: VideoBackend,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VideoPlayer {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val openMutex = Mutex()
    private val _state = MutableStateFlow(PlaybackState())
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    private val _capabilities = MutableStateFlow(backend.capabilities)
    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    private val _statistics = MutableStateFlow(PlaybackStatistics())
    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    private var released = false
    private var nextSessionValue = 0L
    private var activeSessionId: PlaybackSessionId? = null
    private var pendingOpen: Job? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()
    override val capabilities: StateFlow<PlayerCapabilities> = _capabilities.asStateFlow()
    override val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()
    override val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()
    override val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()
    override val statistics: StateFlow<PlaybackStatistics> = _statistics.asStateFlow()
    override val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    init {
        scope.launch { backend.events.collect(::applyBackendEvent) }
    }

    /**
     * Opens one session. Opens are serialized. The pending open is cancelled when the caller is
     * cancelled or when [stop]/[close] ends its session; a cancelled or superseded open never
     * publishes Ready, Error or [PlaybackEvent.Failed]. A caller-cancelled open returns to Idle.
     */
    override suspend fun open(source: PlaybackSource, playWhenReady: Boolean) = openMutex.withLock {
        checkNotReleased()
        // Only the dispatcher is borrowed: the open stays a child of the caller so caller
        // cancellation reaches the backend instead of leaving it waiting for its own timeout.
        withContext(dispatcher) {
            checkNotReleased()
            val openJob = coroutineContext.job
            val sessionId = PlaybackSessionId(++nextSessionValue)
            activeSessionId = sessionId
            pendingOpen = openJob
            _state.value = PlaybackState(
                status = PlaybackStatus.Opening,
                playWhenReady = playWhenReady,
                isBuffering = true,
            )
            _statistics.value = PlaybackStatistics()
            _subtitleCues.value = emptyList()
            try {
                val opened = backend.open(sessionId, source, playWhenReady)
                coroutineContext.ensureActive()
                if (!isCurrent(sessionId)) throw CancellationException("Playback session ended before it opened")
                _audioTracks.value = opened.audioTracks
                _subtitleTracks.value = opened.subtitleTracks
                _videoTracks.value = opened.videoTracks
                _statistics.value = opened.statistics
                _state.value = PlaybackState(
                    status = PlaybackStatus.Ready,
                    playWhenReady = opened.playWhenReady ?: playWhenReady,
                    isPlaying = opened.isPlaying,
                    isBuffering = opened.isBuffering,
                    positionMillis = opened.positionMillis.coerceAtLeast(0),
                    bufferedPositionMillis = opened.bufferedPositionMillis,
                    timeline = opened.timeline,
                    selectedAudioTrackId = opened.selectedAudioTrackId,
                    selectedSubtitleTrackId = opened.selectedSubtitleTrackId,
                    selectedVideoTrackId = opened.selectedVideoTrackId,
                )
            } catch (error: CancellationException) {
                if (isCurrent(sessionId)) {
                    activeSessionId = null
                    backend.stop()
                    clearMedia(PlaybackStatus.Idle)
                }
                throw error
            } catch (error: Throwable) {
                // A stopped, replaced or released session reports only cancellation.
                if (!isCurrent(sessionId)) throw CancellationException("Playback session ended before it opened", error)
                val playbackError = (error as? PlaybackFailure)?.error ?: PlaybackError(
                    code = PlaybackErrorCode.Internal,
                    message = "Playback source could not be opened",
                    recoverable = false,
                )
                _state.value = PlaybackState(status = PlaybackStatus.Error, error = playbackError)
                _events.tryEmit(PlaybackEvent.Failed(playbackError))
                throw error
            } finally {
                if (pendingOpen === openJob) pendingOpen = null
            }
        }
    }

    override fun play() {
        if (!canControl()) return
        backend.play()
        _state.update { it.copy(playWhenReady = true) }
    }

    override fun pause() {
        if (!canControl()) return
        backend.pause()
        _state.update { it.copy(playWhenReady = false) }
    }

    override fun seekTo(positionMillis: Long): Boolean {
        if (!canControl() || state.value.timeline?.canSeek != true) return false
        val timeline = state.value.timeline
        val target = when {
            timeline?.seekableRange != null -> positionMillis.coerceIn(
                timeline.seekableRange.startMillis,
                timeline.seekableRange.endMillis,
            )
            timeline?.durationMillis != null -> positionMillis.coerceIn(0, timeline.durationMillis)
            else -> return false
        }
        backend.seekTo(target)
        _state.update { it.copy(positionMillis = target) }
        return true
    }

    override fun selectAudioTrack(id: String?): TrackSelectionResult = selectTrack(
        id = id,
        supported = capabilities.value.supportsAudioTrackSelection,
        available = audioTracks.value.map(AudioTrack::id),
        backendSelection = backend::selectAudioTrack,
        update = { selected -> _state.update { it.copy(selectedAudioTrackId = selected) } },
    )

    override fun selectSubtitleTrack(id: String?): TrackSelectionResult = selectTrack(
        id = id,
        supported = capabilities.value.supportsSubtitleTrackSelection,
        available = subtitleTracks.value.map(SubtitleTrack::id),
        backendSelection = backend::selectSubtitleTrack,
        update = { selected ->
            // Off always clears current cues; a new track's cues arrive from the backend.
            if (selected != _state.value.selectedSubtitleTrackId) _subtitleCues.value = emptyList()
            _state.update { it.copy(selectedSubtitleTrackId = selected) }
        },
    )

    override fun selectVideoTrack(id: String?): TrackSelectionResult = selectTrack(
        id = id,
        supported = capabilities.value.supportsVideoTrackSelection,
        available = videoTracks.value.map(VideoTrack::id),
        backendSelection = backend::selectVideoTrack,
        update = { selected -> _state.update { it.copy(selectedVideoTrackId = selected) } },
    )

    override fun stop() {
        if (released) return
        activeSessionId = null
        pendingOpen?.cancel()
        backend.stop()
        clearMedia(PlaybackStatus.Idle)
    }

    override fun close() {
        if (released) return
        released = true
        activeSessionId = null
        pendingOpen?.cancel()
        backend.close()
        clearMedia(PlaybackStatus.Released)
        scope.cancel()
    }

    private fun selectTrack(
        id: String?,
        supported: Boolean,
        available: List<String>,
        backendSelection: (String?) -> TrackSelectionResult,
        update: (String?) -> Unit,
    ): TrackSelectionResult {
        if (!canControl() || !supported) return TrackSelectionResult.NotSupported
        if (id != null && id !in available) return TrackSelectionResult.NotFound(id)
        return backendSelection(id).also { result ->
            when (result) {
                is TrackSelectionResult.Selected -> update(result.trackId)
                TrackSelectionResult.Disabled -> update(null)
                is TrackSelectionResult.Requested,
                is TrackSelectionResult.NotFound,
                TrackSelectionResult.NotSupported,
                -> Unit
            }
        }
    }

    private fun applyBackendEvent(event: BackendEvent) {
        if (released || event.sessionId != activeSessionId) return
        when (event) {
            is BackendEvent.FirstFrame -> _events.tryEmit(PlaybackEvent.FirstFrame)
            is BackendEvent.PlaybackChanged -> _state.update {
                it.copy(isPlaying = event.isPlaying, playWhenReady = event.playWhenReady)
            }
            is BackendEvent.BufferingChanged -> _state.update {
                it.copy(
                    isBuffering = event.isBuffering,
                    bufferedPositionMillis = event.bufferedPositionMillis,
                )
            }
            is BackendEvent.PositionChanged -> _state.update {
                it.copy(positionMillis = event.positionMillis.coerceAtLeast(0))
            }
            is BackendEvent.TimelineChanged -> _state.update { it.copy(timeline = event.timeline) }
            is BackendEvent.TracksChanged -> {
                _audioTracks.value = event.audio
                _subtitleTracks.value = event.subtitles
                _videoTracks.value = event.video
                if (event.selectedSubtitleTrackId == null) _subtitleCues.value = emptyList()
                _state.update {
                    it.copy(
                        selectedAudioTrackId = event.selectedAudioTrackId,
                        selectedSubtitleTrackId = event.selectedSubtitleTrackId,
                        selectedVideoTrackId = event.selectedVideoTrackId,
                    )
                }
            }
            is BackendEvent.StatisticsChanged -> _statistics.value = event.statistics
            is BackendEvent.CuesChanged -> _subtitleCues.value = event.cues
            is BackendEvent.SeekFinished -> {
                _state.update { it.copy(positionMillis = event.positionMillis.coerceAtLeast(0)) }
                _events.tryEmit(PlaybackEvent.SeekCompleted(event.positionMillis))
            }
            is BackendEvent.PlaybackEnded -> {
                _state.update { it.copy(status = PlaybackStatus.Ended, isPlaying = false, playWhenReady = false) }
                _events.tryEmit(PlaybackEvent.Ended)
            }
            is BackendEvent.Failed -> {
                _state.update { it.copy(status = PlaybackStatus.Error, isPlaying = false, error = event.error) }
                _events.tryEmit(PlaybackEvent.Failed(event.error))
            }
        }
    }

    private fun clearMedia(status: PlaybackStatus) {
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _statistics.value = PlaybackStatistics()
        _subtitleCues.value = emptyList()
        _state.value = PlaybackState(status = status)
    }

    private fun canControl(): Boolean = !released && state.value.status in setOf(PlaybackStatus.Ready, PlaybackStatus.Ended)

    private fun isCurrent(sessionId: PlaybackSessionId): Boolean = !released && activeSessionId == sessionId

    private fun checkNotReleased() {
        check(!released) { "VideoPlayer has been released" }
    }
}

class PlaybackFailure(val error: PlaybackError) : IllegalStateException(error.message)
