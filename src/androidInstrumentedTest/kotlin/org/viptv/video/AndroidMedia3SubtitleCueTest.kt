package org.viptv.video

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in device proof that selected text subtitles reach [VideoPlayer.subtitleCues].
 *
 * Serve `corpus/output` (after `corpus/generate.sh`) over HTTP and run with
 * `airCorpusUrl=http://127.0.0.1:<port>` (for example through `adb reverse`). Normal connected
 * runs skip this gate when the argument is absent. Each case opens paused, selects, then plays
 * from zero so the 0.25-1.75 s first cue is not missed.
 */
class AndroidMedia3SubtitleCueTest {
    private val baseUrl: String by lazy {
        val url = InstrumentationRegistry.getArguments().getString("airCorpusUrl")
        assumeTrue("airCorpusUrl instrumentation argument is required", !url.isNullOrBlank())
        checkNotNull(url).trimEnd('/')
    }

    private fun player(): AndroidMedia3VideoPlayer =
        AndroidMedia3BackendFactory(InstrumentationRegistry.getInstrumentation().targetContext).createAndroidPlayer()

    @Test fun inStreamDefaultSubRipIsSelectedAndDelivered() = runBlocking {
        val player = player()
        try {
            player.open(PlaybackSource("$baseUrl/h264-multitrack.mkv"), playWhenReady = false)
            val english = awaitTrack(player) { it.language == "en" || it.language == "eng" }
            assertTrue(english.isDefault)
            player.selectSubtitleTrack(english.id)
            playFromStart(player)
            assertEquals("Air subtitle: English", awaitCue(player))
            player.selectSubtitleTrack(null)
            awaitNoCue(player)
        } finally {
            player.close()
        }
    }

    @Test fun forcedSubRipIsChosenWithoutAnExplicitSelection() = runBlocking {
        val player = player()
        try {
            player.open(PlaybackSource("$baseUrl/h264-forced-subtitle.mkv"), playWhenReady = false)
            val forced = awaitTrack(player) { it.isForced }
            assertTrue(forced.isDefault)
            playFromStart(player)
            assertEquals("Air subtitle: English", awaitCue(player))
            assertEquals(forced.id, player.state.value.selectedSubtitleTrackId)
        } finally {
            player.close()
        }
    }

    @Test fun sideloadedWebVttAndSrtAreDelivered() = runBlocking {
        for ((file, mime, text) in listOf(
            Triple("external-en.vtt", "text/vtt", "Air subtitle: WebVTT"),
            Triple("external-en.srt", "application/x-subrip", "Air subtitle: English"),
        )) {
            val player = player()
            try {
                player.open(
                    PlaybackSource(
                        uri = "$baseUrl/h264-multitrack.mkv",
                        externalSubtitles = listOf(
                            ExternalSubtitleSource("sideload", "$baseUrl/$file", mime, language = "en", isDefault = true),
                        ),
                        options = PlaybackOptions(preferredSubtitleLanguage = "en", subtitlesEnabled = true),
                    ),
                    playWhenReady = false,
                )
                val external = awaitTrack(player) { it.external }
                player.selectSubtitleTrack(external.id)
                playFromStart(player)
                assertEquals(text, awaitCue(player), file)
            } finally {
                player.close()
            }
        }
    }

    private suspend fun awaitTrack(player: VideoPlayer, match: (SubtitleTrack) -> Boolean): SubtitleTrack {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            player.subtitleTracks.value.firstOrNull(match)?.let { return it }
            delay(50)
        }
        error("Expected subtitle track was not reported: ${player.subtitleTracks.value}")
    }

    private suspend fun playFromStart(player: VideoPlayer) {
        delay(250)
        player.seekTo(0)
        player.play()
    }

    private suspend fun awaitCue(player: VideoPlayer): String {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            player.subtitleCues.value.firstOrNull()?.let { return it.text }
            delay(25)
        }
        error("No subtitle cue was delivered")
    }

    private suspend fun awaitNoCue(player: VideoPlayer) {
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (player.subtitleCues.value.isEmpty()) return
            delay(25)
        }
        error("Subtitle Off did not clear cues")
    }
}
