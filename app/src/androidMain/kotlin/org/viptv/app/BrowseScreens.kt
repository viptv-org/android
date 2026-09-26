package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
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
