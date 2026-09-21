package org.viptv.video

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in player measurement against corpus/live_hls_server.py.
 *
 * Run with `airLiveFixtureUrl=http://127.0.0.1:18080`. The URL is never logged. Normal connected
 * test runs skip this gate when the argument is absent.
 */
class AndroidMedia3LiveResilienceMeasurementTest {
    @Test
    fun resilientReservoirMasksDelayedSegmentAfterItIsEstablished() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val fixtureUrl = arguments.getString("airLiveFixtureUrl")
        assumeTrue("airLiveFixtureUrl instrumentation argument is required", !fixtureUrl.isNullOrBlank())
        val baseUrl = checkNotNull(fixtureUrl).trimEnd('/')
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()
        val startedAt = SystemClock.elapsedRealtime()

        try {
            player.open(
                PlaybackSource(
                    uri = "$baseUrl/hls/live.m3u8",
                    mimeType = "application/x-mpegURL",
                    kindHint = PlaybackKind.Live,
                    options = PlaybackOptions(LivePlaybackPolicy.Resilient),
                ),
            )
            val startupMillis = SystemClock.elapsedRealtime() - startedAt
            val reservoirDeadline = SystemClock.elapsedRealtime() + 30_000
            var reservoirStatistics: PlaybackStatistics? = null
            var maximumBufferedAheadMillis = 0L
            while (SystemClock.elapsedRealtime() < reservoirDeadline) {
                val statistics = player.statistics.value
                maximumBufferedAheadMillis = maxOf(
                    maximumBufferedAheadMillis,
                    statistics.bufferedAheadMillis ?: 0,
                )
                val delayedRequests = delayedRequestCount(baseUrl)
                if ((statistics.bufferedAheadMillis ?: 0) >= 8_000 && delayedRequests == 0L) {
                    reservoirStatistics = statistics
                    break
                }
                if (delayedRequests > 0) break
                delay(250)
            }

            val reservoir = assertNotNull(
                reservoirStatistics,
                "The delayed segment was requested before an 8-second reservoir was measured",
            )
            val baselineRebuffers = reservoir.rebufferCount
            val delayDeadline = SystemClock.elapsedRealtime() + 20_000
            var delayObserved = false
            while (SystemClock.elapsedRealtime() < delayDeadline) {
                val statistics = player.statistics.value
                maximumBufferedAheadMillis = maxOf(
                    maximumBufferedAheadMillis,
                    statistics.bufferedAheadMillis ?: 0,
                )
                if (delayedRequestCount(baseUrl) > 0) {
                    delayObserved = true
                    break
                }
                delay(250)
            }
            assertTrue(delayObserved, "The configured delayed segment was not requested")
            delay(4_000)

            val finalStatistics = player.statistics.value
            maximumBufferedAheadMillis = maxOf(
                maximumBufferedAheadMillis,
                finalStatistics.bufferedAheadMillis ?: 0,
            )
            Log.i(
                "AirLiveMeasurement",
                "startupMs=$startupMillis maxBufferedAheadMs=$maximumBufferedAheadMillis " +
                    "liveOffsetMs=${finalStatistics.liveEdgeOffsetMillis ?: -1} " +
                    "rebufferDelta=${finalStatistics.rebufferCount - baselineRebuffers} " +
                    "droppedFrames=${finalStatistics.droppedVideoFrames}",
            )

            assertEquals(LivePlaybackPolicy.Resilient, finalStatistics.livePolicy)
            assertEquals(10_000, finalStatistics.minimumBufferMillis)
            assertTrue(maximumBufferedAheadMillis >= 8_000)
            assertEquals(baselineRebuffers, finalStatistics.rebufferCount)
        } finally {
            player.close()
        }
    }

    private fun delayedRequestCount(baseUrl: String): Long {
        val connection = URL("$baseUrl/__air/status").openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        return try {
            check(connection.responseCode == 200)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            DELAYED_REQUESTS.find(body)?.groupValues?.get(1)?.toLong()
                ?: error("fixture status omitted delayedRequests")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val DELAYED_REQUESTS = Regex("\\\"delayedRequests\\\":\\s*(\\d+)")
    }
}
