package org.viptv.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Public Guide composition and native remote Back; fixture setup owns no media. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GuideProgrammeFocusReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun futureProgrammeBackRestoresExactTvCellWithoutStartingPlayback() {
        val base: Context = ApplicationProvider.getApplicationContext()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("guide-focus-fixture-$name", mode)
        }
        val now = System.currentTimeMillis()
        val channel = LiveChannel("guide-focus-channel", "Fixture channel")
        val entries = listOf(
            GuideProgramme("Fixture current", now - 20 * 60_000, now + 20 * 60_000),
            GuideProgramme("Fixture future", now + 20 * 60_000, now + 60 * 60_000),
        )
        lateinit var controller: AppController
        compose.runOnUiThread {
            controller = AppController(context, "https://127.0.0.1:1")
        }
        // Finish startup before installing the isolated Guide fixture state.
        compose.waitUntil(10_000) { !controller.state.value.loading }
        compose.runOnUiThread {
            controller.guideScheduleCache[channel.id] = AppController.GuideScheduleCache(entries, now + 300_000)
            controller._state.value = AppState(route = Route.Guide(channel), loading = false,
                guideUi = GuideUiState(channels = listOf(channel), selectedChannelId = channel.id,
                    schedulesByChannelId = mapOf(channel.id to entries), windowStartMillis = GuidePolicy.nowWindow(now)))
        }
        try {
            compose.setContent {
                val state by controller.state.collectAsState()
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalContentFocus provides remember { FocusRequester() }) {
                    ViptvTheme(false, Color.White) { GuideScreen(state, channel, controller) }
                }
            }
            compose.waitForIdle()
            // The sole explicit focus request establishes the starting cell.
            compose.onNodeWithText("Fixture future")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Watch live").assertExists()
            compose.runOnIdle {
                assertTrue(controller.state.value.route is Route.Guide)
                assertNull(controller.state.value.preparingSourceId)
                assertNull(controller.state.value.message)
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("Watch live").assertDoesNotExist()
            compose.onNodeWithText("Fixture future").assertIsFocused()
            compose.runOnIdle {
                assertTrue(controller.state.value.route is Route.Guide)
                assertNull(controller.state.value.preparingSourceId)
                assertNull(controller.state.value.message)
            }
        } finally {
            compose.runOnUiThread { controller.close() }
        }
    }
}
