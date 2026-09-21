@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Android-only tuning for the Resilient live intent.
 *
 * Media3 still owns playlist refresh, segment loading, retries, allocation and decoder queues.
 * These values only configure its documented [DefaultLoadControl] streaming thresholds. The byte
 * value is a loading threshold rather than an absolute heap cap, and measured
 * [PlaybackStatistics.bufferedAheadMillis] remains authoritative.
 */
data class AndroidMedia3ResilientBufferConfig(
    val targetLiveOffsetMillis: Int = 10_000,
    val minimumBufferMillis: Int = 10_000,
    val maximumBufferMillis: Int = 15_000,
    val bufferForPlaybackMillis: Int = 1_000,
    val bufferForPlaybackAfterRebufferMillis: Int = 5_000,
    val bufferMemoryThresholdBytes: Int = 64 * 1024 * 1024,
) {
    init {
        require(targetLiveOffsetMillis > 0)
        require(bufferForPlaybackMillis >= 0)
        require(bufferForPlaybackAfterRebufferMillis >= 0)
        require(minimumBufferMillis >= bufferForPlaybackMillis)
        require(minimumBufferMillis >= bufferForPlaybackAfterRebufferMillis)
        require(maximumBufferMillis >= minimumBufferMillis)
        require(targetLiveOffsetMillis >= minimumBufferMillis) {
            "targetLiveOffsetMillis must leave room for minimumBufferMillis"
        }
        require(targetLiveOffsetMillis.toLong() >= 2L * bufferForPlaybackMillis) {
            "Media3 caps the startup threshold at half the target live offset"
        }
        require(targetLiveOffsetMillis.toLong() >= 2L * bufferForPlaybackAfterRebufferMillis) {
            "Media3 caps the rebuffer threshold at half the target live offset"
        }
        require(bufferMemoryThresholdBytes > 0)
    }
}

internal enum class Media3BufferMode { NativeDefault, Resilient }

internal data class Media3LivePolicyTuning(
    val policy: LivePlaybackPolicy,
    val targetLiveOffsetMillis: Int?,
    val bufferMode: Media3BufferMode,
    val minimumBufferMillis: Int? = null,
    val maximumBufferMillis: Int? = null,
    val bufferForPlaybackMillis: Int? = null,
    val bufferForPlaybackAfterRebufferMillis: Int? = null,
    val bufferMemoryThresholdBytes: Int? = null,
    val prioritizeTimeOverSizeThresholds: Boolean = false,
) {
    fun statistics(): PlaybackStatistics = PlaybackStatistics(
        livePolicy = policy,
        targetLiveOffsetMillis = targetLiveOffsetMillis?.toLong(),
        minimumBufferMillis = minimumBufferMillis?.toLong(),
        maximumBufferMillis = maximumBufferMillis?.toLong(),
        bufferMemoryThresholdBytes = bufferMemoryThresholdBytes?.toLong(),
    )
}

internal fun media3LivePolicyTuning(
    policy: LivePlaybackPolicy,
    resilient: AndroidMedia3ResilientBufferConfig,
): Media3LivePolicyTuning = when (policy) {
    LivePlaybackPolicy.LowLatency -> Media3LivePolicyTuning(
        policy = policy,
        targetLiveOffsetMillis = 3_000,
        bufferMode = Media3BufferMode.NativeDefault,
    )
    LivePlaybackPolicy.Balanced -> Media3LivePolicyTuning(
        policy = policy,
        targetLiveOffsetMillis = null,
        bufferMode = Media3BufferMode.NativeDefault,
    )
    LivePlaybackPolicy.Resilient -> Media3LivePolicyTuning(
        policy = policy,
        targetLiveOffsetMillis = resilient.targetLiveOffsetMillis,
        bufferMode = Media3BufferMode.Resilient,
        minimumBufferMillis = resilient.minimumBufferMillis,
        maximumBufferMillis = resilient.maximumBufferMillis,
        bufferForPlaybackMillis = resilient.bufferForPlaybackMillis,
        bufferForPlaybackAfterRebufferMillis = resilient.bufferForPlaybackAfterRebufferMillis,
        bufferMemoryThresholdBytes = resilient.bufferMemoryThresholdBytes,
        prioritizeTimeOverSizeThresholds = true,
    )
}

internal fun buildMedia3LoadControl(tuning: Media3LivePolicyTuning): LoadControl? {
    if (tuning.bufferMode == Media3BufferMode.NativeDefault) return null
    return DefaultLoadControl.Builder()
        .setBufferDurationsMsForStreaming(
            checkNotNull(tuning.minimumBufferMillis),
            checkNotNull(tuning.maximumBufferMillis),
            checkNotNull(tuning.bufferForPlaybackMillis),
            checkNotNull(tuning.bufferForPlaybackAfterRebufferMillis),
        )
        .setTargetBufferBytes(checkNotNull(tuning.bufferMemoryThresholdBytes))
        .setPrioritizeTimeOverSizeThresholdsForStreaming(tuning.prioritizeTimeOverSizeThresholds)
        .build()
}
