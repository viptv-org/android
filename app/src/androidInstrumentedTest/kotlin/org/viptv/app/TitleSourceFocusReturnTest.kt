package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TitleSourceFocusReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backFromManualPickerRestoresTvTitleSourceFocus() {
        lateinit var controller: AppController
        compose.runOnUiThread {
            controller = AppController(ApplicationProvider.getApplicationContext(), "https://127.0.0.1:1")
            controller._state.value = AppState(route = Route.Details(Media("focus-fixture", "movie", "Fixture movie")))
        }
        try {
            compose.setContent {
                val state by controller.state.collectAsState()
                val route = state.route
                val key = route.screenKey()
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                val saved = rememberSaveableStateHolder()
                val initial = remember(key) { FocusRequester() }
                BackHandler(route is Route.Sources) { controller.back() }
                CompositionLocalProvider(LocalTv provides true, LocalContentFocus provides initial) {
                    ViptvTheme(false, Color.White) {
                        saved.SaveableStateProvider(key) {
                            when (route) {
                                is Route.Details -> DetailsScreen(route.media, controller)
                                is Route.Sources -> SourcePicker(route.media, state.sources, controller)
                                else -> error("Unexpected fixture route")
                            }
                        }
                    }
                }
            }
            compose.onNodeWithContentDescription("Choose source")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil { controller.state.value.route is Route.Sources }
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil { controller.state.value.route is Route.Details }
            compose.onNodeWithContentDescription("Choose source").assertIsFocused()
            // Consuming the source return must not redirect the next Play visit.
            compose.onNodeWithText("Play")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil { controller.state.value.route is Route.Sources }
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil { controller.state.value.route is Route.Details }
            compose.onNodeWithText("Play").assertIsFocused()
        } finally {
            compose.runOnUiThread { controller.close() }
        }
    }
}
