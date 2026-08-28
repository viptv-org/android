package com.getair.video

import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.w3c.dom.HTMLMediaElement
import org.w3c.dom.HTMLTrackElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.MediaError
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.math.roundToLong

class BrowserVideoBackendFactory : VideoBackendFactory {
    override val id: String = "html-video"

    override suspend fun probe(): PlayerCapabilities {
        val video = document.createElement("video") as HTMLVideoElement
        fun supports(mime: String): Boolean = video.canPlayType(mime).toString().isNotBlank()
        return PlayerCapabilities(
            containers = buildSet {
                if (supports("video/mp4")) add("mp4")
                if (supports("video/webm")) add("webm")
                if (supports("application/vnd.apple.mpegurl")) add("hls")
            },
            videoCodecs = buildSet {
                if (supports("video/mp4; codecs=avc1.42E01E")) add("h264")
                if (supports("video/mp4; codecs=hvc1")) add("hevc")
                if (supports("video/mp4; codecs=av01.0.05M.08")) add("av1")
                if (supports("video/webm; codecs=vp9")) add("vp9")
            },
            audioCodecs = buildSet {
                if (supports("video/mp4; codecs=mp4a.40.2")) add("aac")
                if (supports("video/webm; codecs=opus")) add("opus")
            },
            subtitleFormats = setOf("vtt"),
            adaptiveProtocols = buildSet { if (supports("application/vnd.apple.mpegurl")) add("hls") },
            supportsSubtitleTrackSelection = true,
            supportsExternalSubtitles = true,
            supportsLive = true,
            supportsSeekableLive = true,
            supportsPlaybackRate = true,
            supportsPictureInPicture = false,
            supportsMovableSurface = true,
            supportsSurfaceReattachment = true,
            supportsCompositedOverlays = true,
            hardwareAcceleration = HardwareAcceleration.Unknown,
        )
    }

    override fun create(): VideoPlayer = createBrowserPlayer()

    fun createBrowserPlayer(
        element: HTMLVideoElement = document.createElement("video") as HTMLVideoElement,
    ): BrowserVideoPlayer = BrowserVideoPlayer(element)
}

class BrowserVideoPlayer internal constructor(
    val videoElement: HTMLVideoElement,
) : VideoPlayer by DefaultVideoPlayer(BrowserVideoBackend(videoElement), Dispatchers.Main)

