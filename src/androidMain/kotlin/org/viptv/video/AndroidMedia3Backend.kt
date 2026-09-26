@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidMedia3Backend(
    private val context: Context,
    private val openTimeoutMillis: Long,
    override val capabilities: PlayerCapabilities,
    private val resilientBufferConfig: AndroidMedia3ResilientBufferConfig,
) : VideoBackend {
    internal lateinit var player: ExoPlayer
    private lateinit var handler: Handler
    internal lateinit var listener: Player.Listener
    internal lateinit var analyticsListener: AnalyticsListener
    private var bufferMode = Media3BufferMode.NativeDefault
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val eventsFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 64)
    @Volatile internal var sessionId: PlaybackSessionId? = null
    @Volatile internal var opening = false
    @Volatile internal var released = false
    @Volatile internal var trackTargets: Map<String, TrackTarget> = emptyMap()
    @Volatile private var externalSubtitleIds: Set<String> = emptySet()
    @Volatile internal var kindHint: PlaybackKind? = null
    internal var lastPlaybackState = Player.STATE_IDLE
    internal var hasReachedReady = false
    internal var recoveringBehindLiveWindow = false
    internal var manualSeekPending = false
    internal var manualSeekInProgress = false
    internal var estimatedThroughputBitsPerSecond: Long? = null
    internal var droppedVideoFrames = 0L
    internal var rebufferCount = 0L
    internal var behindLiveWindowRecoveryCount = 0L
    internal var discontinuityCount = 0L
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
                    openingPlayer.setMediaSource(mediaSource, source.startPositionMillis.coerceAtLeast(0))
                } else {
                    openingPlayer.setMediaSource(mediaSource)
                }
                openingPlayer.trackSelectionParameters = openingPlayer.trackSelectionParameters.buildUpon()
                    .clearOverrides()
                    .setPreferredAudioLanguage(source.options.preferredAudioLanguage)
                    .setPreferredTextLanguage(source.options.preferredSubtitleLanguage)
                    .apply { source.options.subtitlesEnabled?.let { setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !it) } }
                    .build()
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


    internal fun recoverBehindLiveWindow(): Boolean {
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

    internal fun emitPlaybackChanged() {
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

    internal fun snapshotStatistics(): PlaybackStatistics {
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

    internal fun snapshotTimeline(): PlaybackTimeline {
        if (player.currentTimeline.isEmpty || player.currentMediaItemIndex < 0) {
            return PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = 0)
        }
        val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        val duration = window.durationMs.takeUnless { it == C.TIME_UNSET || it < 0 }
        return media3Timeline(
            isLive = window.isLive,
            isSeekable = window.isSeekable,
            durationMillis = duration,
            kindHint = kindHint,
            windowPositionInFirstPeriodMillis = window.positionInFirstPeriodMs,
        )
    }

    /**
     * Media3 positions are relative to the current timeline window. Managed VOD uses a
     * rolling HLS playlist, so that local zero moves as old segments are removed. The
     * window's first-period position is the native, paused-safe session clock; adding it
     * avoids guessing elapsed wall time or clamping backwards samples. Live coordinates
     * deliberately remain native because their ranges are window-relative by contract.
     */
    internal fun sessionPositionMillis(nativePositionMillis: Long): Long = media3SessionPositionMillis(
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

    internal fun snapshotTracks(tracks: Tracks): TrackSnapshot {
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
