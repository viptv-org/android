package org.viptv.app

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SourceProviderFiltersTest {
    @get:Rule val compose = createComposeRule()
    private val media = Media("provider-filter-fixture", "movie", "Provider fixture")
    private val alpha = Source("alpha", "Alpha", name = "Alpha result", quality = "1080p",
        displayResolved = true, providerKey = "addon:alpha", providerLabel = "Alpha")
    private val beta = Source("beta", "Beta", name = "Beta result", quality = "720p",
        displayResolved = true, providerKey = "addon:beta", providerLabel = "Beta")

    @Test fun phoneShowsProvidersTogetherAndSelectsOnlyOne() = withPicker(false) {
        compose.onNodeWithText("Alpha").assertIsDisplayed().performClick()
        compose.onNodeWithText("Alpha").assertIsSelected()
        compose.onNodeWithText("Beta").assertIsNotSelected()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithText("Alpha result").assertIsDisplayed()
        compose.onNodeWithText("Beta result").assertDoesNotExist()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Beta result").assertIsDisplayed()
        compose.onNodeWithText("Alpha result").assertDoesNotExist()
        compose.onNodeWithText("All providers").performClick()
        compose.onNodeWithText("Alpha result").assertExists()
        compose.onNodeWithText("Beta result").assertExists()
    }

    @Test fun televisionUsesRemoteToSwitchProvidersWithoutADialog() = withPicker(true) {
        compose.onNodeWithText("Alpha")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Alpha").assertIsFocused().assertIsSelected()
        compose.onNodeWithText("Alpha result").assertIsDisplayed()
        compose.onNodeWithText("Beta result").assertDoesNotExist()
        compose.onNodeWithText("Alpha").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Beta").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Beta").assertIsFocused().assertIsSelected()
        compose.onNodeWithText("Beta result").assertIsDisplayed()
        compose.onNodeWithText("Alpha result").assertDoesNotExist()
        compose.onNodeWithText("Provider").assertDoesNotExist()
    }

    @Test fun lateResultsDoNotStealFocusAfterProviderInteraction() = withPicker(true, initialSources = emptyList()) { controller ->
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(sourceProducers = listOf(
                SourceProducerOutcome("empty", "Empty"),
                SourceProducerOutcome("addon:beta", "Beta"),
            ))
        }
        compose.onNodeWithText("Empty")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .assertIsFocused().assertIsSelected()
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(sources = listOf(beta))
        }
        compose.onNodeWithText("Empty").assertIsFocused()
        compose.onNodeWithText("All providers")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("All providers").assertIsFocused().assertIsSelected()
        compose.onNodeWithText("Beta result").assertIsDisplayed()
    }

    @Test fun progressiveDiscoveryKeepsProviderWithoutQualityFiltering() = withPicker(false) { controller ->
        compose.onNodeWithText("All qualities").assertDoesNotExist()
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithText("Alpha").performClick()
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(
                sources = listOf(alpha, beta, alpha.copy(id = "alpha-720", name = "Another Alpha result", quality = "720p"),
                    beta.copy(id = "later", name = "Later Beta result")),
                sourceProducers = listOf(SourceProducerOutcome("empty", "Empty", "source_format_unsupported")),
            )
        }
        compose.onNodeWithText("Alpha result").assertIsDisplayed()
        compose.onNodeWithText("Another Alpha result").assertExists()
        compose.onNodeWithText("Later Beta result").assertDoesNotExist()
        compose.onNodeWithText("Empty").performClick()
        compose.onNodeWithText("Empty returned formats this app cannot play. Choose another source.").assertIsDisplayed()
        compose.onNodeWithText("Alpha result").assertDoesNotExist()
    }

    @Test fun televisionScrollsProvidersOnOneRowWithTheRemote() = withPicker(true) { controller ->
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(sourceProducers = listOf(
                SourceProducerOutcome("third", "Third provider"),
                SourceProducerOutcome("fourth", "Fourth provider"),
            ))
        }
        val rowHeight = compose.onNodeWithTag("source-provider-filters").fetchSemanticsNode().boundsInRoot.height
        val badgeY = compose.onNodeWithText("All providers").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithText("All providers").performSemanticsAction(SemanticsActions.RequestFocus)
        listOf("Alpha", "Beta", "Third provider", "Fourth provider").forEach { label ->
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
            assertEquals(badgeY, compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center.y)
        }
        assertEquals(rowHeight, compose.onNodeWithTag("source-provider-filters").fetchSemanticsNode().boundsInRoot.height)
        compose.onNodeWithText("Fourth provider").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Fourth provider").assertIsSelected()
        compose.onNodeWithText("No playable sources from Fourth provider").assertIsDisplayed()
    }

    @Test fun phoneScrollsOverflowWithoutWrapping() = withPicker(false) { controller ->
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(sourceProducers = (1..12).map {
                SourceProducerOutcome("extra-$it", "Extra provider $it")
            })
        }
        val row = compose.onNodeWithTag("source-provider-filters")
        val rowHeight = row.fetchSemanticsNode().boundsInRoot.height
        val badgeY = compose.onNodeWithText("All providers").fetchSemanticsNode().boundsInRoot.center.y
        row.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Extra provider 12").performScrollTo().assertIsDisplayed()
        assertEquals(badgeY, compose.onNodeWithText("Extra provider 12").fetchSemanticsNode().boundsInRoot.center.y)
        assertEquals(rowHeight, row.fetchSemanticsNode().boundsInRoot.height)
        compose.onNodeWithText("Extra provider 12").performClick().assertIsSelected()
    }

    @Test fun televisionSourceListReachesPanelBottomAndShowsTheLastCard() = withPicker(true) { controller ->
        compose.runOnIdle {
            controller._state.value = controller.state.value.copy(sources = (1..12).map {
                alpha.copy(id = "row-$it", name = "Source row $it")
            })
        }
        val panelBottom = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.bottom
        val results = compose.onNodeWithTag("source-results")
        assertEquals(panelBottom, results.fetchSemanticsNode().boundsInRoot.bottom)
        val cardHeight = compose.onNodeWithText("Source row 1").fetchSemanticsNode().boundsInRoot.height
        results.performScrollToNode(hasText("Source row 12"))
        compose.onNodeWithText("Source row 12").assertIsDisplayed()
        assertEquals(cardHeight, compose.onNodeWithText("Source row 12").fetchSemanticsNode().boundsInRoot.height)
    }

    private fun withPicker(tv: Boolean, initialSources: List<Source> = listOf(alpha, beta), check: (AppController) -> Unit) {
        lateinit var controller: AppController
        compose.runOnUiThread {
            val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    super.getSharedPreferences("source-provider-filter-test.$name", mode)
            }
            controller = AppController(context, "https://127.0.0.1:1")
            controller._state.value = AppState(route = Route.Sources(media), sources = initialSources)
        }
        try {
            compose.setContent {
                val state by controller.state.collectAsState()
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalTv provides tv, LocalDensity provides Density(if (tv) 0.5f else 1f)) {
                    ViptvTheme(false, Color.White) { SourcePicker(media, state.sources, controller) }
                }
            }
            check(controller)
        } finally {
            compose.runOnUiThread { controller.close() }
        }
    }
}
