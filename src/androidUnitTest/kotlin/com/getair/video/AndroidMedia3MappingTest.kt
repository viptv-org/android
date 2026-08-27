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

        assertEquals(PlaybackKind.Live, live.kind)
        assertFalse(live.showSeekBar)
        assertEquals(PlaybackKind.SeekableLive, dvr.kind)
        assertTrue(dvr.showSeekBar)
        assertEquals(90_000, dvr.seekableRange?.endMillis)
        assertEquals(PlaybackKind.OnDemand, vod.kind)
        assertTrue(vod.showSeekBar)
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
}
