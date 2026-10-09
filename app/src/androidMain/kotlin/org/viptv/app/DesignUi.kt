@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.viptv.app.theme.ViptvColor as C

internal val LocalTv = staticCompositionLocalOf { false }
internal val LocalRailFocus = staticCompositionLocalOf { FocusRequester.Default }
internal val LocalContentFocus = staticCompositionLocalOf { FocusRequester.Default }
internal val LocalCloseRail = staticCompositionLocalOf<() -> Unit> { {} }
internal class FocusMemory {
    var target: FocusRequester? = null

    /** Refreshes can dispose the last focused control while the rail still owns focus. */
    fun restore(initial: FocusRequester): Boolean {
        if (target?.let { runCatching { it.requestFocus() }.getOrDefault(false) } == true) return true
        target = null
        return runCatching { initial.requestFocus() }.getOrDefault(false)
    }
}
internal val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }
internal val LocalAccent = staticCompositionLocalOf { C.accentDefault }
internal val LocalGround = staticCompositionLocalOf { C.bg }
internal val Onest = FontFamily(Font(R.font.onest_400), Font(R.font.onest_500, FontWeight.Medium), Font(R.font.onest_600, FontWeight.SemiBold), Font(R.font.onest_700, FontWeight.Bold))
internal val Bricolage = FontFamily(Font(R.font.bricolage_grotesque_700, FontWeight.Bold), Font(R.font.bricolage_grotesque_800, FontWeight.ExtraBold))

/** TV's platform pivot scrolls even fully visible controls. Keep the design's viewport stable. */
internal object VisibleFocusScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0 && end <= containerSize -> 0f
            offset < 0 && end > containerSize -> 0f
            kotlin.math.abs(offset) < kotlin.math.abs(end - containerSize) -> offset
            else -> end - containerSize
        }
    }
}

@Composable internal fun ViptvTheme(oled: Boolean, accent: Color, content: @Composable () -> Unit) {
    val ground = if (oled) C.bgOled else C.bg
    CompositionLocalProvider(LocalAccent provides accent, LocalGround provides ground) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = accent, onPrimary = C.onAccent, background = ground, surface = C.surfaceN1, onSurface = C.textPrimary, outline = C.lineOutline, error = C.statusDanger),
            typography = Typography(bodyLarge = TextStyle(fontFamily = Onest, fontSize = 16.sp), bodyMedium = TextStyle(fontFamily = Onest, fontSize = 14.sp), labelLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold)),
        ) { content() }
    }
}

@Composable internal fun measure(tv: Int, phone: Int): Dp = (if (LocalTv.current) tv else phone).dp
@Composable internal fun VText(text: String, size: Int = if (LocalTv.current) 26 else 16, modifier: Modifier = Modifier, color: Color = C.textPrimary, bold: Boolean = false, display: Boolean = false, lines: Int = Int.MAX_VALUE, align: TextAlign = TextAlign.Start) {
    Text(text, modifier, color, fontSize = size.sp, fontFamily = if (display) Bricolage else Onest,
        fontWeight = if (bold || display) FontWeight.Bold else FontWeight.Normal, textAlign = align,
        maxLines = lines, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.35f).sp)
}

@Composable internal fun VIcon(name: String, description: String? = null, modifier: Modifier = Modifier.size(measure(28, 22)), color: Color = C.textPrimary) {
    val extra: ImageVector? = when (name) {
        "back" -> Icons.AutoMirrored.Rounded.ArrowBack
        "more" -> Icons.Rounded.MoreHoriz
        "close" -> Icons.Rounded.Close
        "person" -> Icons.Rounded.PersonOutline
        "profiles" -> Icons.Rounded.Group
        "moon" -> Icons.Rounded.DarkMode
        "addons" -> Icons.Rounded.Extension
        "delete" -> Icons.Rounded.DeleteOutline
        "keyboard-delete" -> Icons.AutoMirrored.Rounded.Backspace
        "space" -> Icons.Rounded.SpaceBar
        "right" -> Icons.Rounded.ChevronRight
        "lock" -> Icons.Rounded.Lock
        "alert" -> Icons.Rounded.ErrorOutline
        "expand" -> Icons.Rounded.Fullscreen
        else -> null
    }
    if (extra != null) Icon(extra, description, modifier, tint = color)
    else AsyncImage("file:///android_asset/design/lucide/$name-primary.png", description, modifier, colorFilter = ColorFilter.tint(color))
}

