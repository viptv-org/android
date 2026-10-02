package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun TitleSourceControl(summary: SourceSummary?, onClick: () -> Unit) {
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    var focused by remember { mutableStateOf(false) }
    val foreground = if (tv && focused) C.onLight else C.textPrimary
    val secondary = if (tv && focused) C.textOnLightSecondary else C.textSecondary
    val shape = RoundedCornerShape(if (tv) 36.dp else 20.dp)
    Holdable(onClick, modifier = Modifier.then(if (tv) Modifier else Modifier.fillMaxWidth())
        .height(measure(72, 58)).onFocusChanged { focused = it.isFocused; if (focused) closeRail() }
        .clip(shape).background(if (tv && focused) C.textPrimary else if (tv) C.surfaceN3 else C.surfaceN1)
        .then(if (tv) Modifier else Modifier.border(1.dp, C.lineOutline, shape))
        .semantics { contentDescription = "Choose source" }) {
        Row(Modifier.then(if (tv) Modifier else Modifier.fillMaxWidth()).padding(horizontal = measure(30, 16)),
            horizontalArrangement = Arrangement.spacedBy(measure(14, 12)), verticalAlignment = Alignment.CenterVertically) {
            val source = summary?.best
            if (source != null) {
                Box(Modifier.height(measure(32, 26)).clip(RoundedCornerShape(8.dp))
                    .background(if (tv && focused) Color.Black.copy(alpha = .12f) else C.surfaceN3)
                    .padding(horizontal = measure(10, 8)), contentAlignment = Alignment.Center) {
                    VText(source.quality?.takeIf { it.isNotBlank() } ?: "Auto", if (tv) 18 else 12, color = foreground, bold = true, lines = 1)
                }
                if (tv) VText(SourceDisplayPolicy.providerLabel(source), 24, color = foreground, bold = true, lines = 1)
                else {
                    Column(Modifier.weight(1f)) {
                        VText("Best source", 12, color = C.textTertiary)
                        VText(SourceDisplayPolicy.providerLabel(source), 15, color = foreground, bold = true, lines = 1)
                    }
                    VText("${summary.count} " + if (summary.count == 1) "source" else "sources", 14, color = secondary, lines = 1)
                }
            } else if (summary?.done == true) {
                VText(if (summary.failed) "Sources unavailable" else "No sources found", if (tv) 24 else 14,
                    Modifier.then(if (tv) Modifier else Modifier.weight(1f)), color = secondary, lines = 1)
            } else {
                val skeleton = if (tv && focused) Color.Black.copy(alpha = .12f) else C.surfaceN2
                SkeletonBlock(Modifier.width(measure(56, 42)).height(measure(32, 26)), 8.dp, skeleton)
                SkeletonBlock(Modifier.width(measure(150, 128)).height(measure(26, 20)), 8.dp, skeleton)
                if (!tv) Spacer(Modifier.weight(1f))
            }
            if (!tv) VIcon("down", color = secondary)
        }
    }
}
