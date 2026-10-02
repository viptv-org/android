package org.viptv.app

import android.view.KeyEvent
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/** Opt-in real HTTPS/controller Guide flow on the owned fixture TV emulator. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GuideRemoteFixtureTest {
    @get:Rule val compose = createComposeRule()
    private val origin = "https://10.0.2.2:9444"

    @Test fun filterSearchFutureDetailsBackPreservesExactCellAndNeverAutoplays() {
        assumeTrue("Requires paired isolated HTTPS fixture; see qualification/README.md",
            InstrumentationRegistry.getArguments().getString("viptvGuideFixture") == "true")
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,
            context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
        lateinit var controller: AppController
        compose.runOnUiThread {
            controller = AppController(context, origin)
        }
        try {
            compose.waitUntil(15_000) {
                controller.state.value.selectedProfile != null && !controller.state.value.loading
            }
            val before = playbackAdmissions()
            compose.runOnUiThread { controller.navigate(Destination.Live) }
            compose.waitUntil(15_000) {
                controller.state.value.guideUi.channels.isNotEmpty() &&
                    controller.state.value.guideUi.categoryPage.loaded &&
                    controller.state.value.guideUi.schedulesByChannelId.isNotEmpty()
            }
            compose.setContent {
                val state by controller.state.collectAsState()
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalContentFocus provides remember { FocusRequester() }) {
                    ViptvTheme(false, Color.White) {
                        GuideScreen(state, (state.route as? Route.Guide)?.channel, controller)
                    }
                }
            }
            // Only this initial setup may request focus explicitly.
            compose.onNodeWithText("All").performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
            repeat(3) { press(Key.DirectionRight) }
            compose.onNodeWithText("News").assertIsFocused()
            press(Key.DirectionCenter)
            compose.waitUntil(15_000) {
                !controller.state.value.loading && controller.state.value.guideUi.channels.any { it.name == "CNN" }
            }
            compose.waitForIdle()
            // A rendered loading frame can focus the selected channel; a fast
            // replacement can retain the filter. Both use ordinary remote keys.
            if (focusedLabels().contains("ABC News Live")) press(Key.DirectionUp)
            reach("Search channels", Key.DirectionRight)
            press(Key.DirectionCenter)
            val field = compose.onNode(hasSetTextAction())
            field.performTextInput("CNBC")
            field.performImeAction()
            compose.waitUntil(15_000) {
                !controller.state.value.loading && controller.state.value.guideUi.channels.map { it.name } == listOf("CNBC") &&
                    controller.state.value.guideUi.schedulesByChannelId.values.flatten().any { it.title == "Halftime Report" }
            }
            compose.waitForIdle()
            if (focusedLabels().contains("Search channels")) {
                reach("News", Key.DirectionLeft)
                press(Key.DirectionDown)
            }
            reach("Halftime Report", Key.DirectionRight, 4)
            compose.onNodeWithText("Halftime Report").assertIsFocused()
            val cell = compose.onNodeWithText("Halftime Report").fetchSemanticsNode()
            press(Key.DirectionCenter)
            compose.onNodeWithText("Watch live").assertExists()
            assertEquals(before, playbackAdmissions())
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("Watch live").assertDoesNotExist()
            compose.onNodeWithText("Halftime Report").assertIsFocused()
            val restored = compose.onNodeWithText("Halftime Report").fetchSemanticsNode()
            assertEquals(cell.id, restored.id)
            assertEquals(cell.boundsInRoot, restored.boundsInRoot)
            assertEquals("cnbc", controller.state.value.guideUi.selectedChannelId)
            assertTrue(controller.state.value.route is Route.Guide)
            assertEquals(before, playbackAdmissions())
        } finally {
            compose.runOnUiThread { controller.close() }
        }
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focusedLabels(): List<String> {
        val config = compose.onNode(isFocused()).fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.Text)) config[SemanticsProperties.Text].map { it.text } else emptyList()
    }

    private fun reach(label: String, direction: Key, limit: Int = 16) {
        repeat(limit) {
            if (label in focusedLabels()) return
            press(direction)
        }
        throw AssertionError("Expected fixture Guide control was not remotely reachable")
    }

    private fun playbackAdmissions(): Int {
        val connection = URL("$origin/__requests").openConnection().apply {
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        val requests = connection.getInputStream().bufferedReader().use { JSONArray(it.readText()) }
        return (0 until requests.length()).count {
            val request = requests.getJSONObject(it)
            request.getString("method") == "POST" && request.getString("path") == "/api/v2/playback"
        }
    }
}