/** One input owner for touch, accessibility and release-to-activate remotes. */
@Composable internal fun Holdable(onActivate: () -> Unit, onHold: (() -> Unit)? = null, modifier: Modifier = Modifier, selected: Boolean = false, onInfo: (() -> Unit)? = null, rememberFocus: Boolean = true, enabled: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    var consumed by remember { mutableStateOf(false) }
    val activate by rememberUpdatedState(onActivate)
    val hold by rememberUpdatedState(onHold)
    val info by rememberUpdatedState(onInfo ?: onHold)
    val focus = remember { FocusRequester() }
    val memory = LocalFocusMemory.current
    val closeRail = LocalCloseRail.current
    LaunchedEffect(pressed) {
        if (pressed && hold != null) { delay(HoldPolicy.thresholdMillis); if (pressed) { consumed = true; hold?.invoke() } }
    }
    Box(modifier.focusRequester(focus).onFocusChanged {
        if (it.isFocused && rememberFocus) { memory.target = focus; closeRail() }
        if (!it.hasFocus) { pressed = false; consumed = true }
    }
        .onPreviewKeyEvent { event ->
            if (!enabled) return@onPreviewKeyEvent false
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> if (info == null) false else {
                    if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) info?.invoke()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (key.action == KeyEvent.ACTION_DOWN && !pressed && key.repeatCount == 0) { consumed = false; pressed = true }
                    else if (key.action == KeyEvent.ACTION_UP) {
                        val fire = pressed && !consumed; pressed = false; if (fire) activate()
                    }
                    true
                }
                else -> false
            }
        }.combinedClickable(enabled = enabled, role = Role.Button, onClick = { activate() }, onLongClick = onHold?.let { { hold?.invoke() } }),
        contentAlignment = Alignment.Center, content = content)
}

@Composable internal fun AppButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, primary: Boolean = false, selected: Boolean = false, danger: Boolean = false, onHold: (() -> Unit)? = null, onFocused: (() -> Unit)? = null, pill: Boolean = true, tvAccent: Boolean = false, enabled: Boolean = true) {
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    var focused by remember { mutableStateOf(false) }
    val accented = (primary && !tv) || (tv && tvAccent)
    val fill = when { accented -> LocalAccent.current; tv && focused -> C.textPrimary; primary && !tv -> LocalAccent.current; selected -> C.surfaceN3; else -> C.surfaceN3 }
    val foreground = when { accented -> C.onAccent; tv && focused -> C.onLight; primary && !tv -> C.onAccent; danger -> C.statusDanger; else -> C.textPrimary }
    val shape = if (pill) RoundedCornerShape(50) else RoundedCornerShape(measure(14, 12))
    Holdable(onClick, onHold, modifier.alpha(if (enabled) 1f else .4f).height(measure(72, 54)).onFocusChanged {
        focused = it.isFocused; if (it.isFocused) { closeRail(); onFocused?.invoke() }
    }.clip(shape).background(fill).then(if (tv && tvAccent && focused) Modifier.border(4.dp, C.textPrimary, shape) else Modifier), enabled = enabled) {
        Row(Modifier.padding(horizontal = if (pill) measure(32, 22) else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(16, 10))) {
            if (icon != null) VIcon(icon, color = foreground)
            VText(label, if (tv) 26 else 16, color = foreground, bold = true, lines = 1)
        }
    }
}

