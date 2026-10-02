package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceDescriptionWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overflowingDescriptionMovesWithinTwoLinesAndResetsOnBlur() {
        val active = mutableStateOf(false)
        val description = "LongUnbrokenProviderFilename0123456789".repeat(9)
        compose.setContent {
            Box(Modifier.width(210.dp).background(Color.Black).testTag("row")) {
                SourceDescriptionWindow(description, 20, Color.White, active.value)
            }
        }
        val window = compose.onNodeWithTag("source-description-window")
        window.assertContentDescriptionEquals(description)
        val height = window.fetchSemanticsNode().boundsInRoot.height
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(description, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The overflowing text must be measured beyond the two-line window", layouts.single().size.height > height)
        val first = window.captureToImage().toPixelMap()
        fun fingerprint() = window.captureToImage().toPixelMap().let { pixels ->
            var hash = 1L
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                hash = 31 * hash + pixels[x, y].value.toLong()
            }
            hash
        }
        val initialInk = fingerprint()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { active.value = true }
        // The dwell is a coroutine delay on Android's main dispatcher; advancing
        // Compose's frame clock alone does not elapse that wall-clock delay.
        compose.waitUntil(timeoutMillis = 6000) {
            compose.mainClock.advanceTimeBy(100)
            fingerprint() != initialInk
        }
        assertEquals(height, window.fetchSemanticsNode().boundsInRoot.height)
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals(initialInk, fingerprint())
        assertEquals(first.height, window.captureToImage().height)
    }
}
