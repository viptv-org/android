package org.viptv.app

import android.view.accessibility.CaptioningManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
