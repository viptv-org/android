@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.getair.video

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

internal fun AndroidMedia3Backend.createListener(callbackPlayer: ExoPlayer): Player.Listener = object : Player.Listener {
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

internal fun AndroidMedia3Backend.createAnalyticsListener(callbackPlayer: ExoPlayer): AnalyticsListener = object : AnalyticsListener {
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
            droppedVideoFrames += droppedFrames.coerceAtLeast(0).toLong()
        }
    }
