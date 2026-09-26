package org.viptv.app

import kotlin.test.*

class UpNextCountdownTest {
    @Test fun pauseBufferingAndMenusDoNotConsumeTheVisibleCountdown() {
        val clock = NextEpisodeCountdown()
        assertFalse(clock.advance(2_250, true))
        assertEquals(8, UpNextPrompt(Media("next", "series"), clock.remainingMillis).seconds)
        assertFalse(clock.advance(60_000, false))
        assertEquals(7_750, clock.remainingMillis)
        assertFalse(clock.advance(7_749, true))
        assertEquals(1, UpNextPrompt(Media("next", "series"), clock.remainingMillis).seconds)
        assertTrue(clock.advance(1, true))
        assertEquals(0, clock.remainingMillis)
    }
    @Test fun replacementStartsWithACompleteIndependentCountdown() {
        val old = NextEpisodeCountdown()
        old.advance(10_000, true)
        val replacement = NextEpisodeCountdown()
        assertFalse(replacement.advance(-100, true))
        assertEquals(10_000, replacement.remainingMillis)
    }
}
