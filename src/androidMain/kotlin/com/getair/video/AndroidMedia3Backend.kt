@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.getair.video

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidMedia3BackendFactory(
    context: Context,
    private val openTimeoutMillis: Long = 20_000,
    private val resilientBufferConfig: AndroidMedia3ResilientBufferConfig =
        AndroidMedia3ResilientBufferConfig(),
) : VideoBackendFactory {
    private val applicationContext = context.applicationContext
    @Volatile private var probedCapabilities: PlayerCapabilities? = null

    init {
        require(openTimeoutMillis > 0)
    }

    override val id: String = "media3"

    override suspend fun probe(): PlayerCapabilities = withContext(Dispatchers.Default) {
        probeMedia3Capabilities()
    }.also { probedCapabilities = it }

    override fun create(): VideoPlayer = createAndroidPlayer()

    fun createAndroidPlayer(): AndroidMedia3VideoPlayer = onMainThreadBlocking {
        AndroidMedia3VideoPlayer(
            AndroidMedia3Backend(
                applicationContext,
                openTimeoutMillis,
                probedCapabilities ?: probeMedia3Capabilities(),
                resilientBufferConfig,
            ),
        )
    }
}

class AndroidMedia3VideoPlayer internal constructor(
    private val backend: AndroidMedia3Backend,
) : VideoPlayer by DefaultVideoPlayer(backend, Dispatchers.Main.immediate) {
    fun attach(surfaceView: SurfaceView) = backend.attach(surfaceView)
    fun attach(textureView: TextureView) = backend.attach(textureView)
    fun detachSurface() = backend.detachSurface()
}

