@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.viptv.app.hero

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeroArtPreloaderTest {
    @Test fun immediatelyCompletedLoadsDoNotRestartThePreloadWorker() = runTest(UnconfinedTestDispatcher()) {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; url }, { 4 })
        art.setWindow("a", listOf("b", "c"))
        assertEquals(listOf("b", "c"), calls)
        art.close()
    }

    @Test fun speculativeLoadsRunSerially() = runTest {
        var active = 0
        var peak = 0
        val art = HeroArtPreloader(backgroundScope, { url: String ->
            active++; peak = maxOf(peak, active)
            try { delay(250); url } finally { active-- }
        }, { 4 })
        art.setWindow("a", listOf("b", "c"))
        advanceTimeBy(500); runCurrent()
        assertEquals(1, peak)
        assertEquals(0, active)
        art.close()
    }

    @Test fun entryCountIsBoundedEvenWhenBitmapsAreSmall() = runTest {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; url }, { 1 })
        for (url in listOf("a", "b", "c", "d")) art.load(url)
        art.load("b")
        assertEquals(1, calls.count { it == "b" })
        art.load("a")
        assertEquals(2, calls.count { it == "a" })
        art.close()
    }

    @Test fun warmedNeighbourHasNoFetchDecodeDelayWhenFocused() = runTest {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; delay(250); url }, { 4 })
        art.setWindow("a", listOf("b", "c"))
        advanceTimeBy(250); runCurrent()
        val start = testScheduler.currentTime
        assertEquals("b", art.load("b"))
        assertEquals(start, testScheduler.currentTime)
        assertEquals(1, calls.count { it == "b" })
        art.close()
    }

    @Test fun focusingAnInFlightNeighbourSharesItsDecode() = runTest {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; delay(250); url }, { 4 })
        art.setWindow("a", listOf("b", "c")); runCurrent()
        advanceTimeBy(100)
        art.setWindow("b", listOf("c", "a"))
        val focused = async { art.load("b") }; runCurrent()
        advanceTimeBy(150); runCurrent()
        assertEquals("b", focused.await())
        assertEquals(1, calls.count { it == "b" })
        art.close()
    }

    @Test fun unrelatedForegroundWorkCancelsSpeculationAndLoadsFirst() = runTest {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> started += url; delay(250); completed += url; url }, { 4 })
        art.setWindow("a", listOf("b", "c", "d")); runCurrent()
        val foreground = async { art.load("z") }; runCurrent()
        advanceTimeBy(250); runCurrent()
        assertEquals("z", foreground.await())
        assertEquals(listOf("z"), completed)
        assertFalse("d" in started)
        art.close()
    }

    @Test fun rapidWindowChangesCancelOldNeighbours() = runTest {
        val finished = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> delay(250); finished += url; url }, { 4 })
        art.setWindow("a", listOf("b", "c")); runCurrent()
        art.setWindow("x", listOf("y", "z")); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf("y", "z"), finished)
        art.close()
    }

    @Test fun duplicateAndBlankUrlsDoNotConsumePreloadSlots() = runTest {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; url }, { 4 })
        art.setWindow("a", listOf("", "a", "b", "b", "c", "d")); runCurrent()
        assertEquals(listOf("b", "c"), calls)
        art.close()
    }

    @Test fun byteBudgetEvictsOldArtAndDoesNotRetainOversizeBitmaps() = runTest {
        val calls = mutableListOf<String>()
        val art = HeroArtPreloader(backgroundScope, { url: String -> calls += url; url }, { if (it == "large") 20 else 6 }, maxBytes = 12)
        art.load("a"); art.load("b"); art.load("c")
        art.load("b")
        assertEquals(1, calls.count { it == "b" })
        art.load("a")
        assertEquals(2, calls.count { it == "a" })
        art.load("large"); art.load("large")
        assertEquals(2, calls.count { it == "large" })
        art.close()
    }

    @Test fun screenDisposalCancelsRequestsAndClearsRetainedArt() = runTest {
        var complete = false
        val art = HeroArtPreloader(backgroundScope, { _: String -> delay(250); complete = true; "image" }, { 4 })
        art.setWindow("a", listOf("b")); runCurrent()
        art.close()
        advanceTimeBy(500); runCurrent()
        assertFalse(complete)
        assertEquals(null, art.load("b"))
    }

    @Test fun failedSpeculationDoesNotCancelForegroundOrPreventRetry() = runTest {
        var attempts = 0
        val art = HeroArtPreloader(backgroundScope, { _: String -> if (++attempts == 1) error("fixture") else "image" }, { 4 })
        art.setWindow("a", listOf("b")); runCurrent()
        assertEquals("image", art.load("b"))
        assertEquals(2, attempts)
        art.close()
    }

    @Test fun adjacencyStaysWithinTheVisibleRow() {
        assertEquals(listOf("b", "c"), neighbouringHeroItems(listOf("a", "b", "c", "d"), 0))
        assertEquals(listOf("c", "a"), neighbouringHeroItems(listOf("a", "b", "c", "d"), 1))
        assertEquals(listOf("c", "b"), neighbouringHeroItems(listOf("a", "b", "c", "d"), 3))
        assertTrue(neighbouringHeroItems(emptyList<String>(), -1).isEmpty())
    }
}
