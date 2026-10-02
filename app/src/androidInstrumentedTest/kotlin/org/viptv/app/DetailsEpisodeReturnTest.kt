package org.viptv.app

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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Details state survives a real Sources route round trip for a distant episode. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailsEpisodeReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceCancelKeepsTheSelectedDistantEpisode() {
        val show = Media("show", "series", name = "Long Show", episodes = (1..1410).map { number ->
            Media("show:1:$number", "episode", name = "Long Show", seriesId = "show", season = 1,
                episode = number, episodeTitle = if (number == 1059) "The Future" else "Episode $number")
        })
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, "https://example.invalid")
        try {
            compose.setContent {
                val state by controller.state.collectAsState()
                val holder = rememberSaveableStateHolder()
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) {
                        Box(Modifier.fillMaxSize()) {
                            when (val route = state.route) {
                                is Route.Details -> holder.SaveableStateProvider("detail:${route.media.id}") { DetailsScreen(route.media, controller) }
                                is Route.Sources -> holder.SaveableStateProvider("sources:${route.media.id}") { VText("Source picker") }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            compose.runOnIdle { controller._state.value = AppState(route = Route.Details(show), loading = false, sessionRestoring = false) }
            compose.onNodeWithContentDescription("Jump to episode number").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("1059")
            compose.onNodeWithText("Go").performClick()
            val episode = compose.onNodeWithText("The Future")
            episode.assertIsFocused()
            episode.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5_000) { controller.state.value.route is Route.Sources }
            compose.runOnIdle { controller.back() }
            compose.waitUntil(5_000) { controller.state.value.route is Route.Details }
            episode.assertIsFocused()
            assertEquals("show", (controller.state.value.route as Route.Details).media.id)
        } finally {
            controller.scope.cancel()
        }
    }
}
