package com.getair.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoPlayerContractTest {
    @Test
    fun plainLiveNeverShowsASeekBar() {
        val live = PlaybackTimeline(kind = PlaybackKind.Live)
        assertFalse(live.canSeek)
        assertFalse(live.showSeekBar)
        assertFailsWith<IllegalArgumentException> {
            PlaybackTimeline(PlaybackKind.Live, seekableRange = SeekableRange(0, 10_000))
        }
    }

    @Test
    fun vodAndDvrWindowsCanSeek() {
        assertTrue(PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = 120_000).canSeek)
        assertTrue(
            PlaybackTimeline(
                PlaybackKind.SeekableLive,
                seekableRange = SeekableRange(30_000, 90_000),
                liveEdgeMillis = 90_000,
            ).canSeek,
        )
    }

    @Test
    fun sourceStringNeverLeaksCredentials() {
        val source = PlaybackSource(
            uri = "https://provider.example/live/user/password/1.ts",
            headers = mapOf("Authorization" to "secret"),
        ).toString()
        assertFalse("provider.example" in source)
        assertFalse("Authorization" in source)
        assertFalse("secret" in source)
    }
}
