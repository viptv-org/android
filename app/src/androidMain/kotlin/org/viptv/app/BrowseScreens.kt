package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun DiscoverScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val ui = state.discoverUi
    val catalog = ui.catalogs.firstOrNull { it.key == ui.selectedCatalogKey }
    var choice by remember { mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null) }
    var entry by remember { mutableStateOf<CatalogFilter?>(null) }
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    var claimedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading) { if (tv && !ui.loading && !claimedFocus) { withFrameNanos {}; claimedFocus = runCatching { first.requestFocus() }.getOrDefault(false) } }
    Column(Modifier.fillMaxSize().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("Discover")
        val types = ui.catalogs.filter { it.key.type != "live" }.map { DiscoverPolicy.typeGroup(it.key.type) }.distinct()
        FilterTabs(types.map(DiscoverPolicy::groupLabel), DiscoverPolicy.groupLabel(DiscoverPolicy.typeGroup(ui.selectedType)), { label ->
            types.firstOrNull { DiscoverPolicy.groupLabel(it) == label }?.let(controller::setDiscoverType)
        }, Modifier.fillMaxWidth(), first)
        LazyRow(Modifier.padding(top = measure(24, 12), bottom = measure(28, 20)), horizontalArrangement = Arrangement.spacedBy(measure(16, 8)), contentPadding = PaddingValues(4.dp)) {
            items(ui.catalogs.filter { DiscoverPolicy.typeGroup(it.key.type) == DiscoverPolicy.typeGroup(ui.selectedType) }, key = { it.key.stableId }) { item ->
                AppChip(item.name, { controller.setDiscoverCatalog(item.key) }, item.key == ui.selectedCatalogKey)
            }
            items(catalog?.filters.orEmpty(), key = { it.name }) { filter ->
                AppChip(ui.selectedFilters[filter.name] ?: filter.name.replaceFirstChar(Char::titlecase), {
                    if (filter.options.isEmpty()) entry = filter
                    else choice = filter.name.replaceFirstChar(Char::titlecase) to
                        (if (filter.required) emptyList() else listOf("All" to { controller.setDiscoverFilter(filter.name, null); choice = null })) +
                        filter.options.map { value -> value to { controller.setDiscoverFilter(filter.name, value); choice = null } }
                }, ui.selectedFilters.containsKey(filter.name))
            }
        }
        if (ui.error != null && ui.items.isNotEmpty()) VText(ui.error, if (tv) 24 else 14, Modifier.padding(bottom = 16.dp), C.statusDanger)
        if (ui.items.isEmpty()) EmptyState(if (ui.loading) "Finding titles…" else "No titles yet",
            ui.error ?: if (ui.catalogs.isEmpty()) "Add or enable a catalog addon in Settings." else "Choose another catalog or filter.", "discover",
            retry = if (ui.loading) null else { { claimedFocus = false; controller.openDiscover() } },
            retryModifier = Modifier.focusRequester(first).focusProperties { if (tv) left = rail })
        else MediaGrid(ui.items, onClick = { controller.activateCard(it) }, onHold = { controller.requestDialog(DialogKind.MyListManage, it.name, it) },
            hasMore = ui.nextSkip != null, loading = ui.loading, onMore = { controller.appendDiscoverPage() })
    }
    choice?.let { ChoiceDialog(it.first, it.second, { choice = null }) }
    entry?.let { filter -> TextEntry(filter.name.replaceFirstChar(Char::titlecase), if (filter.required) "Enter a value for this required filter." else "Enter a value or leave empty to clear.", ui.selectedFilters[filter.name].orEmpty(), onDone = {
        if (!filter.required || it.isNotBlank()) { controller.setDiscoverFilter(filter.name, it); entry = null }
    }, onCancel = { entry = null }) }
}

@Composable internal fun LibraryScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val first = LocalContentFocus.current
    val queue = state.libraryQueue
    val items = if (queue) state.queue else state.favorites
    LaunchedEffect(Unit) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    Column(Modifier.fillMaxSize().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("My List")
        FilterTabs(listOf("My List", "Continue Watching"), if (queue) "Continue Watching" else "My List", {
            if (it == "My List") controller.openMyList() else controller.openContinueWatching()
        }, Modifier.padding(bottom = measure(40, 24)), first)
        if (items.isEmpty()) EmptyState(if (queue) "Nothing in progress" else "Your list is empty.",
            if (queue) "Titles you start watching appear here." else "Add titles with the + button.", "list")
        else if (queue && !tv) LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 164.dp)) {
            items(items, key = { HomeFocusPolicy.mediaKey(it) }) { media ->
                QueueCard(media, { controller.activateCard(media, true) }, { controller.requestQueueManage(media) }, Modifier.fillMaxWidth())
            }
        } else MediaGrid(items, queue = queue, onClick = { controller.activateCard(it, queue) },
            onHold = { if (queue) controller.requestQueueManage(it) else controller.requestDialog(DialogKind.MyListManage, it.name, it) })
    }
}

