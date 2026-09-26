package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

internal const val NEXT_COUNTDOWN_MILLIS = 10_000L

data class UpNextPrompt(val media: Media, val remainingMillis: Long = NEXT_COUNTDOWN_MILLIS) {
    val seconds: Long get() = ((remainingMillis + 999) / 1000).coerceAtLeast(0)
}

/** Count elapsed foreground playback time; pause/buffering/menu time never leaks into the timer. */
internal class NextEpisodeCountdown {
    var remainingMillis = NEXT_COUNTDOWN_MILLIS; private set
    fun advance(elapsedMillis: Long, progressing: Boolean): Boolean {
        if (progressing) remainingMillis = (remainingMillis - elapsedMillis.coerceAtLeast(0)).coerceAtLeast(0)
        return remainingMillis == 0L
    }
}

@Composable internal fun UpNextCard(prompt: UpNextPrompt, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val tv = LocalTv.current
    val first = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val still = remember(prompt.media) { CoreModels.presentation(prompt.media).episodeImage }
    Column(modifier.clip(RoundedCornerShape(measure(24, 22))).background(C.surfaceN2).padding(measure(24, 14)).focusGroup()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(20, 12))) {
            Box(Modifier.size(measure(152, 114), measure(86, 64)).clip(RoundedCornerShape(measure(12, 8))).background(C.surfaceN3), contentAlignment = Alignment.Center) {
                if (still.isNullOrBlank()) VIcon("next") else Artwork(still, null, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                VText("NEXT EPISODE", if (tv) 18 else 11, color = C.textSecondary, bold = true)
                VText(listOfNotNull(prompt.media.episode?.let { "E$it" }, prompt.media.episodeTitle?.takeIf(String::isNotBlank) ?: prompt.media.name).joinToString(" · "), if (tv) 24 else 14, Modifier.padding(top = 4.dp), bold = true, lines = 2)
                VText("Starts in ${prompt.seconds}", if (tv) 20 else 13, Modifier.padding(top = 4.dp), C.textSecondary)
            }
        }
        ProgressLine(prompt.remainingMillis.toFloat() / NEXT_COUNTDOWN_MILLIS, Modifier.padding(vertical = measure(20, 12)))
        Row(horizontalArrangement = Arrangement.spacedBy(measure(16, 10))) {
            AppButton("Play now", onPlay, Modifier.weight(1.2f).height(measure(64, 48)).focusRequester(first).focusProperties { left = FocusRequester.Cancel; right = cancel; up = FocusRequester.Cancel; down = FocusRequester.Cancel }, icon = "play", primary = !tv)
            AppButton("Cancel", onCancel, Modifier.weight(1f).height(measure(64, 48)).focusRequester(cancel).focusProperties { left = first; right = FocusRequester.Cancel; up = FocusRequester.Cancel; down = FocusRequester.Cancel })
        }
    }
    LaunchedEffect(prompt.media.id) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
}
