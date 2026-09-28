package org.viptv.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

internal val LocalTvRemote = staticCompositionLocalOf<TvRemoteController?> { null }

@Composable internal fun PhoneRemoteButton() {
    if (LocalTv.current) return
    val remote = LocalTvRemote.current ?: return
    if (remote.visible) Box {
        IconButton(remote::open, Modifier.size(44.dp).clip(CircleShape).background(C.surfaceN1).border(1.dp, C.lineOutline, CircleShape)) {
            Icon(Icons.Rounded.SettingsRemote, "TV remote: ${remote.selected?.name}", tint = C.textPrimary)
        }
        DropdownMenu(remote.tip && remote.page.isEmpty(), remote::dismissTip, containerColor = C.textPrimary) {
            Column(Modifier.width(260.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VText("Your TV remote", 20, color = C.onLight, display = true)
                VText("It stays up here on Home, Discover, Live and My List. Turn it off in Settings › Watch on TV.", 14, color = C.onLight)
                TextButton(remote::dismissTip) { VText("Got it", color = C.onLight, bold = true) }
            }
        }
    }
}

@Composable internal fun PhoneTabActions(state: AppState, controller: AppController) {
    if (!LocalTv.current) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhoneRemoteButton()
        Holdable({ controller.navigate(Destination.Settings) }, modifier = Modifier.size(44.dp)) { ProfileAvatar(state.selectedProfile, Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TvRemoteOverlay(remote: TvRemoteController) {
    val context = LocalContext.current
    val page = remote.page
    if (page.isEmpty()) return
    BackHandler { remote.back() }
    if (page == "remote") {
        ModalBottomSheet(onDismissRequest = remote::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = C.surfaceN1, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            RemoteControls(remote)
        }
        return
    }
    Surface(Modifier.fillMaxSize(), color = LocalGround.current) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ScreenHeader("Watch on TV", remote::back)
            when (page) {
                "intro" -> {
                    Icon(Icons.Rounded.SettingsRemote, null, Modifier.size(64.dp), tint = LocalAccent.current)
                    VText("Use this phone as your TV remote", 34, display = true)
                    VText("Works with Vizio SmartCast TVs on the same Wi-Fi as this phone. It's off until you set it up.", color = C.textSecondary)
                    VText("Arrows, OK, Back, play and volume for your TV.")
                    VText("Opens VIPTV on the TV in one tap.")
                    VText("What's playing on this phone stays here. Nothing is sent to the TV.")
                    VText("VIPTV searches your local network only to find your TV.", 13, color = C.textSecondary)
                    AppButton("Set up a TV", remote::discover, Modifier.fillMaxWidth(), primary = true)
                }
                "search" -> {
                    VText("Choose your TV", 34, display = true)
                    if (remote.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); VText("Looking for Vizio TVs on your Wi-Fi…", color = C.textSecondary) }
                    remote.results.forEach { tv -> AppButton(tv.name, { remote.connect(tv.origin, tv.name) }, Modifier.fillMaxWidth(), "live") }
                    VText("Don't see your TV?", 20, display = true)
                    VText("Turn the TV on, then check it's on the same Wi-Fi as this phone. It can take a few seconds to show up.", color = C.textSecondary)
                    AppButton("Enter IP address", remote::manual, Modifier.fillMaxWidth())
                    if (!remote.busy) AppButton("Try again", remote::discover, Modifier.fillMaxWidth())
                }
                "manual" -> {
                    var address by rememberSaveable { mutableStateOf("") }
                    VText("Enter your TV's IP address", 34, display = true)
                    VText("Find it on the TV under Settings › Network.", color = C.textSecondary)
                    AppField(address, { address = it }, "IP address", keyboardType = KeyboardType.Uri, onSubmit = { remote.connect(address) })
                    AppButton("Connect", { remote.connect(address) }, Modifier.fillMaxWidth(), primary = true)
                    AppButton("Cancel", remote::back, Modifier.fillMaxWidth())
                }
                "pin" -> {
                    VText("Enter the PIN on your TV", 34, display = true)
                    VText("${remote.tvName} is showing a 4-digit PIN.", color = C.textSecondary)
                    BasicTextField(remote.pin, remote::enterPin, enabled = !remote.busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        cursorBrush = SolidColor(Color.Transparent), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "4-digit PIN" },
                        decorationBox = { inner ->
                            Box {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                                    repeat(4) { index -> Box(Modifier.size(64.dp, 72.dp).clip(RoundedCornerShape(18.dp)).background(C.surfaceN1)
                                        .border(1.dp, if (index == remote.pin.length) LocalAccent.current else C.lineOutline, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                                        VText(if (index < remote.pin.length) "●" else "", 24)
                                    } }
                                }
                                Box(Modifier.alpha(0f)) { inner() }
                            }
                        })
                    VText("Pairs as soon as all 4 digits are in.", 13, color = C.textSecondary)
                    if (remote.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    AppButton("New PIN", remote::newPin, Modifier.fillMaxWidth(), enabled = !remote.busy)
                    AppButton("Cancel", remote::back, Modifier.fillMaxWidth())
                }
                "done" -> {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(64.dp), tint = LocalAccent.current)
                    VText("Connected to ${remote.tvName}", 34, display = true)
                    VText("You can control it from this phone now.")
                    VText("The remote button shows at the top of Home, Discover, Live and My List.", color = C.textSecondary)
                    AppButton("Open the remote", remote::open, Modifier.fillMaxWidth(), primary = true)
                    AppButton("Done", remote::dismiss, Modifier.fillMaxWidth())
                }
                "settings" -> {
                    VText(remote.selected?.name ?: "Vizio TV", 24, display = true)
                    VText(remote.selected?.origin?.removePrefix("https://").orEmpty(), color = C.textSecondary)
                    AppButton("Open the remote", remote::open, Modifier.fillMaxWidth(), primary = true)
                    RemotePreference("Remote button", "At the top of Home, Discover, Live and My List", remote.header) { remote.preference("header", it) }
                    RemotePreference("Vibrate on press", "", remote.vibrate) { remote.preference("vibrate", it) }
                    RemotePreference("Keep screen on", "While the remote is open", remote.keepAwake) { remote.preference("awake", it) }
                    AppButton("Change TV", remote::changeTv, Modifier.fillMaxWidth())
                    AppButton("Forget this TV", remote::confirmForget, Modifier.fillMaxWidth(), danger = true)
                }
                "forget" -> {
                    VText("Forget ${remote.tvName}?", 24, display = true)
                    VText("The remote button goes away. You can set up a TV again at any time.", color = C.textSecondary)
                    AppButton("Forget TV", remote::forget, Modifier.fillMaxWidth(), danger = true)
                    AppButton("Cancel", remote::cancelForget, Modifier.fillMaxWidth())
                }
                "access" -> {
                    VText("VIPTV can't look for TVs", 34, display = true)
                    VText("Check local network access in your phone's settings, then come back here.")
                    AppButton("Open phone settings", { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, Modifier.fillMaxWidth(), primary = true)
                    AppButton("Try again", remote::discover, Modifier.fillMaxWidth())
                }
            }
            if (remote.message.isNotBlank()) VText(remote.message, 14, color = C.statusDanger)
        }
    }
}

@Composable private fun RemotePreference(title: String, detail: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surfaceN1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { VText(title, bold = true); if (detail.isNotEmpty()) VText(detail, 13, color = C.textSecondary) }
        Switch(value, change)
    }
}

