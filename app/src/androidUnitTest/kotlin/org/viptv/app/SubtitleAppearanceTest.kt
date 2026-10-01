package org.viptv.app

import android.view.accessibility.CaptioningManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.viptv.video.SubtitleCue
import org.viptv.video.SubtitleCueAlignment

class SubtitleAppearanceTest {
    @Test fun `system default size follows the device caption scale while small and large are explicit`() {
        val system = SystemCaptionStyle(fontScale = 1.5f)
        assertEquals(SUBTITLE_TEXT_FRACTION * 1.5f, subtitleAppearance("normal", "shadow", system).textFraction)
        assertEquals(SUBTITLE_TEXT_FRACTION * 0.75f, subtitleAppearance("small", "shadow", system).textFraction)
        assertEquals(SUBTITLE_TEXT_FRACTION * 1.35f, subtitleAppearance("large", "shadow", system).textFraction)
        assertEquals(SUBTITLE_TEXT_FRACTION, subtitleAppearance("normal", "shadow", SystemCaptionStyle(fontScale = Float.NaN)).textFraction)
    }

    @Test fun `appearance choices map to shadow, opaque box or the device caption style`() {
        val shadow = subtitleAppearance("normal", "shadow")
        assertEquals(Color.Transparent, shadow.background)
        assertEquals(SubtitleEdge.Shadow, shadow.edge)
        val opaque = subtitleAppearance("normal", "opaque")
        assertEquals(Color.Black, opaque.background)
        assertEquals(SubtitleEdge.None, opaque.edge)
        val device = subtitleAppearance(
            "normal", "system",
            SystemCaptionStyle(foreground = 0xFFFFFF00.toInt(), background = 0x00000000, edgeType = CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE),
        )
        assertEquals(Color(0xFFFFFF00), device.foreground)
        assertEquals(Color.Transparent, device.background)
        assertEquals(SubtitleEdge.Outline, device.edge)
    }

    @Test fun `visible chrome lifts default cues only by the part that overlaps the video`() {
        assertEquals(0.dp, subtitleChromeLift(false, 230.dp, 844.dp, 219.dp))
        // Portrait: the letterbox gap below the video (312.5dp) already clears 230dp of controls.
        assertEquals(0.dp, subtitleChromeLift(true, 230.dp, 844.dp, 219.dp))
        // Landscape full-height video: lift by the whole controls height.
        assertEquals(140.dp, subtitleChromeLift(true, 140.dp, 390.dp, 390.dp))
    }

    @Test fun `simultaneous automatic cues stay in one vertical stack regardless of horizontal position`() {
        val cues = listOf(SubtitleCue("Default"), SubtitleCue("Left", position = 0.1f), SubtitleCue("Right", position = 0.9f))
        assertEquals(cues, cues.filter(::subtitleUsesAutomaticLine))
        assertTrue(subtitleUsesAutomaticLine(SubtitleCue("Positioned", position = 0.1f)))
        assertTrue(subtitleUsesAutomaticLine(SubtitleCue("Positioned", position = 0.9f)))
        assertTrue(subtitleUsesAutomaticLine(SubtitleCue("Default")))
        assertFalse(subtitleUsesAutomaticLine(SubtitleCue("Explicit line", line = 0.2f)))
    }

    @Test fun `position-only cues keep their viewport anchor at the safe bottom`() {
        val viewport = IntSize(1000, 500)
        val text = IntSize(100, 20)
        val inset = IntOffset(50, 15)
        assertEquals(IntOffset(100, 440), subtitleCueOffset(SubtitleCue("Left", position = 0.1f, alignment = SubtitleCueAlignment.Start), viewport, text, inset, 40))
        assertEquals(IntOffset(800, 440), subtitleCueOffset(SubtitleCue("Right", position = 0.9f, alignment = SubtitleCueAlignment.End), viewport, text, inset, 40))
        assertEquals(IntOffset(450, 115), subtitleCueOffset(SubtitleCue("Explicit", line = 0.25f), viewport, text, inset, 40))
    }

    @Test fun `positioned text stays within video bounds and clears visible controls`() {
        val viewport = IntSize(1000, 500)
        val text = IntSize(100, 20)
        val inset = IntOffset(50, 15)
        assertEquals(IntOffset(50, 340), subtitleCueOffset(SubtitleCue("Left edge", position = 0f), viewport, text, inset, 140))
        assertEquals(IntOffset(850, 340), subtitleCueOffset(SubtitleCue("Right edge", position = 1f), viewport, text, inset, 140))
    }
    @Test fun `automatic cue measurement reserves controls height even for tall text`() {
        val viewport = IntSize(1000, 500)
        val inset = IntOffset(50, 15)
        val bottomInset = 140
        val limit = subtitleAutomaticHeightLimit(viewport.height, inset.y, bottomInset)
        assertEquals(345, limit)
        val measured = IntSize(100, 400.coerceAtMost(limit))
        val offset = subtitleCueOffset(SubtitleCue("Tall", position = 0.1f), viewport, measured, inset, bottomInset)
        assertEquals(inset.y, offset.y)
        assertEquals(viewport.height - bottomInset, offset.y + measured.height)
        assertEquals(0, subtitleAutomaticHeightLimit(100, 15, 140))
    }

}
