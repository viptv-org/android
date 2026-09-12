package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min

private val GuideCanvas = Color(0xFF101112)
private val GuideSurface = Color(0xFF202224)
private val GuideMuted = Color(0xFFA6A8AA)
private const val GuideWindowMillis = 7_200_000L
private const val GuideWidth = 804f
private const val GuideFiltersPerPage = 4

/** Canonical five-row, two-hour EPG. Controller owns paging/cache/window state. */
@Composable
internal fun GuideScreen(state: AppState, initialChannel: LiveChannel, controller: AppController) {
    val model = state.guideUi
    // `channels` is the server-selected 40-channel page. Never filter or page
    // it again in Compose: doing so loses server categories and cursor state.
    val pageChannels = model.channels.ifEmpty { state.liveChannels.ifEmpty { listOf(initialChannel) } }
    val schedules = model.schedulesByChannelId.ifEmpty { mapOf(initialChannel.id to state.guide) }
    val selectedId = model.selectedChannelId ?: initialChannel.id
    val now = System.currentTimeMillis()
    val selectedIndex = pageChannels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val firstRow = max(0, selectedIndex - 4)
    val visible = pageChannels.drop(firstRow).take(5)
    val window = model.windowStartMillis.takeIf { it > 0 } ?: floorGuideWindow(System.currentTimeMillis())
    val followsNow = model.followsNow
    var detail by remember { mutableStateOf<Pair<LiveChannel, GuideProgramme>?>(null) }
    var detailOrigin by remember { mutableStateOf<GuideDetailOrigin?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var filterPage by remember { mutableIntStateOf(0) }
    val filterItems = buildList {
        add(GuideFilterItem.Search)
        add(GuideFilterItem.Value("all", "All US channels", LiveChannelFilter.AllUs))
        add(GuideFilterItem.Value("mine", "My channels", LiveChannelFilter.MyChannels))
        add(GuideFilterItem.Value("recent", "Recent", LiveChannelFilter.Recent))
        model.categories.forEach { category ->
            add(GuideFilterItem.Value("category:${category.id}", category.name, LiveChannelFilter.Category(category.id)))
        }
    }
    val filterFocus = remember(filterItems.map { it.key }) { filterItems.associate { it.key to FocusRequester() } }
    val initialFocus = filterFocus.getValue(GuideFilterItem.Search.key)
    val filterPageCount = max(1, (filterItems.size + GuideFiltersPerPage - 1) / GuideFiltersPerPage)
    val shownFilters = filterItems.drop(filterPage * GuideFiltersPerPage).take(GuideFiltersPerPage)
    val visibleCellKeys = visible.flatMap { channel ->
        guideCells(schedules[channel.id].orEmpty(), window).mapIndexed { index, cell ->
            guideCellKey(channel, cell.programme, index)
        }
    }
    val cellFocus = remember(visibleCellKeys) { visibleCellKeys.associateWith { FocusRequester() } }

    fun closeDetail() {
        detail = null
    }

    LaunchedEffect(initialChannel.id) { initialFocus.requestFocus() }
    LaunchedEffect(filterItems.size) { filterPage = filterPage.coerceIn(0, filterPageCount - 1) }
    LaunchedEffect(filterPage, shownFilters.firstOrNull()?.key) {
        shownFilters.firstOrNull()?.let { filterFocus.getValue(it.key).requestFocus() }
    }
    LaunchedEffect(searchOpen) {
        if (!searchOpen) filterFocus.getValue(GuideFilterItem.Search.key).requestFocus()
    }
    LaunchedEffect(detail == null, cellFocus) {
        if (detail != null) return@LaunchedEffect
        val origin = detailOrigin ?: return@LaunchedEffect
        detailOrigin = null
        (cellFocus[origin.cellKey] ?: cellFocus.entries.firstOrNull { it.key.startsWith("${origin.channelId}:") }?.value ?: initialFocus).requestFocus()
    }

    Box(Modifier.fillMaxSize().background(GuideCanvas)) {
        Text("Live TV", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(104.dp, 98.dp))
        shownFilters.forEachIndexed { index, item ->
            GuideButton(
                item.label,
                Modifier.offset(104.dp, (166 + index * 52).dp).width(184.dp).height(42.dp).focusRequester(filterFocus.getValue(item.key)),
                selected = item.matches(model.channelFilter),
            ) {
                when (item) {
                    GuideFilterItem.Search -> searchOpen = true
                    is GuideFilterItem.Value -> controller.setGuideFilter(item.filter)
                }
            }
        }
        GuideButton("Now", Modifier.offset(104.dp, 442.dp).width(184.dp).height(42.dp), selected = followsNow) { controller.followGuideNow() }
        GuideButton("Earlier hour", Modifier.offset(104.dp, 494.dp).width(184.dp).height(42.dp)) { controller.shiftGuideWindow(-1) }
        GuideButton("Later hour", Modifier.offset(104.dp, 546.dp).width(184.dp).height(42.dp)) { controller.shiftGuideWindow(1) }
        if (filterPage > 0) GuideButton("Prev", Modifier.offset(104.dp, 598.dp).width(88.dp).height(42.dp)) { filterPage-- }
        if (filterPage + 1 < filterPageCount) GuideButton("More", Modifier.offset(200.dp, 598.dp).width(88.dp).height(42.dp)) { filterPage++ }
        if (model.channelOffset > 0) GuideButton("Previous", Modifier.offset(104.dp, 650.dp).width(88.dp).height(42.dp)) { controller.changeGuidePage(-1) }
        if (model.channelOffset + pageChannels.size < model.channelTotal) GuideButton("Next", Modifier.offset(200.dp, 650.dp).width(88.dp).height(42.dp)) { controller.changeGuidePage(1) }

        repeat(4) { index ->
            val hour = window + index * 1_800_000L
            Text(guideTime(hour), color = GuideMuted, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.offset((432 + 201 * index).dp, 138.dp).width(197.dp))
        }
        visible.forEachIndexed { row, channel ->
            val y = 166 + row * 91
            GuideChannelCell(channel, selected = channel.id == selectedId, modifier = Modifier.offset(308.dp, (y + 7).dp))
            val schedule = schedules[channel.id] ?: emptyList()
            GuideProgrammeRow(
                channel = channel,
                programmes = schedule,
                windowStart = window,
                y = y,
                selected = channel.id == selectedId,
                onFocus = { controller.selectGuideChannel(channel) },
                focusFor = { programme, index -> cellFocus.getValue(guideCellKey(channel, programme, index)) },
                onActivate = { programme, index ->
                    if (programme.startMillis <= now && programme.endMillis > now) controller.watchGuideChannel(channel)
                    else {
                        detailOrigin = GuideDetailOrigin(guideCellKey(channel, programme, index), channel.id)
                        detail = channel to programme
                    }
                },
                onWatch = { controller.watchGuideChannel(channel) },
            )
        }
        if (now in window until window + GuideWindowMillis) {
            val x = 432 + ((now - window).toFloat() / GuideWindowMillis * GuideWidth).toInt()
            Box(Modifier.offset(x.dp, 150.dp).width(2.dp).height(470.dp).background(Color(0x66FFFFFF)))
        }
        Text("${if (followsNow) "Following now" else "Time shifted"} · ${visible.size} channels${model.searchScope?.let { " · $it" }.orEmpty()}", color = GuideMuted, fontSize = 15.sp, modifier = Modifier.offset(432.dp, 650.dp).width(804.dp), textAlign = TextAlign.Center)
        detail?.let { (channel, programme) ->
            GuideDetail(channel, programme, onWatch = { controller.watchGuideChannel(channel) }, onClose = ::closeDetail)
        }
        if (searchOpen) GuideSearchEntry(
            initial = (model.channelFilter as? LiveChannelFilter.Search)?.query.orEmpty(),
            onSubmit = { query -> controller.setGuideSearch(query); searchOpen = false },
            onClose = { searchOpen = false },
        )
    }
}

@Composable
private fun GuideChannelCell(channel: LiveChannel, selected: Boolean, modifier: Modifier) = Box(
    modifier.width(112.dp).height(73.dp).background(if (selected) Color(0xFF303234) else GuideSurface, RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center,
) {
    if (!channel.logo.isNullOrBlank()) AsyncImage(model = channel.logo, contentDescription = channel.name, contentScale = ContentScale.Fit, modifier = Modifier.size(96.dp))
    else Text(channel.name, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
}

@Composable
private fun GuideProgrammeRow(channel: LiveChannel, programmes: List<GuideProgramme>, windowStart: Long, y: Int, selected: Boolean, onFocus: () -> Unit, focusFor: (GuideProgramme, Int) -> FocusRequester, onActivate: (GuideProgramme, Int) -> Unit, onWatch: () -> Unit) {
    val cells = guideCells(programmes, windowStart)
    cells.forEachIndexed { index, cell ->
        val x = 432 + (cell.left * GuideWidth).toInt()
        val width = max(1, (cell.width * GuideWidth).toInt() - 3)
        GuideCell(
            programme = cell.programme,
            selected = selected,
            modifier = Modifier.offset(x.dp, y.dp).width(width.dp).height(87.dp).focusRequester(focusFor(cell.programme, index)),
            onFocus = onFocus,
            onActivate = { onActivate(cell.programme, index) },
            onWatch = onWatch,
        )
    }
}

@Composable
private fun GuideCell(programme: GuideProgramme, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onActivate: () -> Unit, onWatch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(if (focused) Color(0xFFF5F5F5) else GuideSurface)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) { onWatch(); true } else false
            }
            .clickable(onClick = onActivate)
            .padding(10.dp),
    ) {
        Text(programme.title, color = if (focused) GuideCanvas else Color.White, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private data class GuideCellModel(val programme: GuideProgramme, val left: Float, val width: Float)
private data class GuideDetailOrigin(val cellKey: String, val channelId: String)
private fun guideCellKey(channel: LiveChannel, programme: GuideProgramme, index: Int) = "${channel.id}:${programme.startMillis}:${programme.endMillis}:$index"

private sealed interface GuideFilterItem {
    val key: String
    val label: String

    data object Search : GuideFilterItem {
        override val key = "search"
        override val label = "Search Live TV"
    }

    data class Value(override val key: String, override val label: String, val filter: LiveChannelFilter) : GuideFilterItem
}

private fun GuideFilterItem.matches(active: LiveChannelFilter): Boolean = when (this) {
    GuideFilterItem.Search -> active is LiveChannelFilter.Search
    is GuideFilterItem.Value -> filter == active
}

private fun guideCells(programmes: List<GuideProgramme>, start: Long): List<GuideCellModel> {
    val end = start + GuideWindowMillis
    val sorted = programmes.filter { it.endMillis > start && it.startMillis < end }.sortedBy(GuideProgramme::startMillis)
    val bounded = ArrayList<GuideCellModel>(32)
    var cursor = start
    for (programme in sorted) {
        if (bounded.size >= 32) break
        if (programme.startMillis > cursor) bounded += guideCell(GuideProgramme("No schedule available", cursor, min(programme.startMillis, end)), start)
        bounded += guideCell(programme.copy(startMillis = max(programme.startMillis, start), endMillis = min(programme.endMillis, end)), start)
        cursor = max(cursor, programme.endMillis)
    }
    if (cursor < end && bounded.size < 32) bounded += guideCell(GuideProgramme("No schedule available", cursor, end), start)
    return bounded
}
private fun guideCell(programme: GuideProgramme, start: Long) = GuideCellModel(programme, (programme.startMillis - start).toFloat() / GuideWindowMillis, (programme.endMillis - programme.startMillis).toFloat() / GuideWindowMillis)
private fun floorGuideWindow(now: Long) = now / GuideWindowMillis * GuideWindowMillis
private fun guideTime(millis: Long): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(millis))

@Composable
private fun GuideButton(label: String, modifier: Modifier, selected: Boolean = false, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.background(if (focused) Color.White else if (selected) Color(0xFF303234) else Color.Transparent, RoundedCornerShape(21.dp)).onFocusChanged { focused = it.hasFocus }.clickable(onClick = onActivate), contentAlignment = Alignment.Center) {
        Text(label, color = if (focused) GuideCanvas else Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A separate, capped Guide text entry. Submitting delegates trim/filter policy to the controller. */
@Composable
private fun GuideSearchEntry(initial: String, onSubmit: (String) -> Unit, onClose: () -> Unit) {
    var query by remember(initial) { mutableStateOf(initial.take(128)) }
    val firstKey = remember { FocusRequester() }
    val keys = remember { ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").map(Char::toString) + listOf("Space", "Delete", "Clear") }
    fun edit(next: String) { query = next.take(128) }

    BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color(0xDC080909)).onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.keyCode == KeyEvent.KEYCODE_BACK) false
            else if (native.action != KeyEvent.ACTION_DOWN) true
            else when {
                native.keyCode == KeyEvent.KEYCODE_DEL -> { edit(query.dropLast(1)); true }
                native.unicodeChar in 32..126 -> { edit(query + native.unicodeChar.toChar()); true }
                else -> false
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(720.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF191B1D)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Search Live TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Search US channels, sections, or a programme title.", color = GuideMuted, fontSize = 17.sp)
            Box(Modifier.width(656.dp).height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303234)), contentAlignment = Alignment.CenterStart) {
                Text(query.ifBlank { "Enter a channel or programme" }, color = if (query.isBlank()) GuideMuted else Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                keys.chunked(7).forEachIndexed { row, entries ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEachIndexed { column, key ->
                            val index = row * 7 + column
                            GuideButton(key, Modifier.width(if (key.length > 1) 116.dp else 72.dp).height(38.dp).then(if (index == 0) Modifier.focusRequester(firstKey) else Modifier)) {
                                edit(
                                    when (key) {
                                        "Space" -> query + " "
                                        "Delete" -> query.dropLast(1)
                                        "Clear" -> ""
                                        else -> query + key
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideButton("Search", Modifier.width(180.dp).height(48.dp), onActivate = { onSubmit(query) })
                GuideButton("Cancel", Modifier.width(180.dp).height(48.dp), onActivate = onClose)
            }
        }
    }
    LaunchedEffect(Unit) { firstKey.requestFocus() }
}

@Composable
private fun GuideDetail(channel: LiveChannel, programme: GuideProgramme, onWatch: () -> Unit, onClose: () -> Unit) {
    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(programme.startMillis, channel.id) { watchFocus.requestFocus() }
    BackHandler(onBack = onClose)
    // A guide detail is deliberately a modal remote state: OK/Play watches the
    // channel and Back returns to the exact programme cell. Directional and
    // other player keys must not leak into the guide below it.
    Box(
        Modifier.fillMaxSize().background(Color(0xDC080909)).onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when {
                key.keyCode == KeyEvent.KEYCODE_BACK -> false
                key.action == KeyEvent.ACTION_DOWN && key.keyCode in setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                ) -> {
                    onWatch()
                    true
                }
                else -> true
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(880.dp).background(Color(0xFF191B1D), RoundedCornerShape(12.dp)).padding(44.dp)) {
            androidx.compose.foundation.layout.Column {
                Text(programme.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(channel.name, color = GuideMuted, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                Text(programme.description ?: "This programme has not started yet.", color = Color(0xFFD5D6D7), fontSize = 19.sp, modifier = Modifier.padding(top = 20.dp))
                GuideButton("Watch channel", Modifier.padding(top = 30.dp).width(220.dp).height(52.dp).focusRequester(watchFocus), onActivate = onWatch)
                GuideButton("Close", Modifier.padding(top = 12.dp).width(220.dp).height(52.dp), onActivate = onClose)
            }
        }
    }
}
