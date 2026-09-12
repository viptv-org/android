package org.viptv.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.max

private val Canvas = Color(0xFF101112); private val Surface = Color(0xFF202224); private val White = Color(0xFFF5F5F5); private val Muted = Color(0xFFC5C6C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { VipTvApp() } }
}

@Composable private fun VipTvApp() {
    val context = LocalContext.current
    val controller = remember { AppController(context.applicationContext) }
    val state by controller.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(controller::close) }
    Box(Modifier.fillMaxSize().background(Canvas).onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && event.nativeKeyEvent.action == KeyEvent.ACTION_UP) { controller.back(); true } else false
    }) {
        when (val route = state.route) {
            Route.Pairing -> Pairing(state, controller)
            Route.Profiles -> ProfileChooser(state, controller)
            is Route.Browse -> Browse(state, route.destination, controller)
            is Route.Details -> Details(route.media, controller)
            is Route.Sources -> SourcePicker(route.media, state.sources, controller)
            is Route.Player -> Player(route.media, state.playerChromeVisible, controller)
            Route.Search -> SearchScreen(state, controller)
            Route.Settings -> SettingsScreen(state, controller)
            Route.Addons -> AddonsScreen(state, controller)
            is Route.ProfileEditor -> ProfileEditor(route.profile, controller)
            is Route.Guide -> GuideScreen(route.channel, state.guide, controller)
        }
        if (state.loading) Text("LOADING", color = White, modifier = Modifier.align(Alignment.Center), fontSize = 20.sp)
        state.message?.let { Text(it, color = White, modifier = Modifier.align(Alignment.TopCenter).padding(28.dp).background(Surface, RoundedCornerShape(12.dp)).padding(18.dp)) }
        state.pinPrompt?.let { PinDialog(it, controller, Modifier.align(Alignment.Center)) }
        state.dialog?.let { ActionDialog(it, controller, Modifier.align(Alignment.Center)) }
    }
}

