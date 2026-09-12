package org.viptv.app

import android.os.Bundle
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.SurfaceView
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.getair.video.PlaybackStatus
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
    Box(Modifier.width(1280.dp).height(720.dp).align(Alignment.Center)) {
        when (val route = state.route) {
            Route.Pairing -> Pairing(state, controller)
            Route.Profiles -> ProfileChooser(state, controller)
            is Route.Browse -> if (route.destination == Destination.Discover) {
                Box(Modifier.fillMaxSize()) {
                    DiscoverScreen(state, controller)
                    Rail(route.destination, controller)
                }
            } else Browse(state, route.destination, controller)
            is Route.Details -> Details(route.media, controller)
            is Route.Sources -> SourcePicker(route.media, state.sources, controller)
            is Route.Player -> PlaybackScreen(route.media, state.playerChromeVisible, state.seekPreview, state.playbackTracks, controller)
            Route.Search -> SearchScreen(state, controller)
            Route.Settings -> SettingsScreen(
                preferences = state.preferences,
                addons = state.addons,
                serverAbout = state.serverAbout,
                onSavePreferences = controller::setPreference,
                onInstallAddon = controller::installAddon,
                onToggleAddon = controller::toggleAddon,
                onRemoveAddon = controller::removeAddon,
                onOpenProfiles = { controller.navigate(Destination.Profile) },
                onSignOut = { controller.requestDialog(DialogKind.SignOut, "Sign out of VIPTV?") },
            )
            Route.Addons -> AddonsScreen(state, controller)
            is Route.ProfileEditor -> ProfileEditor(route.profile, controller)
            is Route.Guide -> GuideScreen(state, route.channel, controller)
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
    Text(
        "Visit this address, then enter the code shown below.",
        color = Muted,
        fontSize = 32.sp,
        modifier = Modifier.offset(96.dp, 260.dp).width(640.dp).height(84.dp),
    )
    Text(state.deviceCode?.verificationUri ?: "Preparing secure pairing…", color = White, fontSize = 28.sp, modifier = Modifier.offset(96.dp, 364.dp))
    Text(state.deviceCode?.userCode ?: "", color = White, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(96.dp, 450.dp))
    state.deviceCode?.let { code -> PairingQr(code.verificationUriComplete ?: "${code.verificationUri}?code=${code.userCode}", Modifier.offset(886.dp, 184.dp)) }
    if (state.message != null) TvButton("Try again", controller::retryAuthentication, Modifier.offset(96.dp, 540.dp).width(170.dp).height(56.dp))
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
    val firstProfileFocus = remember { FocusRequester() }
    LaunchedEffect(state.profilePage, state.profiles) { if (state.profiles.drop(state.profilePage * 5).isNotEmpty()) firstProfileFocus.requestFocus() }
    Text(if (state.managingProfiles) "Manage profiles" else "Who's watching?", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.offset(100.dp, 146.dp).width(1080.dp))
    Row(Modifier.offset(y = 252.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterHorizontally)) {
        state.profiles.drop(state.profilePage * 5).take(5).forEachIndexed { index, profile ->
            ProfileCard(profile, state.managingProfiles, controller, if (index == 0) firstProfileFocus else null)
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

@Composable private fun ProfileCard(profile: Profile, managing: Boolean, controller: AppController, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Holdable(
        onActivate = { if (managing) controller.editProfile(profile) else controller.chooseProfile(profile) },
        onHold = null,
        modifier = Modifier.width(178.dp).height(210.dp)
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(3.dp, White, RoundedCornerShape(12.dp)) else Modifier),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(178.dp).height(178.dp).padding(9.dp).clip(RoundedCornerShape(12.dp)).background(avatarFallback(profile.name)), contentAlignment = Alignment.Center) {
            Text(profile.name.take(2).uppercase(), color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(model = profile.avatarUrl, contentDescription = "${profile.name} avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(profile.name, color = if (focused) White else Muted, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
        }
    }
}

private fun avatarFallback(name: String): Color = when ((name.fold(0) { hash, char -> hash * 31 + char.code } and Int.MAX_VALUE) % 4) {
    0 -> Color(0xFF3D4A5A); 1 -> Color(0xFF55435C); 2 -> Color(0xFF40584D); else -> Color(0xFF5A4D3D)
}

@Composable private fun Browse(state: AppState, destination: Destination, controller: AppController) = Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        val homeFocus = remember { FocusRequester() }
        LaunchedEffect(destination, state.shelves) { if (destination == Destination.Home && state.shelves.firstOrNull()?.items?.isNotEmpty() == true) homeFocus.requestFocus() }
        val hero = state.shelves.firstOrNull()?.items?.firstOrNull()
        if (destination == Destination.Home && hero != null) HomeHero(hero, controller, state.shelves.firstOrNull()?.title == "Continue Watching")
        Column(
            Modifier.fillMaxSize()
                .padding(
                    start = if (destination == Destination.Home && hero != null) 100.dp else 100.dp,
                    top = if (destination == Destination.Home && hero != null) 466.dp else 46.dp,
                    end = 84.dp,
                )
                .then(if (destination == Destination.Home && hero != null) Modifier.height(254.dp).clipToBounds() else Modifier),
        ) {
            if (destination != Destination.Home) {
                Text(destination.label, color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Text(state.selectedProfile?.name ?: "", color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            if (destination == Destination.Live) {
                state.liveChannels.forEach { channel -> TvButton(channel.name, { controller.openGuide(channel) }, Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp)) }
                if (!state.loading && state.liveChannels.isEmpty()) Text("No channels are available for this filter.", color = Muted, modifier = Modifier.padding(top = 120.dp))
            } else {
                val shelves = if (destination == Destination.Home) state.shelves else listOf(HomeShelf(destination.label, state.catalog.ifEmpty { state.shelves.flatMap(HomeShelf::items) }))
                shelves.forEachIndexed { index, shelf -> Shelf(shelf, controller, if (destination == Destination.Home && index == 0) homeFocus else null, if (destination == Destination.Home) 0.dp else 26.dp) }
                if (!state.loading && shelves.isEmpty()) Text("Nothing is available here yet.", color = Muted, modifier = Modifier.padding(top = 120.dp))
            }
        }
    }
    Rail(destination, controller)
}

@Composable private fun HomeHero(media: Media, controller: AppController, resumeSurface: Boolean) = Box(Modifier.fillMaxWidth().height(450.dp).background(Canvas)) {
    val density = LocalDensity.current
    val leftGradientEnd = with(density) { 760.dp.toPx() }
    val bottomGradientStart = with(density) { 140.dp.toPx() }
    if (!media.poster.isNullOrBlank()) {
        AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    // The server currently supplies landscape poster art only. These source-faithful
    // overlays preserve readable hero copy until a dedicated backdrop field arrives.
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Canvas.copy(alpha = .97f), Canvas.copy(alpha = .78f), Color.Transparent), endX = leftGradientEnd)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Canvas.copy(alpha = .18f), Canvas), startY = bottomGradientStart)))
    Text(if (media.type == "live") "LIVE NOW" else if (media.positionMillis > 0) "CONTINUE WATCHING" else "FEATURED ${media.type.uppercase()}", color = Muted, fontSize = 18.sp, modifier = Modifier.offset(100.dp, 128.dp))
    Text(media.name, color = White, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(100.dp, 166.dp).width(600.dp))
    Text(media.description ?: "", color = Muted, fontSize = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(100.dp, 228.dp).width(548.dp).height(80.dp))
    Row(Modifier.offset(100.dp, 375.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(if (MediaCardPolicy.primary(resumeSurface, media) == MediaCardAction.ResumeExactSource) "Resume" else "Play", { if (MediaCardPolicy.primary(resumeSurface, media) == MediaCardAction.ResumeExactSource) controller.chooseSources(media, resume = true) else controller.open(media) }, Modifier.width(144.dp).height(50.dp))
        TvButton("My List", { controller.toggleMyList(media) }, Modifier.width(144.dp).height(50.dp))
    }
}

