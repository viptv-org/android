package org.viptv.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLeaseMaintenanceTest {
    @Test fun `successful renewals rearm expiry and cancellation stops future calls`() = runTest {
        var deadline = 60_000L
        var renewals = 0
        val job = launch {
            maintainPlaybackLease({ deadline-testScheduler.currentTime }, { 20_000 }, {
                renewals++; deadline = testScheduler.currentTime+60_000
            }, { fail("Healthy lease must not expire") })
        }
        advanceTimeBy(60_001); runCurrent()
        assertEquals(3, renewals)
        job.cancel(); runCurrent(); advanceTimeBy(60_000)
        assertEquals(3, renewals)
    }
    @Test fun `network failure cannot extend an unrenewed lease`() = runTest {
        var failure: Throwable? = null
        maintainPlaybackLease({ 60_000-testScheduler.currentTime }, { 20_000 }, { throw IOException("Offline") }, { failure = it })
        assertEquals(60_000L, testScheduler.currentTime)
        assertEquals("playback_expired", (failure as GatewayError).code)
    }
    @Test fun `stalled heartbeat cannot survive expiry`() = runTest {
        var failure: Throwable? = null
        maintainPlaybackLease({ 60_000-testScheduler.currentTime }, { 20_000 }, { awaitCancellation() }, { failure = it })
        assertEquals(60_000L, testScheduler.currentTime)
        assertEquals("playback_expired", (failure as GatewayError).code)
    }
    @Test fun `authorization refusal is terminal at the first renewal`() = runTest {
        val denied = GatewayError(403, "Authorization expired", "authorization_expired")
        var failure: Throwable? = null
        maintainPlaybackLease({ 60_000-testScheduler.currentTime }, { 20_000 }, { throw denied }, { failure = it })
        assertSame(denied, failure)
        assertEquals(20_000L, testScheduler.currentTime)
    }
}
