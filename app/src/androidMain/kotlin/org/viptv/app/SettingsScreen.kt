package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.yield
import java.net.URI

private val SettingsCanvas = Color(0xFF101112)
private val SettingsSurface = Color(0xFF202224)
private val SettingsWhite = Color(0xFFF5F5F5)
private val SettingsMuted = Color(0xFFC5C6C7)
private val SettingsShape = RoundedCornerShape(12.dp)

/**
 * Standalone Android-TV Settings UI. The host owns navigation, parent-PIN
 * authorization and persistence; this surface only emits deliberate choices.
 */
@Composable
fun SettingsScreen(
    preferences: PlaybackPreferences,
    addons: List<Addon>,
    serverAbout: ServerAbout?,
    onSavePreferences: (PlaybackPreferences) -> Unit,
    onInstallAddon: (String) -> Unit,
    onToggleAddon: (Addon) -> Unit,
    onRemoveAddon: (Addon) -> Unit,
    onOpenProfiles: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choice by remember { mutableStateOf<SettingsChoice?>(null) }
    var pendingRemoval by remember { mutableStateOf<Addon?>(null) }
    var installing by remember { mutableStateOf(false) }
    var manifestUrl by remember { mutableStateOf("") }
    var manifestError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val profilesFocus = remember { FocusRequester() }
    val subtitlesEnabledFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val subtitleSizeFocus = remember { FocusRequester() }
    val subtitleAppearanceFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val addonFocus = remember(addons.map { it.id }) { addons.associate { it.id to FocusRequester() } }
    var dialogOrigin by remember { mutableStateOf<SettingsDialogOrigin?>(null) }

    fun showChoice(origin: FocusRequester, dialog: SettingsChoice) {
        dialogOrigin = SettingsDialogOrigin(origin, scrollState.value)
        choice = dialog
    }

    fun showRemoval(origin: FocusRequester, addon: Addon) {
        dialogOrigin = SettingsDialogOrigin(origin, scrollState.value)
        pendingRemoval = addon
    }

    LaunchedEffect(Unit) { profilesFocus.requestFocus() }
    // A dialog temporarily owns focus. Restore the row and its viewport only
    // when the final nested dialog (Manage add-on → Remove) has closed.
    LaunchedEffect(choice != null, pendingRemoval != null) {
        if (choice != null || pendingRemoval != null) return@LaunchedEffect
        val origin = dialogOrigin ?: return@LaunchedEffect
        dialogOrigin = null
        // Focus may request automatic bring-into-view. Let it settle first, then
        // restore the captured viewport rather than only keeping the row visible.
        origin.focus.requestFocus()
        yield()
        scrollState.scrollTo(origin.scrollOffset)
    }
    Box(modifier.fillMaxSize().background(SettingsCanvas)) {
        Column(
            Modifier.fillMaxSize().padding(start = 100.dp, top = 54.dp, end = 84.dp, bottom = 48.dp)
                .verticalScroll(scrollState),
        ) {
            Text("Settings", color = SettingsWhite, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            SettingsHeading("Account")
            SettingsAction("Profiles", onOpenProfiles, Modifier.fillMaxWidth().focusRequester(profilesFocus))

            SettingsHeading("Playback")
            SettingsAction("Autoplay next episode: ${onOff(preferences.autoplay)}", onActivate = {
                onSavePreferences(preferences.copy(autoplay = !preferences.autoplay))
            })
            SettingsAction("Start with subtitles: ${onOff(preferences.subtitlesEnabled)}", onActivate = {
                showChoice(subtitlesEnabledFocus, SettingsChoice(
                    "Start with subtitles",
                    listOf(
                        SettingOption("On") { onSavePreferences(preferences.copy(subtitlesEnabled = true)) },
                        SettingOption("Off") { onSavePreferences(preferences.copy(subtitlesEnabled = false)) },
                    ),
                ))
            }, Modifier.fillMaxWidth().focusRequester(subtitlesEnabledFocus))
            SettingsAction("Preferred audio: ${languageLabel(preferences.audioLanguage)}", onActivate = {
                showChoice(audioFocus, languageChoice("Preferred audio") {
                    onSavePreferences(preferences.copy(audioLanguage = it))
                })
            }, Modifier.fillMaxWidth().focusRequester(audioFocus))
            SettingsAction("Preferred subtitles: ${languageLabel(preferences.subtitleLanguage)}", onActivate = {
                showChoice(subtitlesFocus, languageChoice("Preferred subtitles") {
                    onSavePreferences(preferences.copy(subtitleLanguage = it))
                })
            }, Modifier.fillMaxWidth().focusRequester(subtitlesFocus))
            SettingsAction("Subtitle size: ${sizeLabel(preferences.subtitleSize)}", onActivate = {
                showChoice(subtitleSizeFocus, SettingsChoice(
                    "Subtitle size",
                    listOf(
                        SettingOption("Small") { onSavePreferences(preferences.copy(subtitleSize = "small")) },
                        SettingOption("System default") { onSavePreferences(preferences.copy(subtitleSize = "normal")) },
                        SettingOption("Large") { onSavePreferences(preferences.copy(subtitleSize = "large")) },
                    ),
                ))
            }, Modifier.fillMaxWidth().focusRequester(subtitleSizeFocus))
            SettingsAction("Subtitle appearance: ${styleLabel(preferences.subtitleStyle)}", onActivate = {
                showChoice(subtitleAppearanceFocus, SettingsChoice(
                    "Subtitle appearance",
                    listOf(
                        SettingOption("System default") { onSavePreferences(preferences.copy(subtitleStyle = "system")) },
                        SettingOption("Text with shadow") { onSavePreferences(preferences.copy(subtitleStyle = "shadow")) },
                        SettingOption("White text on black") { onSavePreferences(preferences.copy(subtitleStyle = "opaque")) },
                    ),
                ))
            }, Modifier.fillMaxWidth().focusRequester(subtitleAppearanceFocus))
            SettingsAction("Maximum quality: ${qualityLabel(preferences.quality)}", onActivate = {
                showChoice(qualityFocus, SettingsChoice(
                    "Maximum quality",
                    listOf(
                        SettingOption("Auto") { onSavePreferences(preferences.copy(quality = "auto")) },
                        SettingOption("1080p") { onSavePreferences(preferences.copy(quality = "1080p")) },
                        SettingOption("720p") { onSavePreferences(preferences.copy(quality = "720p")) },
                        SettingOption("480p") { onSavePreferences(preferences.copy(quality = "480p")) },
                    ),
                ))
            }, Modifier.fillMaxWidth().focusRequester(qualityFocus))
            Text(
                "Applies to your next playback. Manual track choices take priority.",
                color = SettingsMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            SettingsHeading("Add-ons")
            Text("Shared by all profiles and devices on your account.", color = SettingsMuted, modifier = Modifier.padding(bottom = 10.dp))
            SettingsAction("Install add-on", onActivate = {
                installing = true
                manifestError = null
            })
            if (installing) {
                TextField(
                    value = manifestUrl,
                    onValueChange = { manifestUrl = it; manifestError = null },
                    label = { Text("Add-on manifest URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.width(720.dp).padding(top = 12.dp),
                )
                manifestError?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 8.dp)) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsAction("Install", {
                        val normalized = manifestUrl.trim()
                        if (!isHttpsManifest(normalized)) {
                            manifestError = "Enter an HTTPS manifest URL."
                        } else {
                            onInstallAddon(normalized)
                            installing = false
                            manifestUrl = ""
                        }
                    }, Modifier.width(170.dp))
                    SettingsAction("Cancel", { installing = false; manifestError = null }, Modifier.width(170.dp))
                }
            }
            addons.forEach { addon ->
                val focus = addonFocus.getValue(addon.id)
                SettingsAction("${addon.name} · ${if (addon.enabled) "Enabled" else "Disabled"}", onActivate = {
                    showChoice(focus, SettingsChoice(
                        "Manage ${addon.name}",
                        listOf(
                            SettingOption(if (addon.enabled) "Disable" else "Enable") { onToggleAddon(addon) },
                            SettingOption("Remove add-on") { showRemoval(focus, addon) },
                            SettingOption("Cancel") {},
                        ),
                    ))
                }, Modifier.fillMaxWidth().focusRequester(focus))
            }

            SettingsHeading("Server & About")
            Text("VIPTV Android TV", color = SettingsWhite, fontWeight = FontWeight.Bold)
            Text(
                when (serverAbout) {
                    null -> "Server information is unavailable."
                    else -> "Media service: ${if (serverAbout.mediaServiceAvailable) "Available" else "Unavailable"}"
                },
                color = SettingsMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text("One library and the same remote actions across your TVs.", color = SettingsMuted, modifier = Modifier.padding(top = 6.dp))

            SettingsHeading("Session")
            SettingsAction("Sign out", onSignOut)
        }
        choice?.let { dialog ->
            SettingsChoiceDialog(
                title = dialog.title,
                options = dialog.options,
                onDismiss = { choice = null },
            ) { option ->
                choice = null
                option.action()
            }
        }
        pendingRemoval?.let { addon ->
            SettingsChoiceDialog(
                title = "Remove ${addon.name}?",
                options = listOf(
                    SettingOption("Cancel") {},
                    SettingOption("Remove add-on") { onRemoveAddon(addon) },
                ),
                onDismiss = { pendingRemoval = null },
            ) { option ->
                pendingRemoval = null
                option.action()
            }
        }
    }
}

private data class SettingsChoice(val title: String, val options: List<SettingOption>)
private data class SettingOption(val label: String, val action: () -> Unit)
private data class SettingsDialogOrigin(val focus: FocusRequester, val scrollOffset: Int)

@Composable
private fun SettingsHeading(label: String) = Text(
    label,
    color = SettingsMuted,
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
)

@Composable
private fun SettingsAction(label: String, onActivate: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(56.dp)
            .padding(top = 4.dp)
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(2.dp, SettingsWhite, SettingsShape) else Modifier)
            .background(if (focused) SettingsWhite else SettingsSurface, SettingsShape)
            .clickable(onClick = onActivate)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = if (focused) SettingsCanvas else SettingsWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsChoiceDialog(
    title: String,
    options: List<SettingOption>,
    onDismiss: () -> Unit,
    onSelect: (SettingOption) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val focus = remember { FocusRequester() }
    LaunchedEffect(title) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).background(SettingsSurface, SettingsShape).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = SettingsWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            options.forEachIndexed { index, option ->
                SettingsAction(option.label, { onSelect(option) }, Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier).padding(top = 12.dp))
            }
            SettingsAction("Back", onDismiss, Modifier.width(170.dp).padding(top = 20.dp))
        }
    }
}

