package org.viptv.app

import android.view.accessibility.CaptioningManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import org.viptv.video.SubtitleCue
import org.viptv.video.SubtitleCueAlignment

/** Media3/WebVTT default cue height: 5.33% of the video viewport. */
internal const val SUBTITLE_TEXT_FRACTION = 0.0533f

internal enum class SubtitleEdge { None, Shadow, Outline }

/** Resolved cue paint from the profile's Subtitle size/appearance preferences. */
internal data class SubtitleAppearance(
    val textFraction: Float,
    val foreground: Color,
    val background: Color,
    val edge: SubtitleEdge,
)

/** The device's accessibility caption settings, read only for the "System default" choices. */
internal data class SystemCaptionStyle(
    val fontScale: Float = 1f,
    val foreground: Int? = null,
    val background: Int? = null,
    val edgeType: Int? = null,
)

/**
 * Size: Small / System default / Large. "System default" follows the device caption font scale;
 * Small and Large are explicit and ignore it. Appearance: "system" follows the device caption
 * colours and edge, "shadow" is white text with a drop shadow, "opaque" is white text on black.
 */
internal fun subtitleAppearance(size: String, style: String, system: SystemCaptionStyle = SystemCaptionStyle()): SubtitleAppearance {
    val scale = when (size) {
        "small" -> 0.75f
        "large" -> 1.35f
        else -> system.fontScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.5f, 2f) ?: 1f
    }
    val white = Color(0xFFF4F2EE)
    return when (style) {
        "shadow" -> SubtitleAppearance(SUBTITLE_TEXT_FRACTION * scale, white, Color.Transparent, SubtitleEdge.Shadow)
        "opaque" -> SubtitleAppearance(SUBTITLE_TEXT_FRACTION * scale, white, Color.Black, SubtitleEdge.None)
        else -> SubtitleAppearance(
            textFraction = SUBTITLE_TEXT_FRACTION * scale,
            foreground = system.foreground?.let(::Color) ?: Color.White,
            background = system.background?.let(::Color) ?: Color.Black.copy(alpha = .75f),
            edge = when (system.edgeType) {
                CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE -> SubtitleEdge.Outline
                CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW,
                CaptioningManager.CaptionStyle.EDGE_TYPE_RAISED,
                CaptioningManager.CaptionStyle.EDGE_TYPE_DEPRESSED,
                -> SubtitleEdge.Shadow
                else -> SubtitleEdge.None
            },
        )
    }
}

/**
 * How far default-placed cues must rise so visible player chrome never covers them: the measured
 * controls height minus the letterbox gap already below the video.
 */
internal fun subtitleChromeLift(chromeVisible: Boolean, controlsHeight: Dp, containerHeight: Dp, videoHeight: Dp): Dp =
    if (!chromeVisible) 0.dp else (controlsHeight - (containerHeight - videoHeight) / 2).coerceAtLeast(0.dp)

@Composable internal fun rememberSystemCaptionStyle(): SystemCaptionStyle {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(CaptioningManager::class.java) ?: return@remember SystemCaptionStyle()
        val style = manager.userStyle
        SystemCaptionStyle(
            fontScale = manager.fontScale,
            foreground = style.foregroundColor.takeIf { style.hasForegroundColor() },
            background = style.backgroundColor.takeIf { style.hasBackgroundColor() },
            edgeType = style.edgeType.takeIf { style.hasEdgeType() },
        )
    }
}

internal fun subtitleUsesAutomaticLine(cue: SubtitleCue): Boolean = cue.line == null

/** The automatic stack is measured only in the space above controls and below the top inset. */
internal fun subtitleAutomaticHeightLimit(viewportHeight: Int, topInset: Int, bottomInset: Int): Int =
    (viewportHeight - topInset - bottomInset).coerceAtLeast(0)

