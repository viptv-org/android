package org.viptv.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RokuApplication() }
    }
}

/** All presentation measures once in Roku's 1280 × 720 coordinate space. */
@Composable private fun RokuApplication() {
    val context = LocalContext.current
    val controller = remember { AppController(context.applicationContext) }
    val state by controller.state.collectAsStateWithLifecycle()
    DisposableEffect(controller) { onDispose(controller::close) }
    BackHandler(controller.consumesBack(state)) { controller.handleBack() }
    BoxWithConstraints(Modifier.fillMaxSize().background(RokuCanvas)) {
        val physicalDensity = LocalDensity.current
        val scale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f)
        val railFocus = remember { FocusRequester() }
        CompositionLocalProvider(LocalDensity provides Density(physicalDensity.density * scale, 1f), LocalRokuRailFocus provides railFocus) {
            Box(Modifier.requiredSize(1280.dp,720.dp).align(Alignment.Center).clipToBounds()) {
                val route = state.route
                when (route) {
                    Route.Pairing -> Pairing(state, controller)
                    Route.Profiles -> ProfileChooser(state, controller)
                    is Route.ProfileEditor -> ProfileEditor(route.profile, controller)
                    is Route.Browse -> when (route.destination) {
                        Destination.Home -> RokuHomeScreen(state, controller)
                        Destination.Discover -> DiscoverScreen(state, controller)
                        else -> RokuCollection(state, route.destination, controller)
                    }
                    is Route.Details -> DetailsScreen(route.media, controller)
                    is Route.Sources -> SourcePicker(route.media, state.sources, controller)
                    is Route.Player -> PlaybackScreen(route.media, state.playerChromeVisible, state.seekPreview, state.playbackTracks, controller)
                    is Route.Guide -> GuideScreen(state, route.channel, controller)
                    Route.Search -> SearchScreen(state, controller)
                    Route.Settings -> SettingsScreen(
                        state.preferences, state.addons, state.serverAbout,
                        controller::setPreference, controller::installAddon,
                        controller::toggleAddon, controller::removeAddon,
                        { controller.navigate(Destination.Profile) },
                        { controller.requestDialog(DialogKind.SignOut,"Sign out of VIPTV?") },
                        onManageProfiles = controller::openProfileManagement,
                    )
                    Route.Addons -> AddonsScreen(state, controller)
                }
                if (route is Route.Browse || route is Route.Guide || route is Route.Details || route is Route.Sources || route == Route.Search || route == Route.Settings || route == Route.Addons) RokuRail(state,controller)
                if (state.loading && route !is Route.Sources && route !is Route.Player && route !is Route.Guide) {
                    Box(Modifier.fillMaxSize().background(RokuCanvas.copy(alpha=.78f))) {
                        RokuSpinner(Modifier.offset(610.dp,330.dp).size(60.dp))
                        Text("Loading",color=RokuWhite,fontSize=20.sp,textAlign=TextAlign.Center,modifier=Modifier.offset(280.dp,414.dp).width(720.dp))
                    }
                }
                state.message?.let { message -> RokuNotice(message) }
                state.dialog?.let { ActionDialog(it,controller) }
                state.pinPrompt?.let { PinDialog(it,controller) }
            }
        }
    }
}

@Composable private fun RokuNotice(message:String) {
    var visible by remember(message) { mutableStateOf(true) }
    LaunchedEffect(message) { delay(5000); visible=false }
    if (visible) Box(Modifier.offset(320.dp,42.dp).size(640.dp,100.dp).background(RokuSurface,RoundedCornerShape(12.dp)).padding(28.dp,18.dp)) {
        Text(message,color=RokuWhite,fontSize=19.sp,maxLines=2)
    }
}

@Composable private fun RokuRail(state:AppState,controller:AppController) {
    AsyncImage(rokuAsset("viptv-mark.png"),"viptv",Modifier.offset(32.dp,29.dp).size(36.dp,31.dp))
    val names=listOf("", "ui-nav-home.png","ui-nav-discover.png","ui-nav-tv.png","ui-nav-list.png","ui-nav-search.png","ui-nav-settings.png")
    val selectedDestination = when(val route=state.route) {
        is Route.Browse -> route.destination
        is Route.Guide -> Destination.Live
        Route.Search -> Destination.Search
        Route.Settings, Route.Addons -> Destination.Settings
        else -> Destination.Home
    }
    val railFocus=LocalRokuRailFocus.current
    Destination.entries.forEachIndexed { index,destination ->
        var focused by remember { mutableStateOf(false) }
        Holdable({ controller.navigate(destination) },null,
            Modifier.offset(21.dp,(108+74*index).dp).size(60.dp)
                .then(if(destination==selectedDestination)Modifier.focusRequester(railFocus)else Modifier)
                .onPreviewKeyEvent { event ->
                    if(event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT && state.route==Route.Browse(Destination.Home)) {
                        if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN)controller.restoreHomeFocus()
                        true
                    } else false
                }
                .onFocusChanged { focused=it.isFocused }
                .clip(RoundedCornerShape(12.dp))
                .background(if(focused) RokuWhite else androidx.compose.ui.graphics.Color.Transparent),
        ) {
            if (destination==Destination.Profile) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(RokuSurface),contentAlignment=Alignment.Center) {
                    Text(state.selectedProfile?.name?.take(2)?.uppercase().orEmpty(),color=RokuWhite,fontSize=18.sp)
                    state.selectedProfile?.avatarUrl?.let { AsyncImage(it,"Profile",Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
                }
            } else AsyncImage(rokuAsset(names[index]),destination.label,Modifier.size(30.dp),colorFilter=ColorFilter.tint(if(focused)RokuCanvas else RokuWhite))
        }
    }
}

@Composable private fun RokuCollection(state:AppState,destination:Destination,controller:AppController) {
    val items=if(destination==Destination.MyList)state.favorites else state.catalog
    var selected by remember(destination) { mutableIntStateOf(0) }
    val page=selected/8
    val focus=remember(items,page) { List(8) { FocusRequester() } }
    LaunchedEffect(items,page) { if(items.isNotEmpty()) focus[selected%8].requestFocus() }
    Text(destination.label,color=RokuWhite,fontSize=44.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(100.dp,54.dp))
    if(items.isEmpty()) Text("Nothing here yet",color=RokuMuted,fontSize=24.sp,textAlign=TextAlign.Center,modifier=Modifier.offset(250.dp,304.dp).width(780.dp))
    items.drop(page*8).take(8).forEachIndexed { index,media ->
        val absolute=page*8+index
        RokuArtworkCard(media,Modifier.offset((100+(index%4)*280).dp,(198+(index/4)*220).dp).size(256.dp,200.dp)
            .focusRequester(focus[index]).onFocusChanged { if(it.isFocused) selected=absolute }
            .onPreviewKeyEvent { event ->
                val key=event.nativeKeyEvent
                val delta=when(key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> 4
                    KeyEvent.KEYCODE_DPAD_UP -> -4
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    KeyEvent.KEYCODE_DPAD_LEFT -> if(index%4==0) 0 else -1
                    else -> 0
                }
                if(delta==0) false else {
                    if(key.action==KeyEvent.ACTION_DOWN) {
                        val target=(absolute+delta).coerceIn(0,items.lastIndex)
                        selected=target
                        if(target/8==page)focus[target%8].requestFocus()
                    }
                    true
                }
            },
            onActivate={controller.open(media)},onHold={controller.toggleMyList(media)})
    }
}
