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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        val first = window.captureToImage().toPixelMap()
        fun fingerprint() = window.captureToImage().toPixelMap().let { pixels ->
            (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> pixels[x, y].red > .8f }
            }
        }
        val initialInk = fingerprint()
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeBy(4000)
        compose.waitForIdle()
        assertEquals(height, window.fetchSemanticsNode().boundsInRoot.height)
        assertNotEquals(initialInk, fingerprint())
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        assertEquals(initialInk, fingerprint())
        assertEquals(first.height, window.captureToImage().height)
    }
}
