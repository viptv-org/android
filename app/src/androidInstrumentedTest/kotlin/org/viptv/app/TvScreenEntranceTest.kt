package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvScreenEntranceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incomingTvRoutesAreFullyVisibleOnTheirFirstFrame() {
        val movie = Media("fixture", "movie", "Fixture")
        var route by mutableStateOf<Route>(Route.Browse(Destination.Home))
        lateinit var entrance: ScreenEntrance
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true) {
                entrance = rememberScreenEntrance(route, route)
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            for (next in listOf(Route.Browse(Destination.Discover), Route.Details(movie),
                Route.Sources(movie), Route.Details(movie), Route.Player(movie, Source("fixture", "Fixture")),
                Route.Browse(Destination.Home))) {
                compose.runOnIdle { route = next }
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertEquals("TV route must not start transparent or wait for an animation", 1f, entrance.progress.value)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
