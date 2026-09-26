package org.viptv.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun Pairing(state: AppState, controller: AppController, model: ViptvModel) {
    val tv = LocalTv.current
    if (!tv && !state.sessionRestoring && !state.pairingRequested) { PhoneSignIn(state, controller, model); return }
    val context = LocalContext.current
    var server by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(state.sessionRestoring, state.deviceCode?.userCode) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    Box(Modifier.fillMaxSize().background(LocalGround.current)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = measure(192, 24), vertical = measure(54, 28))) {
            VText("VIPTV", if (tv) 40 else 26, display = true)
            Spacer(Modifier.height(measure(160, 96)))
            if (state.sessionRestoring) {
                VText(if (state.loading) "Starting VIPTV…" else "Could not connect", if (tv) 56 else 34, display = true)
                Spacer(Modifier.height(24.dp))
                if (state.loading) CircularProgressIndicator(color = LocalAccent.current)
                else {
                    VText(state.message ?: "Your saved session is retained. Check your connection and try again.", if (tv) 26 else 16, color = C.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    AppButton("Try again", controller::retryAuthentication, Modifier.focusRequester(first))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(measure(160, 0))) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(measure(30, 22))) {
                        VText("Sign in to VIPTV", if (tv) 56 else 34, display = true)
                        VText(if (tv) "Visit this address, then enter the code shown below." else "Your shows, channels and progress. All in one place.", if (tv) 28 else 16, color = C.textSecondary)
                        VText(state.deviceCode?.verificationUri ?: "Connecting to your server…", if (tv) 28 else 16, color = C.textBody, lines = 3)
                        VText(state.deviceCode?.userCode.orEmpty(), if (tv) 80 else 38, bold = true, display = true)
                        if (!tv && state.deviceCode != null) AppButton("Open sign-in page", {
                            val code = state.deviceCode
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUriComplete ?: code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, Modifier.fillMaxWidth(), primary = true)
                        if (state.message != null) AppButton("Try again", controller::retryAuthentication, Modifier.focusRequester(first))
                        AppButton("Change server", { server = true }, Modifier.then(if (state.message == null) Modifier.focusRequester(first) else Modifier), icon = "settings")
                        if (!tv) AppButton("Use username and password", controller::usePasswordSignIn, Modifier.fillMaxWidth())
                    }
                    if (tv) state.deviceCode?.let { code ->
                        val data = code.verificationUriComplete ?: code.verificationUri + "?code=" + code.userCode
                        val bitmap = remember(data) { runCatching {
                            val matrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 360, 360)
                            Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888).apply {
                                for (y in 0 until 360) for (x in 0 until 360) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                            }.asImageBitmap()
                        }.getOrNull() }
                        if (bitmap != null) Box(Modifier.padding(top = 16.dp).size(432.dp).clip(RoundedCornerShape(32.dp)).background(C.textPrimary).padding(36.dp)) {
                            Image(bitmap, "Scan to pair VIPTV", Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        if (server) ServerAddressEntry(model) { server = false }
    }
}

@Composable private fun PhoneSignIn(state: AppState, controller: AppController, model: ViptvModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun submit() {
        if (state.loading) return
        val supplied = password; password = ""
        keyboard?.hide(); focus.clearFocus()
        controller.signIn(username, supplied)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        VText("VIPTV", 26, display = true)
        Spacer(Modifier.height(56.dp))
        VText("Sign in to VIPTV", 34, display = true)
        VText("Your shows, channels and progress. All in one place.", 16, color = C.textSecondary)
        Spacer(Modifier.height(4.dp))
        AppField(username, { username = it.take(128) }, "Username")
        AppField(password, { password = it.take(256) }, "Password", secret = true, keyboardType = KeyboardType.Password, onSubmit = ::submit)
        state.message?.let { VText(it, 14, color = C.statusDanger) }
        if (state.loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = LocalAccent.current, strokeWidth = 2.dp)
            VText("Signing in…", 15, color = C.textSecondary)
        }
        AppButton(if (state.loading) "Signing in…" else "Sign in", ::submit, Modifier.fillMaxWidth(), primary = true)
        AppButton("Use device code", { password = ""; keyboard?.hide(); controller.beginPairing() }, Modifier.fillMaxWidth())
        AppButton("Change server", { password = ""; server = true }, Modifier.fillMaxWidth(), icon = "settings")
    }
    if (server) ServerAddressEntry(model) { server = false }
}

@Composable internal fun ServerAddressEntry(model: ViptvModel, onClose: () -> Unit) {
    var error by remember { mutableStateOf("") }
    TextEntry("Server address", error.ifBlank { "Enter an HTTPS origin. Changing server signs this device out." }, model.origin, onDone = { value ->
        val origin = ServerOrigin.validate(value)
        if (origin == null) error = "Enter a valid HTTPS origin without a path or credentials."
        else { model.changeOrigin(origin); onClose() }
    }, onCancel = onClose)
}

@Composable internal fun ProfileChooser(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val visible = if (tv) state.profiles.drop(state.profilePage * 5).take(5) else state.profiles
    val first = remember(state.profilePage) { FocusRequester() }
    val manage = remember { FocusRequester() }
    val previous = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    var rememberedProfile by rememberSaveable { mutableStateOf(state.selectedProfile?.id) }
    val focusIndex = visible.indexOfFirst { it.id == rememberedProfile }.coerceAtLeast(0)
    val canAdd = state.profiles.size < 12
    LaunchedEffect(visible.map { it.id }, state.profilePage) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = measure(96, 16), vertical = measure(54, 24))) {
        VText("VIPTV", if (tv) 40 else 24, display = true)
        Spacer(Modifier.height(measure(120, 158)))
        VText(if (state.managingProfiles) "Manage profiles" else "Who’s watching?", if (tv) 56 else 30, Modifier.fillMaxWidth(), display = true, align = TextAlign.Center)
        Spacer(Modifier.height(measure(96, 40)))
        if (tv) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally)) {
            visible.forEachIndexed { index, profile -> ProfileTile(profile, state.managingProfiles, { rememberedProfile = profile.id; if (state.managingProfiles) controller.editProfile(profile) else controller.chooseProfile(profile) }, { rememberedProfile = profile.id; controller.editProfile(profile) }, Modifier.focusProperties { down = manage }.then(if (index == focusIndex) Modifier.focusRequester(first) else Modifier)) }
            if (canAdd) ProfileTile(null, false, { controller.editProfile() }, {}, Modifier.focusProperties { down = manage }.then(if (visible.isEmpty()) Modifier.focusRequester(first) else Modifier))
        } else Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 720.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            (visible.map { it as Profile? } + if (canAdd) listOf(null) else emptyList()).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    row.forEach { profile -> ProfileTile(profile, state.managingProfiles, { if (profile == null || state.managingProfiles) controller.editProfile(profile) else controller.chooseProfile(profile) }, { controller.editProfile(profile) }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(measure(80, 36)))
        AppButton(if (state.managingProfiles) "Done" else "Manage profiles", controller::toggleProfileManagement, Modifier.align(Alignment.CenterHorizontally).then(if (tv) Modifier.width(360.dp).focusRequester(manage).focusProperties { up = first; down = if (state.profiles.size > 5) previous else FocusRequester.Cancel } else Modifier.fillMaxWidth()), if (state.managingProfiles) "check" else "settings")
        if (tv && state.profiles.size > 5) Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AppButton("Previous", { controller.setProfilePage(state.profilePage - 1) }, Modifier.focusRequester(previous).focusProperties { up = manage; right = next; left = FocusRequester.Cancel; down = FocusRequester.Cancel })
            VText((state.profilePage + 1).toString() + " / " + ((state.profiles.size + 4) / 5), 22, Modifier.align(Alignment.CenterVertically))
            AppButton("Next", { controller.setProfilePage(state.profilePage + 1) }, Modifier.focusRequester(next).focusProperties { up = manage; left = previous; right = FocusRequester.Cancel; down = FocusRequester.Cancel })
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable private fun ProfileTile(profile: Profile?, managing: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val tv = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    Holdable(onClick, if (profile == null) null else onHold, modifier.then(if (tv) Modifier.width(220.dp) else Modifier).onFocusChanged { focused = it.isFocused }) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(20)).background(if (profile == null) C.surfaceN1 else C.surfaceAvatar)
            .border(if (tv && focused) 4.dp else 1.dp, if (tv && focused) C.fillWhite else C.lineOutline, RoundedCornerShape(20)), contentAlignment = Alignment.Center) {
            if (profile != null) ProfileAvatar(profile, Modifier.fillMaxSize())
            else VIcon("plus", "Add profile", Modifier.size(measure(60, 40)), C.textSecondary)
            if (profile != null && managing) Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(measure(40, 30)).clip(CircleShape).background(C.textPrimary), contentAlignment = Alignment.Center) {
                VIcon("pencil", "Edit profile", Modifier.size(measure(24, 18)), C.onLight)
            }
        }
        VText(profile?.name ?: "Add profile", if (tv) 28 else 15, Modifier.padding(top = measure(18, 12)), if (focused || !tv) C.textPrimary else C.textSecondary, bold = true, lines = 1, align = TextAlign.Center)
      }
    }
}

