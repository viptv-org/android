package org.viptv.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Keep text until a transparent logo has decoded; errors never leave an empty title. */
@Composable internal fun TitleArtwork(title: String, logo: String?, modifier: Modifier, textSize: Int, lines: Int = 2) {
    var ready by remember(logo) { mutableStateOf(false) }
    var failed by remember(logo) { mutableStateOf(false) }
    val opacity by animateFloatAsState(if (ready && !failed) 1f else 0f, tween(180), label = "title-artwork")
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        val usingLogo = !logo.isNullOrBlank() && !failed
        if (usingLogo && opacity < .999f) SkeletonBlock(Modifier.width(if (LocalTv.current) 360.dp else 240.dp).height(if (LocalTv.current) 46.dp else 28.dp).graphicsLayer { alpha = 1f - opacity }, radius = 8.dp)
        if (!usingLogo) VText(title, textSize, Modifier.fillMaxWidth(), display = true, lines = lines, marquee = true)
        if (!logo.isNullOrBlank() && !failed) SizedArtwork(logo, title,
            Modifier.widthIn(max = if (LocalTv.current) 410.dp else 310.dp).fillMaxHeight().graphicsLayer { alpha = opacity },
            fit = ContentScale.Fit, alignment = Alignment.CenterStart, onSuccess = { ready = true }, onError = { failed = true }, trimTransparency = true)
    }
}