@Composable private fun Rail(selected: Destination, controller: AppController) = Column(Modifier.width(76.dp).fillMaxHeight().padding(top = 108.dp), horizontalAlignment = Alignment.End) {
    Destination.entries.forEach { destination ->
        var focused by remember(destination) { mutableStateOf(false) }
        Holdable(
            onActivate = { controller.navigate(destination) },
            onHold = null,
            modifier = Modifier.size(60.dp).padding(vertical = 2.dp)
                .onFocusChanged { focused = it.hasFocus }
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) White else Color.Transparent),
        ) { RailIcon(destination, focused) }
    }
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

@Composable private fun Shelf(shelf: HomeShelf, controller: AppController, initialFocus: FocusRequester? = null, topPadding: androidx.compose.ui.unit.Dp = 26.dp) = Column(Modifier.padding(top = topPadding)) {
    Text(shelf.title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    val resumeSurface = shelf.title == "Continue Watching"
    LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) { itemsIndexed(shelf.items, key = { _, media -> media.type + media.id }) { index, media -> MediaCard(media, controller, if (index == 0) initialFocus else null, resumeSurface) } }
}

@Composable internal fun MediaCard(media: Media, controller: AppController, focusRequester: FocusRequester? = null, resumeSurface: Boolean = false) {
    val action = MediaCardPolicy.primary(resumeSurface, media)
    val resumable = action == MediaCardAction.ResumeExactSource
    var focused by remember { mutableStateOf(false) }
    Holdable(
        { if (action == MediaCardAction.ResumeExactSource) controller.chooseSources(media, resume = true) else controller.open(media) },
        if (MediaCardPolicy.supportsChooseSourceHold(resumeSurface, media)) ({ controller.chooseSources(media) }) else null,
        Modifier.width(256.dp).height(200.dp).then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(2.dp, White, RoundedCornerShape(8.dp)) else Modifier),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
                Text(media.name.take(1), color = Muted, fontSize = 42.sp)
                if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text(media.name, color = if (focused) Color.White else White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(media.type, color = Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable private fun Details(media: Media, controller: AppController) = Box(Modifier.fillMaxSize().background(Canvas)) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(media.id, media.type) { initialFocus.requestFocus() }
    if (!media.poster.isNullOrBlank()) {
        AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(620.dp))
        Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(listOf(Canvas.copy(alpha = .32f), Canvas.copy(alpha = .90f), Canvas))))
    }
    if (media.type == "series") {
        Text(media.name, color = White, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(112.dp, 74.dp).width(900.dp))
        Text(media.type.uppercase(), color = Muted, fontSize = 18.sp, modifier = Modifier.offset(112.dp, 132.dp).width(706.dp))
        Row(Modifier.offset(112.dp, 188.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TvButton("Season ${media.season ?: 1}", { }, Modifier.width(256.dp).height(48.dp).focusRequester(initialFocus))
            TvButton("My List", { controller.toggleMyList(media) }, Modifier.width(256.dp).height(48.dp))
            TvButton("More info", { controller.open(media) }, Modifier.width(256.dp).height(48.dp))
        }
        Text("Episodes", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.offset(976.dp, 198.dp).width(220.dp))
        LazyRow(Modifier.offset(112.dp, 262.dp).width(1096.dp).height(330.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(media.episodes, key = { it.id }) { episode -> EpisodeCard(episode, controller) }
        }
    } else {
        Box(Modifier.offset(112.dp, 126.dp).width(236.dp).height(354.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
            Text(media.name.take(1), color = Muted, fontSize = 42.sp)
            if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.offset(380.dp, 126.dp).width(804.dp)) {
            Text(media.name, color = White, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(media.type.uppercase(), color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp).height(48.dp))
            Text(media.description ?: "No description available.", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 28.dp).height(128.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(if (media.positionMillis > 0) "Resume" else "Play", { controller.chooseSources(media, media.positionMillis > 0) }, Modifier.width(192.dp).height(56.dp).focusRequester(initialFocus))
                TvButton("Choose source", { controller.chooseSources(media) }, Modifier.width(192.dp).height(56.dp))
                TvButton("My List", { controller.toggleMyList(media) }, Modifier.width(192.dp).height(56.dp))
            }
        }
    }
}

@Composable private fun EpisodeCard(episode: Media, controller: AppController) {
    var focused by remember { mutableStateOf(false) }
    Holdable(
        onActivate = { controller.chooseSources(episode) },
        onHold = null,
        modifier = Modifier.width(256.dp).height(330.dp).onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(2.dp, White, RoundedCornerShape(8.dp)) else Modifier),
    ) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
            Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = Muted, fontSize = 20.sp)
            if (!episode.poster.isNullOrBlank()) AsyncImage(model = episode.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(episode.episodeTitle ?: episode.name.ifBlank { "Episode ${episode.episode ?: ""}" }, color = if (focused) Color.White else White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
        Text(episode.description ?: "", color = Muted, fontSize = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).height(94.dp))
        }
    }
}