private fun languageChoice(title: String, save: (String) -> Unit) = SettingsChoice(
    title,
    listOf(
        // The server stores its default as `en`; selecting this remains a real,
        // valid preference mutation rather than sending an unsupported sentinel.
        SettingOption("System default") { save("en") },
        SettingOption("English") { save("en") },
        SettingOption("Spanish") { save("es") },
        SettingOption("French") { save("fr") },
        SettingOption("German") { save("de") },
        SettingOption("Italian") { save("it") },
        SettingOption("Portuguese") { save("pt") },
        SettingOption("Japanese") { save("ja") },
        SettingOption("Korean") { save("ko") },
        SettingOption("Chinese") { save("zh") },
        SettingOption("Hindi") { save("hi") },
        SettingOption("Arabic") { save("ar") },
    ),
)

private fun languageLabel(value: String) = mapOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
    "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "hi" to "Hindi", "ar" to "Arabic",
)[value] ?: "English"
private fun sizeLabel(value: String) = if (value == "normal") "System default" else value.replaceFirstChar(Char::uppercase)
private fun styleLabel(value: String) = when (value) { "shadow" -> "Text with shadow"; "opaque" -> "White text on black"; else -> "System default" }
private fun qualityLabel(value: String) = if (value == "auto") "Auto" else value
private fun onOff(value: Boolean) = if (value) "On" else "Off"
private fun isHttpsManifest(value: String) = runCatching {
    URI(value).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
}.getOrDefault(false)
