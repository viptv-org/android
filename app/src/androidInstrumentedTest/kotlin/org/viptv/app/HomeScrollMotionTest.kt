@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package org.viptv.app

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real Home, DPAD input and numeric scroll positions; no image fixtures. */
@RunWith(AndroidJUnit4::class)
class HomeScrollMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueWatchingAndHeroKeepTheTopViewport() = withHome { list ->
        compose.onNodeWithText("Resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        card("Queue fixture").assertIsFocused()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Resume").assertIsFocused()
        assertEquals(0, list.firstVisibleItemScrollOffset)
    }

    @Test fun returningToContinueWatchingMovesOverMultipleFrames() = withHome { list ->
        compose.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionDown) }
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
        val before = list.firstVisibleItemScrollOffset
        assertTrue("Fixture must scroll away from the first shelf: $before", before > 100)
        compose.mainClock.autoAdvance = false
        val offsets = mutableListOf(before)
        card("Shelf fixture").performKeyInput { pressKey(Key.DirectionUp) }
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            offsets += list.firstVisibleItemScrollOffset
        }
        compose.mainClock.autoAdvance = true
        card("Queue fixture").assertIsFocused()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        val maxStep = offsets.zipWithNext { a, b -> abs(a - b) }.maxOrNull() ?: 0
        println("HOME_SCROLL before=$before maxFrameStep=$maxStep samples=${offsets.joinToString(",")}")
        assertTrue("Top restoration teleported $maxStep of $before pixels in one frame", maxStep < before * .75f)
    }

    @Test fun leavingContinueWatchingMovesOverMultipleFrames() = withHome { list ->
        compose.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionDown) }
        card("Queue fixture").assertIsFocused()
        compose.mainClock.autoAdvance = false
        val offsets = mutableListOf(0)
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            offsets += list.firstVisibleItemScrollOffset
        }
        compose.mainClock.autoAdvance = true
        card("Shelf fixture").assertIsFocused()
        val distance = list.firstVisibleItemScrollOffset
        val maxStep = offsets.zipWithNext { a, b -> abs(a - b) }.maxOrNull() ?: 0
        println("HOME_SCROLL_DOWN distance=$distance maxFrameStep=$maxStep")
        assertTrue("Next shelf must be revealed", distance > 100)
        assertTrue("Leaving Continue Watching moved too harshly: $maxStep of $distance pixels", maxStep < distance * .4f)
    }

    @Test fun reversingDirectionCancelsTheOlderTopRestore() = withHome { list ->
        compose.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionDown) }
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
        compose.mainClock.autoAdvance = false
        card("Shelf fixture").performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeByFrame()
        card("Queue fixture").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        repeat(45) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.autoAdvance = true
        card("Shelf fixture").assertIsFocused()
        assertTrue("Old top restore must not hide the newer focused shelf", list.firstVisibleItemScrollOffset > 100)
    }

    @Test fun lowerShelvesKeepTheLastContinueWatchingHero() = withHome {
        compose.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionDown) }
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue next fixture").assertIsFocused()
        compose.onNode(hasText("Queue next fixture") and !hasClickAction()).assertExists()
        card("Queue next fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
        compose.onNode(hasText("Queue next fixture") and !hasClickAction()).assertExists()
        compose.onNodeWithText("Resume").assertExists()
    }

    private fun card(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun withHome(action: (LazyListState) -> Unit) {
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, "https://example.invalid")
        val list = LazyListState()
        val initial = FocusRequester()
        val memory = FocusMemory()
        try {
            controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false,
                homeLoading = false, shelves = listOf(
                    HomeShelf("Continue Watching", listOf(
                        Media("queue", "movie", name = "Queue fixture", positionMillis = 60_000, durationMillis = 120_000),
                        Media("queue-next", "movie", name = "Queue next fixture", positionMillis = 30_000, durationMillis = 120_000)), isQueueShelf = true),
                    HomeShelf("Shelf one", listOf(Media("one", "movie", name = "Shelf fixture"))),
                    HomeShelf("Shelf two", listOf(Media("two", "movie", name = "Lower fixture")))))
            compose.setContent {
                val state by controller.state.collectAsState()
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalContentFocus provides initial, LocalFocusMemory provides memory,
                    LocalBringIntoViewSpec provides VisibleFocusScroll) {
                    ViptvTheme(false, Color.White) { Box(Modifier.fillMaxSize()) { HomeScreen(state, controller, list) } }
                }
            }
            action(list)
        } finally {
            compose.mainClock.autoAdvance = true
            controller.scope.cancel()
        }
    }
}