internal class AndroidMedia3Backend(
    private val context: Context,
    private val openTimeoutMillis: Long,
    override val capabilities: PlayerCapabilities,
    private val resilientBufferConfig: AndroidMedia3ResilientBufferConfig,
) : VideoBackend {
    private lateinit var player: ExoPlayer
    private lateinit var handler: Handler
    private lateinit var listener: Player.Listener
    private lateinit var analyticsListener: AnalyticsListener
    private var bufferMode = Media3BufferMode.NativeDefault
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventsFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 64)
    @Volatile private var sessionId: PlaybackSessionId? = null
    @Volatile private var opening = false
    @Volatile private var released = false
    @Volatile private var trackTargets: Map<String, TrackTarget> = emptyMap()
    @Volatile private var externalSubtitleIds: Set<String> = emptySet()
    @Volatile private var kindHint: PlaybackKind? = null
    private var lastPlaybackState = Player.STATE_IDLE
    private var hasReachedReady = false
    private var recoveringBehindLiveWindow = false
    private var manualSeekPending = false
    private var manualSeekInProgress = false
    private var estimatedThroughputBitsPerSecond: Long? = null
    private var droppedVideoFrames = 0L
    private var rebufferCount = 0L
    private var behindLiveWindowRecoveryCount = 0L
    private var discontinuityCount = 0L
    private var videoOutput: AndroidVideoOutput? = null
    private var activeLiveTuning = media3LivePolicyTuning(
        LivePlaybackPolicy.Balanced,
        resilientBufferConfig,
    )

    override val events: Flow<BackendEvent> = eventsFlow

    init {
        replacePlayer(Media3BufferMode.NativeDefault)
        scope.launch {
            while (true) {
                delay(500)
                val active = sessionId ?: continue
                eventsFlow.tryEmit(BackendEvent.PositionChanged(active, sessionPositionMillis(player.currentPosition)))
                eventsFlow.tryEmit(
                    BackendEvent.BufferingChanged(
                        active,
                        player.playbackState == Player.STATE_BUFFERING,
                        player.bufferedPosition.takeIf { it >= 0 }?.let(::sessionPositionMillis),
                    ),
                )
                eventsFlow.tryEmit(BackendEvent.StatisticsChanged(active, snapshotStatistics()))
            }
        }
    }

    override suspend fun open(
        sessionId: PlaybackSessionId,
        source: PlaybackSource,
        playWhenReady: Boolean,
    ): OpenedMedia {
        this.sessionId = null
        val policy = if (source.kindHint == PlaybackKind.OnDemand) {
            LivePlaybackPolicy.Balanced
        } else {
            source.options.livePolicy
        }
        val tuning = media3LivePolicyTuning(policy, resilientBufferConfig)
        withContext(Dispatchers.Main.immediate) {
            if (bufferMode == tuning.bufferMode) {
                player.stop()
            } else {
                replacePlayer(tuning.bufferMode)
            }
        }
        activeLiveTuning = tuning
        this.sessionId = sessionId
        kindHint = source.kindHint
        resetRuntimeMetrics()
        externalSubtitleIds = source.externalSubtitles.mapTo(mutableSetOf(), ExternalSubtitleSource::id)
        opening = true
        return try {
            withContext(Dispatchers.Main.immediate) {
                withTimeout(openTimeoutMillis) { openOnMain(source, playWhenReady) }
            }
        } catch (_: TimeoutCancellationException) {
            this.sessionId = null
            withContext(Dispatchers.Main.immediate) { player.stop() }
            throw PlaybackFailure(
                PlaybackError(PlaybackErrorCode.Network, "Media3 timed out while opening media", recoverable = true),
            )
        } catch (error: CancellationException) {
            this.sessionId = null
            throw error
        } catch (error: Throwable) {
            this.sessionId = null
            throw error
        } finally {
            opening = false
        }
    }

    private suspend fun openOnMain(source: PlaybackSource, playWhenReady: Boolean): OpenedMedia =
        suspendCancellableCoroutine { continuation ->
            val openingPlayer = player
            val openListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (openingPlayer !== player || playbackState != Player.STATE_READY ||
                        !continuation.isActive
                    ) {
                        return
                    }
                    openingPlayer.removeListener(this)
                    continuation.resume(snapshotOpenedMedia())
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (openingPlayer !== player || !continuation.isActive) return
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                        recoverBehindLiveWindow()
                    ) {
                        return
                    }
                    openingPlayer.removeListener(this)
                    continuation.resumeWithException(PlaybackFailure(error.toAirError()))
                }
            }
            continuation.invokeOnCancellation {
                runOnPlayerThread {
                    openingPlayer.removeListener(openListener)
                    openingPlayer.stop()
                }
            }
            openingPlayer.addListener(openListener)
            try {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(false)
                    .setDefaultRequestProperties(source.headers)
                val mediaSource = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(DefaultDataSource.Factory(context, httpFactory))
                    .createMediaSource(source.toMediaItem(resilientBufferConfig))
                // A server-managed VOD HLS playlist is dynamic but its title position is
                // supplied by the launch request. Do not let Media3 choose the playlist's
                // live-edge default: the controller applies an explicit direct resume after
                // readiness, while managed delivery starts at this session's zero.
                if (source.kindHint == PlaybackKind.OnDemand) {
                    openingPlayer.setMediaSource(mediaSource, 0L)
                } else {
                    openingPlayer.setMediaSource(mediaSource)
                }
                openingPlayer.playWhenReady = playWhenReady
                openingPlayer.prepare()
            } catch (_: Exception) {
                openingPlayer.removeListener(openListener)
                continuation.resumeWithException(
                    PlaybackFailure(PlaybackError(PlaybackErrorCode.Source, "Media3 source is invalid", false)),
                )
                return@suspendCancellableCoroutine
            }
        }

    override fun play() = runOnPlayerThread { player.play() }
    override fun pause() = runOnPlayerThread { player.pause() }
    override fun seekTo(positionMillis: Long) = runOnPlayerThread {
        manualSeekPending = true
        manualSeekInProgress = true
        player.seekTo(nativeSeekPositionMillis(positionMillis))
    }

    override fun selectAudioTrack(id: String?): TrackSelectionResult = selectTrack(C.TRACK_TYPE_AUDIO, id)
    override fun selectSubtitleTrack(id: String?): TrackSelectionResult = selectTrack(C.TRACK_TYPE_TEXT, id)
    override fun selectVideoTrack(id: String?): TrackSelectionResult = selectTrack(C.TRACK_TYPE_VIDEO, id)

    private fun selectTrack(type: Int, id: String?): TrackSelectionResult {
        val target = id?.let(trackTargets::get)
        if (id != null && (target == null || target.type != type)) return TrackSelectionResult.NotFound(id)
        runOnPlayerThread {
            val builder = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, id == null)
            if (target != null) {
                builder.setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(target.group, listOf(target.trackIndex)))
            }
            player.trackSelectionParameters = builder.build()
        }
        return TrackSelectionResult.Requested(id)
    }

    override fun stop() {
        sessionId = null
        kindHint = null
        trackTargets = emptyMap()
        runOnPlayerThread {
            player.stop()
            player.clearMediaItems()
        }
    }

    fun attach(surfaceView: SurfaceView) {
        if (released) return
        runOnPlayerThread {
            clearVideoOutput()
            player.setVideoSurfaceView(surfaceView)
            videoOutput = AndroidVideoOutput.Surface(surfaceView)
        }
    }

    fun attach(textureView: TextureView) {
        if (released) return
        runOnPlayerThread {
            clearVideoOutput()
            player.setVideoTextureView(textureView)
            videoOutput = AndroidVideoOutput.Texture(textureView)
        }
    }

    fun detachSurface() {
        runOnPlayerThread { clearVideoOutput() }
    }

    override fun close() {
        if (released) return
        released = true
        sessionId = null
        kindHint = null
        scope.cancel()
        runOnPlayerThread(allowAfterRelease = true) {
            clearVideoOutput()
            player.release()
        }
    }

    private fun createListener(callbackPlayer: ExoPlayer): Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (callbackPlayer !== player || released) return
            emitPlaybackChanged()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (callbackPlayer !== player || released) return
            emitPlaybackChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (callbackPlayer !== player || released) return
            val active = sessionId ?: return
            if (playbackState == Player.STATE_BUFFERING &&
                lastPlaybackState == Player.STATE_READY &&
                player.playWhenReady &&
                !manualSeekInProgress
            ) {
                rebufferCount += 1
            }
            if (playbackState == Player.STATE_READY) {
                hasReachedReady = true
                recoveringBehindLiveWindow = false
                manualSeekInProgress = false
            }
            lastPlaybackState = playbackState
            eventsFlow.tryEmit(
                BackendEvent.BufferingChanged(
                    active,
                    playbackState == Player.STATE_BUFFERING,
                    player.bufferedPosition.takeIf { it >= 0 },
                ),
            )
            if (playbackState == Player.STATE_ENDED) eventsFlow.tryEmit(BackendEvent.PlaybackEnded(active))
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (callbackPlayer !== player || released) return
            val active = sessionId ?: return
            eventsFlow.tryEmit(BackendEvent.TimelineChanged(active, snapshotTimeline()))
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (callbackPlayer !== player || released) return
            val active = sessionId ?: return
            val snapshot = snapshotTracks(tracks)
            eventsFlow.tryEmit(
                BackendEvent.TracksChanged(
                    active,
                    snapshot.audio,
                    snapshot.subtitles,
                    snapshot.video,
                    snapshot.selectedAudio,
                    snapshot.selectedSubtitle,
                    snapshot.selectedVideo,
                ),
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (callbackPlayer !== player || released) return
            val active = sessionId ?: return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                if (manualSeekPending) {
                    manualSeekPending = false
                    eventsFlow.tryEmit(BackendEvent.SeekFinished(active, sessionPositionMillis(newPosition.positionMs)))
                }
            } else if (hasReachedReady) {
                discontinuityCount += 1
                eventsFlow.tryEmit(BackendEvent.StatisticsChanged(active, snapshotStatistics()))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (callbackPlayer !== player || released) return
            if (opening) return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                recoverBehindLiveWindow()
            ) {
                return
            }
            sessionId?.let { eventsFlow.tryEmit(BackendEvent.Failed(it, error.toAirError())) }
        }
    }

    private fun createAnalyticsListener(callbackPlayer: ExoPlayer): AnalyticsListener = object : AnalyticsListener {
        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            if (callbackPlayer !== player || released) return
            estimatedThroughputBitsPerSecond = bitrateEstimate.takeIf { it >= 0 }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            if (callbackPlayer !== player || released) return
            this@AndroidMedia3Backend.droppedVideoFrames += droppedFrames.coerceAtLeast(0).toLong()
        }
    }

    private fun recoverBehindLiveWindow(): Boolean {
        if (!shouldRecoverMedia3BehindLiveWindow(
                errorCode = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                kindHint = kindHint,
                recoveryInProgress = recoveringBehindLiveWindow,
            )
        ) {
            return false
        }
        recoveringBehindLiveWindow = true
        behindLiveWindowRecoveryCount += 1
        manualSeekPending = false
        manualSeekInProgress = false
        player.seekToDefaultPosition()
        player.prepare()
        sessionId?.let { eventsFlow.tryEmit(BackendEvent.StatisticsChanged(it, snapshotStatistics())) }
        return true
    }

    private fun resetRuntimeMetrics() {
        lastPlaybackState = Player.STATE_IDLE
        hasReachedReady = false
        recoveringBehindLiveWindow = false
        manualSeekPending = false
        manualSeekInProgress = false
        estimatedThroughputBitsPerSecond = null
        droppedVideoFrames = 0
        rebufferCount = 0
        behindLiveWindowRecoveryCount = 0
        discontinuityCount = 0
    }

    private fun emitPlaybackChanged() {
        sessionId?.let {
            eventsFlow.tryEmit(BackendEvent.PlaybackChanged(it, player.isPlaying, player.playWhenReady))
        }
    }

    private fun snapshotOpenedMedia(): OpenedMedia {
        val tracks = snapshotTracks(player.currentTracks)
        return OpenedMedia(
            timeline = snapshotTimeline(),
            audioTracks = tracks.audio,
            subtitleTracks = tracks.subtitles,
            videoTracks = tracks.video,
            selectedAudioTrackId = tracks.selectedAudio,
            selectedSubtitleTrackId = tracks.selectedSubtitle,
            selectedVideoTrackId = tracks.selectedVideo,
            statistics = snapshotStatistics(),
        )
    }

    private fun snapshotStatistics(): PlaybackStatistics {
        val liveOffset = player.currentLiveOffset.takeUnless { it == C.TIME_UNSET || it < 0 }
        val configured = activeLiveTuning
        return PlaybackStatistics(
            liveEdgeOffsetMillis = liveOffset,
            bufferedAheadMillis = player.totalBufferedDuration.coerceAtLeast(0),
            livePolicy = configured.policy,
            targetLiveOffsetMillis = configured.targetLiveOffsetMillis?.toLong(),
            minimumBufferMillis = configured.minimumBufferMillis?.toLong(),
            maximumBufferMillis = configured.maximumBufferMillis?.toLong(),
            bufferMemoryThresholdBytes = configured.bufferMemoryThresholdBytes?.toLong(),
            estimatedThroughputBitsPerSecond = estimatedThroughputBitsPerSecond,
            droppedVideoFrames = droppedVideoFrames,
            rebufferCount = rebufferCount,
            behindLiveWindowRecoveryCount = behindLiveWindowRecoveryCount,
            discontinuityCount = discontinuityCount,
            playbackSpeed = player.playbackParameters.speed.toDouble(),
        )
    }

    private fun snapshotTimeline(): PlaybackTimeline {
        if (player.currentTimeline.isEmpty || player.currentMediaItemIndex < 0) {
            return PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = 0)
        }
        val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        val duration = window.durationMs.takeUnless { it == C.TIME_UNSET || it < 0 }
        return media3Timeline(window.isLive, window.isSeekable, duration, kindHint)
    }

    /**
     * Media3 positions are relative to the current timeline window. Managed VOD uses a
     * rolling HLS playlist, so that local zero moves as old segments are removed. The
     * window's first-period position is the native, paused-safe session clock; adding it
     * avoids guessing elapsed wall time or clamping backwards samples. Live coordinates
     * deliberately remain native because their ranges are window-relative by contract.
     */
    private fun sessionPositionMillis(nativePositionMillis: Long): Long = media3SessionPositionMillis(
        kindHint = kindHint,
        nativePositionMillis = nativePositionMillis,
        windowPositionInFirstPeriodMillis = currentWindowPositionInFirstPeriodMillis(),
    )

    private fun nativeSeekPositionMillis(sessionPositionMillis: Long): Long = media3NativeSeekPositionMillis(
        kindHint = kindHint,
        sessionPositionMillis = sessionPositionMillis,
        windowPositionInFirstPeriodMillis = currentWindowPositionInFirstPeriodMillis(),
    )

    private fun currentWindowPositionInFirstPeriodMillis(): Long {
        if (player.currentTimeline.isEmpty || player.currentMediaItemIndex < 0) return 0
        val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        return window.positionInFirstPeriodMs.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0
    }

    private fun snapshotTracks(tracks: Tracks): TrackSnapshot {
        val audio = mutableListOf<AudioTrack>()
        val subtitles = mutableListOf<SubtitleTrack>()
        val video = mutableListOf<VideoTrack>()
        val targets = mutableMapOf<String, TrackTarget>()
        var selectedAudio: String? = null
        var selectedSubtitle: String? = null
        var selectedVideo: String? = null
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type !in setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT, C.TRACK_TYPE_VIDEO)) return@forEachIndexed
            repeat(group.length) { trackIndex ->
                if (!group.isTrackSupported(trackIndex)) return@repeat
                val format = group.getTrackFormat(trackIndex)
                val id = "${group.type}:$groupIndex:$trackIndex:${format.id.orEmpty()}"
                targets[id] = TrackTarget(group.type, group.mediaTrackGroup, trackIndex)
                val selected = group.isTrackSelected(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        audio += format.toAudioTrack(id, audio.size)
                        if (selected) selectedAudio = id
                    }
                    C.TRACK_TYPE_TEXT -> {
                        subtitles += format.toSubtitleTrack(id, subtitles.size, externalSubtitleIds)
                        if (selected) selectedSubtitle = id
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        video += format.toVideoTrack(id, video.size)
                        if (selected) selectedVideo = id
                    }
                }
            }
        }
        trackTargets = targets
        return TrackSnapshot(audio, subtitles, video, selectedAudio, selectedSubtitle, selectedVideo)
    }

    private fun replacePlayer(mode: Media3BufferMode) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val retainedOutput = videoOutput
        if (::player.isInitialized) {
            val previous = player
            previous.removeListener(listener)
            previous.removeAnalyticsListener(analyticsListener)
            clearVideoOutput(previous, clearReference = false)
            previous.release()
        }
        val tuning = when (mode) {
            Media3BufferMode.NativeDefault -> media3LivePolicyTuning(
                LivePlaybackPolicy.Balanced,
                resilientBufferConfig,
            )
            Media3BufferMode.Resilient -> media3LivePolicyTuning(
                LivePlaybackPolicy.Resilient,
                resilientBufferConfig,
            )
        }
        player = ExoPlayer.Builder(context).apply {
            buildMedia3LoadControl(tuning)?.let(::setLoadControl)
        }.build()
        handler = Handler(player.applicationLooper)
        listener = createListener(player)
        analyticsListener = createAnalyticsListener(player)
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        bufferMode = mode
        retainedOutput?.let { output ->
            attachVideoOutput(player, output)
            videoOutput = output
        }
    }

    private fun runOnPlayerThread(allowAfterRelease: Boolean = false, block: () -> Unit) {
        if (released && !allowAfterRelease) return
        val targetPlayer = player
        val targetHandler = handler
        if (Looper.myLooper() == targetPlayer.applicationLooper) {
            block()
        } else {
            targetHandler.post {
                if ((!released || allowAfterRelease) && player === targetPlayer) block()
            }
        }
    }

    private fun attachVideoOutput(target: ExoPlayer, output: AndroidVideoOutput) {
        when (output) {
            is AndroidVideoOutput.Surface -> target.setVideoSurfaceView(output.view)
            is AndroidVideoOutput.Texture -> target.setVideoTextureView(output.view)
        }
    }

    private fun clearVideoOutput(
        target: ExoPlayer = player,
        clearReference: Boolean = true,
    ) {
        when (val output = videoOutput) {
            is AndroidVideoOutput.Surface -> target.clearVideoSurfaceView(output.view)
            is AndroidVideoOutput.Texture -> target.clearVideoTextureView(output.view)
            null -> Unit
        }
        if (clearReference) videoOutput = null
    }
}

