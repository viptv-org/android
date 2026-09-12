package org.viptv.app

import android.os.Bundle
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.SurfaceView
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.max

private val Canvas = Color(0xFF101112); private val Surface = Color(0xFF202224); private val White = Color(0xFFF5F5F5); private val Muted = Color(0xFFC5C6C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { VipTvApp() } }
}

@Composable private fun VipTvApp() = BoxWithConstraints(Modifier.fillMaxSize().background(Canvas)) {
    val context = LocalContext.current
    val controller = remember { AppController(context.applicationContext) }
    val state by controller.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(controller::close) }
    BackHandler(enabled = controller.consumesBack()) { controller.handleBack() }
    // Scale density, rather than a rendered layer, so the logical frame measures to the viewport.
    // This keeps both coordinates and focus hit targets in the 1280×720 design space.
    val scale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
    Box(Modifier.width(1280.dp).height(720.dp).align(Alignment.Center).onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && event.nativeKeyEvent.action == KeyEvent.ACTION_UP) controller.handleBack() else false
    }) {
        when (val route = state.route) {
            Route.Pairing -> Pairing(state, controller)
            Route.Profiles -> ProfileChooser(state, controller)
            is Route.Browse -> Browse(state, route.destination, controller)
            is Route.Details -> Details(route.media, controller)
            is Route.Sources -> SourcePicker(route.media, state.sources, controller)
            is Route.Player -> Player(route.media, state.playerChromeVisible, state.seekPreview, controller)
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
}

@Composable private fun Pairing(state: AppState, controller: AppController) = Box(Modifier.fillMaxSize()) {
    Image(painterResource(R.drawable.viptv_mark), contentDescription = "VIPTV", modifier = Modifier.offset(96.dp, 44.dp).width(42.dp).height(36.dp))
    Text("Sign in to VIPTV", color = White, fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(96.dp, 170.dp))
    Text("Visit this address, then enter the code shown below.", color = Muted, fontSize = 32.sp, modifier = Modifier.offset(96.dp, 260.dp))
    Text(state.deviceCode?.verificationUri ?: "Preparing secure pairing…", color = White, fontSize = 28.sp, modifier = Modifier.offset(96.dp, 364.dp))
    Text(state.deviceCode?.userCode ?: "", color = White, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(96.dp, 450.dp))
    state.deviceCode?.let { code -> PairingQr(code.qrUri ?: "${code.verificationUri}?code=${code.userCode}", Modifier.offset(886.dp, 184.dp)) }
    if (state.message != null) TvButton("Try again", controller::beginPairing, Modifier.offset(96.dp, 540.dp).width(170.dp).height(56.dp))
}

@Composable private fun PairingQr(value: String, modifier: Modifier = Modifier) {
    val image = remember(value) { qrBitmap(value).asImageBitmap() }
    Box(modifier.width(298.dp).height(298.dp).background(White).padding(24.dp), contentAlignment = Alignment.Center) {
        Image(image, contentDescription = "Scan to pair VIPTV", modifier = Modifier.size(250.dp))
    }
}

private fun qrBitmap(value: String): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 250, 250)
    return Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 250) for (x in 0 until 250) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

