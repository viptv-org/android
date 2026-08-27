package com.getair.video

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
)

@JvmInline
value class PlaybackSessionId(val value: Long)

sealed interface BackendEvent {
    val sessionId: PlaybackSessionId

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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VideoPlayer {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val openMutex = Mutex()
    private val _state = MutableStateFlow(PlaybackState())
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    private val _capabilities = MutableStateFlow(backend.capabilities)
    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    private var released = false
    private var nextSessionValue = 0L
    private var activeSessionId: PlaybackSessionId? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()
    override val capabilities: StateFlow<PlayerCapabilities> = _capabilities.asStateFlow()
    override val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()
    override val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()
    override val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()

    init {
        scope.launch { backend.events.collect(::applyBackendEvent) }
    }

    override suspend fun open(source: PlaybackSource, playWhenReady: Boolean) = openMutex.withLock {
        ensureActive()
        withContext(scope.coroutineContext) {
            val sessionId = PlaybackSessionId(++nextSessionValue)
            activeSessionId = sessionId
            _state.value = PlaybackState(
                status = PlaybackStatus.Opening,
                playWhenReady = playWhenReady,
                isBuffering = true,
            )
            try {
                val opened = backend.open(sessionId, source, playWhenReady)
                _audioTracks.value = opened.audioTracks
                _subtitleTracks.value = opened.subtitleTracks
                _videoTracks.value = opened.videoTracks
                _state.value = PlaybackState(
                    status = PlaybackStatus.Ready,
                    playWhenReady = playWhenReady,
                    isPlaying = false,
                    isBuffering = false,
                    timeline = opened.timeline,
                    selectedAudioTrackId = opened.selectedAudioTrackId,
                    selectedSubtitleTrackId = opened.selectedSubtitleTrackId,
                    selectedVideoTrackId = opened.selectedVideoTrackId,
                )
            } catch (error: Throwable) {
                val playbackError = (error as? PlaybackFailure)?.error ?: PlaybackError(
                    code = PlaybackErrorCode.Internal,
                    message = "Playback source could not be opened",
                    recoverable = false,
                )
                _state.value = PlaybackState(status = PlaybackStatus.Error, error = playbackError)
                _events.tryEmit(PlaybackEvent.Failed(playbackError))
                throw error
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
            timeline?.durationMillis != null -> positionMillis.coerceIn(0, timeline.durationMillis)
            timeline?.seekableRange != null -> positionMillis.coerceIn(
                timeline.seekableRange.startMillis,
                timeline.seekableRange.endMillis,
            )
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
        update = { selected -> _state.update { it.copy(selectedSubtitleTrackId = selected) } },
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
        backend.stop()
        clearMedia(PlaybackStatus.Idle)
    }

    override fun close() {
        if (released) return
        released = true
        activeSessionId = null
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
                is TrackSelectionResult.NotFound, TrackSelectionResult.NotSupported -> Unit
            }
        }
    }

    private fun applyBackendEvent(event: BackendEvent) {
        if (released || event.sessionId != activeSessionId) return
        when (event) {
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
            }
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
        _state.value = PlaybackState(status = status)
    }

    private fun canControl(): Boolean = !released && state.value.status in setOf(PlaybackStatus.Ready, PlaybackStatus.Ended)

    private fun ensureActive() {
        check(!released) { "VideoPlayer has been released" }
    }
}

class PlaybackFailure(val error: PlaybackError) : IllegalStateException(error.message)