@Composable private fun RemoteControls(remote: TvRemoteController) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    DisposableEffect(view, remote.keepAwake) {
        val original = view.keepScreenOn
        view.keepScreenOn = remote.keepAwake
        onDispose { view.keepScreenOn = original }
    }
    val send: (String) -> Unit = { key ->
        if (remote.online && !remote.busy) {
            if (remote.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            remote.key(key)
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tv, null, Modifier.size(44.dp), tint = C.textPrimary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                VText(remote.tvName, 17, bold = true)
                VText(if (remote.online) "Connected · ${remote.address}" else "Not reachable", 13, color = C.textSecondary)
            }
            IconButton(remote::dismiss) { Icon(Icons.Rounded.Close, "Close remote", tint = C.textPrimary) }
        }
        if (remote.online) AppButton("Open VIPTV on TV", remote::launchTv, Modifier.fillMaxWidth(), "live", primary = true, enabled = !remote.busy)
        else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surfaceN2).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VText("Can't reach ${remote.tvName}", 20, display = true)
            VText("Turn the TV on and check it's on the same Wi-Fi as this phone.", 14, color = C.textSecondary)
            AppButton("Try again", remote::retry, enabled = !remote.busy)
            AppButton("Pair again", remote::repair, enabled = !remote.busy)
        }
        if (remote.message.isNotBlank()) VText(remote.message, 13, color = C.textSecondary)
        Spacer(Modifier.height(32.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            FilterTabs(listOf("Buttons", "Swipe"), if (remote.swipe) "Swipe" else "Buttons", { remote.preference("swipe", it == "Swipe") })
        }
        if (remote.swipe) {
            val density = LocalDensity.current
            var drag by remember { mutableStateOf(Offset.Zero) }
            val currentSend by rememberUpdatedState(send)
            Box(Modifier.fillMaxWidth().height(272.dp).clip(RoundedCornerShape(28.dp)).background(C.surfaceN2)
                .clickable(enabled = remote.online, onClickLabel = "OK") { send("OK") }
                .pointerInput(remote.online) {
                    if (remote.online) detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                        RemoteInput.swipe(drag.x, drag.y, with(density) { 32.dp.toPx() })?.let(currentSend)
                    }, onDrag = { change, delta -> change.consume(); drag += delta })
                }, contentAlignment = Alignment.Center) { VText("Swipe to move · Tap for OK", 14, color = C.textSecondary) }
        } else Box(Modifier.size(272.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(C.surfaceN2)) {
            RemoteKey(Icons.Rounded.KeyboardArrowUp, "Up", "UP", remote, send, Modifier.align(Alignment.TopCenter), 88)
            RemoteKey(Icons.Rounded.KeyboardArrowDown, "Down", "DOWN", remote, send, Modifier.align(Alignment.BottomCenter), 88)
            RemoteKey(Icons.Rounded.KeyboardArrowLeft, "Left", "LEFT", remote, send, Modifier.align(Alignment.CenterStart), 88)
            RemoteKey(Icons.Rounded.KeyboardArrowRight, "Right", "RIGHT", remote, send, Modifier.align(Alignment.CenterEnd), 88)
            Button({ send("OK") }, Modifier.size(104.dp).align(Alignment.Center), enabled = remote.online,
                colors = ButtonDefaults.buttonColors(containerColor = C.textPrimary, contentColor = C.onLight), shape = CircleShape) { VText("OK", 20, color = C.onLight, bold = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Triple(Icons.Rounded.Undo, "Back", "BACK"), Triple(Icons.Rounded.PlayArrow, "Play", "PLAY"), Triple(Icons.Rounded.Pause, "Pause", "PAUSE")).forEach { (icon, label, key) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteKey(icon, label, key, remote, send)
                    VText(label, 12, color = C.textTertiary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    RemoteKey(Icons.Rounded.Remove, "Volume down", "VOL_DOWN", remote, send)
                    RemoteKey(Icons.Rounded.Add, "Volume up", "VOL_UP", remote, send)
                }
                VText("Volume", 12, color = C.textTertiary)
            }
        }
    }
}

@Composable private fun RemoteKey(icon: ImageVector, label: String, key: String, remote: TvRemoteController, send: (String) -> Unit, modifier: Modifier = Modifier, size: Int = 60) {
    IconButton({ send(key) }, modifier.size(size.dp).clip(CircleShape).background(if (size == 88) C.surfaceN2 else C.surfaceN3), enabled = remote.online) {
        Icon(icon, label, Modifier.size(30.dp), tint = if (remote.online) C.textPrimary else C.textTertiary.copy(alpha = .4f))
    }
}
