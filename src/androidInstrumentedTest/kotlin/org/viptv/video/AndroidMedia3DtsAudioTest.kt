@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidMedia3DtsAudioTest {
    @Test fun sixChannelDtsIsSelectedAndRenderedWithoutAPlatformDtsDecoder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val backend = withContext(Dispatchers.Main.immediate) {
            AndroidMedia3Backend(context, 5_000, probeMedia3Capabilities(context), AndroidMedia3ResilientBufferConfig())
        }
        val player = AndroidMedia3VideoPlayer(backend)
        val advancing = CompletableDeferred<Unit>()
        val decoder = CompletableDeferred<String>()
        withContext(Dispatchers.Main.immediate) {
            backend.player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime,
                    decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                    decoder.complete(decoderName)
                }
                override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                    advancing.complete(Unit)
                }
            })
        }
        try {
            player.open(PlaybackSource("asset:///dts-tone.mka", kindHint = PlaybackKind.OnDemand), true)
            val audio = player.audioTracks.value.single()
            assertEquals("audio/vnd.dts", audio.codec)
            assertEquals(6, audio.channels)
            assertEquals(audio.id, assertNotNull(player.state.value.selectedAudioTrackId))
            assertTrue("dts" in player.capabilities.value.audioCodecs)
            withTimeout(5_000) { advancing.await() }
            assertTrue(withTimeout(5_000) { decoder.await() }.startsWith("ffmpeg"))
        } finally {
            player.close()
        }
    }
}
