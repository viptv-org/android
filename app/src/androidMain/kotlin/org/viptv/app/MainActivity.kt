package org.viptv.app

import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.viptv.app.theme.ViptvColor as C

class MainActivity : ComponentActivity() {
    private lateinit var model: ViptvModel
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.remote.onForeground()
        if (::model.isInitialized) model.controller.onForeground()
    }
    override fun onPause() {
        if (::model.isInitialized && !isChangingConfigurations) { model.remote.onBackground(); model.controller.onBackground() }
        super.onPause()
    }
    override fun onStop() {
        if (::model.isInitialized && !isChangingConfigurations && !isInPictureInPictureMode) model.controller.stopForBackground()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) intent.getStringExtra("preview-origin")?.let { value ->
            ServerOrigin.validate(value)?.takeIf { it != ServerOrigin.load(this) }?.let { origin ->
                getSharedPreferences("viptv.auth", MODE_PRIVATE).edit().clear().commit()
                ServerOrigin.save(this, origin)
            }
        }
        val television = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        if (television) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            if (television) hide(WindowInsetsCompat.Type.systemBars())
        }
        model = ViewModelProvider(this)[ViptvModel::class.java]
        setContent { CompositionLocalProvider(LocalTv provides television) { ViptvApplication(model) } }
    }
}

/** Keep credentials, requests and playback alive across phone rotation. */
class ViptvModel(application: Application) : AndroidViewModel(application) {
    internal val remote = TvRemoteController(application)
    private val preferences = application.getSharedPreferences("viptv.display", 0)
    var oled by mutableStateOf(preferences.getBoolean("oled", false)); private set
    var accent by mutableStateOf(Color(preferences.getInt("accent", 0xFFF5C542.toInt()))); private set
    var origin by mutableStateOf(ServerOrigin.load(application)); private set
    var controller by mutableStateOf(AppController(application, origin)); private set
    fun updateOled(value: Boolean) { oled = value; preferences.edit().putBoolean("oled", value).apply() }
    fun updateAccent(value: Color) { accent = value; preferences.edit().putInt("accent", value.toArgb()).apply() }
    fun changeOrigin(value: String) {
        if (value == origin) return
        controller.wipeCredentialsForOriginChange(); controller.close()
        ServerOrigin.save(getApplication(), value); origin = value
        controller = AppController(getApplication(), value)
    }
    override fun onCleared() { remote.close(); controller.close() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun ViptvApplication(model: ViptvModel) {
    val controller = model.controller
    val state by controller.state.collectAsStateWithLifecycle()
    val tv = LocalTv.current
    CompositionLocalProvider(LocalTvRemote provides if (tv) null else model.remote) {
    ViptvTheme(model.oled, model.accent) {
        BoxWithConstraints(Modifier.fillMaxSize().background(LocalGround.current)) {
            if (tv) {
                val density = LocalDensity.current
                val scale = minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, 1f), LocalBringIntoViewSpec provides VisibleFocusScroll) {
                    Box(Modifier.requiredSize(1920.dp, 1080.dp).align(Alignment.Center)) { ApplicationShell(state, controller, model) }
                }
            } else {
                ApplicationShell(state, controller, model)
                TvRemoteOverlay(model.remote)
            }
        }
    }
}

}

internal fun Route.screenKey(): String = when (this) {
    is Route.Browse -> "browse:" + destination.name
    is Route.Details -> "detail:${media.id}:$entryId"
    is Route.Player -> "player:" + media.id
    is Route.Sources -> "sources:" + media.id
    is Route.ProfileEditor -> "profile:" + profile?.id
    is Route.Guide -> "guide"
    else -> javaClass.simpleName
}

