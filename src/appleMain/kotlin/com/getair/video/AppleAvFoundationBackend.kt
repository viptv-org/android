@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.getair.video

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.AVFoundation.AVErrorDecodeFailed
import platform.AVFoundation.AVErrorDecoderNotFound
import platform.AVFoundation.AVErrorFileFormatNotRecognized
import platform.AVFoundation.AVErrorUndecodableMediaData
import platform.AVFoundation.AVFoundationErrorDomain
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeErrorKey
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.playbackBufferEmpty
import platform.AVFoundation.playbackLikelyToKeepUp
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSURLErrorDomain
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.roundToLong

/** Native Apple playback with an app-owned, freely movable AVPlayerLayer. */
class AppleAvFoundationBackendFactory(
    private val openTimeoutMillis: Long = 20_000,
) : VideoBackendFactory {
    init {
        require(openTimeoutMillis > 0)
    }

    override val id: String = "avfoundation"

    override suspend fun probe(): PlayerCapabilities = appleAvFoundationCapabilities()

    override fun create(): VideoPlayer = createApplePlayer()

    fun createApplePlayer(): AppleAvFoundationVideoPlayer {
        val backend = AppleAvFoundationBackend(openTimeoutMillis)
        return AppleAvFoundationVideoPlayer(backend)
    }
}

/**
 * Apple-only surface seam. The app may move one layer between inline, in-app PiP,
 * and optional fullscreen layouts without reopening media or replacing the player.
 */
class AppleAvFoundationVideoPlayer internal constructor(
    private val backend: AppleAvFoundationBackend,
) : VideoPlayer by DefaultVideoPlayer(backend, Dispatchers.Main) {
    fun createVideoLayer(
        videoGravity: String? = AVLayerVideoGravityResizeAspect,
    ): AVPlayerLayer = AVPlayerLayer().also { layer ->
        layer.videoGravity = videoGravity
        backend.attach(layer)
    }

    fun attach(layer: AVPlayerLayer) = backend.attach(layer)

    fun detach(layer: AVPlayerLayer? = null) = backend.detach(layer)
}

