package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import java.net.URI
import org.viptv.app.theme.ViptvColor as C

private data class SettingRow(val title: String, val detail: String, val icon: String, val action: () -> Unit, val danger: Boolean = false, val checked: Boolean? = null)

@Composable internal fun SettingsScreen(state: AppState, controller: AppController, model: ViptvModel) {
    val tv = LocalTv.current
    val prefs = state.preferences
    var page by rememberSaveable { mutableStateOf("Settings") }
    var choice by remember { mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null) }
    var server by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var selected by remember(page) { mutableIntStateOf(0) }
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val avatarFocus = remember { FocusRequester() }
    fun choices(title: String, values: List<Pair<String, String>>, apply: (String) -> Unit) {
        choice = title to values.map { (label, value) -> label to { apply(value); choice = null } }
    }
    val languages = listOf("System default" to "", "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de", "Japanese" to "ja")
    fun language(code: String) = languages.firstOrNull { it.second == code }?.first ?: code
    val rows = if (page == "Playback preferences") listOf(
        SettingRow("Preferred audio", language(prefs.audioLanguage), "audio", { choices("Preferred audio", languages) { controller.setPreference(prefs.copy(audioLanguage = it)) } }),
        SettingRow("Subtitle language", language(prefs.subtitleLanguage), "captions", { choices("Subtitle language", languages) { controller.setPreference(prefs.copy(subtitleLanguage = it)) } }),
        SettingRow("Subtitles", if (prefs.subtitlesEnabled) "On" else "Off", "captions", { controller.setPreference(prefs.copy(subtitlesEnabled = !prefs.subtitlesEnabled)) }, checked = prefs.subtitlesEnabled),
        SettingRow("Subtitle size", prefs.subtitleSize, "captions", { choices("Subtitle size", listOf("Small" to "small", "Normal" to "normal", "Large" to "large")) { controller.setPreference(prefs.copy(subtitleSize = it)) } }),
        SettingRow("Subtitle appearance", prefs.subtitleStyle, "captions", { choices("Subtitle appearance", listOf("System default" to "system", "Text with shadow" to "shadow", "White text on black" to "opaque")) { controller.setPreference(prefs.copy(subtitleStyle = it)) } }),
        SettingRow("Maximum quality", prefs.quality, "settings", { choices("Maximum quality", listOf("Auto" to "auto", "1080p" to "1080p", "720p" to "720p", "480p" to "480p")) { controller.setPreference(prefs.copy(quality = it)) } }),
        SettingRow("Autoplay next episode", if (prefs.autoplay) "On" else "Off", "next", { controller.setPreference(prefs.copy(autoplay = !prefs.autoplay)) }, checked = prefs.autoplay),
    ) else listOf(
        SettingRow("Switch profile", if (tv) "Choose who's watching." else "", "profiles", { controller.navigate(Destination.Profile) }),
        SettingRow("Manage profiles", "Add, rename or delete profiles.", "person", controller::openProfileManagement),
        SettingRow("Playback preferences", "Audio, subtitles and quality", "play", { page = "Playback preferences" }),
        SettingRow("OLED mode", "Pure black background", "moon", { model.updateOled(!model.oled) }, checked = model.oled),
        SettingRow("Accent colour", "Make it yours", "settings", {
            choice = "Accent colour" to listOf("Gold" to C.accentDefault, "Coral" to C.accentOptionsCoral, "Mint" to C.accentOptionsMint, "Periwinkle" to C.accentOptionsPeriwinkle).map { (name, color) -> name to { model.updateAccent(color); choice = null } }
        }),
        SettingRow("Addons", "Shared by your account", "addons", { page = "Addons" }),
        SettingRow("Server", model.origin, "settings", { server = true }),
        SettingRow("About VIPTV", "Version " + BuildConfig.VERSION_NAME, "info", { about = true }),
        SettingRow("Sign out", if (tv) "Sign out of this TV?" else "Sign out of this device?", "exit", { controller.requestDialog(DialogKind.SignOut, if (tv) "Sign out of this TV?" else "Sign out of this device?") }, true),
    )
    BackHandler(page != "Settings") { page = "Settings" }
    LaunchedEffect(page) { if (tv && page != "Addons") { withFrameNanos {}; runCatching { first.requestFocus() } } }
    if (page == "Addons") AddonsContent(state, controller) { page = "Settings" }
    else Row(Modifier.fillMaxSize().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 32)), horizontalArrangement = Arrangement.spacedBy(144.dp)) {
        Column(Modifier.then(if (tv) Modifier.width(704.dp) else Modifier.fillMaxWidth())) {
            ScreenHeader(page, { if (page != "Settings") page = "Settings" else controller.back() })
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(measure(14, 0)), contentPadding = PaddingValues(bottom = 40.dp)) {
                itemsIndexed(rows, key = { _, row -> row.title }) { index, row ->
                    if (!tv && page == "Settings") when (index) {
                        0 -> SettingGroupLabel("PROFILE")
                        2 -> SettingGroupLabel("PLAYBACK")
                        3 -> SettingGroupLabel("THIS DEVICE")
                        5 -> SettingGroupLabel("ACCOUNT")
                    }
                    val starts = page != "Settings" || index in listOf(0, 2, 3, 5, 8)
                    val ends = page != "Settings" || index in listOf(1, 2, 4, 7, 8)
                    if (!tv && index == 8 && page == "Settings") Spacer(Modifier.height(24.dp))
                    SettingsRow(row, Modifier.then(if (index == 0 && tv) Modifier.focusRequester(first).focusProperties { left = rail; if (state.profiles.isNotEmpty() && page == "Settings") right = avatarFocus } else Modifier.focusProperties { if (tv) left = rail }), { selected = index }, starts, ends)
                }
            }
        }
        if (tv) Column(Modifier.weight(1f).padding(top = 136.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            VText(rows.getOrNull(selected)?.title.orEmpty(), 40, display = true)
            VText(rows.getOrNull(selected)?.detail.orEmpty(), 26, color = C.textSecondary)
            if (selected == 0 && page == "Settings") Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                state.profiles.take(4).forEachIndexed { index, profile ->
                    Holdable({ controller.chooseProfile(profile) }, modifier = Modifier.width(160.dp).then(if (index == 0) Modifier.focusRequester(avatarFocus) else Modifier)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileAvatar(profile, Modifier.size(160.dp))
                            VText(profile.name, 24, Modifier.padding(top = 18.dp), bold = true, lines = 1)
                            if (profile.id == state.selectedProfile?.id) VText("Watching now", 18, color = C.textSecondary)
                        }
                    }
                }
            }
        }
    }
    choice?.let { ChoiceDialog(it.first, it.second, { choice = null }) }
    if (server) ServerAddressEntry(model) { server = false }
    if (about) FullInfo("About VIPTV", "VIPTV " + BuildConfig.VERSION_NAME + "\n\nYour shows, channels and progress. All in one place.", { about = false })
}

