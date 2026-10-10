package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Home and rail composables; text/focus assertions only. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TelevisionRailFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rightFromHomeRailRestoresTheOriginatingCard() {
        withHome {
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithContentDescription("Home").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused()
        }
    }

    @Test fun rightFromHomeRailFocusesContentRecreatedDuringRefresh() {
        withHome { controller ->
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithContentDescription("Home").assertIsFocused()
            val populated = controller.state.value.shelves
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = emptyList(), homeLoading = true) }
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = populated, homeLoading = false) }
            compose.onNodeWithContentDescription("Home").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused()
        }
    }

    @Test fun rightRestoresTheShelfCard() {
        withHome {
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused()
            val card = compose.onNode(hasText("Fixture movie") and hasClickAction())
            card.assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithContentDescription("Home").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            card.assertIsFocused()
        }
    }

    @Test fun rightFromProfileUsesFreshHomeContentAfterRefresh() {
        withHome { controller ->
            compose.onNode(hasText("Fixture movie") and hasClickAction()).performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithContentDescription("Home").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithContentDescription("Search").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithText("Switch profile").assertIsFocused()
            val populated = controller.state.value.shelves
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = emptyList(), homeLoading = true) }
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = populated, homeLoading = false) }
            compose.onNodeWithText("Switch profile").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused()
        }
    }

    @Test fun loadingHomeKeepsNavigationFocusedUntilContentArrives() {
        withHome { controller ->
            compose.onNode(hasText("Fixture movie") and hasClickAction()).performKeyInput { pressKey(Key.DirectionLeft) }
            val populated = controller.state.value.shelves
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = emptyList(), homeLoading = true) }
            compose.onNodeWithContentDescription("Home").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithContentDescription("Home").assertIsFocused()
            compose.onNodeWithText("Home").assertExists() // The rail remains expanded without an actionable target.
            compose.runOnIdle { controller._state.value = controller.state.value.copy(shelves = populated, homeLoading = false) }
            compose.onNodeWithContentDescription("Home").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasText("Fixture movie") and hasClickAction()).assertIsFocused()
        }
    }

    private fun withHome(action: (AppController) -> Unit) {
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, "https://example.invalid")
        val memory = FocusMemory()
        try {
            compose.runOnIdle {
                controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false,
                    homeLoading = false, shelves = listOf(HomeShelf("Fixture", listOf(Media("movie", "movie", name = "Fixture movie")))))
            }
            compose.setContent {
                val state by controller.state.collectAsState()
                val rail = remember { FocusRequester() }
                val initial = remember { FocusRequester() }
                var expanded by remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalRailFocus provides rail, LocalContentFocus provides initial,
                    LocalFocusMemory provides memory, LocalCloseRail provides { expanded = false }) {
                    ViptvTheme(false, Color.White) {
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(state, controller)
                            TelevisionRail(state, controller, expanded, { expanded = it }, rail, initial, memory)
                        }
                    }
                }
            }
            action(controller)
        } finally { controller.scope.cancel() }
    }
}
