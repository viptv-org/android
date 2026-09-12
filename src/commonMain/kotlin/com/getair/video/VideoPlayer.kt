package com.getair.video

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class PlaybackSource(
    val uri: String,
    val mimeType: String? = null,
    headers: Map<String, String> = emptyMap(),
    val title: String? = null,
    externalSubtitles: List<ExternalSubtitleSource> = emptyList(),
    val kindHint: PlaybackKind? = null,
    val options: PlaybackOptions = PlaybackOptions(),
) {
    val headers: Map<String, String> = headers.toMap()
    val externalSubtitles: List<ExternalSubtitleSource> = externalSubtitles.toList()

    override fun toString(): String =
        "PlaybackSource(uri=<redacted>, mimeType=$mimeType, headers=<redacted>, title=$title, " +
            "externalSubtitles=${externalSubtitles.size}, kindHint=$kindHint, options=$options)"
}

/**
 * Live intent only. Backends translate this through their native live controls, and measured
 * [PlaybackStatistics] remain authoritative because a manifest or engine may constrain the result.
 */
enum class LivePlaybackPolicy { LowLatency, Balanced, Resilient }

data class PlaybackOptions(
    val livePolicy: LivePlaybackPolicy = LivePlaybackPolicy.Balanced,
)

data class ExternalSubtitleSource(
    val id: String,
    val uri: String,
    val mimeType: String,
    val language: String? = null,
    val label: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
) {
    override fun toString(): String =
        "ExternalSubtitleSource(id=$id, uri=<redacted>, mimeType=$mimeType, language=$language, label=$label)"
}

enum class PlaybackKind { OnDemand, Live, SeekableLive }

data class SeekableRange(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(startMillis >= 0)
        require(endMillis >= startMillis)
    }
}

data class PlaybackTimeline(
    val kind: PlaybackKind,
    val durationMillis: Long? = null,
    val seekableRange: SeekableRange? = null,
    val liveEdgeMillis: Long? = null,
) {
    init {
        require(durationMillis == null || durationMillis >= 0)
        require(kind != PlaybackKind.Live || seekableRange == null) {
            "Plain live playback cannot expose a seekable range"
        }
        require(kind != PlaybackKind.OnDemand || durationMillis != null) {
            "On-demand playback requires a duration"
        }
    }

    val canSeek: Boolean get() = kind != PlaybackKind.Live && (durationMillis != null || seekableRange != null)
    val showSeekBar: Boolean get() = canSeek
}

enum class PlaybackStatus { Idle, Opening, Ready, Ended, Error, Released }

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMillis: Long = 0,
    val bufferedPositionMillis: Long? = null,
    val timeline: PlaybackTimeline? = null,
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val selectedVideoTrackId: String? = null,
    val error: PlaybackError? = null,
)

/**
 * Low-frequency diagnostic facts kept outside [PlaybackState] so stats sampling cannot invalidate
 * controls or browsing UI. Live-edge offset and buffered-ahead media are deliberately independent.
 */
data class PlaybackStatistics(
    val liveEdgeOffsetMillis: Long? = null,
    val bufferedAheadMillis: Long? = null,
    val estimatedThroughputBitsPerSecond: Long? = null,
    val droppedVideoFrames: Long = 0,
    val rebufferCount: Long = 0,
    val behindLiveWindowRecoveryCount: Long = 0,
    val discontinuityCount: Long = 0,
    val playbackSpeed: Double = 1.0,
    /** Backend-neutral intent selected for the current potentially-live source. */
    val livePolicy: LivePlaybackPolicy? = null,
    /** Native target offset requested from the live edge, not a measured result. */
    val targetLiveOffsetMillis: Long? = null,
    /** Native minimum forward buffer target, not a guarantee that it was reached. */
    val minimumBufferMillis: Long? = null,
    /** Native maximum forward buffer target, not a hard byte or heap limit. */
    val maximumBufferMillis: Long? = null,
    /** Native allocator loading threshold. This is not an absolute heap cap. */
    val bufferMemoryThresholdBytes: Long? = null,
) {
    init {
        require(liveEdgeOffsetMillis == null || liveEdgeOffsetMillis >= 0)
        require(bufferedAheadMillis == null || bufferedAheadMillis >= 0)
        require(targetLiveOffsetMillis == null || targetLiveOffsetMillis >= 0)
        require(minimumBufferMillis == null || minimumBufferMillis >= 0)
        require(maximumBufferMillis == null || maximumBufferMillis >= 0)
        require(
            minimumBufferMillis == null || maximumBufferMillis == null ||
                maximumBufferMillis >= minimumBufferMillis,
        )
        require(bufferMemoryThresholdBytes == null || bufferMemoryThresholdBytes > 0)
        require(estimatedThroughputBitsPerSecond == null || estimatedThroughputBitsPerSecond >= 0)
        require(droppedVideoFrames >= 0)
        require(rebufferCount >= 0)
        require(behindLiveWindowRecoveryCount >= 0)
        require(discontinuityCount >= 0)
        require(playbackSpeed > 0 && playbackSpeed.isFinite())
    }
}

