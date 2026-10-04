package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelevisionNavigationTest {
    @Test fun `opening the keyboard closes an expanded rail`() {
        assertFalse(televisionRailExpanded(requested = true, keyboardVisible = true))
    }

    @Test fun `rail focus cannot reopen navigation while the keyboard is visible`() {
        assertFalse(televisionRailExpanded(requested = true, keyboardVisible = true))
        assertFalse(televisionRailExpanded(requested = false, keyboardVisible = true))
    }

    @Test fun `dismissing the keyboard keeps the collapsed rail closed`() {
        val collapsed = televisionRailExpanded(requested = true, keyboardVisible = true)
        assertFalse(televisionRailExpanded(requested = collapsed, keyboardVisible = false))
    }

    @Test fun `navigation can expand normally with the keyboard hidden`() {
        assertTrue(televisionRailExpanded(requested = true, keyboardVisible = false))
        assertFalse(televisionRailExpanded(requested = false, keyboardVisible = false))
    }
}