internal fun subtitleCueOffset(cue: SubtitleCue, viewport: IntSize, text: IntSize, inset: IntOffset, bottomInset: Int): IntOffset {
    val anchor = when (cue.alignment) {
        SubtitleCueAlignment.Start -> 0f
        SubtitleCueAlignment.End -> 1f
        SubtitleCueAlignment.Center -> 0.5f
    }
    val x = (viewport.width * (cue.position ?: anchor) - text.width * anchor).roundToInt()
    val y = cue.line?.let { (viewport.height * it - text.height / 2f).roundToInt() }
        ?: (viewport.height - bottomInset - text.height)
    val left = inset.x.coerceAtMost((viewport.width - text.width).coerceAtLeast(0))
    val top = inset.y.coerceAtMost((viewport.height - text.height).coerceAtLeast(0))
    val right = (viewport.width - inset.x - text.width).coerceAtLeast(left)
    val bottom = (viewport.height - (if (cue.line == null) bottomInset else inset.y) - text.height).coerceAtLeast(top)
    return IntOffset(x.coerceIn(left, right), y.coerceIn(top, bottom))
}

/** Draws the player's active text cues inside the video viewport ([modifier] gives its bounds). */
@Composable internal fun SubtitleLayer(cues: List<SubtitleCue>, appearance: SubtitleAppearance, lift: Dp, modifier: Modifier) {
    if (cues.isEmpty()) return
    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        val density = LocalDensity.current
        val textSize = with(density) { max(maxHeight * appearance.textFraction, 12.dp).toSp() }
        val inset = maxHeight * 0.06f
        val automatic = cues.filter(::subtitleUsesAutomaticLine)
        val bottomInset = with(density) { max(inset, lift).roundToPx() }
        if (automatic.isNotEmpty()) Column(
            Modifier.fillMaxWidth().layout { measurable, constraints ->
                val topInset = (constraints.maxHeight * .03f).roundToInt()
                val height = subtitleAutomaticHeightLimit(constraints.maxHeight, topInset, bottomInset)
                val stack = measurable.measure(constraints.copy(minHeight = 0, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    stack.place(0, (constraints.maxHeight - bottomInset - stack.height).coerceAtLeast(topInset))
                }
            }.clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            automatic.forEach { cue ->
                CueText(cue, appearance, textSize, Modifier.layout { measurable, constraints ->
                    val width = constraints.maxWidth
                    val text = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (width * .9f).roundToInt()))
                    val x = if (cue.position == null) (width - text.width) / 2 else subtitleCueOffset(
                        cue, IntSize(width, text.height), IntSize(text.width, text.height),
                        IntOffset((width * .05f).roundToInt(), 0), 0,
                    ).x
                    layout(width, text.height) { text.place(x, 0) }
                })
            }
        }
        cues.filterNot(::subtitleUsesAutomaticLine).forEach { cue ->
            CueText(cue, appearance, textSize, Modifier.layout { measurable, constraints ->
                val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
                val text = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (viewport.width * .9f).roundToInt()))
                val safeInset = IntOffset((viewport.width * .05f).roundToInt(), (viewport.height * .03f).roundToInt())
                val offset = subtitleCueOffset(cue, viewport, IntSize(text.width, text.height), safeInset, bottomInset)
                layout(viewport.width, viewport.height) { text.place(offset.x, offset.y) }
            })
        }
    }
}

@Composable private fun CueText(cue: SubtitleCue, appearance: SubtitleAppearance, textSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val shadow = when (appearance.edge) {
        SubtitleEdge.None -> null
        SubtitleEdge.Shadow -> Shadow(Color.Black.copy(alpha = .9f), Offset(2f, 2f), 6f)
        SubtitleEdge.Outline -> Shadow(Color.Black, Offset.Zero, 4f)
    }
    Text(
        cue.text,
        modifier
            .then(if (appearance.background.alpha > 0f) Modifier.background(appearance.background, RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = TextStyle(
            color = appearance.foreground,
            fontSize = textSize,
            lineHeight = textSize * 1.25f,
            fontFamily = Onest,
            fontWeight = FontWeight.Medium,
            shadow = shadow,
            textAlign = when (cue.alignment) {
                SubtitleCueAlignment.Start -> TextAlign.Start
                SubtitleCueAlignment.End -> TextAlign.End
                SubtitleCueAlignment.Center -> TextAlign.Center
            },
        ),
    )
}
