package org.viptv.video

import androidx.media3.common.text.Cue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidMedia3CueMappingTest {
    private fun map(
        text: CharSequence?,
        line: Float = Cue.DIMEN_UNSET,
        lineType: Int = Cue.TYPE_UNSET,
        position: Float = Cue.DIMEN_UNSET,
        alignment: SubtitleCueAlignment = SubtitleCueAlignment.Center,
    ) = media3TextCue(text, line, lineType, position, alignment)

    @Test fun `unpositioned text cues use the renderer default placement`() {
        assertEquals(SubtitleCue("Hello there"), map("  Hello there \n"))
    }

    @Test fun `bitmap and blank cues are dropped`() {
        assertNull(map(null))
        assertNull(map("   "))
    }

    @Test fun `fractional lines and positions pass through clamped`() {
        assertEquals(
            SubtitleCue("Top", line = 0.1f, position = 0.25f, alignment = SubtitleCueAlignment.Start),
            map("Top", 0.1f, Cue.LINE_TYPE_FRACTION, 0.25f, SubtitleCueAlignment.Start),
        )
        assertEquals(1f, map("Low", 1.4f, Cue.LINE_TYPE_FRACTION)?.line)
    }

    @Test fun `numbered lines count from the top or bottom of the caption grid`() {
        assertEquals(1f / 15f, map("Row 0", 0f, Cue.LINE_TYPE_NUMBER)?.line)
        assertNull(map("Default bottom row", -1f, Cue.LINE_TYPE_NUMBER)?.line)
        assertEquals(14f / 15f, map("Second to last", -2f, Cue.LINE_TYPE_NUMBER)?.line)
    }

    @Test fun `multi-line cues keep their inner line breaks`() {
        assertEquals("First\nSecond", map("First\nSecond")?.text)
    }

    @Test fun `merged sideloads are recognised by their source-prefixed format id`() {
        val ids = setOf("sideload")
        kotlin.test.assertTrue(media3IsExternalSubtitle("sideload", ids))
        kotlin.test.assertTrue(media3IsExternalSubtitle("1:sideload", ids))
        kotlin.test.assertFalse(media3IsExternalSubtitle("4", ids))
        kotlin.test.assertFalse(media3IsExternalSubtitle(null, ids))
    }

    @Test fun `extraction-parsed subtitles report their original format`() {
        assertEquals("application/x-subrip", media3SubtitleFormat("application/x-media3-cues", "application/x-subrip"))
        assertEquals("text/vtt", media3SubtitleFormat("text/vtt", null))
    }
}
