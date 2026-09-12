package com.getair.video

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMedia3MappingTest {
    @Test
    fun onDemandRollingHlsUsesTheTimelineWindowAsItsSessionClock() {
        // A 32-second HLS window has slid forward 26 seconds. Media3's native
        // position is relative to that window; the app's progress clock is not.
        assertEquals(
            29_000,
            media3SessionPositionMillis(
                kindHint = PlaybackKind.OnDemand,
                nativePositionMillis = 29_000,
                windowPositionInFirstPeriodMillis = 0,
            ),
        )
        assertEquals(
            29_000,
            media3SessionPositionMillis(
                kindHint = PlaybackKind.OnDemand,
                nativePositionMillis = 3_000,
                windowPositionInFirstPeriodMillis = 26_000,
            ),
        )
        // Seeking that session-relative time must return to the current native
        // window coordinate rather than asking Media3 to seek past its window.
        assertEquals(
            3_000,
            media3NativeSeekPositionMillis(
                kindHint = PlaybackKind.OnDemand,
                sessionPositionMillis = 29_000,
                windowPositionInFirstPeriodMillis = 26_000,
            ),
        )
    }

    @Test
    fun rollingWindowMappingDoesNotChangeLiveCoordinatesOrTrustUnsetOffsets() {
        assertEquals(
            3_000,
            media3SessionPositionMillis(
                kindHint = PlaybackKind.Live,
                nativePositionMillis = 3_000,
                windowPositionInFirstPeriodMillis = 26_000,
            ),
        )
        assertEquals(
            3_000,
            media3NativeSeekPositionMillis(
                kindHint = PlaybackKind.SeekableLive,
                sessionPositionMillis = 3_000,
                windowPositionInFirstPeriodMillis = 26_000,
            ),
        )
        assertEquals(
            3_000,
            media3SessionPositionMillis(
                kindHint = PlaybackKind.OnDemand,
                nativePositionMillis = 3_000,
                windowPositionInFirstPeriodMillis = androidx.media3.common.C.TIME_UNSET,
            ),
        )
    }

    @Test
    fun mapsLiveDvrAndVodWithoutGivingPlainLiveASeekBar() {
        val live = media3Timeline(isLive = true, isSeekable = false, durationMillis = null)
        val dvr = media3Timeline(isLive = true, isSeekable = true, durationMillis = 90_000)
        val vod = media3Timeline(isLive = false, isSeekable = true, durationMillis = 120_000)
        val forcedPlainLive = media3Timeline(
            isLive = true,
            isSeekable = true,
            durationMillis = 90_000,
            kindHint = PlaybackKind.Live,
        )

        assertEquals(PlaybackKind.Live, live.kind)
        assertFalse(live.showSeekBar)
        assertEquals(PlaybackKind.SeekableLive, dvr.kind)
        assertTrue(dvr.showSeekBar)
        assertEquals(90_000, dvr.seekableRange?.endMillis)
        assertEquals(PlaybackKind.OnDemand, vod.kind)
        assertTrue(vod.showSeekBar)
        assertFalse(forcedPlainLive.showSeekBar)
    }

    @Test
    fun mapsUnsupportedMediaToTypedMpvFallbacks() {
        val container = media3ErrorCodeToAir(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        val codec = media3ErrorCodeToAir(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        val network = media3ErrorCodeToAir(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

        assertEquals(PlaybackErrorCode.UnsupportedContainer, container.code)
        assertEquals("mpv", container.suggestedBackend)
        assertEquals(PlaybackErrorCode.UnsupportedCodec, codec.code)
        assertEquals("mpv", codec.suggestedBackend)
        assertEquals(PlaybackErrorCode.Network, network.code)
    }

    @Test
    fun videoRenditionLabelPrefersAUsefulNameThenResolution() {
        assertEquals("Director encode", media3VideoTrackLabel("Director encode", 1080, "hevc", 0))
        assertEquals("720p", media3VideoTrackLabel(null, 720, "h264", 0))
        assertEquals("av1", media3VideoTrackLabel("", 0, "av1", 0))
        assertEquals("Video 2", media3VideoTrackLabel(null, 0, null, 1))
    }

    @Test
    fun livePoliciesPreserveDefaultsOrRequestNativeTargetOffsets() {
        assertEquals(null, media3LiveConfiguration(LivePlaybackPolicy.Balanced))
        assertEquals(
            3_000,
            media3LiveConfiguration(LivePlaybackPolicy.LowLatency)?.targetOffsetMs,
        )
        assertEquals(
            10_000,
            media3LiveConfiguration(LivePlaybackPolicy.Resilient)?.targetOffsetMs,
        )
    }

    @Test
    fun resilientPolicyUsesDocumentedBoundedMedia3StreamingThresholds() {
        val tuning = media3LivePolicyTuning(
            LivePlaybackPolicy.Resilient,
            AndroidMedia3ResilientBufferConfig(),
        )

        assertEquals(Media3BufferMode.Resilient, tuning.bufferMode)
        assertEquals(10_000, tuning.targetLiveOffsetMillis)
        assertEquals(10_000, tuning.minimumBufferMillis)
        assertEquals(15_000, tuning.maximumBufferMillis)
        assertEquals(1_000, tuning.bufferForPlaybackMillis)
        assertEquals(5_000, tuning.bufferForPlaybackAfterRebufferMillis)
        assertEquals(64 * 1024 * 1024, tuning.bufferMemoryThresholdBytes)
        assertTrue(tuning.prioritizeTimeOverSizeThresholds)
        assertTrue(buildMedia3LoadControl(tuning) != null)
    }

    @Test
    fun lowLatencyAndBalancedKeepMedia3DefaultLoadControl() {
        val config = AndroidMedia3ResilientBufferConfig()
        val lowLatency = media3LivePolicyTuning(LivePlaybackPolicy.LowLatency, config)
        val balanced = media3LivePolicyTuning(LivePlaybackPolicy.Balanced, config)

        assertEquals(Media3BufferMode.NativeDefault, lowLatency.bufferMode)
        assertEquals(Media3BufferMode.NativeDefault, balanced.bufferMode)
        assertEquals(null, buildMedia3LoadControl(lowLatency))
        assertEquals(null, buildMedia3LoadControl(balanced))
        assertEquals(3_000, lowLatency.targetLiveOffsetMillis)
        assertEquals(null, balanced.targetLiveOffsetMillis)
    }

    @Test
    fun resilientConfigurationFlowsToNativeTuningAndAdvancedStatistics() {
        val config = AndroidMedia3ResilientBufferConfig(
            targetLiveOffsetMillis = 12_000,
            minimumBufferMillis = 8_000,
            maximumBufferMillis = 12_000,
            bufferForPlaybackMillis = 750,
            bufferForPlaybackAfterRebufferMillis = 4_000,
            bufferMemoryThresholdBytes = 24 * 1024 * 1024,
        )
        val tuning = media3LivePolicyTuning(LivePlaybackPolicy.Resilient, config)
        val statistics = tuning.statistics()

        assertEquals(12_000, media3LiveConfiguration(LivePlaybackPolicy.Resilient, config)?.targetOffsetMs)
        assertEquals(LivePlaybackPolicy.Resilient, statistics.livePolicy)
        assertEquals(12_000, statistics.targetLiveOffsetMillis)
        assertEquals(8_000, statistics.minimumBufferMillis)
        assertEquals(12_000, statistics.maximumBufferMillis)
        assertEquals(24L * 1024 * 1024, statistics.bufferMemoryThresholdBytes)
    }

    @Test
    fun resilientConfigurationRejectsValuesMedia3WouldSilentlyCap() {
        assertFailsWith<IllegalArgumentException> {
            AndroidMedia3ResilientBufferConfig(
                targetLiveOffsetMillis = 8_000,
                minimumBufferMillis = 10_000,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidMedia3ResilientBufferConfig(
                targetLiveOffsetMillis = 10_000,
                minimumBufferMillis = 5_000,
                maximumBufferMillis = 10_000,
                bufferForPlaybackAfterRebufferMillis = 6_000,
            )
        }
    }

    @Test
    fun behindLiveWindowRecoveryIsLiveOnlyAndBoundedPerAttempt() {
        assertTrue(
            shouldRecoverMedia3BehindLiveWindow(
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                PlaybackKind.Live,
                recoveryInProgress = false,
            ),
        )
        assertTrue(
            shouldRecoverMedia3BehindLiveWindow(
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                kindHint = null,
                recoveryInProgress = false,
            ),
        )
        assertFalse(
            shouldRecoverMedia3BehindLiveWindow(
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                PlaybackKind.OnDemand,
                recoveryInProgress = false,
            ),
        )
        assertFalse(
            shouldRecoverMedia3BehindLiveWindow(
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                PlaybackKind.Live,
                recoveryInProgress = true,
            ),
        )
        assertFalse(
            shouldRecoverMedia3BehindLiveWindow(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackKind.Live,
                recoveryInProgress = false,
            ),
        )
        assertEquals(
            PlaybackErrorCode.Network,
            media3ErrorCodeToAir(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW).code,
        )
    }
}
