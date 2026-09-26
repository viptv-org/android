package org.viptv.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

/** One track coordinate system owns rendering, touch and accessibility seeking. */
@Composable internal fun PlayerTimeline(position: Long, duration: Long, buffered: Long?, modifier: Modifier = Modifier,
    enabled: Boolean = true, onSeek: ((Long) -> Unit)? = null, onCommit: () -> Unit = {}, onCancel: () -> Unit = {}) {
    val tv = LocalTv.current
    val accent = LocalAccent.current
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffer = if (duration > 0) ((buffered ?: 0).toFloat() / duration).coerceIn(progress, 1f) else progress
    val seek by rememberUpdatedState(onSeek)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val interactive = enabled && duration > 0 && onSeek != null
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(measure(36, 44)).then(if (interactive) Modifier.systemGestureExclusion() else Modifier)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                if (interactive) setProgress { value -> seek?.invoke((duration * value.coerceIn(0f, 1f)).toLong()); commit(); true }
            }.pointerInput(duration, interactive) {
                if (interactive) awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun preview(x: Float) { seek?.invoke((duration * (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)).toLong()) }
                    preview(down.position.x)
                    var completed = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            preview(change.position.x)
                            change.consume()
                            if (!change.pressed) { completed = true; break }
                        }
                    } finally { if (completed) commit() else cancel() }
                }
            }) {
            val height = (if (tv) 6.dp else 4.dp).toPx()
            val top = (size.height - height) / 2
            val radius = CornerRadius(height / 2)
            fun segment(fraction: Float, color: androidx.compose.ui.graphics.Color) {
                if (fraction > 0) drawRoundRect(color, Offset(0f, top), Size(size.width * fraction, height), radius)
            }
            segment(1f, C.lineStrong)
            segment(buffer, C.textSecondary.copy(alpha = .6f))
            segment(progress, accent)
            val knob = (if (tv) 9.dp else 7.dp).toPx()
            if (duration > 0) drawCircle(C.textPrimary, knob, Offset((size.width * progress).coerceIn(knob, (size.width - knob).coerceAtLeast(knob)), size.height / 2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            VText(formatTime(position), if (tv) 22 else 13, color = C.textSecondary)
            VText(formatTime(duration), if (tv) 22 else 13, color = C.textSecondary)
        }
    }
}
