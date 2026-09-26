package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun SearchScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    var filter by rememberSaveable { mutableStateOf("All") }
    var keyboardTarget by remember { mutableStateOf(first) }
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
        val section = state.searchSections.firstOrNull { it.items.isNotEmpty() } ?: return
        scope.launch {
            resultRows.scrollToItem(0)
            entrySection = section.id
            entryRequest++
        }
    }
    LaunchedEffect(Unit) { withFrameNanos {}; runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().imePadding().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("Search", if (tv) null else controller::back)
        if (tv) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.width(560.dp)) {
                VText(state.searchQuery.ifBlank { "Search movies and series" }, 32, Modifier.fillMaxWidth().background(C.surfaceN1, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(22.dp), if (state.searchQuery.isBlank()) C.textTertiary else C.textPrimary, lines = 1)
                Spacer(Modifier.height(28.dp))
                RemoteKeyboard(state.searchQuery, controller::search, first, onResults = ::enterResults, leftBoundary = rail, onKeyFocused = { keyboardTarget = it })
                VText("▶▶ Jump to results", 20, Modifier.padding(top = 16.dp), C.textTertiary)
            }
            Column(Modifier.weight(1f)) {
                VText(state.searchStatus, 22, Modifier.padding(bottom = 20.dp), C.textTertiary)
                LazyColumn(state = resultRows, verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
                    items(state.searchSections, key = { state.searchQuery + "\u0000" + it.id }) { section ->
                        SearchResultShelf(section, keyboardTarget, if (section.id == entrySection) entryRequest else 0, { entrySection = null }, controller)
                    }
                }
            }
        } else {
            AppField(state.searchQuery, controller::search, "Search movies and series", Modifier.focusRequester(first))
            FilterTabs(listOf("All", "Movies", "Series", "Live TV"), filter, { filter = it }, Modifier.padding(vertical = 18.dp))
            if (results.isEmpty()) EmptyState(if (state.searchQuery.isBlank()) "Find your next favorite" else "No matching titles", if (state.searchQuery.isBlank()) "Search movies, series and live TV." else state.searchStatus, "search")
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