internal fun televisionRailExpanded(requested: Boolean, keyboardVisible: Boolean, modalVisible: Boolean = false, windowFocused: Boolean = true): Boolean =
    requested && !keyboardVisible && !modalVisible && windowFocused

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ApplicationShell(state: AppState, controller: AppController, model: ViptvModel) {
    val tv = LocalTv.current
    val route = state.route
    val key = route.screenKey()
    val saved = rememberSaveableStateHolder()
    val rail = remember { FocusRequester() }
    val initial = remember(key) { FocusRequester() }
    val focusMemory = remember(key) { FocusMemory() }
    val entrance = rememberScreenEntrance(route, key)
    var railOpen by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.isImeVisible
    val modalVisible = state.dialog != null || state.pinPrompt != null
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val railExpanded = televisionRailExpanded(railOpen, keyboardVisible, modalVisible, windowFocused)
    LaunchedEffect(tv, keyboardVisible, modalVisible, windowFocused) {
        // Input/window handoffs must not preserve expansion for focus restoration.
        if (tv && (keyboardVisible || modalVisible || !windowFocused)) railOpen = false
    }
    val browse = route is Route.Browse || route is Route.Guide || route == Route.Search || route == Route.Settings || route == Route.Addons || route is Route.Details
    val tab = route is Route.Browse || route is Route.Guide
    BackHandler(controller.consumesBack(state)) { controller.handleBack() }
    BackHandler(tv && railExpanded) { if (focusMemory.restore(initial)) railOpen = false }
    CompositionLocalProvider(LocalRailFocus provides rail, LocalContentFocus provides initial, LocalFocusMemory provides focusMemory, LocalCloseRail provides { railOpen = false }) {
        Box(Modifier.fillMaxSize()) {
            val insets = if (tv || route is Route.Player || route is Route.Details) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            saved.SaveableStateProvider(key) {
                Box(Modifier.fillMaxSize().then(insets).screenEntrance(entrance, vertical = tv)) {
                    when (route) {
                        Route.Pairing -> Pairing(state, controller, model)
                        Route.Profiles -> ProfileChooser(state, controller)
                        is Route.ProfileEditor -> ProfileEditor(route.profile, controller)
                        is Route.Browse -> when (route.destination) {
                            Destination.Home -> HomeScreen(state, controller)
                            Destination.Discover -> DiscoverScreen(state, controller)
                            else -> LibraryScreen(state, controller)
                        }
                        is Route.Details -> DetailsScreen(route.media, controller)
                        is Route.Sources -> SourcePicker(route.media, state.sources, controller)
                        is Route.Player -> PlaybackScreen(route.media, state.playerChromeVisible, state.seekPreview, state.playbackTracks, controller)
                        is Route.Guide -> GuideScreen(state, route.channel, controller)
                        Route.Search -> SearchScreen(state, controller)
                        Route.Settings -> SettingsScreen(state, controller, model)
                        Route.Addons -> AddonsScreen(state, controller)
                    }
                }
            }
            if (!tv && tab) PhoneNavigation(route, controller, Modifier.align(Alignment.BottomCenter))
            if (tv && browse) TelevisionRail(state, controller, railExpanded, { railOpen = televisionRailExpanded(it, keyboardVisible, modalVisible, windowFocused) }, rail, initial, focusMemory)
            if (state.loading && route !is Route.Player && route != Route.Pairing) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).then(if (tv) Modifier else Modifier.statusBarsPadding()).padding(measure(40, 16)).size(measure(32, 22)), color = LocalAccent.current, strokeWidth = measure(4, 2))
            }
            state.message?.takeUnless { route == Route.Pairing && !tv && !state.pairingRequested }?.let { message ->
                var visible by remember(message) { mutableStateOf(true) }
                LaunchedEffect(message) { delay(5000); visible = false }
                if (visible) Box(Modifier.align(if (tv) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = measure(64, 16), vertical = measure(32, if (tab) 116 else 40))
                    .widthIn(max = measure(660, 440)).clip(RoundedCornerShape(20.dp)).background(C.surfaceN3).padding(measure(24, 16))) {
                    VText(message, if (tv) 22 else 14, lines = 3)
                }
            }
            state.foregroundError?.let { error ->
                Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = measure(64, 16), vertical = measure(32, if (tab) 116 else 24))
                    .widthIn(max = measure(660, 440)).clip(RoundedCornerShape(20.dp)).background(C.surfaceN3).padding(measure(24, 16)),
                    verticalArrangement = Arrangement.spacedBy(measure(12, 8))) {
                    VText(error, if (tv) 22 else 14, lines = 3)
                    AppButton("Try again", controller::retryForegroundValidation)
                }
            }
            state.dialog?.let { ActionDialog(it, controller) }
            state.pinPrompt?.let { PinDialog(it, controller) }
        }
    }
}

private val navItems = listOf(
    Triple(Destination.Search, "search", 202), Triple(Destination.Home, "home", 282),
    Triple(Destination.Discover, "discover", 360), Triple(Destination.Live, "live", 440),
    Triple(Destination.MyList, "list", 516), Triple(Destination.Settings, "settings", 978),
)
private fun destination(route: Route) = when (route) {
    is Route.Browse -> route.destination
    is Route.Guide -> Destination.Live
    Route.Search -> Destination.Search
    Route.Settings, Route.Addons -> Destination.Settings
    else -> Destination.Home
}

