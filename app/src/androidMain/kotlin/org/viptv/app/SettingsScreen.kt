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
    serverOrigin: String,
    onSavePreferences: (PlaybackPreferences) -> Unit,
    onInstallAddon: (String) -> Unit,
    onToggleAddon: (Addon) -> Unit,
    onRemoveAddon: (Addon) -> Unit,
    onOpenProfiles: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onManageProfiles: () -> Unit = onOpenProfiles,
    onServerChange: (String) -> Unit = {},
) {
    val context=LocalContext.current
    val version=remember {runCatching {context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull() ?: "Unknown"}
    var page by remember { mutableStateOf("Settings") }
    var choice by remember { mutableStateOf<Pair<String,List<Pair<String,()->Unit>>>?>(null) }
    var serverEntry by remember { mutableStateOf(false) }
    BackHandler(enabled=page!="Settings" && choice==null && !serverEntry) {page="Settings"}
    fun choices(title:String,values:List<Pair<String,String>>,save:(String)->Unit) {
        choice=title to values.map { (label,value)->label to {choice=null;save(value)} }
    }
    val languages=listOf("English" to "en","Spanish" to "es","French" to "fr","German" to "de","Italian" to "it","Portuguese" to "pt","Japanese" to "ja","Korean" to "ko","Chinese" to "zh","Hindi" to "hi","Arabic" to "ar")
    val languageName={ value:String -> languages.firstOrNull {it.second==value}?.first ?: value }
    Box(modifier.fillMaxSize().background(RokuCanvas)) {
        when(page) {
            "Addons" -> RokuAddons(addons,onInstallAddon,onToggleAddon,onRemoveAddon,{page="Settings"})
            "Playback preferences" -> SettingsRows(page,listOf(
                "Preferred audio" to {choices("Preferred audio",languages) {onSavePreferences(preferences.copy(audioLanguage=it))}},
                "Preferred subtitles" to {choices("Preferred subtitles",languages) {onSavePreferences(preferences.copy(subtitleLanguage=it))}},
                "Start with subtitles" to {choices("Start with subtitles",listOf("On" to "on","Off" to "off")) {onSavePreferences(preferences.copy(subtitlesEnabled=it=="on"))}},
                "Subtitle size" to {choices("Subtitle size",listOf("Small" to "small","System default" to "normal","Large" to "large")) {onSavePreferences(preferences.copy(subtitleSize=it))}},
                "Subtitle appearance" to {choices("Subtitle appearance",listOf("System default" to "system","Text with shadow" to "shadow","White text on black" to "opaque")) {onSavePreferences(preferences.copy(subtitleStyle=it))}},
                "Maximum quality" to {choices("Maximum quality",listOf("Auto" to "auto","1080p" to "1080p","720p" to "720p","480p" to "480p")) {onSavePreferences(preferences.copy(quality=it))}},
            ),"Applies to your next playback. Manual track choices take priority.",modalOpen=choice!=null, descriptions=listOf(
                languageName(preferences.audioLanguage),languageName(preferences.subtitleLanguage),if(preferences.subtitlesEnabled) "On" else "Off",
                when(preferences.subtitleSize){"small"->"Small";"large"->"Large";else->"System default"},
                when(preferences.subtitleStyle){"shadow"->"Text with shadow";"opaque"->"White text on black";else->"System default"},
                if(preferences.quality=="auto") "Auto" else preferences.quality,
            ))
            else -> SettingsRows("Settings",listOf(
                "Switch profile" to onOpenProfiles,
                "Playback preferences" to {page="Playback preferences"},
                "Manage profiles" to onManageProfiles,
                "Server" to {serverEntry=true},
                "About VIPTV" to {},
                "Addons" to {page="Addons"},
                "Sign out" to onSignOut,
            ),descriptions=listOf("Choose who's watching.","Audio, subtitles and quality for this profile.","Add, rename, choose avatars or delete profiles.",serverOrigin,"Version $version\n$serverOrigin","Manage addons shared by your account.","Sign out of VIPTV on this TV."))
        }
        choice?.let { (title,options)->RokuChoiceDialog(title,options,{choice=null}) }
        if (serverEntry) RokuTextEntry(
            "Server address",
            "Enter a compatible VIPTV backend origin (https://host). Changing it signs this TV out and starts pairing against the new server.",
            serverOrigin,
            onDone = { value ->
                val validated = ServerOrigin.validate(value)
                if (validated == null) {
                    serverEntry = false
                    // Keep the entry open semantics simple: dismiss with a retry hint.
                    choice = "Server address" to listOf("OK" to { choice = null })
                    return@RokuTextEntry
                }
                serverEntry = false
                if (validated != serverOrigin) onServerChange(validated)
            },
            onCancel = { serverEntry = false },
        )
    }
}

@Composable private fun SettingsRows(title:String,rows:List<Pair<String,()->Unit>>,caption:String="",modalOpen:Boolean=false,descriptions:List<String> = emptyList()) {
    val focuses=remember(title,rows.size) {List(rows.size) {FocusRequester()}}
    var focusedIndex by remember(title) {mutableIntStateOf(0)}
    LaunchedEffect(title,modalOpen) {if(!modalOpen && rows.isNotEmpty()) focuses[focusedIndex.coerceIn(rows.indices)].requestFocus()}
    Text(title,Modifier.offset(100.dp,54.dp).size(1096.dp,64.dp),color=RokuWhite,fontSize=42.sp,fontWeight=FontWeight.Bold)
    if(caption.isNotBlank()) Text(caption,Modifier.offset(100.dp,126.dp).width(1096.dp),color=RokuMuted,fontSize=19.sp)
    val listY=if(caption.isBlank()) 144 else 176
    rows.getOrNull(focusedIndex)?.let { row ->
        Text(row.first,Modifier.offset(778.dp,(listY+8).dp).width(424.dp),color=RokuWhite,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text(descriptions.getOrNull(focusedIndex).orEmpty(),Modifier.offset(778.dp,(listY+52).dp).size(424.dp,208.dp),color=Color(0xFFC5C6C7),fontSize=22.sp,maxLines=7)
    }
    LazyColumn(Modifier.offset(100.dp,listY.dp).size(536.dp,476.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        itemsIndexed(rows) {index,row->TvButton(row.first,row.second,Modifier.size(536.dp,56.dp).focusRequester(focuses[index]),onFocused={focusedIndex=index})}
    }
}

@Composable internal fun RokuAddons(addons:List<Addon>,onInstall:(String)->Unit,onToggle:(Addon)->Unit,onRemove:(Addon)->Unit,onBack:()->Unit) {
    var installing by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var draft by remember {mutableStateOf("https://")}
    var selected by remember {mutableStateOf<Addon?>(null)}
    var removal by remember {mutableStateOf<Addon?>(null)}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        SettingsRows("Addons",listOf("Install addon" to {installing=true})+addons.map {addon->addon.name to {selected=addon}},"Shared by all profiles and devices on your account.",modalOpen=installing||selected!=null||removal!=null,descriptions=listOf("Enter a Stremio manifest URL.")+addons.map {if(it.enabled) "Enabled" else "Disabled"})
        if(installing) RokuTextEntry("Install addon manifest URL",error ?: "Enter the HTTPS add-on manifest URL.",draft,onDone={value->
            draft=value
            if(runCatching {URI(value.trim()).let {it.scheme.equals("https",true)&&!it.host.isNullOrBlank()}}.getOrDefault(false)) {
                onInstall(value.trim());draft="";error=null;installing=false
            } else error="Enter an HTTPS manifest URL."
        },onCancel={installing=false;error=null})
        selected?.let {addon->RokuChoiceDialog("Manage ${addon.name}",listOf(
            (if(addon.enabled) "Disable" else "Enable") to {selected=null;onToggle(addon)},
            "Remove addon" to {selected=null;removal=addon},
            "Cancel" to {selected=null},
        ),{selected=null})}
        removal?.let {addon->RokuChoiceDialog("Remove ${addon.name}?",listOf("Cancel" to {removal=null},"Remove" to {removal=null;onRemove(addon)}),{removal=null})}
    }
    BackHandler(enabled=!installing&&selected==null&&removal==null,onBack=onBack)
}