@Composable internal fun MediaGrid(items: List<Media>, queue: Boolean = false, onClick: (Media) -> Unit, onHold: (Media) -> Unit, hasMore: Boolean = false, loading: Boolean = false, onMore: () -> Unit = {}) {
    val tv = LocalTv.current
    val grid = rememberLazyGridState()
    val rail = LocalRailFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val more by rememberUpdatedState(onMore)
    LaunchedEffect(grid, items.size, hasMore, loading) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.distinctUntilChanged().collect { last ->
            if (last >= items.size - 8 && hasMore && !loading) more()
        }
    }
    LazyVerticalGrid(GridCells.Fixed(if (tv) 4 else 3), state = grid, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = measure(0, 164)),
        horizontalArrangement = Arrangement.spacedBy(measure(36, 12)), verticalArrangement = Arrangement.spacedBy(measure(40, 24))) {
        itemsIndexed(items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { index, media ->
            MediaCard(media, Modifier.then(if (tv && index % 4 == 0) Modifier.focusProperties { left = rail } else Modifier), queue,
                onClick = { keyboard?.hide(); onClick(media) }, onHold = { onHold(media) }, portrait = !tv, wide = tv)
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { VText("Loading more titles…", if (tv) 22 else 14, Modifier.padding(16.dp), C.textTertiary) }
    }
}

@Composable internal fun SearchScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val first = LocalContentFocus.current
    val resultsFocus = remember { FocusRequester() }
    val rail = LocalRailFocus.current
    var filter by rememberSaveable { mutableStateOf("All") }
    val resultRows = rememberLazyListState()
    var keyboardFocused by remember { mutableStateOf(true) }
    val results = remember(state.searchSections, filter) {
        state.searchSections.flatMap { it.items }.distinctBy { HomeFocusPolicy.mediaKey(it) }.filter {
            filter == "All" || (filter == "Movies" && it.type == "movie") || (filter == "Series" && it.type in listOf("series", "episode")) || (filter == "Live TV" && it.type == "live")
        }
    }
    val groups = remember(results) { results.groupBy { if (it.type == "live") "Live TV" else if (it.type in listOf("series", "episode")) "Series" else "Movies" } }
    LaunchedEffect(groups.keys.toList(), keyboardFocused) {
        if (tv && keyboardFocused) resultRows.scrollToItem(0)
    }
    LaunchedEffect(Unit) { withFrameNanos {}; runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().imePadding().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("Search", if (tv) null else controller::back)
        if (tv) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.width(560.dp).onFocusChanged { keyboardFocused = it.hasFocus }.focusProperties { left = rail }) {
                VText(state.searchQuery.ifBlank { "Search movies and series" }, 32, Modifier.fillMaxWidth().background(C.surfaceN1, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(22.dp), if (state.searchQuery.isBlank()) C.textTertiary else C.textPrimary, lines = 1)
                Spacer(Modifier.height(28.dp))
                RemoteKeyboard(state.searchQuery, controller::search, first, onResults = { runCatching { resultsFocus.requestFocus() } })
            }
            Column(Modifier.weight(1f)) {
                VText(state.searchStatus, 22, Modifier.padding(bottom = 20.dp), C.textTertiary)
                LazyColumn(state = resultRows, verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
                    groups.entries.forEachIndexed { sectionIndex, (title, items) -> item(key = title) {
                        VText(title, 32, display = true)
                        LazyRow(Modifier.padding(top = 18.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { index, media ->
                                MediaCard(media, Modifier.then(if (sectionIndex == 0 && index == 0) Modifier.focusRequester(resultsFocus) else Modifier),
                                    onClick = { controller.activateCard(media) }, onHold = { controller.requestDialog(DialogKind.MyListManage, media.name, media) })
                            }
                        }
                    } }
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