@Composable internal fun AppIconButton(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    var focused by remember { mutableStateOf(false) }
    val onLight = (tv && focused) || (primary && !tv)
    Holdable(onClick, modifier = modifier.size(measure(72, 54)).onFocusChanged { focused = it.isFocused; if (it.isFocused) closeRail() }
        .clip(CircleShape).background(if (tv && focused) C.textPrimary else if (primary && !tv) LocalAccent.current else C.surfaceN3)) {
        VIcon(icon, description, color = if (onLight) C.onLight else C.textPrimary)
    }
}

@Composable internal fun AppChip(label: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier, first: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    val default = LocalContentFocus.current
    Holdable(onClick, modifier = modifier.then(if (first && tv) Modifier.focusRequester(default) else Modifier)
        .height(measure(52, 44)).onFocusChanged { focused = it.isFocused; if (focused) closeRail() }
        .clip(RoundedCornerShape(50)).background(if (focused && tv) C.textPrimary else if (selected) C.surfaceN3 else Color.Transparent)
        .border(1.dp, C.lineOutline, RoundedCornerShape(50))) {
        VText(label, if (tv) 22 else 14, Modifier.padding(horizontal = measure(26, 18)), if (focused && tv) C.onLight else C.textPrimary, bold = selected || focused, lines = 1)
    }
}

@Composable internal fun Artwork(url: String?, description: String?, modifier: Modifier, fit: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center) {
    if (!url.isNullOrBlank()) SizedArtwork(url, description, modifier, fit, alignment)
}

@Composable internal fun Avatar(name: String, url: String?, modifier: Modifier) {
    var ready by remember(url) { mutableStateOf(false) }
    Box(modifier.semantics { contentDescription = "$name profile" }.clip(RoundedCornerShape(20)).background(if (ready) C.surfaceAvatar else C.fillProfileLetter), contentAlignment = Alignment.Center) {
        if (!ready) VText(name.take(1).uppercase(), if (LocalTv.current) 64 else 34, bold = true, display = true)
        if (!url.isNullOrBlank()) SizedArtwork(url, null, Modifier.fillMaxSize(), ContentScale.Fit, onSuccess = { ready = true }, onError = { ready = false })
    }
}

@Composable internal fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = measure(6, 4)) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(C.lineStrong)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(LocalAccent.current))
    }
}

@Composable internal fun MediaCard(media: Media, modifier: Modifier = Modifier, queue: Boolean = false, onClick: () -> Unit, onHold: () -> Unit, onFocused: (() -> Unit)? = null, portrait: Boolean = false, wide: Boolean = false) {
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    var focused by remember { mutableStateOf(false) }
    var failed by remember(media.id, media.poster, media.backdrop) { mutableStateOf(emptySet<String>()) }
    val card = remember(media, queue, failed) { CoreModels.card(media, queue, failed) }
    val presentation = remember(media, portrait) { if (portrait) CoreModels.presentation(media) else null }
    val image = if (portrait) presentation?.posterImage ?: card.image else card.image
    val width = measure(if (wide) 360 else 320, 232)
    Holdable(onClick, onHold, modifier.then(if (portrait) Modifier.fillMaxWidth() else Modifier.width(width)).onFocusChanged {
        focused = it.isFocused; if (focused) { closeRail(); onFocused?.invoke() }
    }) {
      Column {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9)
            .clip(RoundedCornerShape(measure(16, 16))).background(C.surfaceN2)
            .border(if (tv && focused) 4.dp else 0.dp, if (tv && focused) C.fillWhite else Color.Transparent, RoundedCornerShape(measure(16, 16))), contentAlignment = Alignment.Center) {
            if (image.isNullOrBlank()) VText(card.title, if (tv) 24 else if (portrait) 13 else 18, Modifier.padding(12.dp), color = C.textSecondary, bold = true, lines = 2, align = TextAlign.Center)
            else SizedArtwork(image, card.title, Modifier.fillMaxSize(), fit = if (!portrait && card.imageRole == "logo") ContentScale.Fit else ContentScale.Crop,
                onError = { image.let { failed = failed + it } })
            val progress = card.progress?.toFloat() ?: 0f
            // AND-042: phones use a 6dp bar lifted 10dp off the art's sides and bottom.
            if (progress > 0f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(measure(14, 10)), height = 6.dp)
            if (!tv) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Holdable(onHold, modifier = Modifier.size(44.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.fillOverflowDisc), contentAlignment = Alignment.Center) { VIcon("more", "More options", Modifier.size(18.dp)) }
                }
            }
        }
        VText(card.title, if (tv) 24 else if (portrait) 12 else 15, Modifier.padding(top = measure(14, 10)), bold = true, lines = 1)
        val context = if (tv) card.subtitle else remember(media) { SharedPresentation.phone(media = media).cardContext }
        if (context.isNotBlank()) VText(context, if (tv) 20 else if (portrait) 12 else 13, Modifier.padding(top = 4.dp), C.textSecondary, lines = 1)
    }
  }
}