@Composable private fun SettingGroupLabel(label: String) {
    VText(label, 12, Modifier.padding(start = 16.dp, top = 26.dp, bottom = 10.dp), C.textSecondary, bold = true)
}

@Composable private fun SettingsRow(row: SettingRow, modifier: Modifier, onFocused: () -> Unit = {}, starts: Boolean = true, ends: Boolean = true) {
    val tv = LocalTv.current
    val closeRail = LocalCloseRail.current
    var focused by remember { mutableStateOf(false) }
    val foreground = if (tv && focused) C.onLight else if (row.danger) C.statusDanger else C.textPrimary
    Holdable(row.action, modifier = modifier.fillMaxWidth().heightIn(min = measure(80, 60)).onFocusChanged { focused = it.isFocused; if (focused) { closeRail(); onFocused() } }
        .clip(if (tv) RoundedCornerShape(22.dp) else RoundedCornerShape(topStart = if (starts) 20.dp else 0.dp, topEnd = if (starts) 20.dp else 0.dp, bottomStart = if (ends) 20.dp else 0.dp, bottomEnd = if (ends) 20.dp else 0.dp)).background(if (tv && focused) C.textPrimary else C.surfaceN1)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = measure(28, 16), vertical = measure(16, 14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(22, 14))) {
            Box(Modifier.size(measure(28, 36)).then(if (tv) Modifier else Modifier.clip(RoundedCornerShape(10.dp)).background(C.surfaceN3)), contentAlignment = Alignment.Center) {
                VIcon(row.icon, modifier = Modifier.size(measure(28, 20)), color = foreground)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VText(row.title, if (tv) 28 else 16, color = foreground, bold = true, lines = 1)
                if (!tv && row.detail.isNotBlank()) VText(row.detail, 13, color = C.textSecondary, lines = 2)
            }
            if (row.checked != null) Switch(checked = row.checked, onCheckedChange = null)
            else VIcon("right", modifier = Modifier.size(measure(28, 20)), color = if (tv && focused) C.onLight else C.textTertiary)
        }
    }
}

@Composable internal fun AddonsScreen(state: AppState, controller: AppController) = AddonsContent(state, controller, controller::back)

@Composable private fun AddonsContent(state: AppState, controller: AppController, onBack: () -> Unit) {
    val tv = LocalTv.current
    var install by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Addon?>(null) }
    var removal by remember { mutableStateOf<Addon?>(null) }
    val first = LocalContentFocus.current
    Column(Modifier.fillMaxSize().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 32))) {
        ScreenHeader("Addons", onBack)
        VText("Shared by your account", if (tv) 24 else 15, color = C.textSecondary)
        AppButton("Install addon", { install = true }, Modifier.padding(vertical = 24.dp).focusRequester(first), "plus", primary = !tv)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(measure(14, 12))) {
            items(state.addons, key = { it.id }) { addon -> SettingsRow(SettingRow(addon.name, if (addon.enabled) "Enabled" else "Disabled", "addons", { selected = addon }), Modifier) }
        }
        LaunchedEffect(Unit) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    }
    if (install) TextEntry("Install addon", error.ifBlank { "Enter an HTTPS manifest URL." }, "https://", maxLength = 4096, onDone = { value ->
        val valid = runCatching { URI(value.trim()).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
        if (valid) { controller.installAddon(value.trim()); install = false; error = "" } else error = "Enter an HTTPS manifest URL."
    }, onCancel = { install = false; error = "" })
    selected?.let { addon -> ChoiceDialog("Manage " + addon.name, listOf(
        (if (addon.enabled) "Disable" else "Enable") to { controller.toggleAddon(addon); selected = null },
        "Remove addon" to { selected = null; removal = addon },
        "Cancel" to { selected = null },
    ), { selected = null }) }
    removal?.let { addon -> ChoiceDialog("Remove " + addon.name + "?", listOf("Cancel" to { removal = null }, "Remove addon" to { controller.removeAddon(addon); removal = null }), { removal = null }) }
}