private sealed interface AndroidVideoOutput {
    data class Surface(val view: SurfaceView) : AndroidVideoOutput
    data class Texture(val view: TextureView) : AndroidVideoOutput
}

private data class TrackTarget(val type: Int, val group: TrackGroup, val trackIndex: Int)
private data class TrackSnapshot(
    val audio: List<AudioTrack>,
    val subtitles: List<SubtitleTrack>,
    val video: List<VideoTrack>,
    val selectedAudio: String?,
    val selectedSubtitle: String?,
    val selectedVideo: String?,
)

private fun Format.toAudioTrack(id: String, index: Int) = AudioTrack(
    id = id,
    label = trackLabel("Audio", index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    channels = channelCount.takeIf { it > 0 },
    codec = codecs ?: sampleMimeType,
)

private fun Format.toSubtitleTrack(id: String, index: Int, externalIds: Set<String>) = SubtitleTrack(
    id = id,
    label = trackLabel("Subtitles", index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    format = sampleMimeType,
    external = this.id in externalIds,
)

private fun Format.toVideoTrack(id: String, index: Int) = VideoTrack(
    id = id,
    label = media3VideoTrackLabel(label, height, codecs, index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    width = width.takeIf { it > 0 },
    height = height.takeIf { it > 0 },
    bitrate = bitrate.takeIf { it > 0 }?.toLong(),
    codec = codecs ?: sampleMimeType,
)

internal fun media3VideoTrackLabel(label: String?, height: Int, codec: String?, index: Int): String =
    label?.takeIf(String::isNotBlank)
        ?: height.takeIf { it > 0 }?.let { "${it}p" }
        ?: codec
        ?: "Video ${index + 1}"

private fun Format.trackLabel(type: String, index: Int): String = label ?: language ?: "$type ${index + 1}"

private fun PlaybackSource.toMediaItem(
    resilientBufferConfig: AndroidMedia3ResilientBufferConfig,
): MediaItem {
    val subtitles = externalSubtitles.map { subtitle ->
        var selectionFlags = 0
        if (subtitle.isDefault) selectionFlags = selectionFlags or C.SELECTION_FLAG_DEFAULT
        if (subtitle.isForced) selectionFlags = selectionFlags or C.SELECTION_FLAG_FORCED
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
            .setId(subtitle.id)
            .setMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setLabel(subtitle.label)
            .setSelectionFlags(selectionFlags)
            .build()
    }
    return MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setMimeType(mimeType)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .setSubtitleConfigurations(subtitles)
        .apply {
            if (kindHint != PlaybackKind.OnDemand) {
                media3LiveConfiguration(options.livePolicy, resilientBufferConfig)?.let(::setLiveConfiguration)
            }
        }
        .build()
}

/** Balanced deliberately leaves manifest/Media3 defaults untouched. Target offset is not a promise
 * about buffered-ahead media; [PlaybackStatistics.bufferedAheadMillis] reports that separately. */
internal fun media3LiveConfiguration(
    policy: LivePlaybackPolicy,
    resilientBufferConfig: AndroidMedia3ResilientBufferConfig = AndroidMedia3ResilientBufferConfig(),
): MediaItem.LiveConfiguration? = media3LivePolicyTuning(policy, resilientBufferConfig)
    .targetLiveOffsetMillis
    ?.let { MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(it.toLong()).build() }

internal fun shouldRecoverMedia3BehindLiveWindow(
    errorCode: Int,
    kindHint: PlaybackKind?,
    recoveryInProgress: Boolean,
): Boolean = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
    kindHint != PlaybackKind.OnDemand &&
    !recoveryInProgress

private fun PlaybackException.toAirError(): PlaybackError = media3ErrorCodeToAir(errorCode)

internal fun media3ErrorCodeToAir(errorCode: Int): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackError(
        PlaybackErrorCode.UnsupportedContainer,
        "Media3 does not support this container on the current device",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackError(
        PlaybackErrorCode.UnsupportedCodec,
        "Media3 does not support a required codec on the current device",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackError(
        PlaybackErrorCode.Decode,
        "Media3 could not decode the media",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError(
        PlaybackErrorCode.Network,
        "Media3 could not load the media",
        recoverable = true,
    )
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackError(
        PlaybackErrorCode.Network,
        "Media3 fell behind the live window",
        recoverable = true,
    )
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError(
        PlaybackErrorCode.Source,
        "Media3 cannot access the media source",
        recoverable = false,
    )
    else -> PlaybackError(PlaybackErrorCode.Internal, "Media3 playback failed", recoverable = false)
}

internal fun media3Timeline(
    isLive: Boolean,
    isSeekable: Boolean,
    durationMillis: Long?,
    kindHint: PlaybackKind? = null,
): PlaybackTimeline = when {
    kindHint == PlaybackKind.Live -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = durationMillis)
    kindHint == PlaybackKind.SeekableLive -> PlaybackTimeline(
        kind = PlaybackKind.SeekableLive,
        seekableRange = durationMillis?.let { SeekableRange(0, it) },
        liveEdgeMillis = durationMillis,
    )
    kindHint == PlaybackKind.OnDemand -> PlaybackTimeline(
        PlaybackKind.OnDemand,
        durationMillis = durationMillis ?: 0,
    )
    isLive && isSeekable -> PlaybackTimeline(
        kind = PlaybackKind.SeekableLive,
        seekableRange = durationMillis?.let { SeekableRange(0, it) },
        liveEdgeMillis = durationMillis,
    )
    isLive -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = durationMillis)
    else -> PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = durationMillis ?: 0)
}

/**
 * Converts Media3's current-window coordinate to a stable coordinate within one playback
 * session. `Timeline.Window.positionInFirstPeriodMs` is the native offset of the rolling
 * window; it is not wall-clock time and remains valid while playback is paused.
 */
internal fun media3SessionPositionMillis(
    kindHint: PlaybackKind?,
    nativePositionMillis: Long,
    windowPositionInFirstPeriodMillis: Long,
): Long {
    val native = nativePositionMillis.coerceAtLeast(0)
    if (kindHint != PlaybackKind.OnDemand) return native
    val offset = windowPositionInFirstPeriodMillis.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0
    return native.saturatingAdd(offset)
}

/** The inverse used for a direct native seek within the currently available VOD HLS window. */
internal fun media3NativeSeekPositionMillis(
    kindHint: PlaybackKind?,
    sessionPositionMillis: Long,
    windowPositionInFirstPeriodMillis: Long,
): Long {
    val session = sessionPositionMillis.coerceAtLeast(0)
    if (kindHint != PlaybackKind.OnDemand) return session
    val offset = windowPositionInFirstPeriodMillis.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0
    return (session - offset).coerceAtLeast(0)
}

private fun Long.saturatingAdd(other: Long): Long =
    if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other

private fun probeMedia3Capabilities(): PlayerCapabilities {
    val decoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot(MediaCodecInfo::isEncoder)
    }.getOrDefault(emptyList())
    val mimeTypes = decoders.flatMap { it.supportedTypes.asIterable() }.map(String::lowercase).toSet()
    val directVideoLimits = media3DirectVideoLimits(decoders)
    val hardwareMimeTypes = decoders.filter { info ->
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.isSoftwareCodecName()
    }.flatMap { it.supportedTypes.asIterable() }.filter { it.startsWith("video/", ignoreCase = true) }
        .map(String::lowercase).toSet()
    val hardwareVideoCodecs = videoCodecs(hardwareMimeTypes)
    return PlayerCapabilities(
        containers = setOf("mp4", "m4v", "mov", "mkv", "webm", "mpegts", "ts", "flv", "ogg"),
        videoCodecs = videoCodecs(mimeTypes),
        audioCodecs = buildSet {
            if ("audio/mp4a-latm" in mimeTypes) add("aac")
            if ("audio/opus" in mimeTypes) add("opus")
            if ("audio/vorbis" in mimeTypes) add("vorbis")
            if ("audio/flac" in mimeTypes) add("flac")
            if ("audio/ac3" in mimeTypes) add("ac3")
            if ("audio/eac3" in mimeTypes || "audio/eac3-joc" in mimeTypes) add("eac3")
            if ("audio/mpeg" in mimeTypes) add("mp3")
        },
        subtitleFormats = setOf("vtt", "srt", "ssa", "ass", "ttml", "tx3g", "cea608", "cea708", "dvb", "pgs"),
        adaptiveProtocols = setOf("hls", "dash"),
        drmSchemes = buildSet {
            if (runCatching { MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID) }.getOrDefault(false)) add("widevine")
            if (runCatching { MediaDrm.isCryptoSchemeSupported(C.CLEARKEY_UUID) }.getOrDefault(false)) add("clearkey")
        },
        hardwareAcceleratedVideoCodecs = hardwareVideoCodecs,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        supportsVideoTrackSelection = true,
        supportsExternalSubtitles = true,
        supportsLive = true,
        supportsSeekableLive = true,
        supportsPlaybackRate = true,
        supportsPictureInPicture = Build.VERSION.SDK_INT >= 26,
        supportsHdr = false,
        supportsAudioPassthrough = false,
        supportsMovableSurface = true,
        supportsSurfaceReattachment = true,
        supportsCompositedOverlays = true,
        supportedLivePolicies = LivePlaybackPolicy.entries.toSet(),
        hardwareAcceleration = if (hardwareVideoCodecs.isNotEmpty()) {
            HardwareAcceleration.DecodeAndRender
        } else {
            HardwareAcceleration.Unknown
        },
        maxVideoWidth = directVideoLimits?.maxWidth,
        maxVideoHeight = directVideoLimits?.maxHeight,
        supportsHevcSdr = directVideoLimits?.supportsHevcSdr == true,
    )
}

