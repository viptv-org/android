package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.viptv.video.SubtitleCue
import org.viptv.video.SubtitleCueAlignment

/** Real Compose measurement/rendering only; no account, network or media fixture. */
@RunWith(AndroidJUnit4::class)
class SubtitleOverlayPlacementTest {
    @get:Rule val compose = createComposeRule()

    private data class Band(val top: Int, val bottom: Int, val left: Int, val right: Int)

    private fun paint(cues: List<SubtitleCue>): Pair<Int, List<Band>> {
        compose.setContent {
            Box(Modifier.size(400.dp, 200.dp).background(Color.Black).testTag("viewport")) {
                SubtitleLayer(cues, SubtitleAppearance(.08f, Color.White, Color.Red, SubtitleEdge.None), 40.dp, Modifier.fillMaxSize())
            }
        }
        val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val bands = mutableListOf<Band>()
        var current: Band? = null
        for (y in 0 until pixels.height) {
            val row = (0 until pixels.width).filter { x ->
                val color = pixels[x, y]
                color.red > .8f && color.green < .2f && color.blue < .2f
            }
            if (row.isEmpty()) {
                current?.let { bands.add(it) }
                current = null
            } else {
                current = current?.let { it.copy(bottom = y, left = minOf(it.left, row.first()), right = maxOf(it.right, row.last())) }
                    ?: Band(y, y, row.first(), row.last())
            }
        }
        current?.let { bands.add(it) }
        assertTrue("Subtitle paint must be visible", bands.isNotEmpty())
        assertTrue("Automatic cues must clear the visible controls", bands.all { it.bottom < pixels.height * .8f })
        return pixels.width to bands
    }

    @Test fun simultaneousAutomaticCuesStackAndKeepHorizontalAnchors() {
        val (width, bands) = paint(listOf(
            SubtitleCue("Left", position = .1f, alignment = SubtitleCueAlignment.Start),
            SubtitleCue("Right", position = .9f, alignment = SubtitleCueAlignment.End),
            SubtitleCue("Default"),
        ))
        assertEquals(3, bands.size)
        assertTrue(bands[0].right < width * .5f)
        assertTrue(bands[1].left > width * .5f)
        assertTrue(bands[2].left < width * .5f && bands[2].right > width * .5f)
        assertTrue(bands.zipWithNext().all { (first, next) -> first.bottom < next.top })
    }

    @Test fun tallAutomaticCueIsClippedAboveVisibleControls() {
        val (_, bands) = paint(listOf(SubtitleCue(List(20) { "Tall subtitle" }.joinToString("\n"), position = .1f)))
        assertEquals(1, bands.size)
    }
}
