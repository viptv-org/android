package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenMotionTest {
    private val movie = Media("m1", "movie", "Movie")
    private val home = Route.Browse(Destination.Home)

    @Test fun drillingInAndBackScaleInOppositeDirections() {
        assertEquals(ScreenMotion.Forward, screenMotion(home, Route.Details(movie)))
        assertEquals(ScreenMotion.Forward, screenMotion(Route.Details(movie), Route.Sources(movie)))
        assertEquals(ScreenMotion.Back, screenMotion(Route.Sources(movie), Route.Details(movie)))
        assertEquals(ScreenMotion.Back, screenMotion(Route.Details(movie), home))
        assertEquals(ScreenMotion.Forward, screenMotion(Route.Settings, Route.Addons))
    }

    @Test fun railDestinationsTravelInRailOrder() {
        assertEquals(ScreenMotion.Next, screenMotion(home, Route.Browse(Destination.Discover)))
        assertEquals(ScreenMotion.Previous, screenMotion(Route.Browse(Destination.Discover), Route.Search))
        assertEquals(ScreenMotion.Next, screenMotion(home, Route.Guide()))
        assertEquals(ScreenMotion.Previous, screenMotion(Route.Settings, Route.Browse(Destination.MyList)))
    }

    @Test fun changesWithinADestinationAndTheFirstScreenDoNotAnimate() {
        assertEquals(ScreenMotion.None, screenMotion(null, home))
        assertEquals(ScreenMotion.None, screenMotion(home, home))
        assertEquals(ScreenMotion.None, screenMotion(Route.Guide(), Route.Browse(Destination.Live)))
    }

    @Test fun peerScreensAtTheSameDepthFade() {
        assertEquals(ScreenMotion.Fade, screenMotion(Route.Details(movie), Route.Details(Media("m2", "movie", "Other"))))
        assertEquals(ScreenMotion.Fade, screenMotion(Route.Profiles, home))
        assertEquals(ScreenMotion.Fade, screenMotion(Route.Details(movie), Route.Player(movie, Source("stream", "Provider"))))
    }
}
