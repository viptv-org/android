package org.viptv.app

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Text/semantics assertions only: no screenshots, pixel assertions or image fixtures. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class EpisodeSeasonNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteEdgeCardsFollowAvailableSeasonsWithoutWrapping() {
        withDetails {
            episode("S5 E11").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithText("Previous season").assertIsFocused()
            compose.onNodeWithText("Season 5").assertExists() // Focusing the card does not switch.
            compose.onNodeWithText("Previous season").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Season 0").assertExists()
            episode("S0 E8").assertIsFocused()
            compose.onNodeWithText("Previous season").assertDoesNotExist()
            episode("S0 E8").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText("Next season").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            episode("S5 E11").assertIsFocused()
            episode("S5 E11").performKeyInput { pressKey(Key.DirectionRight) }
            episode("S5 E31").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText("Next season").assertIsFocused()
            compose.onNodeWithText("Season 5").assertExists()
            compose.onNodeWithText("Next season").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Season 9").assertExists()
            episode("S9 E1").assertIsFocused()
            compose.onNodeWithText("Next season").assertDoesNotExist()
            episode("S9 E1").performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithText("Previous season").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            episode("S5 E31").assertIsFocused()
        }
    }

    @Test fun savedEpisodeAndNumberJumpAccountForPreviousSeasonCard() {
        withDetails(media = series().copy(episode = 31)) {
            episode("S5 E31").assertIsFocused()
            compose.onNodeWithContentDescription("Jump to episode number").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("11")
            compose.onNodeWithText("Go").performClick()
            episode("S5 E11").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithText("Previous season").assertIsFocused()
            compose.onNodeWithText("Season 5").assertExists()
        }
    }

    @Test fun episodeHoldMarksWatchedAndUnwatchedWithoutOpeningSources() {
        WatchedEpisodeServer().use { server ->
            withDetails(server.origin) { controller ->
                holdEpisode("S5 E11")
                compose.onNodeWithText("Mark watched").assertIsFocused()
                compose.onNodeWithText("Mark unwatched").assertDoesNotExist()
                assertTrue(controller.state.value.route is Route.Details)
                compose.onNodeWithText("Mark watched").performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(5_000) { detailsEpisode(controller).watched }
                compose.onAllNodesWithTag("episode-watched-badge", useUnmergedTree = true).assertCountEquals(1)
                episode("S5 E11").assertIsFocused()
                compose.onNodeWithText("Season 5").assertExists()
                holdEpisode("S5 E11")
                compose.onNodeWithText("Mark unwatched").assertIsFocused()
                compose.onNodeWithText("Mark watched").assertDoesNotExist()
                compose.onNodeWithText("Mark unwatched").performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(5_000) { !detailsEpisode(controller).watched }
                compose.onAllNodesWithTag("episode-watched-badge", useUnmergedTree = true).assertCountEquals(0)
                episode("S5 E11").assertIsFocused()
                assertEquals(listOf("watched", "unwatched"), server.corrections.toList())
                assertTrue(controller.state.value.route is Route.Details)
            }
        }
    }

    @Test fun sourceBackKeepsNavigatedSeasonAndItsEpisodeFocus() {
        WatchedEpisodeServer().use { server ->
            withDetails(server.origin) { controller ->
                episode("S5 E11").performKeyInput { pressKey(Key.DirectionRight) }
                episode("S5 E31").performKeyInput { pressKey(Key.DirectionRight) }
                compose.onNodeWithText("Next season").performKeyInput { pressKey(Key.DirectionCenter) }
                episode("S9 E1").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.waitUntil(5_000) { controller.state.value.route is Route.Sources }
                compose.runOnIdle { controller.back() }
                compose.onNodeWithText("Season 9").assertExists()
                episode("S9 E1").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
                compose.onNodeWithText("Previous season").assertIsFocused()
            }
        }
    }

    @Test fun cancelAndFailedCorrectionKeepTheEpisodeUnwatchedAndFocused() {
        WatchedEpisodeServer(failCorrection = true).use { server ->
            withDetails(server.origin) { controller ->
                holdEpisode("S5 E11")
                compose.onNodeWithText("Cancel").performClick()
                episode("S5 E11").assertIsFocused()
                assertTrue(server.corrections.isEmpty())
                holdEpisode("S5 E11")
                compose.onNodeWithText("Mark watched").performClick()
                compose.waitUntil(5_000) { controller.state.value.message != null }
                assertTrue(!detailsEpisode(controller).watched)
                compose.onAllNodesWithTag("episode-watched-badge", useUnmergedTree = true).assertCountEquals(0)
                episode("S5 E11").assertIsFocused()
                compose.onNodeWithText("Season 5").assertExists()
                assertTrue(controller.state.value.route is Route.Details)
            }
        }
    }

    private fun episode(title: String) = compose.onNodeWithText(title)

    private fun holdEpisode(title: String) {
        compose.mainClock.autoAdvance = false
        episode(title).assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Watch from beginning").assertExists()
        // Key-up reaches the menu after the hold; it must not trigger a short click.
        InstrumentationRegistry.getInstrumentation().sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    private fun detailsEpisode(controller: AppController) =
        (controller.state.value.route as Route.Details).media.episodes.first { it.id == "show:5:11" }

    private fun withDetails(origin: String = "https://example.invalid", media: Media = series(),
        action: (AppController) -> Unit) {
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, origin)
        try {
            compose.runOnIdle {
                controller._state.value = AppState(route = Route.Details(media, entryId = 1L),
                    selectedProfile = Profile("fixture-profile", "Fixture"), sessionRestoring = false)
            }
            compose.setContent {
                val state by controller.state.collectAsState()
                val holder = rememberSaveableStateHolder()
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) {
                        Box(Modifier.fillMaxSize()) {
                            (state.route as? Route.Details)?.let { route ->
                                holder.SaveableStateProvider(route.screenKey()) { DetailsScreen(route.media, controller) }
                            }
                            state.dialog?.let { ActionDialog(it, controller) }
                        }
                    }
                }
            }
            action(controller)
        } finally {
            compose.mainClock.autoAdvance = true
            controller.scope.cancel()
        }
    }

    private fun series() = Media("show", "series", name = "Fixture Show", season = 5, episode = 11,
        episodes = listOf(0 to listOf(2, 8), 5 to listOf(11, 31), 9 to listOf(1, 7)).flatMap { (season, numbers) ->
            numbers.map { number ->
                Media("show:$season:$number", "episode", name = "Fixture Show", seriesId = "show",
                    season = season, episode = number, episodeTitle = "S$season E$number")
            }
        })
}