internal class AppleAvFoundationBackend(
    private val openTimeoutMillis: Long,
) : VideoBackend {
    private val player = AVPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 64)
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private var sessionId: PlaybackSessionId? = null
    private var source: PlaybackSource? = null
    private var currentItem: AVPlayerItem? = null
    private var attachedLayer: AVPlayerLayer? = null
    private var notificationTokens: List<Any> = emptyList()
    private var trackTargets: Map<String, AppleTrackTarget> = emptyMap()
    private var desiredPlayWhenReady = false
    private var released = false
    private var lastTimeline: PlaybackTimeline? = null
    private var lastTransport: Pair<Boolean, Boolean>? = null
    private var lastBuffering: Pair<Boolean, Long?>? = null
    private var failureReportedFor: PlaybackSessionId? = null

    override val capabilities: PlayerCapabilities = appleAvFoundationCapabilities()
    override val events: Flow<BackendEvent> = eventFlow

    init {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                pollActiveSession()
            }
        }
    }

    override suspend fun open(
        sessionId: PlaybackSessionId,
        source: PlaybackSource,
        playWhenReady: Boolean,
    ): OpenedMedia {
        check(!released) { "AVFoundation video player is closed" }
        if (source.externalSubtitles.isNotEmpty()) {
            throw PlaybackFailure(
                PlaybackError(
                    PlaybackErrorCode.Source,
                    "AVFoundation does not support external subtitle sidecars",
                    recoverable = true,
                    suggestedBackend = "mpv",
                ),
            )
        }

        this.sessionId = sessionId
        this.source = source
        desiredPlayWhenReady = playWhenReady
        failureReportedFor = null
        resetSnapshots()

        return try {
            withContext(Dispatchers.Main) {
                withTimeout(openTimeoutMillis) {
                    detachSession()
                    val item = source.toApplePlayerItem()
                    currentItem = item
                    player.replaceCurrentItemWithPlayerItem(item)
                    awaitReady(item)
                    registerNotifications(sessionId, item)
                    val tracks = snapshotTracks(item)
                    val timeline = snapshotTimeline(item)
                    lastTimeline = timeline
                    if (playWhenReady) player.play() else player.pause()
                    scope.launch { emitTransport(sessionId) }
                    OpenedMedia(
                        timeline = timeline,
                        audioTracks = tracks.audio,
                        subtitleTracks = tracks.subtitles,
                        selectedAudioTrackId = tracks.selectedAudio,
                        selectedSubtitleTrackId = tracks.selectedSubtitle,
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            clearFailedOpen(sessionId)
            throw PlaybackFailure(
                PlaybackError(PlaybackErrorCode.Network, "AVFoundation timed out while opening media", true),
            )
        } catch (error: CancellationException) {
            clearFailedOpen(sessionId)
            throw error
        } catch (error: Throwable) {
            clearFailedOpen(sessionId)
            throw error
        }
    }

    private suspend fun awaitReady(item: AVPlayerItem) {
        while (true) {
            when (item.status) {
                AVPlayerItemStatusReadyToPlay -> return
                AVPlayerItemStatusFailed -> throw PlaybackFailure(item.error.toAirPlaybackError())
                else -> delay(READY_POLL_MILLIS)
            }
        }
    }

    override fun play() {
        desiredPlayWhenReady = true
        runOnMain { player.play() }
    }

    override fun pause() {
        desiredPlayWhenReady = false
        runOnMain { player.pause() }
    }

    override fun seekTo(positionMillis: Long) {
        val issuedFor = sessionId ?: return
        runOnMain {
            player.seekToTime(
                time = CMTimeMake(positionMillis.coerceAtLeast(0), 1_000),
                toleranceBefore = CMTimeMake(0, 1_000),
                toleranceAfter = CMTimeMake(0, 1_000),
            ) { _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    eventFlow.tryEmit(
                        BackendEvent.SeekFinished(issuedFor, currentPositionMillis()),
                    )
                }
            }
        }
    }

    override fun selectAudioTrack(id: String?): TrackSelectionResult =
        selectTrack(AppleTrackType.Audio, id)

    override fun selectSubtitleTrack(id: String?): TrackSelectionResult =
        selectTrack(AppleTrackType.Subtitle, id)

    override fun selectVideoTrack(id: String?): TrackSelectionResult = TrackSelectionResult.NotSupported

    private fun selectTrack(type: AppleTrackType, id: String?): TrackSelectionResult {
        val item = currentItem ?: return TrackSelectionResult.NotSupported
        val target = id?.let(trackTargets::get)
        if (id != null && (target == null || target.type != type)) {
            return TrackSelectionResult.NotFound(id)
        }
        if (target == null) {
            val group = trackTargets.values.firstOrNull { it.type == type }?.group
                ?: return TrackSelectionResult.NotSupported
            if (!group.allowsEmptySelection) return TrackSelectionResult.NotSupported
            runOnMain {
                item.selectMediaOption(null, group)
                publishTracks(item)
            }
            return TrackSelectionResult.Requested(null)
        }
        runOnMain {
            item.selectMediaOption(target.option, target.group)
            publishTracks(item)
        }
        return TrackSelectionResult.Requested(id)
    }

    private fun publishTracks(item: AVPlayerItem) {
        val active = sessionId ?: return
        val tracks = snapshotTracks(item)
        eventFlow.tryEmit(
            BackendEvent.TracksChanged(
                active,
                tracks.audio,
                tracks.subtitles,
                emptyList(),
                tracks.selectedAudio,
                tracks.selectedSubtitle,
                null,
            ),
        )
    }

    override fun stop() {
        sessionId = null
        source = null
        desiredPlayWhenReady = false
        failureReportedFor = null
        trackTargets = emptyMap()
        resetSnapshots()
        runOnMain { detachSession() }
    }

    fun attach(layer: AVPlayerLayer) {
        if (released) return
        runOnMain {
            if (attachedLayer === layer) return@runOnMain
            attachedLayer?.player = null
            layer.player = player
            attachedLayer = layer
        }
    }

    fun detach(layer: AVPlayerLayer? = null) {
        runOnMain {
            if (layer != null && attachedLayer !== layer) return@runOnMain
            attachedLayer?.player = null
            attachedLayer = null
        }
    }

    override fun close() {
        if (released) return
        released = true
        sessionId = null
        source = null
        desiredPlayWhenReady = false
        failureReportedFor = null
        trackTargets = emptyMap()
        resetSnapshots()
        runOnMain {
            detachSession()
            attachedLayer?.player = null
            attachedLayer = null
        }
        scope.cancel()
    }

    private fun pollActiveSession() {
        val active = sessionId ?: return
        val item = currentItem ?: return
        if (item.status == AVPlayerItemStatusFailed && failureReportedFor != active) {
            failureReportedFor = active
            eventFlow.tryEmit(BackendEvent.Failed(active, item.error.toAirPlaybackError()))
            return
        }

        val timeline = snapshotTimeline(item)
        if (timeline != lastTimeline) {
            lastTimeline = timeline
            eventFlow.tryEmit(BackendEvent.TimelineChanged(active, timeline))
        }

        eventFlow.tryEmit(BackendEvent.PositionChanged(active, currentPositionMillis()))
        emitTransport(active)

        val buffering = item.playbackBufferEmpty ||
            (!item.playbackLikelyToKeepUp &&
                player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate)
        val bufferSnapshot = buffering to bufferedPositionMillis(item)
        if (bufferSnapshot != lastBuffering) {
            lastBuffering = bufferSnapshot
            eventFlow.tryEmit(BackendEvent.BufferingChanged(active, buffering, bufferSnapshot.second))
        }
    }

    private fun emitTransport(active: PlaybackSessionId) {
        val snapshot = (player.timeControlStatus == AVPlayerTimeControlStatusPlaying) to desiredPlayWhenReady
        if (snapshot == lastTransport) return
        lastTransport = snapshot
        eventFlow.tryEmit(BackendEvent.PlaybackChanged(active, snapshot.first, snapshot.second))
    }

    private fun snapshotTimeline(item: AVPlayerItem): PlaybackTimeline = appleTimeline(
        durationMillis = cmTimeToMillisOrNull(item.duration),
        seekableRange = seekableRange(item),
        hint = source?.kindHint,
    )

    private fun snapshotTracks(item: AVPlayerItem): AppleTrackSnapshot {
        val targets = mutableMapOf<String, AppleTrackTarget>()
        val audioGroup = item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
        val subtitleGroup = item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
        val audio = audioGroup.toAudioTracks(targets)
        val subtitles = subtitleGroup.toSubtitleTracks(targets)
        trackTargets = targets
        return AppleTrackSnapshot(
            audio = audio,
            subtitles = subtitles,
            selectedAudio = audioGroup?.let(item::selectedMediaOptionInMediaSelectionGroup)?.let { selected ->
                targets.entries.firstOrNull { it.value.option == selected }?.key
            },
            selectedSubtitle = subtitleGroup?.let(item::selectedMediaOptionInMediaSelectionGroup)?.let { selected ->
                targets.entries.firstOrNull { it.value.option == selected }?.key
            },
        )
    }

    private fun registerNotifications(active: PlaybackSessionId, item: AVPlayerItem) {
        removeNotifications()
        notificationTokens = listOf(
            notificationCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                desiredPlayWhenReady = false
                eventFlow.tryEmit(BackendEvent.PlaybackEnded(active))
            },
            notificationCenter.addObserverForName(
                name = AVPlayerItemFailedToPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { notification ->
                val error = notification?.userInfo
                    ?.get(AVPlayerItemFailedToPlayToEndTimeErrorKey) as? NSError
                eventFlow.tryEmit(BackendEvent.Failed(active, error.toAirPlaybackError()))
            },
        )
    }

    private fun removeNotifications() {
        notificationTokens.forEach { notificationCenter.removeObserver(it) }
        notificationTokens = emptyList()
    }

    private fun detachSession() {
        removeNotifications()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        currentItem = null
    }

    private suspend fun clearFailedOpen(failedSession: PlaybackSessionId) {
        withContext(Dispatchers.Main) {
            if (sessionId == failedSession) {
                sessionId = null
                source = null
                desiredPlayWhenReady = false
                trackTargets = emptyMap()
                detachSession()
                resetSnapshots()
            }
        }
    }

    private fun resetSnapshots() {
        lastTimeline = null
        lastTransport = null
        lastBuffering = null
    }

    private fun currentPositionMillis(): Long = cmTimeToMillisOrNull(player.currentTime()) ?: 0L

    private fun runOnMain(block: () -> Unit) {
        if (NSThread.isMainThread()) block()
        else dispatch_async(dispatch_get_main_queue()) { block() }
    }
}

internal fun appleAvFoundationCapabilities(): PlayerCapabilities = PlayerCapabilities(
    containers = setOf("mp4", "m4v", "mov", "m3u8"),
    videoCodecs = setOf("h264", "hevc"),
    audioCodecs = setOf("aac", "alac", "mp3", "ac3", "eac3"),
    subtitleFormats = setOf("webvtt", "cea-608", "cea-708"),
    adaptiveProtocols = setOf("hls"),
    supportsAudioTrackSelection = true,
    supportsSubtitleTrackSelection = true,
    supportsVideoTrackSelection = false,
    supportsExternalSubtitles = false,
    supportsLive = true,
    supportsSeekableLive = true,
    supportsPlaybackRate = true,
    supportsPictureInPicture = false,
    supportsHdr = false,
    supportsMovableSurface = true,
    supportsSurfaceReattachment = true,
    supportsCompositedOverlays = true,
    hardwareAcceleration = HardwareAcceleration.DecodeAndRender,
)

internal fun appleTimeline(
    durationMillis: Long?,
    seekableRange: SeekableRange?,
    hint: PlaybackKind?,
): PlaybackTimeline = when (hint) {
    PlaybackKind.Live -> PlaybackTimeline(
        kind = PlaybackKind.Live,
        liveEdgeMillis = seekableRange?.endMillis,
    )
    PlaybackKind.SeekableLive -> PlaybackTimeline(
        kind = PlaybackKind.SeekableLive,
        seekableRange = seekableRange,
        liveEdgeMillis = seekableRange?.endMillis ?: durationMillis,
    )
    PlaybackKind.OnDemand -> PlaybackTimeline(
        kind = PlaybackKind.OnDemand,
        durationMillis = durationMillis ?: 0,
    )
    null -> when {
        durationMillis != null -> PlaybackTimeline(PlaybackKind.OnDemand, durationMillis)
        seekableRange != null -> PlaybackTimeline(
            kind = PlaybackKind.SeekableLive,
            seekableRange = seekableRange,
            liveEdgeMillis = seekableRange.endMillis,
        )
        else -> PlaybackTimeline(PlaybackKind.Live)
    }
}

private fun PlaybackSource.toApplePlayerItem(): AVPlayerItem {
    val url = URLWithString(uri) ?: throw PlaybackFailure(
        PlaybackError(PlaybackErrorCode.Source, "AVFoundation source URL is invalid", false),
    )
    val options: Map<Any?, *>? = if (headers.isEmpty()) {
        null
    } else {
        mapOf<Any?, Any?>(APPLE_HTTP_HEADERS_KEY to headers.toMap())
    }
    return AVPlayerItem(AVURLAsset(url, options))
}

private fun AVMediaSelectionGroup?.toAudioTracks(
    targets: MutableMap<String, AppleTrackTarget>,
): List<AudioTrack> {
    val group = this ?: return emptyList()
    return group.options.mapIndexedNotNull { index, value ->
        val option = value as? AVMediaSelectionOption ?: return@mapIndexedNotNull null
        val id = "apple-audio:$index"
        targets[id] = AppleTrackTarget(AppleTrackType.Audio, group, option)
        AudioTrack(
            id = id,
            label = option.displayName.ifBlank { "Audio ${index + 1}" },
            language = option.extendedLanguageTag,
            isDefault = group.defaultOption == option,
            codec = option.mediaType,
        )
    }
}

private fun AVMediaSelectionGroup?.toSubtitleTracks(
    targets: MutableMap<String, AppleTrackTarget>,
): List<SubtitleTrack> {
    val group = this ?: return emptyList()
    return group.options.mapIndexedNotNull { index, value ->
        val option = value as? AVMediaSelectionOption ?: return@mapIndexedNotNull null
        val id = "apple-subtitle:$index"
        targets[id] = AppleTrackTarget(AppleTrackType.Subtitle, group, option)
        SubtitleTrack(
            id = id,
            label = option.displayName.ifBlank { "Subtitles ${index + 1}" },
            language = option.extendedLanguageTag,
            isDefault = group.defaultOption == option,
            format = option.mediaType,
        )
    }
}

private fun seekableRange(item: AVPlayerItem): SeekableRange? {
    var startMillis: Long? = null
    var endMillis: Long? = null
    for (rangeValue in item.seekableTimeRanges) {
        val range = (rangeValue as? platform.Foundation.NSValue)?.CMTimeRangeValue ?: continue
        range.useContents {
            val start = start.toFiniteMillisOrNull() ?: return@useContents
            val duration = duration.toFiniteMillisOrNull() ?: return@useContents
            startMillis = minOf(startMillis ?: start, start)
            endMillis = maxOf(endMillis ?: start + duration, start + duration)
        }
    }
    val start = startMillis ?: return null
    val end = endMillis ?: return null
    return SeekableRange(start.coerceAtLeast(0), end.coerceAtLeast(start))
}

private fun bufferedPositionMillis(item: AVPlayerItem): Long? {
    var furthestEnd: Long? = null
    for (rangeValue in item.loadedTimeRanges) {
        val range = (rangeValue as? platform.Foundation.NSValue)?.CMTimeRangeValue ?: continue
        range.useContents {
            val start = start.toFiniteMillisOrNull() ?: return@useContents
            val duration = duration.toFiniteMillisOrNull() ?: return@useContents
            furthestEnd = maxOf(furthestEnd ?: 0, start + duration)
        }
    }
    return furthestEnd
}

private fun cmTimeToMillisOrNull(time: CValue<CMTime>): Long? {
    val seconds = CMTimeGetSeconds(time)
    if (seconds.isNaN() || seconds.isInfinite() || seconds < 0) return null
    return (seconds * 1_000).roundToLong()
}

private fun CMTime.toFiniteMillisOrNull(): Long? {
    if (timescale == 0 || value < 0) return null
    return value * 1_000L / timescale
}

private fun NSError?.toAirPlaybackError(): PlaybackError = when {
    this == null -> PlaybackError(PlaybackErrorCode.Internal, "AVFoundation playback failed", false)
    domain == NSURLErrorDomain -> PlaybackError(
        PlaybackErrorCode.Network,
        "AVFoundation media request failed",
        recoverable = true,
    )
    domain == AVFoundationErrorDomain && code in setOf(
        AVErrorFileFormatNotRecognized,
        AVErrorDecoderNotFound,
        AVErrorUndecodableMediaData,
    ) -> PlaybackError(
        PlaybackErrorCode.UnsupportedContainer,
        "AVFoundation does not support this media format",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    domain == AVFoundationErrorDomain && code == AVErrorDecodeFailed -> PlaybackError(
        PlaybackErrorCode.Decode,
        "AVFoundation could not decode the media",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    else -> PlaybackError(PlaybackErrorCode.Internal, "AVFoundation playback failed", false)
}

private data class AppleTrackSnapshot(
    val audio: List<AudioTrack>,
    val subtitles: List<SubtitleTrack>,
    val selectedAudio: String?,
    val selectedSubtitle: String?,
)

private data class AppleTrackTarget(
    val type: AppleTrackType,
    val group: AVMediaSelectionGroup,
    val option: AVMediaSelectionOption,
)

private enum class AppleTrackType { Audio, Subtitle }

private const val APPLE_HTTP_HEADERS_KEY = "AVURLAssetHTTPHeaderFieldsKey"
private const val READY_POLL_MILLIS = 25L
private const val POLL_INTERVAL_MILLIS = 250L