/** AND-042 phone Home live channel: a small logo tile; the name is the accessible label only. */
@Composable internal fun LiveLogoTile(media: Media, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    Holdable(onClick, onHold, modifier.size(104.dp, 72.dp).semantics { contentDescription = media.name }.clip(RoundedCornerShape(18.dp))
        .background(C.surfaceN1).border(1.dp, C.lineOutline, RoundedCornerShape(18.dp))) {
        val logo = media.poster
        if (!logo.isNullOrBlank()) Artwork(logo, null, Modifier.fillMaxSize().padding(12.dp), ContentScale.Fit)
        else VText(media.name.split(" ").mapNotNull { it.firstOrNull() }.take(3).joinToString(""), 14, color = C.textPrimary, bold = true, lines = 1)
    }
}

/** Layout-matching loading placeholder (AND-042); never a "Loading…" label. */
@Composable internal fun SkeletonBlock(modifier: Modifier, radius: Dp = measure(16, 16), color: Color = C.surfaceN1) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(.55f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "skeleton-alpha")
    Box(modifier.alpha(alpha).clip(RoundedCornerShape(radius)).background(color))
}

@Composable internal fun PosterSkeletonGrid(columns: Int, rows: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(measure(40, 24))) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(measure(36, 12))) {
                repeat(columns) { Column(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (LocalTv.current) 16f / 9 else 2f / 3))
                    SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth(.7f).height(12.dp), 6.dp)
                } }
            }
        }
    }
}

@Composable internal fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = measure(32, 10)), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null && !LocalTv.current) { Holdable(onBack, modifier = Modifier.size(44.dp)) { VIcon("back", "Back") }; Spacer(Modifier.width(8.dp)) }
        VText(title, if (LocalTv.current) 56 else if (onBack != null) 24 else 34, Modifier.weight(1f), display = true, lines = 1)
        trailing()
    }
}

@Composable internal fun FilterTabs(labels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, first: FocusRequester? = null) {
    val tv = LocalTv.current
    androidx.compose.foundation.lazy.LazyRow(modifier.then(if (tv) Modifier else Modifier.clip(CircleShape).background(C.surfaceN1)),
        contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 2.dp)) {
        items(labels.size) { index ->
            val label = labels[index]
            val focus = if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier
            if (tv) AppChip(label, { onSelect(label) }, label == selected, focus)
            else Holdable({ onSelect(label) }, modifier = focus.height(44.dp).clip(CircleShape).background(if (label == selected) C.textPrimary else Color.Transparent)) {
                VText(label, 14, Modifier.padding(horizontal = 20.dp), if (label == selected) C.onLight else C.textSecondary, bold = true, lines = 1)
            }
        }
    }
}