/** Isolated profile fixture. Does not use the owner's backend or persist credentials. */
private class WatchedEpisodeServer(private val failCorrection: Boolean = false) : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
    val origin = "http://127.0.0.1:${socket.localPort}"
    val corrections: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile private var watched = false
    private val worker = Thread {
        while (!socket.isClosed) {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val request = reader.readLine()?.split(' ').orEmpty()
                    val method = request.getOrNull(0).orEmpty()
                    val path = request.getOrNull(1).orEmpty()
                    var length = 0
                    while (true) {
                        val header = reader.readLine().orEmpty()
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true))
                            length = header.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val count = reader.read(body, read, length - read)
                        if (count < 0) break
                        read += count
                    }
                    val correction = method == "PUT" && path.endsWith("/progress/correct")
                    if (correction) {
                        val action = JSONObject(String(body)).getString("action")
                        corrections += action
                        if (!failCorrection) watched = action == "watched"
                    }
                    val status = if (correction && failCorrection) "500 Internal Server Error" else "200 OK"
                    val response = when {
                        correction && failCorrection -> """{"error":"Could not save progress."}"""
                        correction -> "{}"
                        path.contains("/progress/series") -> """[{"id":"show:5:11","type":"episode","series_id":"show","season":5,"episode":11,"watched":$watched}]"""
                        else -> "[]"
                    }.toByteArray()
                    client.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().write(response)
                    client.getOutputStream().flush()
                }
            } catch (_: SocketTimeoutException) { /* Check the close flag. */ }
            catch (error: java.net.SocketException) { if (!socket.isClosed) throw error }
        }
    }.apply { isDaemon = true; start() }

    override fun close() { socket.close(); worker.join(2_000) }
}
