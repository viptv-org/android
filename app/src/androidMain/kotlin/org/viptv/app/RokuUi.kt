package org.viptv.app

import android.view.KeyEvent
import androidx.compose.animation.core.*
import coil.compose.AsyncImage
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val LocalRokuRailFocus = staticCompositionLocalOf<FocusRequester> { FocusRequester.Default }

internal val RokuCanvas = Color(0xFF101112)
internal val RokuSurface = Color(0xFF202224)
internal val RokuWhite = Color(0xFFF5F5F5)
internal val RokuMuted = Color(0xFFA6A8AA)
internal fun rokuAsset(name: String) = "file:///android_asset/roku/images/$name"

/** One remote focus target. A hold dispatches once and consumes the matching release. */
@Composable internal fun Holdable(
    onActivate: () -> Unit,
    onHold: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onInfo: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var consumed by remember { mutableStateOf(false) }
    val activate by rememberUpdatedState(onActivate)
    val hold by rememberUpdatedState(onHold)
    val info by rememberUpdatedState(onInfo ?: onHold)
    LaunchedEffect(pressed) {
        if (pressed && hold != null) {
            delay(700)
            if (pressed) { consumed = true; hold?.invoke() }
        }
    }
    Box(
        modifier.then(if (selected) Modifier.border(2.dp, RokuWhite, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { if (!it.hasFocus) { pressed = false; consumed = true } }
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                when (key.keyCode) {
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                        if (info == null) false else {
                            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) info?.invoke()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (key.action == KeyEvent.ACTION_DOWN && !pressed && key.repeatCount == 0) {
                            consumed = false; pressed = true
                        } else if (key.action == KeyEvent.ACTION_UP) {
                            val shouldActivate = pressed && !consumed
                            pressed = false
                            if (shouldActivate) activate()
                        }
                        true
                    }
                    else -> false
                }
            }.clickable { activate() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable internal fun TvButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier.width(192.dp).height(56.dp),
    selected: Boolean = false,
    multiline: Boolean = false,
    content: (@Composable BoxScope.() -> Unit)? = null,
    onHold: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Holdable(onActivate, onHold,
        modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused?.invoke() }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) RokuWhite else if (selected) Color(0xFF303234) else RokuSurface),
        onInfo = onInfo,
    ) {
        if (content != null) content() else Text(
            label, color = if (focused) RokuCanvas else RokuWhite,
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = if (multiline) 3 else 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

@Composable internal fun RokuSpinner(modifier:Modifier=Modifier) {
    val animation=rememberInfiniteTransition(label="Roku spinner")
    val angle by animation.animateFloat(0f,360f,infiniteRepeatable(tween(1100,easing=LinearEasing)),label="rotation")
    AsyncImage(rokuAsset("ui-spinner.png"),"Loading",modifier.rotate(angle))
}