@Composable private fun Pairing(state: AppState, controller: AppController) = Column(Modifier.fillMaxSize().padding(54.dp), verticalArrangement = Arrangement.Center) {
    Image(painterResource(R.drawable.viptv_mark), contentDescription = "VIPTV", modifier = Modifier.width(42.dp).height(36.dp))
    Spacer(Modifier.height(68.dp)); Text("Sign in to VIPTV", color = White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
    Text("Visit this address, then enter the code shown below.", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 28.dp))
    Text(state.deviceCode?.verificationUri ?: "Preparing secure pairing…", color = White, fontSize = 22.sp, modifier = Modifier.padding(top = 30.dp))
    Text(state.deviceCode?.userCode ?: "", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
    TvButton("Try again", controller::beginPairing, Modifier.padding(top = 34.dp))
}

@Composable private fun ProfileChooser(state: AppState, controller: AppController) = Column(Modifier.fillMaxSize().padding(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(if (state.managingProfiles) "Manage profiles" else "Who's watching?", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(62.dp)); Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { state.profiles.drop(state.profilePage * 5).take(5).forEach { profile -> TvButton(profile.name, { if (state.managingProfiles) controller.editProfile(profile) else controller.chooseProfile(profile) }, Modifier.size(178.dp, 210.dp)) } }
    Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Add profile", { controller.editProfile() }); TvButton(if (state.managingProfiles) "Done" else "Manage", controller::toggleProfileManagement) }
    if (state.profiles.size > 5) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Previous", { controller.setProfilePage(state.profilePage - 1) }); Text("Page ${state.profilePage + 1} of ${(state.profiles.size + 4) / 5}", color = Muted, modifier = Modifier.padding(top = 18.dp)); TvButton("Next", { controller.setProfilePage(state.profilePage + 1) }) }
}

@Composable private fun Browse(state: AppState, destination: Destination, controller: AppController) = Row(Modifier.fillMaxSize()) {
    Rail(destination, controller); Box(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(start = 26.dp, top = 46.dp, end = 84.dp)) {
            Text(destination.label, color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(state.selectedProfile?.name ?: "", color = Muted, modifier = Modifier.padding(top = 6.dp))
            if (destination == Destination.Live) {
                state.liveChannels.forEach { channel -> TvButton(channel.name, { controller.openGuide(channel) }, Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp)) }
                if (!state.loading && state.liveChannels.isEmpty()) Text("No channels are available for this filter.", color = Muted, modifier = Modifier.padding(top = 120.dp))
            } else {
                val shelves = if (destination == Destination.Home) state.shelves else listOf(HomeShelf(destination.label, state.catalog.ifEmpty { state.shelves.flatMap(HomeShelf::items) }))
                shelves.forEach { shelf -> Shelf(shelf, controller) }
                if (!state.loading && shelves.isEmpty()) Text("Nothing is available here yet.", color = Muted, modifier = Modifier.padding(top = 120.dp))
            }
        }
    }
}

@Composable private fun Rail(selected: Destination, controller: AppController) = Column(Modifier.width(92.dp).fillMaxHeight().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Destination.entries.forEach { destination -> TvButton(destination.label.take(1), { controller.navigate(destination) }, Modifier.size(60.dp).padding(vertical = 2.dp), selected = destination == selected) }
}

@Composable private fun Shelf(shelf: HomeShelf, controller: AppController) = Column(Modifier.padding(top = 26.dp)) {
    Text(shelf.title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) { items(shelf.items, key = { it.type + it.id }) { media -> MediaCard(media, controller) } }
}

@Composable private fun MediaCard(media: Media, controller: AppController) {
    Holdable({ if (media.positionMillis > 0 && media.type != "live") controller.chooseSources(media, resume = true) else controller.open(media) }, { controller.chooseSources(media) }, Modifier.width(256.dp).height(200.dp).clip(RoundedCornerShape(8.dp)).background(Surface).padding(10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.fillMaxWidth().height(132.dp).background(Color(0xFF242628)), contentAlignment = Alignment.Center) { Text(media.name.take(1), color = Muted, fontSize = 42.sp) }
            Text(media.name, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(media.type, color = Muted, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable private fun Details(media: Media, controller: AppController) = Column(Modifier.fillMaxSize().padding(start = 100.dp, top = 70.dp, end = 120.dp)) {
    Text(media.name, color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold); Text(media.type.uppercase(), color = Muted, modifier = Modifier.padding(top = 10.dp))
    Text(media.description ?: "No description available.", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 40.dp).width(800.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
    Row(Modifier.padding(top = 42.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TvButton(if (media.positionMillis > 0) "Resume" else "Play", { controller.chooseSources(media, media.positionMillis > 0) })
        TvButton("Choose source", { controller.chooseSources(media) })
        TvButton("My List", { controller.toggleMyList(media) })
    }
}

@Composable private fun SourcePicker(media: Media, sources: List<Source>, controller: AppController) {
    var provider by remember(media.type, media.id) { mutableStateOf<String?>(null) }
    val providers = sources.map(Source::provider).distinct()
    val shown = sources.filter { provider == null || it.provider == provider }
    Column(Modifier.fillMaxSize().padding(start = 100.dp, top = 54.dp, end = 84.dp)) {
    Text("Choose a source", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold); Text(media.name, color = Muted, modifier = Modifier.padding(top = 8.dp))
    if (providers.isNotEmpty()) LazyRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TvButton("All providers", { provider = null }, selected = provider == null) }
        items(providers, key = { it }) { item -> TvButton(item, { provider = item }, selected = provider == item) }
    }
    if (sources.isEmpty()) Text("Finding sources…\nSources appear here as they arrive.", color = Muted, modifier = Modifier.padding(top = 150.dp))
    else if (shown.isEmpty()) Text("No sources from this provider. Choose another provider.", color = Muted, modifier = Modifier.padding(top = 80.dp))
    shown.forEach { source -> TvButton(source.provider + "\n" + source.description, { controller.start(media, source) }, Modifier.fillMaxWidth().height(160.dp).padding(top = 16.dp), multiline = true) }
    }
}

@Composable private fun Player(media: Media, chromeVisible: Boolean, controller: AppController) = Box(
    Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP && event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) controller.showPlayerChrome()
        false
    },
) {
    val playback by controller.player.state.collectAsState()
    val audioTracks by controller.player.audioTracks.collectAsState()
    val subtitleTracks by controller.player.subtitleTracks.collectAsState()
    AndroidView(factory = { SurfaceView(it).also(controller.player::attach) }, modifier = Modifier.fillMaxSize())
    if (chromeVisible) Column(Modifier.align(Alignment.BottomStart).padding(64.dp)) {
        Text(media.name, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (playback.timeline?.canSeek == true) TvButton("↶ 10", { controller.showPlayerChrome(); controller.player.seekTo(max(0, playback.positionMillis - 10_000)) })
            TvButton(if (playback.isPlaying) "Pause" else "Play", { controller.showPlayerChrome(); if (playback.isPlaying) controller.player.pause() else controller.player.play() })
            if (playback.timeline?.canSeek == true) TvButton("10 ↷", { controller.showPlayerChrome(); controller.player.seekTo(playback.positionMillis + 10_000) })
            if (audioTracks.isNotEmpty()) TvButton("Audio", { controller.showPlayerChrome(); val next = audioTracks.firstOrNull { it.id != playback.selectedAudioTrackId } ?: audioTracks.first(); controller.player.selectAudioTrack(next.id) })
            if (subtitleTracks.isNotEmpty()) TvButton("Captions", { controller.showPlayerChrome(); val next = subtitleTracks.firstOrNull { it.id != playback.selectedSubtitleTrackId }; controller.player.selectSubtitleTrack(next?.id) })
            if (media.type == "series") TvButton("Next episode", { controller.nextEpisode(media) })
            TvButton("Exit", { controller.saveProgress(media); controller.back() })
        }
    }
}

@Composable private fun SearchScreen(state: AppState, controller: AppController) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(100.dp)) {
        Text("Search", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        TextField(value = query, onValueChange = { query = it }, label = { Text("Search movies and series") }, modifier = Modifier.width(520.dp).padding(top = 26.dp))
        TvButton("Search", { controller.search(query.trim()) }, Modifier.padding(top = 16.dp))
        LazyRow(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) { items(state.searchResults, key = { it.type + it.id }) { MediaCard(it, controller) } }
        if (query.isNotBlank() && !state.loading && state.searchResults.isEmpty()) Text("No results. Keep the search field focused to try another title.", color = Muted, modifier = Modifier.padding(top = 28.dp))
    }
}

@Composable private fun SettingsScreen(state: AppState, controller: AppController) = Column(Modifier.fillMaxSize().padding(100.dp)) {
    Text("Settings", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
    TvButton("Addons", controller::openAddons, Modifier.padding(top = 28.dp))
    TvButton("Preferred audio: ${state.preferences.audioLanguage}", { controller.setPreference(state.preferences.copy(audioLanguage = if (state.preferences.audioLanguage == "en") "es" else "en")) }, Modifier.padding(top = 12.dp))
    TvButton("Subtitles: ${if (state.preferences.subtitlesEnabled) "On" else "Off"}", { controller.setPreference(state.preferences.copy(subtitlesEnabled = !state.preferences.subtitlesEnabled)) }, Modifier.padding(top = 12.dp))
    TvButton("Autoplay: ${if (state.preferences.autoplay) "On" else "Off"}", { controller.setPreference(state.preferences.copy(autoplay = !state.preferences.autoplay)) }, Modifier.padding(top = 12.dp))
    TvButton("Profiles", { controller.navigate(Destination.Profile) }, Modifier.padding(top = 12.dp))
    TvButton("Sign out", { controller.requestDialog(DialogKind.SignOut, "Sign out of VIPTV?") }, Modifier.padding(top = 12.dp))
}

@Composable private fun AddonsScreen(state: AppState, controller: AppController) = Column(Modifier.fillMaxSize().padding(100.dp)) {
    Text("Addons", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
    Text("Shared by all profiles and devices on your account.", color = Muted, modifier = Modifier.padding(top = 12.dp))
    state.addons.forEach { addon -> Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { TvButton("${addon.name}: ${if (addon.enabled) "Enabled" else "Disabled"}", { controller.toggleAddon(addon) }); TvButton("Remove", { controller.removeAddon(addon) }) } }
}

@Composable private fun ProfileEditor(profile: Profile?, controller: AppController) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: "") }
    var avatarStyle by remember(profile?.id) { mutableStateOf(profile?.avatarStyle ?: "critters") }
    Column(Modifier.fillMaxSize().padding(100.dp)) {
        Text(if (profile == null) "Add a profile" else "Edit profile", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        TextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, modifier = Modifier.width(560.dp).padding(top = 28.dp))
        TvButton("Avatar: $avatarStyle", { avatarStyle = if (avatarStyle == "critters") "pixel-art" else "critters" }, Modifier.padding(top = 16.dp))
        Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Save", { controller.saveProfile(profile, name.trim(), avatarStyle, profile?.avatarSeed) }); TvButton("Cancel", controller::back); if (profile != null && !profile.primary) TvButton("Delete profile", { controller.requestDeleteProfile(profile) }) }
    }
}

@Composable private fun GuideScreen(channel: LiveChannel, entries: List<GuideProgramme>, controller: AppController) = Column(Modifier.fillMaxSize().padding(100.dp)) {
    Text(channel.name, color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
    Text("Program guide", color = Muted, modifier = Modifier.padding(top = 8.dp))
    if (entries.isEmpty()) Text("No schedule available. This channel can still be watched.", color = Muted, modifier = Modifier.padding(top = 40.dp))
    entries.take(10).forEach { entry -> TvButton(entry.title, {}, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) }
    TvButton("Watch live", { controller.open(Media(channel.id, "live", channel.name)) }, Modifier.padding(top = 24.dp))
}

@Composable private fun PinDialog(prompt: PinPrompt, controller: AppController, modifier: Modifier = Modifier) {
    var pin by remember { mutableStateOf("") }
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).padding(32.dp).width(500.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(prompt.title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        TextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 20.dp))
        Text("PIN is never stored.", color = Muted, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Unlock", { controller.submitPin(pin) }); TvButton("Cancel", controller::cancelPin) }
    }
}

@Composable private fun ActionDialog(dialog: DialogState, controller: AppController, modifier: Modifier = Modifier) = Column(modifier.background(Surface, RoundedCornerShape(12.dp)).padding(32.dp).width(620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(dialog.title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    when (dialog.kind) {
        DialogKind.SignOut -> Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Keep watching", controller::dismissDialog); TvButton("Sign out", { controller.dismissDialog(); controller.signOut() }) }
        DialogKind.DeleteProfile -> { Text("This removes this profile's watch history, favorites and preferences. Other profiles are kept.", color = Muted, modifier = Modifier.padding(top = 16.dp)); Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Cancel", controller::dismissDialog); TvButton("Delete profile", { dialog.profile?.let(controller::deleteProfile); controller.dismissDialog() }) } }
        DialogKind.QueueManage -> Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Undo", { dialog.media?.let(controller::undoQueueRemoval) }); TvButton("Done", controller::dismissDialog) }
        else -> TvButton("Done", controller::dismissDialog, Modifier.padding(top = 24.dp))
    }
}

@Composable private fun TvButton(label: String, onActivate: () -> Unit, modifier: Modifier = Modifier.width(170.dp).height(56.dp), selected: Boolean = false, multiline: Boolean = false) = Holdable(onActivate, onActivate, modifier.background(if (selected) White else Surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), selected) {
    Text(label, color = if (selected) Canvas else White, fontWeight = FontWeight.Bold, maxLines = if (multiline) 4 else 1, overflow = TextOverflow.Ellipsis)
}

/** 700ms remote hold: exactly one action on release; hold suppresses ordinary activation. */
@Composable private fun Holdable(onActivate: () -> Unit, onHold: () -> Unit, modifier: Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var downAt by remember { mutableLongStateOf(0L) }; var held by remember { mutableStateOf(false) }
    LaunchedEffect(downAt) { if (downAt != 0L) { delay(HoldPolicy.thresholdMillis); if (downAt != 0L) { held = true; onHold() } } }
    Box(modifier = modifier.border(if (selected) 2.dp else 0.dp, White, RoundedCornerShape(12.dp)).focusable().clickable(onClick = onActivate).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent; if (key.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && key.keyCode != KeyEvent.KEYCODE_ENTER) return@onPreviewKeyEvent false
        if (key.action == KeyEvent.ACTION_DOWN && downAt == 0L) { downAt = key.eventTime; held = false; true }
        else if (key.action == KeyEvent.ACTION_UP) { val doActivate = !held; downAt = 0L; if (doActivate) onActivate(); true } else true
    }, contentAlignment = Alignment.Center, content = content)
}
