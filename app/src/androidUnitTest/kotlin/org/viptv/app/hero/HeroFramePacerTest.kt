package org.viptv.app.hero

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeroFramePacerTest {
    @Test fun restingWorkStaysAtFifteenFramesAcrossDisplayRefreshRates() {
        for (refresh in listOf(30, 60, 120)) {
            val pacer = HeroFramePacer()
            val frames = (0 until refresh).count { pacer.shouldRender(it * 1_000_000_000L / refresh, false) }
            assertEquals(15, frames, "$refresh Hz")
        }
    }

    @Test fun transitionsUseEveryAvailableFrameUpToSixtyHz() {
        for (refresh in listOf(30, 60, 120)) {
            val pacer = HeroFramePacer()
            val frames = (0 until refresh).count { pacer.shouldRender(it * 1_000_000_000L / refresh, true) }
            assertEquals(minOf(60, refresh), frames, "$refresh Hz")
        }
    }

    @Test fun aNewTransitionDoesNotWaitForTheRestingInterval() {
        val pacer = HeroFramePacer()
        assertTrue(pacer.shouldRender(0, false))
        assertFalse(pacer.shouldRender(17_000_000, false))
        assertTrue(pacer.shouldRender(17_000_000, true))
    }

    @Test fun aRecreatedSurfaceRendersImmediately() {
        val pacer = HeroFramePacer()
        assertTrue(pacer.shouldRender(0, false))
        pacer.reset()
        assertTrue(pacer.shouldRender(1, false))
    }
}
