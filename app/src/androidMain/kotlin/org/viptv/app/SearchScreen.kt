package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun SearchScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    var filter by rememberSaveable { mutableStateOf("All") }
    var lastShownEntry by rememberSaveable(state.selectedProfile?.id) { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
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
        keyboard?.hide()
        val sectionIndex = state.searchSections.indexOfFirst { it.items.isNotEmpty() }
        if (sectionIndex < 0) { runCatching { first.requestFocus() }; return }
        val section = state.searchSections[sectionIndex]
        scope.launch {
            resultRows.scrollToItem(sectionIndex)
            entrySection = section.id
            entryRequest++
        }
    }
    BackHandler(tv && imeVisible) { keyboard?.hide() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    LaunchedEffect(state.searchEntryEpoch) {
        withFrameNanos {}
        runCatching { first.requestFocus() }
        if (tv && state.searchEntryEpoch > lastShownEntry) {
            keyboard?.show()
            lastShownEntry = state.searchEntryEpoch
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("Search", if (tv) null else controller::back)
        if (tv) Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AppField(state.searchQuery, controller::search, "Search movies and series",
                    Modifier.width(960.dp).height(80.dp).focusRequester(first).focusProperties { left = rail; right = resultsButton }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (!imeVisible && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) keyboard?.show()
                                true
                            } else if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 &&
                                key.keyCode in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
                                enterResults(); true
                            } else false
                        }, onSubmit = ::enterResults, imeAction = ImeAction.Search, showKeyboardOnFocus = false)
                AppButton("Results", ::enterResults, Modifier.width(220.dp).focusRequester(resultsButton).focusProperties { left = first })
            }
            VText(state.searchStatus, 22, Modifier.padding(top = 24.dp, bottom = 20.dp), C.textTertiary)
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

@Composable private fun SearchResultShelf(section: SearchSection, keyboard: FocusRequester, entryRequest: Int, onEntered: () -> Unit, controller: AppController) {
    val horizontal = rememberLazyListState()
    val first = remember { FocusRequester() }
    LaunchedEffect(entryRequest) {
        if (entryRequest > 0) {
            horizontal.scrollToItem(0)
            withFrameNanos {}
            runCatching { first.requestFocus() }
            onEntered()
        }
    }
    Column {
        VText(section.source, 28, lines = 2, display = true)
        VText(listOfNotNull(section.type?.let { DiscoverPolicy.groupLabel(DiscoverPolicy.typeGroup(it)) }, "${section.items.size} results").joinToString(" · "), 20, Modifier.padding(top = 6.dp), C.textTertiary)
        LazyRow(state = horizontal, modifier = Modifier.padding(top = 18.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
            itemsIndexed(section.items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { index, media ->
                MediaCard(media, Modifier.then(if (index == 0) Modifier.focusRequester(first).focusProperties { left = keyboard } else Modifier),
                    onClick = { controller.activateCard(media) }, onHold = { controller.requestDialog(DialogKind.MyListManage, media.name, media) })
            }
        }
    }
}
