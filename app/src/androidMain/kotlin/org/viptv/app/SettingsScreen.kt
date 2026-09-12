package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

@Composable fun SettingsScreen(
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
    onManageProfiles: () -> Unit = onOpenProfiles,
) {
    val context=LocalContext.current
    val version=remember {runCatching {context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull() ?: "Unknown"}
    var page by remember { mutableStateOf("Settings") }
    var choice by remember { mutableStateOf<Pair<String,List<Pair<String,()->Unit>>>?>(null) }
    BackHandler(enabled=page!="Settings" && choice==null) {page="Settings"}
    fun choices(title:String,values:List<Pair<String,String>>,save:(String)->Unit) {
        choice=title to values.map { (label,value)->label to {choice=null;save(value)} }
    }
    val languages=listOf("English" to "en","Spanish" to "es","French" to "fr","German" to "de","Italian" to "it","Portuguese" to "pt","Japanese" to "ja","Korean" to "ko","Chinese" to "zh","Hindi" to "hi","Arabic" to "ar")
    val languageName={ value:String -> languages.firstOrNull {it.second==value}?.first ?: value }
    Box(modifier.fillMaxSize().background(Color(0xFF101112))) {
        when(page) {
            "Addons" -> RokuAddons(addons,onInstallAddon,onToggleAddon,onRemoveAddon,{page="Settings"})
            "About VIPTV" -> {
                SettingsRows("About VIPTV",listOf("Back" to {page="Settings"}),"Version $version  ·  Media service: ${if(serverAbout?.mediaServiceAvailable==true) "Available" else "Unavailable"}")
            }
            "Playback preferences" -> SettingsRows(page,listOf(
                "Autoplay next episode: ${if(preferences.autoplay) "On" else "Off"}" to {choices("Autoplay next episode",listOf("On" to "on","Off" to "off")) {onSavePreferences(preferences.copy(autoplay=it=="on"))}},
                "Start with subtitles: ${if(preferences.subtitlesEnabled) "On" else "Off"}" to {choices("Start with subtitles",listOf("On" to "on","Off" to "off")) {onSavePreferences(preferences.copy(subtitlesEnabled=it=="on"))}},
                "Preferred audio: ${languageName(preferences.audioLanguage)}" to {choices("Preferred audio",languages) {onSavePreferences(preferences.copy(audioLanguage=it))}},
                "Preferred subtitles: ${languageName(preferences.subtitleLanguage)}" to {choices("Preferred subtitles",languages) {onSavePreferences(preferences.copy(subtitleLanguage=it))}},
                "Subtitle size: ${preferences.subtitleSize}" to {choices("Subtitle size",listOf("Small" to "small","System default" to "normal","Large" to "large")) {onSavePreferences(preferences.copy(subtitleSize=it))}},
                "Subtitle appearance: ${preferences.subtitleStyle}" to {choices("Subtitle appearance",listOf("System default" to "system","Text with shadow" to "shadow","White text on black" to "opaque")) {onSavePreferences(preferences.copy(subtitleStyle=it))}},
                "Maximum quality: ${preferences.quality}" to {choices("Maximum quality",listOf("Auto" to "auto","1080p" to "1080p","720p" to "720p","480p" to "480p")) {onSavePreferences(preferences.copy(quality=it))}},
            ),"Applies to your next playback. Manual track choices take priority.",modalOpen=choice!=null)
            else -> SettingsRows("Settings",listOf(
                "Switch profile" to onOpenProfiles,
                "Playback preferences" to {page="Playback preferences"},
                "Manage profiles" to onManageProfiles,
                "About VIPTV" to {page="About VIPTV"},
                "Addons" to {page="Addons"},
                "Sign out" to onSignOut,
            ))
        }
        choice?.let { (title,options)->RokuChoiceDialog(title,options,{choice=null}) }
    }
}

@Composable private fun SettingsRows(title:String,rows:List<Pair<String,()->Unit>>,caption:String="",modalOpen:Boolean=false) {
    val focuses=remember(title,rows.size) {List(rows.size) {FocusRequester()}}
    var focusedIndex by remember(title) {mutableIntStateOf(0)}
    LaunchedEffect(title,modalOpen) {if(!modalOpen && rows.isNotEmpty()) focuses[focusedIndex.coerceIn(rows.indices)].requestFocus()}
    Text(title,Modifier.offset(100.dp,54.dp).size(1096.dp,64.dp),color=Color(0xFFF5F5F5),fontSize=44.sp,fontWeight=FontWeight.Bold)
    if(caption.isNotBlank()) Text(caption,Modifier.offset(100.dp,126.dp).width(1096.dp),color=Color(0xFFA6A8AA),fontSize=19.sp)
    LazyColumn(Modifier.offset(100.dp,184.dp).size(536.dp,476.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        itemsIndexed(rows) {index,row->TvButton(row.first,row.second,Modifier.size(536.dp,56.dp).focusRequester(focuses[index]),onFocused={focusedIndex=index})}
    }
}

@Composable internal fun RokuAddons(addons:List<Addon>,onInstall:(String)->Unit,onToggle:(Addon)->Unit,onRemove:(Addon)->Unit,onBack:()->Unit) {
    var installing by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var draft by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf<Addon?>(null)}
    var removal by remember {mutableStateOf<Addon?>(null)}
    Box(Modifier.fillMaxSize().background(Color(0xFF101112))) {
        SettingsRows("Addons",listOf("Install add-on" to {installing=true})+addons.map {addon->"${addon.name} · ${if(addon.enabled) "Enabled" else "Disabled"}" to {selected=addon}},"Shared by all profiles and devices on your account.",modalOpen=installing||selected!=null||removal!=null)
        if(installing) RokuTextEntry("Install add-on",error ?: "Enter the HTTPS add-on manifest URL.",draft,onDone={value->
            draft=value
            if(runCatching {URI(value.trim()).let {it.scheme.equals("https",true)&&!it.host.isNullOrBlank()}}.getOrDefault(false)) {
                onInstall(value.trim());draft="";error=null;installing=false
            } else error="Enter an HTTPS manifest URL."
        },onCancel={installing=false;error=null})
        selected?.let {addon->RokuChoiceDialog(addon.name,listOf(
            (if(addon.enabled) "Disable" else "Enable") to {selected=null;onToggle(addon)},
            "Remove add-on" to {selected=null;removal=addon},
            "Cancel" to {selected=null},
        ),{selected=null})}
        removal?.let {addon->RokuChoiceDialog("Remove ${addon.name}?",listOf("Cancel" to {removal=null},"Remove add-on" to {removal=null;onRemove(addon)}),{removal=null})}
    }
    BackHandler(enabled=!installing&&selected==null&&removal==null,onBack=onBack)
}
