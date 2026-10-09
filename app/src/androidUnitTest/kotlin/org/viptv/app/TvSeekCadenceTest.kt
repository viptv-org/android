package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvSeekCadenceTest {
    @Test fun discretePressesAlwaysAddTheRequestedThirtySeconds() {
        val cadence = TvSeekCadence()
        var target = 0L
        repeat(4) { target += cadence.step(30_000, 23, 0, it * 100L)!!; cadence.end() }
        assertEquals(120_000L, target)
    }
    @Test fun heldKeyRepeatCannotAccelerateToMinutesPerSecond() {
        val cadence = TvSeekCadence()
        var target = cadence.step(10_000, 22, 0, 0)!!
        repeat(120) { n -> target += cadence.step(10_000, 22, n + 1, (n + 1) * 16L) ?: 0 }
        assertTrue(target in 10_000..50_000, "Two seconds of repeat must stay within fifty seconds: $target")
        cadence.end()
        assertEquals(null, cadence.step(10_000, 22, 121, 2100))
        assertEquals(-10_000L, cadence.step(-10_000, 21, 0, 2200))
    }
}