private class BrowserVideoBackend(
    private val video: HTMLVideoElement,
    private val openTimeoutMillis: Long = 20_000,
) : VideoBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 64)
    private var sessionId: PlaybackSessionId? = null
    private var source: PlaybackSource? = null
    private var sessionRemovers: List<() -> Unit> = emptyList()
    private var subtitleTracks: List<SubtitleTrack> = emptyList()
    private var released = false

    override val capabilities = PlayerCapabilities(
        subtitleFormats = setOf("vtt"),
        supportsSubtitleTrackSelection = true,
        supportsExternalSubtitles = true,
        supportsLive = true,
        supportsSeekableLive = true,
        supportsPlaybackRate = true,
        supportsPictureInPicture = false,
        supportsMovableSurface = true,
        supportsSurfaceReattachment = true,
        supportsCompositedOverlays = true,
        hardwareAcceleration = HardwareAcceleration.Unknown,
    )
    override val events: Flow<BackendEvent> = eventFlow

    init {
        video.preload = "metadata"
        video.controls = false
        video.playsInline = true
        video.style.width = "100%"
        video.style.height = "100%"
        video.style.objectFit = "contain"
    }

    override suspend fun open(
        sessionId: PlaybackSessionId,
        source: PlaybackSource,
        playWhenReady: Boolean,
    ): OpenedMedia {
        check(!released) { "Browser video player is closed" }
        if (source.headers.isNotEmpty()) {
            throw PlaybackFailure(
                PlaybackError(
                    PlaybackErrorCode.Source,
                    "Browser video elements cannot attach private request headers",
                    recoverable = true,
                ),
            )
        }
        detachSessionListeners()
        unload()
        this.sessionId = sessionId
        this.source = source
        installSubtitles(source)
        video.preload = "auto"
        video.src = source.uri
        return try {
            withTimeout(openTimeoutMillis) { awaitMetadata() }
            sessionRemovers = registerSessionListeners(sessionId)
            if (playWhenReady) startPlay(sessionId)
            OpenedMedia(
                timeline = timeline(),
                subtitleTracks = subtitleTracks,
                selectedSubtitleTrackId = subtitleTracks.firstOrNull { it.isDefault }?.id,
            )
        } catch (_: TimeoutCancellationException) {
            this.sessionId = null
            unload()
            throw PlaybackFailure(
                PlaybackError(PlaybackErrorCode.Network, "Browser timed out while opening media", true),
            )
        } catch (error: CancellationException) {
            this.sessionId = null
            unload()
            throw error
        }
    }

    private suspend fun awaitMetadata() = suspendCancellableCoroutine { continuation ->
        var removers: List<() -> Unit> = emptyList()
        fun cleanup() = removers.forEach { it() }
        removers = listOf(
            video.onEvent("loadedmetadata") {
                cleanup()
                if (continuation.isActive) continuation.resume(Unit)
            },
            video.onEvent("error") {
                cleanup()
                if (continuation.isActive) {
                    continuation.resumeWithException(PlaybackFailure(mediaError(video.error)))
                }
            },
        )
        video.load()
        continuation.invokeOnCancellation { cleanup() }
    }

    private fun registerSessionListeners(session: PlaybackSessionId): List<() -> Unit> = listOf(
        video.onEvent("play") { emitPlayback(session) },
        video.onEvent("playing") {
            emitPlayback(session)
            eventFlow.tryEmit(BackendEvent.BufferingChanged(session, false, bufferedPosition()))
        },
        video.onEvent("pause") { if (!video.ended) emitPlayback(session) },
        video.onEvent("waiting") {
            eventFlow.tryEmit(BackendEvent.BufferingChanged(session, true, bufferedPosition()))
        },
        video.onEvent("canplay") {
            eventFlow.tryEmit(BackendEvent.BufferingChanged(session, false, bufferedPosition()))
        },
        video.onEvent("timeupdate") {
            eventFlow.tryEmit(BackendEvent.PositionChanged(session, positionMillis()))
        },
        video.onEvent("durationchange") {
            eventFlow.tryEmit(BackendEvent.TimelineChanged(session, timeline()))
        },
        video.onEvent("seeked") {
            eventFlow.tryEmit(BackendEvent.SeekFinished(session, positionMillis()))
        },
        video.onEvent("ended") { eventFlow.tryEmit(BackendEvent.PlaybackEnded(session)) },
        video.onEvent("error") { eventFlow.tryEmit(BackendEvent.Failed(session, mediaError(video.error))) },
    )

    private fun emitPlayback(session: PlaybackSessionId) {
        eventFlow.tryEmit(BackendEvent.PlaybackChanged(session, !video.paused && !video.ended, !video.paused))
    }

    private fun startPlay(session: PlaybackSessionId) {
        video.play().catch<JsAny?> {
            eventFlow.tryEmit(BackendEvent.PlaybackChanged(session, false, false))
            null
        }
    }

    override fun play() { sessionId?.let(::startPlay) }
    override fun pause() = video.pause()
    override fun seekTo(positionMillis: Long) { video.currentTime = positionMillis.coerceAtLeast(0) / 1_000.0 }
    override fun selectAudioTrack(id: String?) = TrackSelectionResult.NotSupported
    override fun selectVideoTrack(id: String?) = TrackSelectionResult.NotSupported

    override fun selectSubtitleTrack(id: String?): TrackSelectionResult {
        val index = id?.removePrefix("browser-subtitle:")?.toIntOrNull()
        if (id != null && (index == null || index !in subtitleTracks.indices)) {
            return TrackSelectionResult.NotFound(id)
        }
        for (trackIndex in subtitleTracks.indices) {
            setTextTrackMode(video, trackIndex, if (trackIndex == index) "showing" else "disabled")
        }
        return id?.let(TrackSelectionResult::Selected) ?: TrackSelectionResult.Disabled
    }

    override fun stop() {
        sessionId = null
        source = null
        detachSessionListeners()
        unload()
    }

    override fun close() {
        if (released) return
        released = true
        stop()
        scope.cancel()
    }

    private fun installSubtitles(source: PlaybackSource) {
        clearTracks()
        subtitleTracks = source.externalSubtitles.mapIndexed { index, subtitle ->
            val track = document.createElement("track") as HTMLTrackElement
            track.kind = "subtitles"
            track.src = subtitle.uri
            subtitle.language?.let { track.srclang = it }
            subtitle.label?.let { track.label = it }
            track.default = subtitle.isDefault
            video.appendChild(track)
            SubtitleTrack(
                id = "browser-subtitle:$index",
                label = subtitle.label ?: subtitle.language ?: "Subtitles ${index + 1}",
                language = subtitle.language,
                isDefault = subtitle.isDefault,
                isForced = subtitle.isForced,
                format = subtitle.mimeType,
                external = true,
            )
        }
    }

    private fun clearTracks() {
        while (video.firstChild != null) video.removeChild(video.firstChild!!)
        subtitleTracks = emptyList()
    }

    private fun unload() {
        video.pause()
        video.removeAttribute("src")
        clearTracks()
        video.load()
    }

    private fun detachSessionListeners() {
        sessionRemovers.forEach { it() }
        sessionRemovers = emptyList()
    }

    private fun timeline(): PlaybackTimeline {
        val duration = video.duration.takeIf { it.isFinite() && it >= 0.0 }?.times(1_000)?.roundToLong()
        return when (source?.kindHint) {
            PlaybackKind.Live -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = duration)
            PlaybackKind.SeekableLive -> PlaybackTimeline(
                PlaybackKind.SeekableLive,
                seekableRange = seekableRange(duration),
                liveEdgeMillis = seekableRange(duration)?.endMillis ?: duration,
            )
            PlaybackKind.OnDemand -> PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = duration ?: 0)
            null -> if (duration == null) PlaybackTimeline(PlaybackKind.Live)
            else PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = duration)
        }
    }

    private fun seekableRange(fallbackEnd: Long?): SeekableRange? {
        val ranges = video.seekable
        if (ranges.length == 0) return fallbackEnd?.let { SeekableRange(0, it) }
        val last = ranges.length - 1
        return SeekableRange(
            (ranges.start(0) * 1_000).roundToLong().coerceAtLeast(0),
            (ranges.end(last) * 1_000).roundToLong().coerceAtLeast(0),
        )
    }

    private fun positionMillis(): Long = (video.currentTime * 1_000).roundToLong().coerceAtLeast(0)

    private fun bufferedPosition(): Long? {
        val ranges = video.buffered
        if (ranges.length == 0) return null
        return (ranges.end(ranges.length - 1) * 1_000).roundToLong().coerceAtLeast(0)
    }

    private fun mediaError(error: MediaError?): PlaybackError = when (error?.code) {
        MediaError.MEDIA_ERR_NETWORK -> PlaybackError(PlaybackErrorCode.Network, "Browser media network failure", true)
        MediaError.MEDIA_ERR_DECODE -> PlaybackError(PlaybackErrorCode.Decode, "Browser media decode failure", false)
        MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED -> PlaybackError(
            PlaybackErrorCode.UnsupportedContainer,
            "Browser does not support this media source",
            true,
        )
        else -> PlaybackError(PlaybackErrorCode.Internal, "Browser media playback failed", false)
    }
}

private fun setTextTrackMode(video: HTMLVideoElement, index: Int, mode: String) {
    js("video.textTracks[index].mode = mode")
}

private fun HTMLVideoElement.onEvent(type: String, handler: () -> Unit): () -> Unit {
    val listener: (Event) -> Unit = { handler() }
    addEventListener(type, listener)
    return { removeEventListener(type, listener) }
}
