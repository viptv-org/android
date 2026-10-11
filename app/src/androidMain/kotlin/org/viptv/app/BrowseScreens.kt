@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.viptv.app

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun DiscoverScreen(state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val ui = state.discoverUi
    val catalog = ui.catalogs.firstOrNull { it.key == ui.selectedCatalogKey }
    val groupsByType = remember(ui.catalogs) {
        ui.catalogs.map { it.key.type }.distinct().associateWith(DiscoverPolicy::grouping)
    }
    val types = remember(ui.catalogs) {
        ui.catalogs.filter { it.key.type != "live" }.map { groupsByType.getValue(it.key.type) }.distinctBy { it.group }
    }
    val selectedType = remember(ui.selectedType) { DiscoverPolicy.grouping(ui.selectedType) }
    val visibleCatalogs = remember(ui.catalogs, selectedType.group) {
        ui.catalogs.filter { groupsByType.getValue(it.key.type).group == selectedType.group }
    }
    var choice by remember { mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null) }
    var entry by remember { mutableStateOf<CatalogFilter?>(null) }
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    var claimedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading) { if (tv && !ui.loading && !claimedFocus) { withFrameNanos {}; claimedFocus = runCatching { first.requestFocus() }.getOrDefault(false) } }
    Column(Modifier.fillMaxSize().padding(start = measure(104, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("Discover", trailing = { PhoneTabActions(state, controller) })
        FilterTabs(types.map { it.groupLabel }, selectedType.groupLabel, { label ->
            types.firstOrNull { it.groupLabel == label }?.group?.let(controller::setDiscoverType)
        }, Modifier.fillMaxWidth(), first)
        LazyRow(Modifier.padding(top = measure(24, 12), bottom = measure(28, 20)), horizontalArrangement = Arrangement.spacedBy(measure(16, 8)), contentPadding = PaddingValues(4.dp)) {
            items(visibleCatalogs, key = { it.key.stableId }) { item ->
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
        if (ui.items.isEmpty() && ui.loading) PosterSkeletonGrid(if (tv) 4 else 3)
        else if (ui.items.isEmpty()) EmptyState("No titles yet",
            ui.error ?: if (ui.catalogs.isEmpty()) "Add or enable a catalog addon in Settings." else "Choose another catalog or filter.", "discover",
            retry = { claimedFocus = false; controller.openDiscover() },
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
    Column(Modifier.fillMaxSize().padding(start = measure(104, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        ScreenHeader("My List", trailing = { PhoneTabActions(state, controller) })
        FilterTabs(listOf("My List", "Continue Watching"), if (queue) "Continue Watching" else "My List", {
            if (it == "My List") controller.openMyList() else controller.openContinueWatching()
        }, Modifier.padding(bottom = measure(40, 24)), first)
        if (items.isEmpty()) EmptyState(if (queue) "Nothing in progress" else "Your list is empty.",
            if (queue) "Titles you start watching appear here." else "Add titles with the + button.", "list")
        else if (queue && !tv) LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 164.dp)) {
            items(items, key = { HomeFocusPolicy.mediaKey(it) }) { media ->
                QueueCard(media, { controller.activateCard(media, true) }, { controller.requestQueueManage(media) }, Modifier.fillMaxWidth())
            }
        } else MediaGrid(items, queue = queue, poster = tv && !queue, onClick = { controller.activateCard(it, queue) },
            onHold = { if (queue) controller.requestQueueManage(it) else controller.requestDialog(DialogKind.MyListManage, it.name, it) })
    }
}

/** TV-MYLIST-POSTER-001: focusing a poster row scrolls it to the top of the grid; the next row peeks below. */
private object PosterRowScroll : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(220, easing = FastOutSlowInEasing)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset
}

/** Exactly seven 200-wide poster columns, narrowing only if the viewport cannot fit them. */
private object PosterColumns : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> =
        List(7) { minOf(200.dp.roundToPx(), (availableSize - 6 * spacing) / 7) }
}

/** [poster]: the TV My List grid of seven 200 × 300 posters (TV-MYLIST-POSTER-001). */
@Composable internal fun MediaGrid(items: List<Media>, queue: Boolean = false, poster: Boolean = false, onClick: (Media) -> Unit, onHold: (Media) -> Unit, hasMore: Boolean = false, loading: Boolean = false, onMore: () -> Unit = {}) {
    val tv = LocalTv.current
    val columns = if (poster) 7 else if (tv) 4 else 3
    val grid = rememberLazyGridState()
    val rail = LocalRailFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val more by rememberUpdatedState(onMore)
    LaunchedEffect(grid, items.size, hasMore, loading) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.distinctUntilChanged().collect { last ->
            if (last >= items.size - 8 && hasMore && !loading) more()
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides if (poster) PosterRowScroll else LocalBringIntoViewSpec.current) {
    LazyVerticalGrid(if (poster) PosterColumns else GridCells.Fixed(columns), state = grid, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = measure(0, 164)),
        horizontalArrangement = Arrangement.spacedBy(measure(36, 12), Alignment.Start), verticalArrangement = Arrangement.spacedBy(measure(40, 24))) {
        itemsIndexed(items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { index, media ->
            MediaCard(media, Modifier.then(if (tv && index % columns == 0) Modifier.focusProperties { left = rail } else Modifier), queue,
                onClick = { keyboard?.hide(); onClick(media) }, onHold = { onHold(media) }, portrait = !tv || poster, wide = tv && !poster)
        }
        // AND-042-SKELETON: an appending page shows one row of placeholders, never copy.
        if (loading) items(columns, key = { "skeleton:$it" }) {
            Column {
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (tv && !poster) 16f / 9 else 2f / 3))
                SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth(.7f).height(12.dp), 6.dp)
            }
        }
    }
    }
}
