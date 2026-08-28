package com.getair.video

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun externalSubtitleSourceNeverLeaksItsUrl() {
        val subtitle = ExternalSubtitleSource(
            id = "english",
            uri = "https://secret.invalid/subtitles/token/file.ass",
            mimeType = "text/x-ssa",
            language = "eng",
        )
        val source = PlaybackSource("https://media.invalid/movie.mkv", externalSubtitles = listOf(subtitle))

        assertFalse("secret.invalid" in subtitle.toString())
        assertFalse("token" in subtitle.toString())
        assertFalse("secret.invalid" in source.toString())
        assertEquals(1, source.externalSubtitles.size)
    }

    @Test
    fun sourceCopiesCredentialHeadersAndSubtitleListAtTheBoundary() {
        val headers = mutableMapOf("Authorization" to "first")
        val subtitles = mutableListOf(
            ExternalSubtitleSource("en", "https://subtitle.invalid/en.vtt", "text/vtt"),
        )
        val source = PlaybackSource("https://media.invalid/movie.mkv", headers = headers, externalSubtitles = subtitles)

        headers["Authorization"] = "mutated"
        subtitles.clear()

        assertEquals("first", source.headers["Authorization"])
        assertEquals(1, source.externalSubtitles.size)
    }
}