/**
 * The server accepts one size limit for both H264 and HEVC direct delivery. Use the
 * intersection of the actual H264 decoder and the HEVC Main decoder when both are
 * advertised, rather than claiming a size supported by only one codec.
 */
private fun media3DirectVideoLimits(decoders: List<MediaCodecInfo>): Media3DirectVideoLimits? {
    val codecLimits = decoders.flatMap { decoder ->
        decoder.supportedTypes.asIterable()
            .filter { it.equals("video/avc", ignoreCase = true) || it.equals("video/hevc", ignoreCase = true) }
            .mapNotNull { mimeType ->
                runCatching {
                    val capabilities = decoder.getCapabilitiesForType(mimeType)
                    val video = capabilities.videoCapabilities ?: return@runCatching null
                    val maxWidth = video.supportedWidths.upper
                    val maxHeight = video.supportedHeights.upper
                    if (maxWidth < 2 || maxHeight < 2) return@runCatching null
                    Media3DecoderLimit(
                        mimeType = mimeType.lowercase(),
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        supportsHevcSdr = mimeType.equals("video/hevc", ignoreCase = true) &&
                            capabilities.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain },
                    )
                }.getOrNull()
            }
    }
    val h264 = codecLimits.filter { it.mimeType == "video/avc" }.maxByOrNull(Media3DecoderLimit::area)
        ?: return null
    val hevcSdr = codecLimits.filter { it.mimeType == "video/hevc" && it.supportsHevcSdr }
        .maxByOrNull(Media3DecoderLimit::area)
    return if (hevcSdr == null) {
        Media3DirectVideoLimits(h264.maxWidth, h264.maxHeight, supportsHevcSdr = false)
    } else {
        Media3DirectVideoLimits(
            maxWidth = minOf(h264.maxWidth, hevcSdr.maxWidth),
            maxHeight = minOf(h264.maxHeight, hevcSdr.maxHeight),
            supportsHevcSdr = true,
        )
    }
}