@Composable private fun SourcePicker(media: Media, sources: List<Source>, controller: AppController) = Box(Modifier.fillMaxSize()) {
    var provider by remember(media.type, media.id) { mutableStateOf<String?>(null) }
    var requestFirstSourceFocus by remember(media.type, media.id) { mutableStateOf(true) }
    val initialSourceFocus = remember { FocusRequester() }
    val providers = sources.map(Source::provider).filter(String::isNotBlank).distinct()
    val shown = sources.filter { provider == null || it.provider == provider }
    // Discovery can add better-ranked rows after the viewer has moved focus.  Only
    // focus the first row when the list becomes usable or the viewer changes filter.
    LaunchedEffect(provider, shown.isNotEmpty(), requestFirstSourceFocus) {
        if (requestFirstSourceFocus && shown.isNotEmpty()) {
            initialSourceFocus.requestFocus()
            requestFirstSourceFocus = false
        }
    }
    Text("Choose a source", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(100.dp, 64.dp))
    Text(media.name, color = Muted, fontSize = 20.sp, modifier = Modifier.offset(100.dp, 119.dp).width(620.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        when {
            sources.isEmpty() -> "Finding sources"
            else -> "${shown.size} source${if (shown.size == 1) "" else "s"}"
        },
        color = Muted,
        fontSize = 16.sp,
        modifier = Modifier.offset(384.dp, 178.dp).width(480.dp),
        textAlign = TextAlign.Center,
    )
    Text("Hold a source for details", color = Muted, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.offset(804.dp, 178.dp).width(350.dp))
    if (providers.isNotEmpty()) LazyRow(Modifier.offset(100.dp, 166.dp).width(688.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TvButton("All providers", { provider = null; requestFirstSourceFocus = true }, selected = provider == null, modifier = Modifier.width(170.dp).height(48.dp)) }
        items(providers, key = { it }) { item ->
            TvButton(item, { provider = item; requestFirstSourceFocus = true }, selected = provider == item, modifier = Modifier.width(170.dp).height(48.dp))
        }
    }
    when {
        sources.isEmpty() -> Column(Modifier.offset(250.dp, 380.dp).width(780.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Finding sources", color = White, fontSize = 28.sp)
            Text("Sources appear here as they arrive.", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
        }
        shown.isEmpty() -> Column(Modifier.offset(250.dp, 380.dp).width(780.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sources from this provider", color = White, fontSize = 28.sp)
            Text("Choose another provider to see its sources.", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
        }
        else -> LazyColumn(
            Modifier.offset(100.dp, 234.dp).width(1096.dp).height(448.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(shown, key = { _, source -> source.id }) { index, source ->
                SourceCard(source, controller, media, if (index == 0) initialSourceFocus else null)
            }
        }
    }
}

@Composable private fun SourceCard(source: Source, controller: AppController, media: Media, focusRequester: FocusRequester?) {
    var focused by remember(source.id) { mutableStateOf(false) }
    val foreground = if (focused) Canvas else White
    val secondary = if (focused) Color(0xFF303234) else Muted
    Holdable(
        onActivate = { controller.start(media, source) },
        onHold = { controller.requestDialog(DialogKind.SourceDetails, SourceDisplayPolicy.title(source), source = source) },
        modifier = Modifier.width(1096.dp).height(216.dp)
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.hasFocus }
            .background(if (focused) White else Surface, RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 10.dp, end = 20.dp)) {
            Text(SourceDisplayPolicy.title(source), color = foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SourceDisplayPolicy.body(source), color = secondary, fontSize = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).height(142.dp))
            val facts = listOfNotNull(source.quality, source.audio, source.provider.takeIf { it.isNotBlank() }).joinToString("  •  ")
            if (facts.isNotBlank()) Text(facts, color = secondary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable private fun AddonsScreen(state: AppState, controller: AppController) = Column(Modifier.fillMaxSize().padding(100.dp)) {
    Text("Addons", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
    Text("Shared by all profiles and devices on your account.", color = Muted, modifier = Modifier.padding(top = 12.dp))
    state.addons.forEach { addon -> Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { TvButton("${addon.name}: ${if (addon.enabled) "Enabled" else "Disabled"}", { controller.toggleAddon(addon) }); TvButton("Remove", { controller.removeAddon(addon) }) } }
}

@Composable private fun ProfileEditor(profile: Profile?, controller: AppController) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: "") }
    var avatarStyle by remember(profile?.id) { mutableStateOf(profile?.avatarStyle ?: "critters") }
    var avatarChoice by remember(profile?.id) { mutableStateOf(profile?.avatarChoice ?: 1) }
    var choosingAvatar by remember(profile?.id) { mutableStateOf(false) }
    if (choosingAvatar) {
        AvatarPicker(
            style = avatarStyle,
            selectedChoice = avatarChoice,
            onChoose = { style, choice -> avatarStyle = style; avatarChoice = choice; choosingAvatar = false },
            onCancel = { choosingAvatar = false },
        )
        return
    }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(profile?.id) { nameFocus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.viptv_mark), contentDescription = "VIPTV", modifier = Modifier.offset(96.dp, 44.dp).width(42.dp).height(36.dp))
        Text(if (profile == null) "Add a profile" else "Edit profile", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(256.dp, 148.dp))
        Text("Choose a name and avatar for this viewer.", color = Muted, fontSize = 20.sp, modifier = Modifier.offset(256.dp, 222.dp))
        ProfileAvatarPreview(name, profile?.avatarUrl, avatarChoice, Modifier.offset(256.dp, 302.dp).size(176.dp))
        TvButton("Change avatar", { choosingAvatar = true }, Modifier.offset(256.dp, 486.dp).width(176.dp).height(48.dp))
        Text("Profile name", color = Muted, fontSize = 18.sp, modifier = Modifier.offset(464.dp, 292.dp))
        TextField(
            value = name,
            onValueChange = { name = it.take(64) },
            label = { Text("Name") },
            modifier = Modifier.offset(464.dp, 338.dp).width(560.dp).height(64.dp).focusRequester(nameFocus),
            singleLine = true,
        )
        Text("This name appears on the profile chooser.", color = Muted, fontSize = 16.sp, modifier = Modifier.offset(464.dp, 420.dp))
        Row(Modifier.offset(256.dp, 574.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (profile == null) "Create profile" else "Save", { controller.saveProfile(profile, name, avatarStyle, avatarChoice) }, Modifier.width(240.dp).height(56.dp))
            TvButton("Cancel", controller::back, Modifier.width(240.dp).height(56.dp))
            if (profile != null && !profile.primary) TvButton("Delete profile", { controller.requestDeleteProfile(profile) }, Modifier.width(240.dp).height(56.dp))
        }
    }
}

@Composable private fun ProfileAvatarPreview(name: String, avatarUrl: String?, choice: Int, modifier: Modifier = Modifier) = Box(
    modifier.clip(RoundedCornerShape(12.dp)).background(avatarFallback("$name-$choice")),
    contentAlignment = Alignment.Center,
) {
    Text(name.take(2).ifBlank { choice.toString() }.uppercase(), color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
    if (!avatarUrl.isNullOrBlank()) AsyncImage(model = avatarUrl, contentDescription = "$name avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** Avatar choices remain plain data; remote artwork is never a prerequisite for choosing one. */
@Composable private fun AvatarPicker(style: String, selectedChoice: Int, onChoose: (String, Int) -> Unit, onCancel: () -> Unit) {
    var selectedStyle by remember { mutableStateOf(style) }
    var page by remember(selectedStyle) { mutableIntStateOf((selectedChoice - 1).coerceAtLeast(0) / 18) }
    val firstChoice = page * 18 + 1
    val choices = (firstChoice..minOf(firstChoice + 17, 48)).toList()
    val firstFocus = remember(page, selectedStyle) { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(page, selectedStyle) { firstFocus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Canvas)) {
        Text("Choose an avatar", color = White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(96.dp, 112.dp))
        Text("Choose an avatar, then return to finish the profile.", color = Muted, fontSize = 20.sp, modifier = Modifier.offset(96.dp, 178.dp))
        Column(Modifier.offset(96.dp, 238.dp).width(216.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("critters" to "Critters", "pixel-art" to "Pixel art").forEach { (value, label) ->
                TvButton(label, { selectedStyle = value }, Modifier.width(216.dp).height(44.dp), selected = selectedStyle == value)
            }
        }
        choices.chunked(6).forEachIndexed { row, values ->
            Row(Modifier.offset(360.dp, (238 + row * 140).dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                values.forEachIndexed { column, choice ->
                    AvatarChoice(choice, selected = choice == selectedChoice, modifier = Modifier.size(128.dp).then(if (row == 0 && column == 0) Modifier.focusRequester(firstFocus) else Modifier)) { onChoose(selectedStyle, choice) }
                }
            }
        }
        if (page > 0) TvButton("Previous", { page-- }, Modifier.offset(360.dp, 662.dp).width(180.dp).height(40.dp))
        Text("Avatar ${selectedChoice}", color = White, fontSize = 18.sp, modifier = Modifier.offset(556.dp, 670.dp).width(220.dp))
        Text("Page ${page + 1} of 3", color = Muted, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.offset(812.dp, 670.dp).width(300.dp))
        if (page < 2) TvButton("Next", { page++ }, Modifier.offset(1032.dp, 662.dp).width(180.dp).height(40.dp))
    }
}

@Composable private fun AvatarChoice(choice: Int, selected: Boolean, modifier: Modifier, onActivate: () -> Unit) {
    var focused by remember(choice) { mutableStateOf(false) }
    Holdable(onActivate, null, modifier.onFocusChanged { focused = it.hasFocus }.clip(RoundedCornerShape(12.dp)).background(if (focused) White else avatarFallback("avatar-$choice"))) {
        Text(choice.toString(), color = if (focused) Canvas else White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun PinDialog(prompt: PinPrompt, controller: AppController, modifier: Modifier = Modifier) {
    var pin by remember(prompt.title) { mutableStateOf("") }
    val pinFocus = remember { FocusRequester() }
    LaunchedEffect(prompt.title) { pinFocus.requestFocus() }
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).padding(32.dp).width(500.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(prompt.title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        TextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 20.dp).focusRequester(pinFocus))
        Text("PIN is never stored.", color = Muted, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Unlock", { controller.submitPin(pin) }); TvButton("Cancel", controller::cancelPin) }
    }
}

@Composable private fun ActionDialog(dialog: DialogState, controller: AppController, modifier: Modifier = Modifier) {
    val firstAction = remember(dialog.kind, dialog.title) { FocusRequester() }
    LaunchedEffect(dialog.kind, dialog.title) { firstAction.requestFocus() }
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).padding(32.dp).width(620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dialog.title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        when (dialog.kind) {
            DialogKind.SignOut -> Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Keep watching", controller::dismissDialog, Modifier.focusRequester(firstAction)); TvButton("Sign out", { controller.dismissDialog(); controller.signOut() }) }
            DialogKind.DeleteProfile -> { Text("This removes this profile's watch history, favorites and preferences. Other profiles are kept.", color = Muted, modifier = Modifier.padding(top = 16.dp)); Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Cancel", controller::dismissDialog, Modifier.focusRequester(firstAction)); TvButton("Delete profile", { dialog.profile?.let(controller::deleteProfile); controller.dismissDialog() }) } }
            DialogKind.QueueManage -> Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { TvButton("Undo", { dialog.media?.let(controller::undoQueueRemoval) }, Modifier.focusRequester(firstAction)); TvButton("Done", controller::dismissDialog) }
            DialogKind.SourceDetails -> {
                dialog.source?.let { source ->
                    Text(source.description.ifBlank { source.provider }, color = Muted, fontSize = 19.sp, maxLines = 12, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp).height(260.dp))
                    Text(listOfNotNull(source.quality, source.audio, source.provider.takeIf { it.isNotBlank() }).joinToString("  •  "), color = Muted, modifier = Modifier.padding(top = 12.dp))
                }
                TvButton("Back", controller::dismissDialog, Modifier.padding(top = 24.dp).focusRequester(firstAction))
            }
            else -> TvButton("Done", controller::dismissDialog, Modifier.padding(top = 24.dp).focusRequester(firstAction))
        }
    }
}

@Composable private fun TvButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier.width(170.dp).height(56.dp),
    selected: Boolean = false,
    multiline: Boolean = false,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> White
        selected -> Color(0xFF303234)
        else -> Surface
    }
    Holdable(
        onActivate,
        null,
        modifier.onFocusChanged { focused = it.hasFocus }.background(fill, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (content == null) Text(label, color = if (focused) Canvas else White, fontWeight = FontWeight.Bold, maxLines = if (multiline) 4 else 1, overflow = TextOverflow.Ellipsis)
            else content()
        }
    }
}

/** 700ms remote hold: exactly one action on release; hold suppresses ordinary activation. */
@Composable internal fun Holdable(onActivate: () -> Unit, onHold: (() -> Unit)?, modifier: Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var downAt by remember { mutableLongStateOf(0L) }; var held by remember { mutableStateOf(false) }
    LaunchedEffect(downAt, onHold) { if (downAt != 0L && onHold != null) { delay(HoldPolicy.thresholdMillis); if (downAt != 0L) { held = true; onHold() } } }
    Box(modifier = modifier.then(if (selected) Modifier.border(2.dp, White, RoundedCornerShape(12.dp)) else Modifier)
        .onFocusChanged { if (!it.hasFocus && downAt != 0L) { downAt = 0L; held = true } }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent; if (key.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && key.keyCode != KeyEvent.KEYCODE_ENTER) return@onPreviewKeyEvent false
            if (key.action == KeyEvent.ACTION_DOWN && HoldPressPolicy.begins(downAt, key.repeatCount)) { downAt = key.eventTime; held = false; true }
            else if (key.action == KeyEvent.ACTION_UP) { val doActivate = HoldPressPolicy.activatesOnRelease(downAt, held); downAt = 0L; if (doActivate) onActivate(); true } else true
        }.clickable(onClick = onActivate), contentAlignment = Alignment.Center, content = content)
}
