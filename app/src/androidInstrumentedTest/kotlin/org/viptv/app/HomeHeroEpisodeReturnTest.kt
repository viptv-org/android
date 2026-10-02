package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
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

/** The Home TV hero invokes activateHero for the active Continue Watching episode. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class HomeHeroEpisodeReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleContinueWatchingHeroResumeReturnsToParentDetails() {
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, "https://example.invalid")
        val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059, positionMillis = 60_000, durationMillis = 120_000)
        try {
            compose.setContent {
                val state by controller.state.collectAsState()
                val contentFocus = remember { FocusRequester() }
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f),
                    LocalContentFocus provides contentFocus) {
                    ViptvTheme(false, Color.White) { Box(Modifier.fillMaxSize()) {
                        if (state.route is Route.Browse) HomeScreen(state, controller)
                        else if (state.route is Route.Details) VText("Parent details")
                    } }
                }
            }
            compose.runOnIdle {
                controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false,
                    shelves = listOf(HomeShelf("Continue Watching", listOf(episode), isQueueShelf = true)))
            }
            compose.onNodeWithText("Resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5_000) { controller.state.value.route is Route.Sources }
            val sourceRoute = controller.state.value.route as Route.Sources
            assertTrue(sourceRoute.queueEpisodeReturn)
            compose.runOnIdle { controller.back() }
            compose.waitUntil(5_000) { controller.state.value.route is Route.Details }
            assertEquals("show", (controller.state.value.route as Route.Details).media.id)
            compose.runOnIdle { controller.back() }
            assertEquals(Route.Browse(Destination.Home), controller.state.value.route)
            compose.onNodeWithText("Resume").assertIsFocused()
        } finally {
            controller.scope.cancel()
        }
    }

    @Test fun resumeHeroSourceCancelOpensParentAtEpisodeThenBackGoesHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = AppController(instrumentation.targetContext, "https://example.invalid")
        val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show", season = 1,
            episode = 1059, positionMillis = 60_000, durationMillis = 120_000)
        try {
            instrumentation.runOnMainSync {
                controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false)
                controller.activateHero(episode, queue = true)
            }
            waitForRoute(controller) { it is Route.Sources }
            val sourceRoute = controller.state.value.route as Route.Sources
            assertTrue(sourceRoute.resume)
            assertTrue(sourceRoute.queueEpisodeReturn)

            instrumentation.runOnMainSync { controller.back() }
            waitForRoute(controller) { it is Route.Details }
            val returned = (controller.state.value.route as Route.Details).media
            assertEquals("show", returned.id)
            assertEquals(1, returned.season)
            assertEquals(1059, returned.episode)

            instrumentation.runOnMainSync { controller.back() }
            assertEquals(Route.Browse(Destination.Home), controller.state.value.route)
        } finally {
            controller.scope.cancel()
        }
    }

    @Test fun heroChooseSourceHoldKeepsEpisodeReturnOnlyForQueueSeries() {
        assertHeroHoldReturn(Media("episode", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059), expectedParent = "show")
        assertHeroHoldReturn(Media("show", "series", name = "Fixture Show",
            season = 1, episode = 1059), expectedParent = "show")
        assertHeroHoldReturn(Media("movie", "movie", name = "Fixture Movie"), expectedParent = null)
    }

    @Test fun resumeHeroPlayerExitLoadsParentEpisodeAndThenReturnsHome() {
        assertResumeHeroPlayerExit(Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059, positionMillis = 60_000))
        assertResumeHeroPlayerExit(Media("show", "series", name = "Fixture Show",
            season = 1, episode = 1059, positionMillis = 60_000))
    }

    private fun assertResumeHeroPlayerExit(episode: Media) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = AppController(instrumentation.targetContext, "https://example.invalid")
        val home = Route.Browse(Destination.Home)
        val picker = Route.Sources(episode, resume = true, origin = SourceReturn.Home,
            backRoute = home, queueEpisodeReturn = true)
        val player = Route.Player(episode, Source("source", "Fixture"), PlaybackReturn.Details, sourceRoute = picker)
        try {
            instrumentation.runOnMainSync {
                controller._state.value = AppState(route = player, sessionRestoring = false)
                controller.exitPlayer(player, episode)
            }
            waitForRoute(controller) { it is Route.Details }
            val returned = (controller.state.value.route as Route.Details).media
            assertEquals("show", returned.id)
            assertEquals(1, returned.season)
            assertEquals(1059, returned.episode)
            assertEquals(home, controller.detailReturnRoute)
            instrumentation.runOnMainSync { controller.back() }
            assertEquals(home, controller.state.value.route)
        } finally {
            controller.scope.cancel()
        }
    }

    private fun assertHeroHoldReturn(media: Media, expectedParent: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = AppController(instrumentation.targetContext, "https://example.invalid")
        try {
            instrumentation.runOnMainSync {
                controller._state.value = AppState(route = Route.Browse(Destination.Home), sessionRestoring = false)
                controller.chooseHeroSources(media, queue = true)
            }
            waitForRoute(controller) { it is Route.Sources }
            assertEquals(expectedParent != null, (controller.state.value.route as Route.Sources).queueEpisodeReturn)
            instrumentation.runOnMainSync { controller.back() }
            if (expectedParent == null) assertEquals(Route.Browse(Destination.Home), controller.state.value.route)
            else {
                waitForRoute(controller) { it is Route.Details }
                val returned = (controller.state.value.route as Route.Details).media
                assertEquals(expectedParent, returned.id)
                assertEquals(1, returned.season)
                assertEquals(1059, returned.episode)
                instrumentation.runOnMainSync { controller.back() }
                assertEquals(Route.Browse(Destination.Home), controller.state.value.route)
            }
        } finally {
            controller.scope.cancel()
        }
    }

    private fun waitForRoute(controller: AppController, predicate: (Route) -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!predicate(controller.state.value.route) && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("Expected route transition", predicate(controller.state.value.route))
    }
}
