@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.assertTrue
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opt-in measurement. The source is an instrumentation argument and must remain private. */
class AndroidMedia3StartupMeasurementTest {
    @Test fun directStreamStartsPromptlyWithAudio() = runBlocking {
        val source = InstrumentationRegistry.getArguments().getString("playbackSource")
        assumeTrue("Requires a private playbackSource argument", !source.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backend = withContext(Dispatchers.Main.immediate) {
            AndroidMedia3Backend(context, 60_000, probeMedia3Capabilities(context), AndroidMedia3ResilientBufferConfig())
        }
        backend.diagnosticTransferListener = object : TransferListener {
            private var start = 0L
            private var firstByte = 0L
            private var bytes = 0L
            private var cpuStart = 0L
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                start = SystemClock.elapsedRealtime(); bytes = 0
            }
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                firstByte = SystemClock.elapsedRealtime()
                cpuStart = android.os.Debug.threadCpuTimeNanos()
                android.util.Log.i("PlaybackMeasurement", "range_start=${dataSpec.position} headers_ms=${firstByte - start}")
            }
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) { bytes += bytesTransferred }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                android.util.Log.i("PlaybackMeasurement", "range_end=${dataSpec.position} body_ms=${SystemClock.elapsedRealtime() - firstByte} body_cpu_ms=${(android.os.Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000} bytes=$bytes")
            }
        }
        val player = AndroidMedia3VideoPlayer(backend)
        try {
            val start = SystemClock.elapsedRealtime()
            val position = InstrumentationRegistry.getArguments().getString("startPositionMillis")?.toLong() ?: 0L
            player.open(PlaybackSource(source!!, kindHint = PlaybackKind.OnDemand, startPositionMillis = position), playWhenReady = true)
            val elapsed = SystemClock.elapsedRealtime() - start
            android.util.Log.i("PlaybackMeasurement", "ready_ms=$elapsed audio_tracks=${player.audioTracks.value.size} audio_codecs=${player.audioTracks.value.map { it.codec }}")
            assertTrue(player.audioTracks.value.isNotEmpty(), "Container audio tracks must be reported")
            assertTrue(player.state.value.selectedAudioTrackId != null, "Audio must be selected")
            withTimeout(5_000) {
                while (player.state.value.positionMillis < 500) delay(50)
            }
            assertTrue(elapsed < 5_000, "Direct-stream startup exceeded 5 seconds: $elapsed ms")
        } finally {
            player.close()
        }
    }
}