private data class Media3DecoderLimit(
    val mimeType: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val supportsHevcSdr: Boolean,
) {
    val area: Long get() = maxWidth.toLong() * maxHeight
}

private data class Media3DirectVideoLimits(
    val maxWidth: Int,
    val maxHeight: Int,
    val supportsHevcSdr: Boolean,
)

private fun videoCodecs(mimeTypes: Set<String>): Set<String> = buildSet {
    if ("video/avc" in mimeTypes) add("h264")
    if ("video/hevc" in mimeTypes) add("hevc")
    if ("video/av01" in mimeTypes) add("av1")
    if ("video/x-vnd.on2.vp9" in mimeTypes) add("vp9")
    if ("video/x-vnd.on2.vp8" in mimeTypes) add("vp8")
    if ("video/dolby-vision" in mimeTypes) add("dolby-vision")
}

private fun String.isSoftwareCodecName(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("omx.google.") || normalized.startsWith("c2.android.") ||
        normalized.startsWith("c2.google.") || ".sw." in normalized
}

private fun <T> onMainThreadBlocking(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val result = AtomicReference<Result<T>>()
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        result.set(runCatching(block))
        latch.countDown()
    }
    check(latch.await(10, TimeUnit.SECONDS)) { "Media3 player creation timed out on the main thread" }
    return checkNotNull(result.get()).getOrThrow()
}
