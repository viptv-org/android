@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.text.Layout
import androidx.media3.common.text.Cue

/** CEA-608 addresses 15 caption rows; numbered lines are spread over that grid. */
private const val NUMBERED_LINE_ROWS = 15f

/** Maps one Media3 cue to the backend-neutral cue, dropping bitmap and empty cues. */
internal fun Cue.toSubtitleCue(): SubtitleCue? = media3TextCue(
    text = text,
    line = line,
    lineType = lineType,
    position = position,
    alignment = when (textAlignment) {
        Layout.Alignment.ALIGN_NORMAL -> SubtitleCueAlignment.Start
        Layout.Alignment.ALIGN_OPPOSITE -> SubtitleCueAlignment.End
        else -> SubtitleCueAlignment.Center
    },
)

/**
 * Primitive mapping kept free of Android framework types so host tests cover it. Fractional lines
 * and positions pass through clamped; numbered lines count from the top when non-negative and
 * from the bottom when negative (Media3's default `-1` is the bottom row, reported as `null` so the
 * renderer applies its own safe bottom margin). Styling spans are flattened to plain text.
 */
internal fun media3TextCue(
    text: CharSequence?,
    line: Float,
    lineType: Int,
    position: Float,
    alignment: SubtitleCueAlignment,
): SubtitleCue? {
    val plain = text?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val mappedLine = when {
        line == Cue.DIMEN_UNSET || line.isNaN() -> null
        lineType == Cue.LINE_TYPE_FRACTION -> line.coerceIn(0f, 1f)
        lineType == Cue.LINE_TYPE_NUMBER && line >= 0f -> ((line + 1f) / NUMBERED_LINE_ROWS).coerceIn(0f, 1f)
        lineType == Cue.LINE_TYPE_NUMBER && line < -1f -> ((NUMBERED_LINE_ROWS + line + 1f) / NUMBERED_LINE_ROWS).coerceIn(0f, 1f)
        else -> null
    }
    val mappedPosition = position.takeUnless { it == Cue.DIMEN_UNSET || it.isNaN() }?.coerceIn(0f, 1f)
    return SubtitleCue(plain, mappedLine, mappedPosition, alignment)
}