@Composable internal fun EmptyState(title: String, description: String = "", icon: String = "list", modifier: Modifier = Modifier, retry: (() -> Unit)? = null, retryModifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = measure(64, 40)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(measure(18, 12))) {
        VIcon(icon, modifier = Modifier.size(measure(64, 44)), color = C.textTertiary)
        VText(title, if (LocalTv.current) 32 else 22, display = true, align = TextAlign.Center)
        if (description.isNotBlank()) VText(description, if (LocalTv.current) 24 else 15, color = C.textSecondary, align = TextAlign.Center)
        if (retry != null) AppButton("Try again", retry, retryModifier)
    }
}

@Composable internal fun AppField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, secret: Boolean = false, singleLine: Boolean = true, keyboardType: KeyboardType = if (secret) KeyboardType.NumberPassword else KeyboardType.Text, onSubmit: (() -> Unit)? = null, isError: Boolean = false) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { VText(label, if (LocalTv.current) 22 else 14) },
        singleLine = singleLine, textStyle = TextStyle(fontFamily = Onest, fontSize = (if (LocalTv.current) 28 else 16).sp, color = C.textPrimary),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(errorBorderColor = C.statusDangerTv, errorLabelColor = C.statusDangerTv),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onSubmit == null) ImeAction.Default else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        shape = RoundedCornerShape(measure(20, 16)))
}

@Composable internal fun AppOverlay(title: String, onDismiss: () -> Unit, full: Boolean = false, bottomPadding: Dp? = null, content: @Composable ColumnScope.() -> Unit) {
    val tv = LocalTv.current
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      CompositionLocalProvider(LocalDensity provides density, LocalFocusMemory provides remember { FocusMemory() }) {
        Box(Modifier.fillMaxSize().background(C.scrimTvPanel)) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
            Column(Modifier.align(if (tv) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (tv) Modifier.width(if (full) 1728.dp else 820.dp).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max = 760.dp))
                .clip(if (tv) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(C.surfaceN1).windowInsetsPadding(if (tv) WindowInsets(0) else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .imePadding().padding(start = measure(64, 24), top = measure(64, 24), end = measure(64, 24), bottom = bottomPadding ?: measure(64, 24))
                .pointerInput(Unit) { detectTapGestures {} }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VText(title, if (tv) 44 else 24, Modifier.weight(1f), display = true, lines = 2)
                    if (!tv) AppIconButton("close", "Close", onDismiss, Modifier.size(44.dp))
                }
                Spacer(Modifier.height(measure(28, 20)))
                content()
            }
        }
      }
    }
}

@Composable internal fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit, description: String? = null) {
    val first = remember(title) { FocusRequester() }
    val tv = LocalTv.current
    AppOverlay(title, onDismiss) {
        if (!description.isNullOrBlank()) VText(description, if (tv) 24 else 15, Modifier.padding(bottom = measure(28, 20)), C.textSecondary)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = measure(820, (options.size * 64).coerceAtMost(480))), verticalArrangement = Arrangement.spacedBy(measure(12, 10))) {
            items(options.size) { index -> AppButton(options[index].first, options[index].second, Modifier.fillMaxWidth().then(if (index == 0 && tv) Modifier.focusRequester(first) else Modifier)) }
        }
        LaunchedEffect(title) { if (tv && options.isNotEmpty()) { withFrameNanos {}; first.requestFocus() } }
    }
}

@Composable internal fun FullInfo(title: String, text: String, onDismiss: () -> Unit) {
    AppOverlay(title, onDismiss, full = true) {
        VText(text, if (LocalTv.current) 26 else 16, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), color = C.textBody)
        Spacer(Modifier.height(measure(28, 20)))
        AppButton("Close", onDismiss)
    }
}

internal fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}
internal fun mediaFacts(media: Media) = listOfNotNull(media.year, media.imdbRating?.let { "IMDb $it" }, media.runtime).plus(media.genres.take(3)).joinToString(" · ")
