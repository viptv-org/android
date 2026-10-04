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

    @Test fun `watched menu blocks incidental rail focus without a keyboard`() {
        assertFalse(televisionRailExpanded(requested = true, keyboardVisible = false, modalVisible = true))
    }

    @Test fun `a dialog window taking focus cannot expand the background rail`() {
        assertFalse(televisionRailExpanded(requested = true, keyboardVisible = false, windowFocused = false))
    }

    @Test fun `dismissing a modal does not restore prior rail expansion`() {
        val collapsed = televisionRailExpanded(requested = true, keyboardVisible = false, modalVisible = true)
        assertFalse(televisionRailExpanded(requested = collapsed, keyboardVisible = false, modalVisible = false))
    }

    @Test fun `returning window focus does not reopen a collapsed rail`() {
        val collapsed = televisionRailExpanded(requested = true, keyboardVisible = false, windowFocused = false)
        assertFalse(televisionRailExpanded(requested = collapsed, keyboardVisible = false, windowFocused = true))
    }

    @Test fun `navigation can expand normally with the keyboard hidden`() {
        assertTrue(televisionRailExpanded(requested = true, keyboardVisible = false))
        assertFalse(televisionRailExpanded(requested = false, keyboardVisible = false))
    }
}