enum class TrackType { Audio, Subtitle, Video }

sealed interface MediaTrack {
    val id: String
    val label: String
    val language: String?
    val isDefault: Boolean
    val isForced: Boolean
}

data class AudioTrack(
    override val id: String,
    override val label: String,
    override val language: String? = null,
    override val isDefault: Boolean = false,
    override val isForced: Boolean = false,
    val channels: Int? = null,
    val codec: String? = null,
) : MediaTrack

data class SubtitleTrack(
    override val id: String,
    override val label: String,
    override val language: String? = null,
    override val isDefault: Boolean = false,
    override val isForced: Boolean = false,
    val format: String? = null,
    val external: Boolean = false,
) : MediaTrack

data class VideoTrack(
    override val id: String,
    override val label: String,
    override val language: String? = null,
    override val isDefault: Boolean = false,
    override val isForced: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val codec: String? = null,
) : MediaTrack

sealed interface TrackSelectionResult {
    data class Selected(val trackId: String) : TrackSelectionResult
    /** Backend accepted an asynchronous selection; state changes only after native confirmation. */
    data class Requested(val trackId: String?) : TrackSelectionResult
    data class NotFound(val trackId: String) : TrackSelectionResult
    data object Disabled : TrackSelectionResult
    data object NotSupported : TrackSelectionResult
}

data class PlayerCapabilities(
    val containers: Set<String> = emptySet(),
    val videoCodecs: Set<String> = emptySet(),
    val audioCodecs: Set<String> = emptySet(),
    val subtitleFormats: Set<String> = emptySet(),
    val adaptiveProtocols: Set<String> = emptySet(),
    val drmSchemes: Set<String> = emptySet(),
    val hardwareAcceleratedVideoCodecs: Set<String> = emptySet(),
    val supportsAudioTrackSelection: Boolean = false,
    val supportsSubtitleTrackSelection: Boolean = false,
    val supportsVideoTrackSelection: Boolean = false,
    val supportsExternalSubtitles: Boolean = false,
    val supportsLive: Boolean = false,
    val supportsSeekableLive: Boolean = false,
    val supportsPlaybackRate: Boolean = false,
    val supportsPictureInPicture: Boolean = false,
    val supportsHdr: Boolean = false,
    val supportsAudioPassthrough: Boolean = false,
    val supportsMovableSurface: Boolean = false,
    val supportsSurfaceReattachment: Boolean = false,
    val supportsCompositedOverlays: Boolean = false,
    val supportedLivePolicies: Set<LivePlaybackPolicy> = emptySet(),
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.Unknown,
    /** Measured decoder limits; null means this backend could not determine them. */
    val maxVideoWidth: Int? = null,
    val maxVideoHeight: Int? = null,
    /** A decoder explicitly advertised the SDR HEVC Main profile used by the server. */
    val supportsHevcSdr: Boolean = false,
)

enum class HardwareAcceleration { Unknown, None, Decode, DecodeAndRender }

enum class PlaybackErrorCode { Network, UnsupportedContainer, UnsupportedCodec, Decode, Source, Internal }

data class PlaybackError(
    val code: PlaybackErrorCode,
    val message: String,
    val recoverable: Boolean,
    val suggestedBackend: String? = null,
)

sealed interface PlaybackEvent {
    data object Ended : PlaybackEvent
    data class SeekCompleted(val positionMillis: Long) : PlaybackEvent
    data class Failed(val error: PlaybackError) : PlaybackEvent
}

interface VideoPlayer : AutoCloseable {
    val state: StateFlow<PlaybackState>
    val events: Flow<PlaybackEvent>
    val capabilities: StateFlow<PlayerCapabilities>
    val audioTracks: StateFlow<List<AudioTrack>>
    val subtitleTracks: StateFlow<List<SubtitleTrack>>
    val videoTracks: StateFlow<List<VideoTrack>>
    val statistics: StateFlow<PlaybackStatistics>

    suspend fun open(source: PlaybackSource, playWhenReady: Boolean = true)
    fun play()
    fun pause()
    fun seekTo(positionMillis: Long): Boolean
    fun selectAudioTrack(id: String?): TrackSelectionResult
    fun selectSubtitleTrack(id: String?): TrackSelectionResult
    fun selectVideoTrack(id: String?): TrackSelectionResult
    fun stop()
    override fun close()
}

interface VideoBackendFactory {
    val id: String
    suspend fun probe(): PlayerCapabilities
    fun create(): VideoPlayer
}
