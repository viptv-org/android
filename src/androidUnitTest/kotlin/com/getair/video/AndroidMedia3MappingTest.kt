package com.getair.video

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMedia3MappingTest {
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
