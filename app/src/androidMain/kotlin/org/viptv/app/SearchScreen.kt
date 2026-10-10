package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun SearchScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    var filter by rememberSaveable { mutableStateOf("All") }
    var lastShownEntry by rememberSaveable(state.selectedProfile?.id) { mutableIntStateOf(0) }
    var openKeyboardOnFocus by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val resultsButton = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val resultRows = key(state.searchQuery) { rememberLazyListState() }
    var entrySection by remember(state.searchQuery) { mutableStateOf<String?>(null) }
    var entryRequest by remember(state.searchQuery) { mutableIntStateOf(0) }
    val results = remember(state.searchSections, filter) {
        state.searchSections.flatMap { it.items }.distinctBy { HomeFocusPolicy.mediaKey(it) }.filter {
            filter == "All" || (filter == "Movies" && it.type == "movie") || (filter == "Series" && it.type in listOf("series", "episode")) || (filter == "Live TV" && it.type == "live")
        }
    }
    fun enterResults() {
        val sectionIndex = state.searchSections.indexOfFirst { it.items.isNotEmpty() }
        if (sectionIndex < 0) { keyboard?.hide(); runCatching { first.requestFocus() }; return }
        openKeyboardOnFocus = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        val section = state.searchSections[sectionIndex]
        scope.launch {
            resultRows.scrollToItem(sectionIndex)
            entrySection = section.id
            entryRequest++
        }
    }
    BackHandler(tv && imeVisible) { keyboard?.hide() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    LaunchedEffect(imeVisible) { if (imeVisible) openKeyboardOnFocus = false }
    LaunchedEffect(state.searchEntryEpoch) {
        withFrameNanos {}
        val requested = tv && state.searchEntryEpoch > lastShownEntry
        if (requested) {
            openKeyboardOnFocus = true
            focusManager.clearFocus(force = true)
            withFrameNanos {}
        }
        runCatching { first.requestFocus() }
        if (requested) {
            keyboard?.show()
            lastShownEntry = state.searchEntryEpoch
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(start = measure(104, 16), end = measure(0, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        Box(Modifier.fillMaxWidth().padding(end = measure(96, 0))) { ScreenHeader("Search", if (tv) null else controller::back) }
        if (tv) Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(end = 96.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                key(state.selectedProfile?.id) { TvSearchField(state.searchQuery, controller::search, openKeyboardOnFocus,
                    Modifier.width(960.dp).height(80.dp).focusRequester(first).focusProperties { left = rail; right = resultsButton }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (!imeVisible && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                                    openKeyboardOnFocus = true
                                    focusManager.clearFocus(force = true)
                                    scope.launch { withFrameNanos {}; first.requestFocus(); keyboard?.show() }
                                }
                                true
                            } else if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 &&
                                key.keyCode in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
                                enterResults(); true
                            } else false
                        }, onSubmit = ::enterResults) }
                AppButton("Results", ::enterResults, Modifier.width(220.dp).focusRequester(resultsButton).focusProperties { left = first })
            }
            VText(state.searchStatus, 22, Modifier.padding(top = 24.dp, end = 96.dp, bottom = 20.dp), C.textTertiary)
            LazyColumn(state = resultRows, verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
                items(state.searchSections, key = { state.searchQuery + "\u0000" + it.id }) { section ->
                    SearchResultShelf(section, first, if (section.id == entrySection) entryRequest else 0, { entrySection = null }, controller)
                }
            }
        } else {
            AppField(state.searchQuery, controller::search, "Search movies and series", Modifier.focusRequester(first))
            FilterTabs(listOf("All", "Movies", "Series", "Live TV"), filter, { filter = it }, Modifier.padding(vertical = 18.dp))
            if (results.isEmpty() && state.searchQuery.isNotBlank() && state.searchStatus == "Searching…") PosterSkeletonGrid(3)
            else if (results.isEmpty()) EmptyState(if (state.searchQuery.isBlank()) "Find your next favorite" else "No matching titles", if (state.searchQuery.isBlank()) "Search movies, series and live TV." else state.searchStatus, "search")
            else MediaGrid(results, onClick = { controller.activateCard(it) }, onHold = { controller.requestDialog(DialogKind.MyListManage, it.name, it) })
        }
    }
}

/** The state-based field honours showKeyboardOnFocus=false on a result-to-field focus move. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TvSearchField(query: String, onChange: (String) -> Unit, openKeyboardOnFocus: Boolean, modifier: Modifier, onSubmit: () -> Unit) {
    val field = rememberTextFieldState(query)
    val currentQuery by rememberUpdatedState(query)
    val currentOnChange by rememberUpdatedState(onChange)
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text != currentQuery) currentOnChange(text)
        }
    }
    BasicTextField(
        state = field,
        modifier = modifier,
        inputTransformation = InputTransformation.maxLength(256),
        textStyle = TextStyle(fontFamily = Onest, fontSize = 28.sp, color = C.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search, showKeyboardOnFocus = openKeyboardOnFocus),
        onKeyboardAction = { onSubmit() },
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interaction,
        cursorBrush = SolidColor(LocalAccent.current),
        decorator = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = field.text.toString(), innerTextField = inner, enabled = true, singleLine = true,
                visualTransformation = VisualTransformation.None, interactionSource = interaction,
                label = { VText("Search movies and series", 22) },
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true, isError = false, interactionSource = interaction,
                        shape = RoundedCornerShape(20.dp),
                    )
                },
            )
        },
    )
}

@Composable private fun SearchResultShelf(section: SearchSection, keyboard: FocusRequester, entryRequest: Int, onEntered: () -> Unit, controller: AppController) {
    val horizontal = rememberLazyListState()
    val first = remember { FocusRequester() }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(entryRequest) {
        if (entryRequest > 0) {
            horizontal.scrollToItem(0)
            snapshotFlow { ime.getBottom(density) }.first { it == 0 }
            withFrameNanos {}
            runCatching { first.requestFocus() }.onSuccess { onEntered() }
        }
    }
    Column {
        VText(section.source, 28, Modifier.padding(end = 96.dp), lines = 2, display = true)
        VText(listOfNotNull(section.type?.let { DiscoverPolicy.groupLabel(DiscoverPolicy.typeGroup(it)) }, "${section.items.size} results").joinToString(" · "), 20, Modifier.padding(top = 6.dp, end = 96.dp), C.textTertiary)
        LazyRow(state = horizontal, modifier = Modifier.padding(top = 18.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
            itemsIndexed(section.items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { index, media ->
                MediaCard(media, Modifier.then(if (index == 0) Modifier.focusRequester(first).focusProperties { left = keyboard } else Modifier),
                    onClick = { controller.activateCard(media) }, onHold = { controller.requestDialog(DialogKind.MyListManage, media.name, media) })
            }
        }
    }
}
