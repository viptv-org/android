package org.viptv.app

import android.view.KeyEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun GuideScreen(state: AppState, channel: LiveChannel?, controller: AppController) {
    val tv = LocalTv.current
    val ui = state.guideUi
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val focusManager = LocalFocusManager.current
    val rows = rememberLazyListState(initialFirstVisibleItemIndex = ui.visibleFirst, initialFirstVisibleItemScrollOffset = ui.visibleScrollOffset)
    val categoryPage = ui.categoryPage
    val categoryRows = rememberLazyListState(
        initialFirstVisibleItemIndex = categoryPage.rowFirstIndex,
        initialFirstVisibleItemScrollOffset = categoryPage.rowScrollOffset)
    val categoryFocus = remember(ui.categories.map { it.id }) { ui.categories.associate { it.id to FocusRequester() } }
    val categoryFixedFocus = remember { listOf("all", "my", "recent", "search").associate { "fixed:$it" to FocusRequester() } }
    var categoryFocusedKey by remember(state.selectedProfile?.id, ui.catalogId, ui.generation) { mutableStateOf<String?>(null) }
    val categoryReturnFocusKey = remember(state.selectedProfile?.id, ui.catalogId, ui.generation) { categoryPage.rowFocusKey }
    var categoryReturnPending by remember(state.selectedProfile?.id, ui.catalogId, ui.generation) { mutableStateOf(categoryReturnFocusKey != null) }
    fun categoryRequester(key: String?) = categoryFixedFocus[key] ?: key?.removePrefix("category:")?.let { categoryFocus[it] }
    val categoryUpFocus = categoryRows.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
        (item.key as? String)?.let(::categoryRequester)
    } ?: FocusRequester.Default
    var categoryTerminal by remember(state.selectedProfile?.id, ui.catalogId, ui.generation) { mutableStateOf<String?>(null) }
    var categoryAnchorCursor by remember(state.selectedProfile?.id, ui.catalogId, ui.generation) { mutableStateOf<String?>(null) }
    var categoryObservedRevision by remember { mutableLongStateOf(-1) }
    var categoryRestoringRevision by remember { mutableLongStateOf(-1) }
    var categoryEdgeKey by remember { mutableIntStateOf(-1) }
    var categoryGestureRequested by remember { mutableStateOf(false) }
    var categoryOverscroll by remember { mutableFloatStateOf(0f) }
    val categoryBoundaryDistance = with(LocalDensity.current) { 32.dp.toPx() }
    fun moveCategories(delta: Int): Boolean {
        if (categoryObservedRevision != categoryPage.revision || categoryPage.loading || categoryPage.awaitingAnchor) return false
        val cursor = (if (delta < 0) categoryPage.previousCursor else categoryPage.nextCursor) ?: return false
        if (categoryPage.error != null) {
            if (categoryAnchorCursor != cursor) return false
            controller.retryGuideCategories()
        } else {
            categoryAnchorCursor = cursor
            controller.changeGuideCategoryPage(delta, categoryPage.revision)
        }
        return true
    }
    val categoryScroll = remember(categoryPage.revision, categoryPage.loading, categoryPage.awaitingAnchor, categoryPage.error, tv) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (tv || source != NestedScrollSource.UserInput || categoryGestureRequested) return Offset.Zero
                val delta = guideCategoryOverscrollDirection(available.x, categoryRows.canScrollBackward, categoryRows.canScrollForward)
                if (delta == 0 || consumed.x != 0f) { categoryOverscroll = 0f; return Offset.Zero }
                if (categoryOverscroll * available.x < 0f) categoryOverscroll = 0f
                categoryOverscroll += available.x
                if (kotlin.math.abs(categoryOverscroll) >= categoryBoundaryDistance && moveCategories(delta)) categoryGestureRequested = true
                return Offset.Zero
            }
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var search by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<LiveChannel, GuideProgramme?>?>(null) }
    val selected = ui.channels.firstOrNull { it.id == ui.selectedChannelId } ?: channel
    val programme = selected?.let { ui.schedulesByChannelId[it.id]?.firstOrNull { item -> item.startMillis <= now && item.endMillis > now } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(ui.channels.isNotEmpty()) { if (tv && !categoryReturnPending) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    LaunchedEffect(state.loading, categoryPage.loaded, categoryPage.awaitingAnchor) {
        if (!tv || !categoryReturnPending || state.loading || !categoryPage.loaded || categoryPage.awaitingAnchor) return@LaunchedEffect
        val key = categoryReturnFocusKey ?: return@LaunchedEffect
        val index = guideCategoryRowKeyIndex(ui.categories, key)
        if (index == null) {
            categoryReturnPending = false
            withFrameNanos {}; runCatching { first.requestFocus() }
            return@LaunchedEffect
        }
        if (categoryRows.layoutInfo.visibleItemsInfo.none { it.key == key }) categoryRows.scrollToItem(index)
        withFrameNanos {}
        runCatching { categoryRequester(key)?.requestFocus() }
        withFrameNanos {}
        if (categoryFocusedKey == key) categoryReturnPending = false
    }
    LaunchedEffect(categoryPage.revision, categoryPage.awaitingAnchor, categoryPage.loading, state.selectedProfile?.id, ui.catalogId, ui.generation) {
        if (categoryPage.loading || !categoryPage.awaitingAnchor || ui.categories.isEmpty()) return@LaunchedEffect
        val renderedRevision = categoryPage.revision
        categoryRestoringRevision = renderedRevision
        val index = categoryPage.focusIndex.coerceIn(ui.categories.indices)
        if (!tv) {
            // A fast response must not race the still-held drag or its fling.
            snapshotFlow { !categoryRows.isScrollInProgress && !search && detail == null }.first { it }
            focusManager.clearFocus(force = true)
        }
        categoryRows.scrollToItem(if (categoryPage.cursor == null && index == 0) 0 else index + 3)
        withFrameNanos {}
        val key = "category:${ui.categories[index].id}"
        if (tv && categoryPage.cursor != null) {
            repeat(3) {
                if (categoryFocusedKey != key) runCatching { categoryRequester(key)?.requestFocus() }
                withFrameNanos {}
            }
            if (!guideCategoryAnchorFocused(ui.categories, index, categoryFocusedKey)) return@LaunchedEffect
        }
        categoryAnchorCursor = null
        if (categoryRestoringRevision == renderedRevision) categoryRestoringRevision = -1
    }
    LaunchedEffect(categoryFocusedKey, categoryPage.revision, categoryPage.awaitingAnchor) {
        if (categoryRestoringRevision != categoryPage.revision || !categoryPage.awaitingAnchor) return@LaunchedEffect
        if (guideCategoryAnchorFocused(ui.categories, categoryPage.focusIndex, categoryFocusedKey)) {
            categoryAnchorCursor = null
            categoryRestoringRevision = -1 // Manual focus can recover a failed automatic request.
        }
    }
    LaunchedEffect(categoryRows, categoryPage.revision, categoryPage.loading, ui.categories) {
        if (categoryPage.loading) return@LaunchedEffect
        val renderedRevision = categoryPage.revision
        val renderedCategories = ui.categories
        snapshotFlow { Triple(categoryRestoringRevision, categoryFocusedKey, categoryRows.layoutInfo.visibleItemsInfo.map { Triple(it.index, it.key, it.offset) }) to
            (categoryRows.firstVisibleItemIndex to categoryRows.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().collect { (viewport, position) ->
                val (restoring, focused, visible) = viewport
                val (firstIndex, firstOffset) = position
                if (restoring == renderedRevision) return@collect
                visible.firstOrNull { it.first == firstIndex }?.let { (index, key, _) ->
                    (key as? String)?.let { controller.onGuideCategoryRowViewport(renderedRevision, it, index, firstOffset, focused) }
                }
                guideCategoryRowViewport(renderedCategories, visible)?.let { (firstId, lastId, offset) ->
                    categoryObservedRevision = renderedRevision
                    controller.onGuideCategoryViewport(renderedRevision, firstId, lastId, offset, allowPaging = false)
                }
            }
    }
    LaunchedEffect(categoryRows) {
        snapshotFlow { categoryRows.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling) { categoryGestureRequested = false; categoryOverscroll = 0f }
        }
    }
    LaunchedEffect(rows, ui.channels, ui.paging, state.loading) {
        snapshotFlow { Triple(rows.firstVisibleItemIndex, rows.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1, rows.firstVisibleItemScrollOffset) }.distinctUntilChanged().collect { (first, last, offset) ->
            if (last >= 0) controller.onGuideViewport(first, last, offset)
        }
    }
    if (!tv) LaunchedEffect(rows, ui.channels) {
        snapshotFlow { rows.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            ui.channels.getOrNull(index)?.let { controller.selectGuideChannel(it) }
        }
    }
    Column(Modifier.fillMaxSize().padding(start = measure(192, 16), end = measure(96, 16), top = measure(54, 12), bottom = measure(54, 0))) {
        if (tv) {
            VText("LIVE", 18, color = C.statusLive, bold = true)
            VText(programme?.title ?: selected?.name ?: "Live TV", 56, Modifier.padding(top = 14.dp), display = true, lines = 1)
            VText(selected?.name.orEmpty() + (programme?.let { " · " + programmeTime(it) } ?: ""), 22, Modifier.padding(top = 18.dp), C.textSecondary)
            VText(programme?.description ?: "No guide information. You can still watch this channel.", 26, Modifier.padding(top = 24.dp).widthIn(max = 1100.dp).height(76.dp), C.textBody, lines = 2)
            Spacer(Modifier.height(36.dp))
        } else ScreenHeader("Live TV", trailing = { PhoneTabActions(state, controller) })
        LazyRow(Modifier.fillMaxWidth().padding(bottom = measure(30, 22)).onFocusChanged { if (!it.hasFocus) categoryFocusedKey = null }.focusGroup().nestedScroll(categoryScroll).onPreviewKeyEvent { event ->
            if (!tv) return@onPreviewKeyEvent false
            val key = event.nativeKeyEvent
            if (key.keyCode == categoryEdgeKey) {
                if (key.action == KeyEvent.ACTION_UP) categoryEdgeKey = -1
                return@onPreviewKeyEvent true
            }
            if (key.action != KeyEvent.ACTION_DOWN || key.repeatCount != 0) return@onPreviewKeyEvent false
            val direction = when (key.keyCode) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
            val delta = guideCategoryTerminalDirection(categoryTerminal, direction)
            if (delta == 0 || !moveCategories(delta)) false else { categoryEdgeKey = key.keyCode; true }
        }, state = categoryRows, horizontalArrangement = Arrangement.spacedBy(measure(14, 8))) {
            item(key = "fixed:all") { AppChip("All", { controller.setGuideFilter(LiveChannelFilter.AllUs) }, ui.channelFilter == LiveChannelFilter.AllUs, Modifier.focusRequester(categoryFixedFocus.getValue("fixed:all")).onFocusChanged { if (it.isFocused) { categoryTerminal = "all"; categoryFocusedKey = "fixed:all" } }.then(if (tv && ui.channels.isEmpty()) Modifier.focusRequester(first).focusProperties { left = rail } else Modifier)) }
            item(key = "fixed:my") { AppChip("My channels", { controller.setGuideFilter(LiveChannelFilter.MyChannels) }, ui.channelFilter == LiveChannelFilter.MyChannels, Modifier.focusRequester(categoryFixedFocus.getValue("fixed:my")).onFocusChanged { if (it.isFocused) { categoryTerminal = null; categoryFocusedKey = "fixed:my" } }) }
            item(key = "fixed:recent") { AppChip("Recent", { controller.setGuideFilter(LiveChannelFilter.Recent) }, ui.channelFilter == LiveChannelFilter.Recent, Modifier.focusRequester(categoryFixedFocus.getValue("fixed:recent")).onFocusChanged { if (it.isFocused) { categoryTerminal = null; categoryFocusedKey = "fixed:recent" } }) }
            items(ui.categories, key = { "category:${it.id}" }) { category -> AppChip(category.name, { controller.setGuideFilter(LiveChannelFilter.Category(category.id)) }, ui.channelFilter == LiveChannelFilter.Category(category.id), Modifier.focusRequester(categoryFocus.getValue(category.id)).onFocusChanged { if (it.isFocused) { categoryTerminal = null; categoryFocusedKey = "category:${category.id}" } }) }
            item(key = "fixed:search") { AppChip("Search channels", { search = true }, modifier = Modifier.focusRequester(categoryFixedFocus.getValue("fixed:search")).onFocusChanged { if (it.isFocused) { categoryTerminal = "search"; categoryFocusedKey = "fixed:search" } }) }
        }
        if (tv) Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            VText("Channels", 22, Modifier.width(272.dp), C.textSecondary)
            repeat(4) { offset -> VText(clockLabel(ui.windowStartMillis + offset * 30 * 60_000L), 20, Modifier.weight(1f), C.textSecondary) }
            AppChip("Earlier", { controller.shiftGuideWindow(-1) })
            AppChip("Now", controller::followGuideNow)
            AppChip("Later", { controller.shiftGuideWindow(1) })
        }
        if (ui.channels.isEmpty() && state.loading && !tv) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // AND-042-SKELETON: phone channel-row placeholders.
            repeat(6) { Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(62.dp), 16.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(.4f).height(12.dp), 6.dp)
                    SkeletonBlock(Modifier.fillMaxWidth(.8f).height(16.dp), 6.dp)
                    SkeletonBlock(Modifier.fillMaxWidth().height(4.dp), 2.dp)
                }
            } }
        }
        else if (ui.channels.isEmpty()) EmptyState(if (state.loading) "Finding channels…" else "No channels here yet.", "Choose another category or search.", "live", retry = if (state.loading) null else controller::retryGuidePage)
        else LazyColumn(state = rows, contentPadding = PaddingValues(bottom = measure(0, 160)), verticalArrangement = Arrangement.spacedBy(measure(10, 16))) {
            itemsIndexed(ui.channels, key = { _, item -> item.id }) { index, item ->
                val schedule = ui.schedulesByChannelId[item.id].orEmpty()
                val current = schedule.firstOrNull { it.startMillis <= now && it.endMillis > now }
                if (tv) Row(Modifier.fillMaxWidth().height(94.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    var focused by remember(item.id) { mutableStateOf(false) }
                    Holdable({ controller.watchGuideChannel(item) }, { detail = item to current },
                        Modifier.width(266.dp).fillMaxHeight().then(if (item.id == ui.selectedChannelId) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { left = rail; if (index == rows.firstVisibleItemIndex) up = categoryUpFocus }.onFocusChanged { focused = it.isFocused; if (focused) controller.selectGuideChannel(item) }
                            .clip(RoundedCornerShape(16.dp)).background(if (focused) C.textPrimary else C.surfaceN1)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ChannelLogo(item, Modifier.size(56.dp), focused)
                            VText(item.name, 22, color = if (focused) C.onLight else C.textPrimary, bold = true, lines = 2)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val start = ui.windowStartMillis.takeIf { it > 0 } ?: GuidePolicy.nowWindow(now)
                        val window = 2 * 60 * 60_000L
                        val visible = schedule.filter { it.endMillis > start && it.startMillis < start + window }
                        if (visible.isEmpty()) ProgrammeBlock("No guide information", item, null, Modifier.fillMaxSize(), { controller.watchGuideChannel(item) }, { detail = item to null }, controller)
                        else visible.forEach { entry ->
                            val x = maxWidth * ((entry.startMillis - start).coerceAtLeast(0).toFloat() / window)
                            val w = maxWidth * ((minOf(entry.endMillis, start + window) - maxOf(entry.startMillis, start)).toFloat() / window)
                            ProgrammeBlock(entry.title, item, entry, Modifier.offset(x = x).width((w - 6.dp).coerceAtLeast(40.dp)).fillMaxHeight(),
                                { if (entry.startMillis <= now && entry.endMillis > now) controller.watchGuideChannel(item) else detail = item to entry },
                                { detail = item to entry }, controller)
                        }
                    }
                } else Holdable({ controller.watchGuideChannel(item) }, { detail = item to current }, Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(item, Modifier.size(62.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            VText(item.name, 12, color = C.textSecondary, lines = 1)
                            VText(current?.title ?: "No guide information", 16, bold = true, lines = 1)
                            val progress = current?.let { ((now - it.startMillis).toFloat() / (it.endMillis - it.startMillis).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
                            ProgressLine(progress)
                            val next = schedule.firstOrNull { it.startMillis > now }
                            VText(next?.let { "Next " + clockLabel(it.startMillis) + " · " + it.title } ?: "Watch live", 12, color = C.textTertiary, lines = 1)
                        }
                        Holdable({ detail = item to current }, modifier = Modifier.size(44.dp)) { VIcon("more", "Programme details") }
                    }
                }
            }
        }
    }
    if (search) TextEntry("Search live TV", "Search channels", onDone = { controller.setGuideSearch(it); search = false }, onCancel = { search = false })
    detail?.let { (item, entry) -> AppOverlay(entry?.title ?: "Live TV", { detail = null }) {
        VText(item.name + (entry?.let { " · " + programmeTime(it) } ?: ""), if (tv) 24 else 14, color = C.textSecondary)
        VText(entry?.description ?: "No guide information. You can still watch this channel.", if (tv) 26 else 16, Modifier.padding(vertical = measure(32, 24)), C.textBody)
        AppButton("Watch live", { detail = null; controller.watchGuideChannel(item) }, Modifier.fillMaxWidth(), "play", primary = !tv)
    } }
}

@Composable private fun ProgrammeBlock(title: String, channel: LiveChannel, programme: GuideProgramme?, modifier: Modifier, onClick: () -> Unit, onHold: () -> Unit, controller: AppController) {
    var focused by remember(channel.id, programme?.startMillis) { mutableStateOf(false) }
    Holdable(onClick, onHold, modifier.onFocusChanged { focused = it.isFocused; if (focused) controller.selectGuideChannel(channel) }
        .clip(RoundedCornerShape(16.dp)).background(if (focused) C.textPrimary else C.guideAiring)) {
        VText(title, 22, Modifier.fillMaxWidth().padding(16.dp), if (focused) C.onLight else C.textPrimary, bold = true, lines = 2)
    }
}

@Composable private fun ChannelLogo(channel: LiveChannel, modifier: Modifier, focused: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(C.surfaceN1), contentAlignment = Alignment.Center) {
        if (!channel.logo.isNullOrBlank()) Artwork(channel.logo, channel.name, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
        else VText(channel.name.split(" ").mapNotNull { it.firstOrNull() }.take(3).joinToString(""), if (LocalTv.current) 22 else 13, color = C.textPrimary, bold = true, lines = 1)
    }
}
private fun clockLabel(value: Long) = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(value))
private fun programmeTime(value: GuideProgramme) = value.displayTime ?: clockLabel(value.startMillis) + " – " + clockLabel(value.endMillis)

internal fun guideCategoryTerminalDirection(terminal: String?, direction: Int): Int = when {
    terminal == "all" && direction < 0 -> -1
    terminal == "search" && direction > 0 -> 1
    else -> 0
}
internal fun guideCategoryOverscrollDirection(availableX: Float, canScrollBackward: Boolean, canScrollForward: Boolean): Int = when {
    availableX > 0 && !canScrollBackward -> -1
    availableX < 0 && !canScrollForward -> 1
    else -> 0
}
internal fun guideCategoryRowViewport(categories: List<LiveCategory>, visible: List<Triple<Int, Any, Int>>): Triple<String, String, Int>? {
    val items = visible.mapNotNull { (index, key, offset) -> categories.getOrNull(index - 3)?.takeIf { key == "category:${it.id}" }?.let { it.id to offset } }
    if (items.isEmpty()) return null
    return Triple(items.first().first, items.last().first, (-items.first().second).coerceAtLeast(0))
}
internal fun guideCategoryRowKeyIndex(categories: List<LiveCategory>, key: String): Int? = when (key) {
    "fixed:all" -> 0
    "fixed:my" -> 1
    "fixed:recent" -> 2
    "fixed:search" -> categories.size + 3
    else -> categories.indexOfFirst { key == "category:${it.id}" }.takeIf { it >= 0 }?.plus(3)
}
internal fun guideCategoryAnchorFocused(categories: List<LiveCategory>, index: Int, focusedKey: String?): Boolean =
    categories.getOrNull(index)?.let { focusedKey == "category:${it.id}" } == true
