@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package org.viptv.app

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Home, DPAD input and numeric scroll positions; no image fixtures. */
@RunWith(AndroidJUnit4::class)
class HomeScrollMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeStartsOnCardsWithoutHeroControls() = withHome { list ->
        card("Queue fixture").assertIsFocused()
        compose.onNodeWithText("Resume").assertDoesNotExist()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Hold OK for options").assertDoesNotExist()
        compose.onNodeWithText("FEATURED").assertDoesNotExist()
        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        assertEquals(0, list.firstVisibleItemIndex)
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionUp) }
        card("Queue fixture").assertIsFocused()
    }

    @Test fun movingBetweenShelvesShowsOneRowAndUpdatesTheFixedHero() = withHome { list ->
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
        assertEquals(1, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
        compose.onNode(hasText("Shelf fixture") and !hasClickAction()).assertExists()
        card("Shelf fixture").performKeyInput { pressKey(Key.DirectionUp) }
        card("Queue fixture").assertIsFocused()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
    }

    @Test fun horizontalCardFocusUpdatesTheHero() = withHome {
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue next fixture").assertIsFocused()
        compose.onNode(hasText("Queue next fixture") and !hasClickAction()).assertExists()
    }

    @Test fun aWindowHandoffPreservesTheSelectedCard() {
        var dialog by mutableStateOf(false)
        withHome(overlay = {
            if (dialog) Dialog(onDismissRequest = { dialog = false }) { Box(Modifier.size(40.dp)) }
        }) {
            card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
            card("Queue next fixture").assertIsFocused()
            compose.runOnIdle { dialog = true }
            compose.runOnIdle { dialog = false }
            card("Queue next fixture").assertIsFocused()
        }
    }

    @Test fun reversingRowsDuringAnimationKeepsTheLatestFocus() = withHome { list ->
        compose.mainClock.autoAdvance = false
        try {
            card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
            repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            card("Shelf fixture").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
            repeat(20) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            card("Queue fixture").assertIsFocused()
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun carouselKeepsLeadingFocusUntilTheEnd() = withHome(wideQueue = true) {
        val start = card("Queue fixture").fetchSemanticsNode().boundsInRoot.left
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue next fixture").assertIsFocused()
        assertEquals(start, card("Queue next fixture").fetchSemanticsNode().boundsInRoot.left, 2f)
        card("Queue next fixture").performKeyInput { pressKey(Key.DirectionRight) }
        for (number in 3..8) card("Queue item $number").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue item 9").assertIsFocused()
        assertTrue("At the row end, focus moves through the remaining visible cards", card("Queue item 9").fetchSemanticsNode().boundsInRoot.left > start + 100f)
    }

    @Test fun continueWatchingAndOtherCarouselsShareTheLeadingEdge() = withHome {
        val queueLeft = card("Queue fixture").fetchSemanticsNode().boundsInRoot.left
        assertEquals(104f, queueLeft, 1f)
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
        assertEquals(queueLeft, card("Shelf fixture").fetchSemanticsNode().boundsInRoot.left, 1f)
        card("Shelf fixture").performKeyInput { pressKey(Key.DirectionUp) }
        card("Queue fixture").assertIsFocused()
        assertEquals(queueLeft, card("Queue fixture").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun tvHomeSkipsLiveRowsDuringVerticalNavigation() = withHome(includeLive = true) {
        compose.onNodeWithText("LIVE NOW").assertDoesNotExist()
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused()
    }

    @Test fun delayedQueueStartsAtContinueWatchingInsteadOfFollowingCatalogInsertion() {
        lateinit var controller: AppController
        withHome(pendingQueue = true, onController = { controller = it }) { list ->
            compose.runOnIdle {
                val queue = HomeShelf("Continue Watching", listOf(Media("queue", "movie", "Queue fixture")), isQueueShelf = true)
                controller._state.value = controller.state.value.copy(shelves = listOf(queue) + controller.state.value.shelves)
            }
            card("Queue fixture").assertIsFocused()
            assertEquals(0, list.firstVisibleItemIndex)
        }
    }

    @Test fun carouselHeadingStaysStillWhenRowsChange() = withHome {
        val top = compose.onNodeWithText("CONTINUE WATCHING").fetchSemanticsNode().boundsInRoot.top
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(top, compose.onNodeWithText("SHELF ONE").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun rowReturnWorksAfterHorizontalScroll() = withHome(wideQueue = true) {
        card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue next fixture").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue item 3").performKeyInput { pressKey(Key.DirectionRight) }
        card("Queue item 4").performKeyInput { pressKey(Key.DirectionDown) }
        card("Shelf fixture").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(hasClickAction() and androidx.compose.ui.test.isFocused()).assertExists()
        compose.onNodeWithText("CONTINUE WATCHING").assertExists()
    }

    @Test fun rapidDirectionalBurstKeepsFocusAndTheLatestRow() = withHome { list ->
        card("Queue fixture").performKeyInput {
            repeat(15) { pressKey(Key.DirectionDown) }
            repeat(15) { pressKey(Key.DirectionUp) }
        }
        card("Queue fixture").assertIsFocused()
        assertEquals(0, list.firstVisibleItemIndex)
    }

    @Test fun mouseInputModeDoesNotStrandTelevisionFocus() {
        lateinit var inputMode: InputModeManager
        withHome(onInputMode = { inputMode = it }) {
            card("Queue fixture").assertIsFocused()
            compose.runOnIdle { inputMode.requestInputMode(InputMode.Touch) }
            card("Queue fixture").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            card("Shelf fixture").assertIsFocused()
        }
    }

    @Test fun televisionFocusKeepsFeedMetadataWithoutRequestingReplacement() {
        val requested = java.util.concurrent.CountDownLatch(1)
        val socket = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = kotlin.concurrent.thread(isDaemon = true) {
            try {
                while (!socket.isClosed) socket.accept().use { connection ->
                    val reader = connection.getInputStream().bufferedReader()
                    val target = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    if (target.contains("/meta/")) requested.countDown()
                    val body = "{}"
                    connection.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n" + body).toByteArray())
                }
            } catch (_: java.net.SocketException) { }
        }
        try {
            withHome(origin = "http://127.0.0.1:${socket.localPort}", onController = { controller ->
                compose.runOnIdle {
                    controller._state.value = controller.state.value.copy(selectedProfile = Profile("fixture", "Fixture"),
                        shelves = controller.state.value.shelves.map { shelf -> shelf.copy(items = shelf.items.map { it.copy(description = "Original metadata") }) })
                }
            }) {
                card("Queue fixture").performKeyInput { pressKey(Key.DirectionRight) }
                card("Queue next fixture").assertIsFocused()
                assertTrue("TV focus must not request replacement metadata", !requested.await(600, java.util.concurrent.TimeUnit.MILLISECONDS))
                compose.onNodeWithText("Original metadata").assertExists()
            }
        } finally { socket.close(); worker.join(1_000) }
    }

    private fun card(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun withHome(origin: String = "https://example.invalid", wideQueue: Boolean = false, includeLive: Boolean = false, pendingQueue: Boolean = false, onController: (AppController) -> Unit = {}, onInputMode: (InputModeManager) -> Unit = {}, overlay: @androidx.compose.runtime.Composable () -> Unit = {}, action: (LazyListState) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureContext = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("home-scroll-fixture-$name", mode)
        }
        val controller = AppController(fixtureContext, origin)
        compose.waitUntil(5_000) { !controller.state.value.sessionRestoring }
        val list = LazyListState()
        val initial = FocusRequester()
        val memory = FocusMemory()
        try {
            controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false,
                homeLoading = pendingQueue, shelves = listOf(
                    HomeShelf("Continue Watching", listOf(
                        Media("queue", "movie", name = "Queue fixture", positionMillis = 60_000, durationMillis = 120_000),
                        Media("queue-next", "movie", name = "Queue next fixture", positionMillis = 30_000, durationMillis = 120_000)) + (if (wideQueue) (3..9).map { Media("queue-$it", "movie", "Queue item $it") } else emptyList()), isQueueShelf = true),
                    HomeShelf("Shelf one", listOf(Media("one", "movie", name = "Shelf fixture"))),
                    HomeShelf("Shelf two", listOf(Media("two", "movie", name = "Lower fixture")))).let { rows ->
                        if (pendingQueue) rows.filterNot { it.isQueueShelf } else if (includeLive) rows.toMutableList().apply { add(1, HomeShelf("Live now", listOf(Media("live", "live", "Live fixture")))) } else rows
                    })
            compose.setContent {
                onInputMode(LocalInputModeManager.current)
                val state by controller.state.collectAsState()
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalContentFocus provides initial, LocalFocusMemory provides memory,
                    LocalBringIntoViewSpec provides VisibleFocusScroll) {
                    ViptvTheme(false, Color.White) { Box(Modifier.fillMaxSize()) { HomeScreen(state, controller, list); overlay() } }
                }
            }
            onController(controller)
            action(list)
        } finally {
            compose.mainClock.autoAdvance = true
            controller.scope.cancel()
        }
    }
}