internal data class AvatarEntry(val name: String, val uri: String)
internal data class AvatarCategory(val style: String, val name: String, val entries: List<AvatarEntry>)
private var cachedAvatarCatalog: List<AvatarCategory>? = null
@Composable internal fun avatarCatalog(): List<AvatarCategory> {
    val context = LocalContext.current
    return remember { cachedAvatarCatalog ?: runCatching {
        val json = context.assets.open("roku/data/avatar-catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val categories = json.getJSONArray("categories")
        List(categories.length()) { index ->
            val category = categories.getJSONObject(index); val items = category.optJSONArray("items")
            AvatarCategory(category.getString("style"), category.getString("name"), List(items?.length() ?: 48) { n ->
                val item = items?.getJSONObject(n)
                AvatarEntry(item?.getString("name") ?: category.getString("name") + " " + (n + 1),
                    if (item != null) "file:///android_asset/roku/" + item.getString("local").removePrefix("pkg:/")
                    else "file:///android_asset/roku/images/avatar-catalog/" + category.getString("style") + "-" + (n + 1) + ".png")
            })
        }.filter { it.entries.isNotEmpty() }
    }.getOrDefault(emptyList()).also { cachedAvatarCatalog = it } }
}

@Composable internal fun ProfileAvatar(profile: Profile?, modifier: Modifier) {
    val catalog = avatarCatalog()
    val packaged = catalog.firstOrNull { it.style == profile?.avatarStyle }?.entries?.getOrNull((profile?.avatarChoice ?: 0) - 1)?.uri
    Avatar(profile?.name.orEmpty(), profile?.avatarUrl ?: packaged, modifier)
}

@Composable internal fun ProfileEditor(profile: Profile?, controller: AppController) {
    val tv = LocalTv.current
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var style by remember(profile?.id) { mutableStateOf(profile?.avatarStyle ?: "disney") }
    var choice by remember(profile?.id) { mutableIntStateOf(profile?.avatarChoice ?: 1) }
    var avatarChanged by remember(profile?.id) { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val catalog = avatarCatalog()
    val uri = catalog.firstOrNull { it.style == style }?.entries?.getOrNull(choice - 1)?.uri
    val initial = LocalContentFocus.current
    LaunchedEffect(Unit) { if (tv) { withFrameNanos {}; runCatching { initial.requestFocus() } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(start = measure(256, 20), end = measure(192, 20), top = measure(130, 24), bottom = measure(64, 32))) {
        ScreenHeader(if (profile == null) "Add a profile" else "Edit profile", controller::back)
        VText("A space for their favorites, shows, and discoveries.", if (tv) 26 else 15, color = C.textSecondary)
        Spacer(Modifier.height(measure(64, 32)))
        Holdable({ choosing = true }, modifier = Modifier.size(measure(220, 140)).align(if (tv) Alignment.Start else Alignment.CenterHorizontally).focusRequester(initial)) {
            Avatar(name, if (avatarChanged || profile == null) uri else profile.avatarUrl ?: uri, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(measure(44, 36)).clip(CircleShape).background(C.textPrimary), contentAlignment = Alignment.Center) { VIcon("pencil", "Change avatar", color = C.onLight) }
        }
        Spacer(Modifier.height(measure(36, 28)))
        VText("PROFILE NAME", if (tv) 20 else 12, color = C.textSecondary, bold = true)
        Spacer(Modifier.height(12.dp))
        if (tv) AppButton(name.ifBlank { "Enter a name" }, { editing = true }, Modifier.width(720.dp))
        else AppField(name, { name = it.take(80) }, "Profile name")
        Spacer(Modifier.height(measure(64, 28)))
        AppButton(if (profile == null) "Create profile" else "Save changes", { controller.saveProfile(profile, name, style, choice) }, Modifier.then(if (tv) Modifier.width(380.dp) else Modifier.fillMaxWidth()), primary = true)
        Spacer(Modifier.height(16.dp))
        AppButton("Cancel", controller::back, Modifier.then(if (tv) Modifier.width(380.dp) else Modifier.fillMaxWidth()))
        if (profile != null && !profile.primary) {
            Spacer(Modifier.height(24.dp))
            AppButton("Delete profile", { controller.requestDeleteProfile(profile) }, Modifier.then(if (tv) Modifier.width(380.dp) else Modifier.fillMaxWidth()), "delete", danger = true)
        }
    }
    if (editing) TextEntry("Name this profile", "Enter a name for this viewer.", name, maxLength = 80, onDone = { name = it; editing = false }, onCancel = { editing = false })
    if (choosing) AvatarPicker(catalog, style, { selectedStyle, selectedChoice -> style = selectedStyle; choice = selectedChoice; avatarChanged = true; choosing = false }, { choosing = false })
}

@Composable private fun AvatarPicker(categories: List<AvatarCategory>, initialStyle: String, onChoose: (String, Int) -> Unit, onClose: () -> Unit) {
    val tv = LocalTv.current
    var style by remember { mutableStateOf(initialStyle) }
    var page by remember(style) { mutableIntStateOf(0) }
    val category = categories.firstOrNull { it.style == style } ?: categories.firstOrNull()
    val size = if (tv) 18 else 15
    val pages = ((category?.entries?.size ?: 0) + size - 1) / size
    AppOverlay("Find your favorite", onClose, full = true) {
        VText(categories.sumOf { it.entries.size }.toString() + " avatars. Pick a world, then pick your character.", if (tv) 24 else 14, color = C.textSecondary)
        LazyRow(Modifier.fillMaxWidth().padding(vertical = measure(28, 20)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(categories, key = { it.style }) { item -> AppChip(item.name, { style = item.style }, item.style == category?.style) }
        }
        LazyVerticalGrid(GridCells.Fixed(if (tv) 6 else 3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(measure(28, 12)), verticalArrangement = Arrangement.spacedBy(measure(28, 12))) {
            itemsIndexed(category?.entries?.drop(page * size)?.take(size).orEmpty(), key = { _, item -> item.uri }) { index, avatar ->
                var focused by remember { mutableStateOf(false) }
                Holdable({ category?.let { onChoose(it.style, page * size + index + 1) } }, modifier = Modifier.aspectRatio(1f).onFocusChanged { focused = it.isFocused }.border(4.dp, if (focused && tv) C.textPrimary else Color.Transparent, RoundedCornerShape(20))) {
                    Avatar(avatar.name, avatar.uri, Modifier.fillMaxSize())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AppButton("Previous", { page = (page - 1).coerceAtLeast(0) })
            VText((page + 1).toString() + " / " + pages.coerceAtLeast(1), if (tv) 22 else 14, Modifier.align(Alignment.CenterVertically))
            AppButton("Next", { page = (page + 1).coerceAtMost((pages - 1).coerceAtLeast(0)) })
        }
    }
}

@Composable internal fun TextEntry(title: String, instruction: String, initial: String = "", secret: Boolean = false, maxLength: Int = 256, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val tv = LocalTv.current
    var value by remember(title) { mutableStateOf(initial.take(maxLength)) }
    val first = remember { FocusRequester() }
    fun edit(text: String) { value = (if (secret) text.filter(Char::isDigit) else text).take(if (secret) 8 else maxLength) }
    AppOverlay(title, onCancel, full = tv) {
        VText(instruction, if (tv) 24 else 15, color = C.textSecondary)
        Spacer(Modifier.height(24.dp))
        if (!tv) AppField(value, ::edit, if (secret) "Parent PIN" else title, Modifier.focusRequester(first), secret)
        else {
            VText(if (secret) "•".repeat(value.length) else value.ifBlank { " " }, 34, Modifier.fillMaxWidth().background(C.surfaceN2, RoundedCornerShape(20.dp)).padding(24.dp), lines = 2)
            Spacer(Modifier.height(28.dp))
            RemoteKeyboard(value, ::edit, first, secret, symbols = !secret, compact = true)
        }
        Spacer(Modifier.height(measure(32, 24)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppButton(if (secret) "Unlock" else "Done", { val submitted = value; if (secret) value = ""; onDone(submitted) }, Modifier.weight(1f), primary = true)
            AppButton("Cancel", { value = ""; onCancel() }, Modifier.weight(1f))
        }
        LaunchedEffect(title) { withFrameNanos {}; runCatching { first.requestFocus() } }
    }
}

@Composable internal fun RemoteKeyboard(value: String, onChange: (String) -> Unit, first: FocusRequester, secret: Boolean = false, onResults: (() -> Unit)? = null, symbols: Boolean = false, compact: Boolean = false) {
    var text by remember { mutableStateOf(value) }
    SideEffect { text = value }
    fun change(next: String) { text = next; onChange(next) }
    val keys = if (secret) "1234567890" else "abcdefghijklmnopqrstuvwxyz1234567890"
    var uppercase by remember { mutableStateOf(false) }
    Column(Modifier.onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (key.action != KeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode == KeyEvent.KEYCODE_DEL -> { change(text.dropLast(1)); true }
            key.unicodeChar >= 32 && key.unicodeChar != 127 && (!secret || key.unicodeChar.toChar().isDigit()) -> { change(text + key.unicodeChar.toChar()); true }
            else -> false
        }
    }, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
        keys.chunked(if (secret) 3 else 6).forEachIndexed { row, letters ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                letters.forEachIndexed { column, letter ->
                    val label = if (uppercase) letter.uppercase() else letter.toString()
                    AppButton(label, { change(text + label) }, Modifier.size(if (secret) 110.dp else 84.dp, if (compact) 48.dp else 64.dp)
                        .then(if (row == 0 && column == 0) Modifier.focusRequester(first) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (onResults != null && column == letters.lastIndex && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onResults()
                                true
                            } else false
                        }, pill = false)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!secret) AppButton("Space", { change(text + " ") }, Modifier.width(180.dp).height(if (compact) 52.dp else 72.dp), pill = false)
            AppButton("Delete", { change(text.dropLast(1)) }, Modifier.width(180.dp).height(if (compact) 52.dp else 72.dp), pill = false)
            AppButton("Clear", { change("") }, Modifier.width(180.dp).height(if (compact) 52.dp else 72.dp), pill = false)
        }
        if (symbols) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ":/.?=&_-".forEach { symbol -> AppButton(symbol.toString(), { change(text + symbol) }, Modifier.size(64.dp, 48.dp), pill = false) }
            AppButton("Shift", { uppercase = !uppercase }, Modifier.width(140.dp).height(48.dp), selected = uppercase, pill = false)
        }
    }
}

@Composable internal fun PinDialog(prompt: PinPrompt, controller: AppController) {
    val state by controller.state.collectAsState()
    TextEntry(prompt.title, state.message ?: "Enter a 4–8 digit parent PIN", secret = true, onDone = controller::submitPin, onCancel = controller::cancelPin)
}
