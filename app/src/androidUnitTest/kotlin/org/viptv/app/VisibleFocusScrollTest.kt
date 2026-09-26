package org.viptv.app

import androidx.compose.foundation.ExperimentalFoundationApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalFoundationApi::class)
class VisibleFocusScrollTest {
    @Test fun visibleHeroStaysStillAndClippedCardsBecomeFullyVisible() {
        assertEquals(0f, VisibleFocusScroll.calculateScrollDistance(550f, 72f, 1080f))
        assertEquals(228f, VisibleFocusScroll.calculateScrollDistance(1500f, 360f, 1632f))
        assertEquals(-20f, VisibleFocusScroll.calculateScrollDistance(-20f, 320f, 1632f))
        assertEquals(0f, VisibleFocusScroll.calculateScrollDistance(-100f, 2000f, 1632f))
    }
}