@Composable private fun ProfileChooser(state: AppState, controller: AppController) = Box(Modifier.fillMaxSize()) {
    Text(if (state.managingProfiles) "Manage profiles" else "Who's watching?", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.offset(100.dp, 146.dp).width(1080.dp))
    Row(Modifier.offset(y = 252.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterHorizontally)) {
        state.profiles.drop(state.profilePage * 5).take(5).forEach { profile ->
            ProfileCard(profile, state.managingProfiles, controller)
        }
    }
    Row(Modifier.offset(y = 530.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        TvButton("Add profile", { controller.editProfile() }, Modifier.width(240.dp).height(56.dp))
        TvButton(if (state.managingProfiles) "Done" else "Manage", controller::toggleProfileManagement, Modifier.width(240.dp).height(56.dp))
    }
    if (state.profiles.size > 5) Row(Modifier.offset(y = 612.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        TvButton("Previous", { controller.setProfilePage(state.profilePage - 1) }, Modifier.width(180.dp).height(40.dp))
        Text("Page ${state.profilePage + 1} of ${(state.profiles.size + 4) / 5}", color = Muted, modifier = Modifier.width(220.dp), textAlign = TextAlign.Center)
        TvButton("Next", { controller.setProfilePage(state.profilePage + 1) }, Modifier.width(180.dp).height(40.dp))
    }
}

@Composable private fun ProfileCard(profile: Profile, managing: Boolean, controller: AppController) = Holdable(
    onActivate = { if (managing) controller.editProfile(profile) else controller.chooseProfile(profile) },
    onHold = null,
    modifier = Modifier.width(178.dp).height(210.dp),
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(178.dp).height(178.dp).padding(9.dp).clip(RoundedCornerShape(12.dp)).background(avatarFallback(profile.name)), contentAlignment = Alignment.Center) {
            Text(profile.name.take(2).uppercase(), color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(model = profile.avatarUrl, contentDescription = "${profile.name} avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(profile.name, color = White, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
    }
}

private fun avatarFallback(name: String): Color = when ((name.fold(0) { hash, char -> hash * 31 + char.code } and Int.MAX_VALUE) % 4) {
    0 -> Color(0xFF3D4A5A); 1 -> Color(0xFF55435C); 2 -> Color(0xFF40584D); else -> Color(0xFF5A4D3D)
}

@Composable private fun Browse(state: AppState, destination: Destination, controller: AppController) = Row(Modifier.fillMaxSize()) {
    Rail(destination, controller); Box(Modifier.weight(1f).fillMaxHeight()) {
        val hero = state.shelves.firstOrNull()?.items?.firstOrNull()
        if (destination == Destination.Home && hero != null) HomeHero(hero, controller)
        Column(Modifier.fillMaxSize().padding(start = 8.dp, top = if (destination == Destination.Home && hero != null) 466.dp else 46.dp, end = 84.dp)) {
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

@Composable private fun HomeHero(media: Media, controller: AppController) = Box(Modifier.fillMaxWidth().height(450.dp).background(Canvas)) {
    if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Column(Modifier.padding(start = 8.dp, top = 128.dp).width(600.dp)) {
        Text(if (media.type == "live") "LIVE NOW" else "FEATURED ${media.type.uppercase()}", color = Muted, fontSize = 18.sp)
        Text(media.name, color = White, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        Text(media.description ?: "", color = Muted, fontSize = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(if (media.positionMillis > 0 && media.type != "live") "Resume" else "Play", { if (media.positionMillis > 0 && media.type != "live") controller.chooseSources(media, resume = true) else controller.open(media) }, Modifier.width(144.dp).height(50.dp))
            TvButton("My List", { controller.toggleMyList(media) }, Modifier.width(144.dp).height(50.dp))
        }
    }
}

@Composable private fun Rail(selected: Destination, controller: AppController) = Column(Modifier.width(92.dp).fillMaxHeight().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Destination.entries.forEach { destination -> TvButton("", { controller.navigate(destination) }, Modifier.size(60.dp).padding(vertical = 2.dp), selected = destination == selected) { RailIcon(destination, selected == destination) } }
}

@Composable private fun RailIcon(destination: Destination, selected: Boolean) {
    val image = when (destination) {
        Destination.Profile -> Icons.Default.AccountCircle
        Destination.Home -> Icons.Default.Home
        Destination.Discover -> Icons.Default.Explore
        Destination.Live -> Icons.Default.LiveTv
        Destination.MyList -> Icons.Default.Favorite
        Destination.Search -> Icons.Default.Search
        Destination.Settings -> Icons.Default.Settings
    }
    Icon(image, contentDescription = destination.label, tint = if (selected) Canvas else White, modifier = Modifier.size(30.dp))
}

@Composable private fun Shelf(shelf: HomeShelf, controller: AppController) = Column(Modifier.padding(top = 26.dp)) {
    Text(shelf.title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) { items(shelf.items, key = { it.type + it.id }) { media -> MediaCard(media, controller) } }
}

@Composable private fun MediaCard(media: Media, controller: AppController) {
    val resumable = media.positionMillis > 0 && media.type != "live"
    Holdable({ if (resumable) controller.chooseSources(media, resume = true) else controller.open(media) }, if (resumable) ({ controller.chooseSources(media) }) else null, Modifier.width(256.dp).height(200.dp).clip(RoundedCornerShape(8.dp)).background(Surface).padding(10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.fillMaxWidth().height(132.dp).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
                Text(media.name.take(1), color = Muted, fontSize = 42.sp)
                if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
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
    if (media.type == "series" && media.episodes.isNotEmpty()) {
        Text("Episodes", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 46.dp))
        LazyRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(media.episodes, key = { it.id }) { episode -> EpisodeCard(episode, controller) }
        }
    }
}

@Composable private fun EpisodeCard(episode: Media, controller: AppController) = Holdable(
    onActivate = { controller.chooseSources(episode) },
    onHold = null,
    modifier = Modifier.width(256.dp).height(200.dp).clip(RoundedCornerShape(8.dp)).background(Surface).padding(10.dp),
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.fillMaxWidth().height(132.dp).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
            Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = Muted, fontSize = 20.sp)
            if (!episode.poster.isNullOrBlank()) AsyncImage(model = episode.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(episode.name.ifBlank { "Episode ${episode.episode ?: ""}" }, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = Muted, fontSize = 14.sp)
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

@Composable private fun Player(media: Media, chromeVisible: Boolean, seekPreview: SeekPreview?, controller: AppController) = Box(
    Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP && event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) controller.showPlayerChrome()
        false
    },
) {
    val playback by controller.player.state.collectAsState()
    val audioTracks by controller.player.audioTracks.collectAsState()
    val subtitleTracks by controller.player.subtitleTracks.collectAsState()
    LaunchedEffect(media.type, media.id, playback.positionMillis, playback.isPlaying, playback.timeline?.durationMillis) {
        controller.maybeAutoNext(media, playback.positionMillis, playback.timeline?.durationMillis, playback.isPlaying)
    }
    AndroidView(factory = { SurfaceView(it).also(controller.player::attach) }, modifier = Modifier.fillMaxSize())
    if (chromeVisible) Column(Modifier.align(Alignment.BottomStart).padding(64.dp)) {
        Text(media.name, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        seekPreview?.let { Text("Seek preview: ${it.targetMillis / 1_000}s", color = Muted, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (playback.timeline?.canSeek == true) TvButton("↶ 10", { controller.previewSeek(-10_000) })
            TvButton(if (playback.isPlaying) "Pause" else "Play", { controller.showPlayerChrome(); if (playback.isPlaying) controller.player.pause() else controller.player.play() })
            if (playback.timeline?.canSeek == true) TvButton("30 ↷", { controller.previewSeek(30_000) })
            seekPreview?.let { TvButton("Seek", controller::commitSeek); TvButton("Cancel", controller::cancelSeek) }
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

@Composable private fun TvButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier.width(170.dp).height(56.dp),
    selected: Boolean = false,
    multiline: Boolean = false,
    content: @Composable BoxScope.() -> Unit = { Text(label, color = if (selected) Canvas else White, fontWeight = FontWeight.Bold, maxLines = if (multiline) 4 else 1, overflow = TextOverflow.Ellipsis) },
) = Holdable(onActivate, null, modifier.background(if (selected) White else Surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), selected, content)

/** 700ms remote hold: exactly one action on release; hold suppresses ordinary activation. */
@Composable private fun Holdable(onActivate: () -> Unit, onHold: (() -> Unit)?, modifier: Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var downAt by remember { mutableLongStateOf(0L) }; var held by remember { mutableStateOf(false) }
    LaunchedEffect(downAt, onHold) { if (downAt != 0L && onHold != null) { delay(HoldPolicy.thresholdMillis); if (downAt != 0L) { held = true; onHold() } } }
    Box(modifier = modifier.border(if (selected) 2.dp else 0.dp, White, RoundedCornerShape(12.dp)).focusable().clickable(onClick = onActivate).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent; if (key.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && key.keyCode != KeyEvent.KEYCODE_ENTER) return@onPreviewKeyEvent false
        if (key.action == KeyEvent.ACTION_DOWN && downAt == 0L) { downAt = key.eventTime; held = false; true }
        else if (key.action == KeyEvent.ACTION_UP) { val doActivate = !held; downAt = 0L; if (doActivate) onActivate(); true } else true
    }, contentAlignment = Alignment.Center, content = content)
}