@Composable internal fun TelevisionRail(state: AppState, controller: AppController, expanded: Boolean, onExpanded: (Boolean) -> Unit, rail: FocusRequester, initial: FocusRequester, memory: FocusMemory) {
    val current = destination(state.route)
    val profileTarget = remember { FocusRequester() }
    val targets = remember { navItems.associate { it.first to FocusRequester() } }
    val restoreContentOnRight = Modifier.onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && memory.restore(initial)) onExpanded(false)
            true
        } else false
    }
    Box(Modifier.fillMaxSize()) {
        if (expanded) {
            Box(Modifier.fillMaxSize().background(C.scrimTvMenu))
            Box(Modifier.width(520.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(LocalGround.current, LocalGround.current.copy(alpha = .98f), Color.Transparent))))
        }
        var profileFocused by remember { mutableStateOf(false) }
        Holdable({ onExpanded(false); controller.navigate(Destination.Profile) }, modifier = Modifier.offset(40.dp, 48.dp)
            .focusRequester(profileTarget).focusProperties { up = FocusRequester.Cancel; down = targets.getValue(Destination.Search); left = FocusRequester.Cancel }
            .size(if (expanded) 376.dp else 64.dp, 68.dp).onFocusChanged { profileFocused = it.isFocused; if (it.isFocused) onExpanded(true) }
            .then(restoreContentOnRight)
            .clip(CircleShape).background(if (profileFocused) C.textPrimary else Color.Transparent), rememberFocus = false) {
            Row(Modifier.fillMaxSize().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(state.selectedProfile, Modifier.size(56.dp).clip(CircleShape))
                if (expanded) Column(Modifier.padding(start = 24.dp)) {
                    VText(state.selectedProfile?.name ?: "Profile", 26, color = if (profileFocused) C.onLight else C.textPrimary, bold = true)
                    VText("Switch profile", 20, color = if (profileFocused) C.textOnLightSecondary else C.textSecondary)
                }
            }
        }
        navItems.forEachIndexed { index, (item, icon, y) ->
            var focused by remember(item) { mutableStateOf(false) }
            Holdable({ onExpanded(false); controller.navigate(item) }, modifier = Modifier.offset(40.dp, (y - 20).dp)
                .focusRequester(targets.getValue(item)).focusProperties {
                    up = if (index == 0) profileTarget else targets.getValue(navItems[index - 1].first)
                    down = if (index == navItems.lastIndex) FocusRequester.Cancel else targets.getValue(navItems[index + 1].first)
                    left = FocusRequester.Cancel
                }
                .size(if (expanded) 376.dp else 64.dp, 64.dp)
                .then(if (current == item) Modifier.focusRequester(rail) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onExpanded(true) }
                .then(restoreContentOnRight).clip(CircleShape).background(if (focused) C.textPrimary else if (!expanded && current == item) C.surfaceN3 else Color.Transparent), rememberFocus = false) {
                Row(Modifier.fillMaxSize().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    VIcon(icon, item.label, Modifier.size(24.dp), if (focused) C.onLight else if (current == item) C.textPrimary else C.textSecondary)
                    if (expanded) VText(item.label, 26, Modifier.padding(start = 40.dp), if (focused) C.onLight else C.textPrimary, bold = focused || current == item)
                }
            }
        }
    }
}

@Composable private fun PhoneNavigation(route: Route, controller: AppController, modifier: Modifier) {
    val current = destination(route)
    Box(modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, LocalGround.current)))) {
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(C.fillGlass).border(1.dp, C.lineOutline, CircleShape).padding(5.dp)) {
                listOf(Destination.Home to "home", Destination.Discover to "discover", Destination.Live to "live", Destination.MyList to "list").forEach { (item, icon) ->
                    val active = item == current
                    Holdable({ controller.navigate(item) }, modifier = Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(if (active) C.textPrimary else Color.Transparent)) {
                        // AND-042-NAV: icons only; the destination stays the accessible name.
                        VIcon(icon, item.label, Modifier.size(22.dp), if (active) C.onLight else C.textSecondary)
                    }
                }
            }
            Holdable({ controller.navigate(Destination.Search) }, modifier = Modifier.size(64.dp).clip(CircleShape).background(C.fillGlass).border(1.dp, C.lineOutline, CircleShape)) {
                VIcon("search", "Search", Modifier.size(26.dp))
            }
        }
    }
}
