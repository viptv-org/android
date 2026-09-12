package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private val GuideCanvas = Color(0xFF101112)
private val GuideSurface = Color(0xFF202224)
private val GuideMuted = Color(0xFFA6A8AA)
private const val GuideWindowMillis = 7_200_000L
private const val GuideWidth = 804f

/** One remote focus owner mirrors EpgGrid's row, time-anchor and filter cursor. */
@Composable
internal fun GuideScreen(state: AppState, initialChannel: LiveChannel?, controller: AppController) {
    val model = state.guideUi
    val channels = model.channels.ifEmpty { state.liveChannels }
    val schedules = model.schedulesByChannelId.ifEmpty { initialChannel?.let { mapOf(it.id to state.guide) }.orEmpty() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var anchor by remember { mutableLongStateOf(now) }
    var menuFocus by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableIntStateOf(1) }
    var detail by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var endOfPage by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val railFocus = LocalRokuRailFocus.current
    val followsNow by rememberUpdatedState(model.followsNow)
    val row = channels.indexOfFirst { it.id == model.selectedChannelId }.coerceAtLeast(0)
    val channel = channels.getOrNull(row)
    val window = model.windowStartMillis.takeIf { it > 0 } ?: floorGuideWindow(now)
    val cells = guideCells(schedules[channel?.id].orEmpty(), window)
    val selectedCell = cells.indexOfFirst { anchor >= it.programme.startMillis && anchor < it.programme.endMillis }.let { if (it < 0) 0 else it }
    val programme = cells.getOrNull(selectedCell)?.programme
    val filters = buildList {
        add(GuideFilterItem.Search)
        add(GuideFilterItem.Value("all", "All US channels", LiveChannelFilter.AllUs))
        add(GuideFilterItem.Value("mine", "My channels", LiveChannelFilter.MyChannels))
        add(GuideFilterItem.Value("recent", "Recent", LiveChannelFilter.Recent))
        model.categories.forEach { add(GuideFilterItem.Value("category:${it.id}", it.name, LiveChannelFilter.Category(it.id))) }
    }
    fun applyFilter() {
        when (val item = filters[menuIndex.coerceIn(0, filters.lastIndex)]) {
            GuideFilterItem.Search -> searchOpen = true
            is GuideFilterItem.Value -> { menuFocus = false; if (!item.matches(model.channelFilter)) controller.setGuideFilter(item.filter) }
        }
    }
    fun activate() {
        if (menuFocus) applyFilter()
        else if (channel == null) controller.retryGuidePage()
        else if (programme != null && programme.startMillis <= now) controller.watchGuideChannel(channel)
        else detail = true
    }
    LaunchedEffect(Unit) { focus.requestFocus(); while (true) { delay(30_000); now = System.currentTimeMillis(); if (followsNow) controller.followGuideNow() } }
    LaunchedEffect(model.followsNow, model.windowStartMillis) { if (model.followsNow) anchor = System.currentTimeMillis() }
    LaunchedEffect(model.channelOffset) { if (endOfPage) { channels.lastOrNull()?.let(controller::selectGuideChannel); endOfPage = false } }
    LaunchedEffect(searchOpen) { if (!searchOpen) focus.requestFocus() }
    BackHandler(detail) { detail = false }
    Box(Modifier.fillMaxSize().background(GuideCanvas).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (searchOpen) return@onPreviewKeyEvent false
        if (key.keyCode == KeyEvent.KEYCODE_BACK) {
            if (detail) { if (key.action == KeyEvent.ACTION_UP) detail = false; return@onPreviewKeyEvent true }
            return@onPreviewKeyEvent false
        }
        if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
        if (detail) {
            if (key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == KeyEvent.KEYCODE_ENTER) channel?.let(controller::watchGuideChannel)
            return@onPreviewKeyEvent true
        }
        when (key.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { controller.followGuideNow(); anchor = now; menuFocus = false }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { controller.shiftGuideWindow(-1); anchor = max(floorGuideWindow(now), window - 3_600_000) }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { controller.shiftGuideWindow(1); anchor = window + 3_600_000 }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> channel?.let(controller::watchGuideChannel)
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> if (channel != null) detail = true
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val direction = if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                if (menuFocus) menuIndex = (menuIndex + direction).coerceIn(0, filters.lastIndex)
                else {
                    val next = row + direction
                    if (next in channels.indices) controller.selectGuideChannel(channels[next])
                    else if ((direction < 0 && model.channelOffset > 0) || (direction > 0 && model.channelOffset + channels.size < model.channelTotal)) {
                        endOfPage = direction < 0; controller.changeGuidePage(direction)
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val left = key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                if (menuFocus) { if (left) railFocus.requestFocus() else applyFilter() }
                else if (channel == null) { if (left) menuFocus = true }
                else {
                    val next = selectedCell + if (left) -1 else 1
                    if (next in cells.indices) { anchor = cells[next].programme.startMillis; controller.shiftGuideWindow(0) }
                    else if (left && window <= floorGuideWindow(now)) menuFocus = true
                    else { controller.shiftGuideWindow(if (left) -1 else 1); anchor = if (left) window - 1 else window + GuideWindowMillis }
                }
            }
            else -> return@onPreviewKeyEvent false
        }
        true
    }.focusRequester(focus).focusable()) {
        Text("Live TV", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(112.dp, 34.dp).width(220.dp))
        Box(Modifier.offset(112.dp,149.dp).size(1124.dp,1.dp).background(Color(0xFF303234)))
        repeat(4) { i -> Text(guideTime(window + i * 1_800_000L), color = Color.White, fontSize = 19.sp, modifier = Modifier.offset((432 + i * 201).dp,116.dp).width(197.dp)) }
        val firstMenu = max(0, menuIndex - 7)
        filters.drop(firstMenu).take(8).forEachIndexed { slot, item ->
            val selected = slot + firstMenu == menuIndex
            Box(Modifier.offset(104.dp,(163 + slot * 48).dp).size(184.dp,42.dp).background(if (selected && menuFocus) Color(0xFFF5F5F5) else Color.Transparent).clickable { menuIndex = slot + firstMenu; applyFilter() }, contentAlignment = Alignment.CenterStart) {
                Text(item.label, color = if (selected && menuFocus) GuideCanvas else if (selected) Color.White else GuideMuted, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).width(172.dp))
            }
        }
        val first = max(0,row - 4)
        channels.drop(first).take(5).forEachIndexed { slot, item ->
            val y = 166 + slot * 91
            Box(Modifier.offset(300.dp,y.dp).size(128.dp,87.dp).background(if (slot + first == row && !menuFocus) Color(0xFF303234) else GuideSurface))
            var logoFailed by remember(item.id, item.logo) { mutableStateOf(false) }
            if (!item.logo.isNullOrBlank() && !logoFailed) AsyncImage(item.logo, item.name, contentScale = ContentScale.Fit, onError = { logoFailed = true }, modifier = Modifier.offset(308.dp,(y+7).dp).size(112.dp,73.dp))
            else Text(item.name,color=Color.White,fontSize=16.sp,maxLines=3,textAlign=TextAlign.Center,modifier=Modifier.offset(308.dp,(y+16).dp).size(112.dp,60.dp))
            val rowCells = guideCells(schedules[item.id].orEmpty(),window)
            rowCells.forEach { cell ->
                val p = cell.programme
                val x = 432 + (cell.left * GuideWidth).toInt()
                val width = max(1,(cell.width * GuideWidth).toInt()-3)
                val selected = slot + first == row && anchor >= p.startMillis && anchor < p.endMillis && !menuFocus
                Box(Modifier.offset(x.dp,y.dp).size(width.dp,87.dp).background(if(selected) Color(0xFFF5F5F5) else GuideSurface).clickable { controller.selectGuideChannel(item); anchor=p.startMillis; menuFocus=false; if(p.startMillis<=now) controller.watchGuideChannel(item) else detail=true }) {
                    if(selected) Box(Modifier.size(3.dp,87.dp).background(Color.White))
                    if(width>49) {
                        val missing = p.title == "No schedule available"
                        val hint = if(missing) { if(item.id in model.loadingChannelIds) "LOADING GUIDE…" else "LIVE CHANNEL" } else if(p.startMillis<=now && p.endMillis>now) "${(p.endMillis-now+59_999)/60_000} MIN LEFT" else guideTime(p.startMillis)
                        Text(hint,color=if(selected) Color(0xFF414548) else GuideMuted,fontSize=15.sp,maxLines=1,modifier=Modifier.offset(12.dp,10.dp).width((width-22).dp))
                        Text(p.title,color=if(selected) GuideCanvas else Color(0xFFF5F5F5),fontSize=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(12.dp,38.dp).size((width-22).dp,48.dp))
                    }
                }
            }
        }
        if(now in window until window+GuideWindowMillis) Box(Modifier.offset((432+((now-window)*804/GuideWindowMillis).toInt()).dp,150.dp).size(2.dp,470.dp).background(Color(0x80FFFFFF)))
        Text(if(channels.isEmpty()) "${model.channelTotal} channels" else "${model.channelOffset+row+1} / ${model.channelTotal}",color=GuideMuted,fontSize=18.sp,modifier=Modifier.offset(300.dp,639.dp).width(124.dp))
        Text(if(programme?.title=="No schedule available") channel?.name.orEmpty() else programme?.title.orEmpty(),color=Color.White,fontSize=26.sp,maxLines=1,modifier=Modifier.offset(432.dp,635.dp).size(804.dp,38.dp))
        Text("OK  Watch / Details     *  Details     Replay  Now     Back  Sidebar",color=GuideMuted,fontSize=18.sp,modifier=Modifier.offset(112.dp,682.dp).width(1124.dp))
        if(channels.isEmpty()) Text(if(state.loading) "Loading channels…" else state.message ?: if(model.channelFilter is LiveChannelFilter.Search) "No matching US channels or current programmes. Try a channel name, section, or another title." else "No channels here yet. Choose another filter.",color=Color.White,fontSize=26.sp,modifier=Modifier.offset(450.dp,292.dp).size(770.dp,130.dp))
        if(detail && channel!=null && programme!=null) {
            Box(Modifier.fillMaxSize().background(Color(0xC7000000)))
            Box(Modifier.offset(224.dp,195.dp).size(1012.dp,360.dp).background(Color(0xFF242628)))
            Text("${channel.name}  ·  ${programme.title}",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,maxLines=2,modifier=Modifier.offset(254.dp,219.dp).size(952.dp,64.dp))
            val body=if(programme.title=="No schedule available") "Schedule unavailable. You can still watch this channel live." else "${if(programme.startMillis>now) "UPCOMING  ·  " else ""}${guideTime(programme.startMillis)}  ·  ${programme.description ?: "No programme description available."}"
            Text(body,color=Color.White,fontSize=20.sp,maxLines=6,modifier=Modifier.offset(254.dp,297.dp).size(952.dp,174.dp))
            Text("OK  Watch this channel live     Back  Return to guide",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(254.dp,499.dp).width(952.dp))
        }
        if(searchOpen) GuideSearchEntry((model.channelFilter as? LiveChannelFilter.Search)?.query.orEmpty(), { controller.setGuideSearch(it.trim()); searchOpen=false }, { searchOpen=false })
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
        bounded += guideCell(programme, start)
        cursor = max(cursor, programme.endMillis)
    }
    if (cursor < end && bounded.size < 32) bounded += guideCell(GuideProgramme("No schedule available", cursor, end), start)
    return bounded
}
private fun guideCell(programme: GuideProgramme, start: Long): GuideCellModel {
    val left = max(programme.startMillis, start)
    val right = min(programme.endMillis, start + GuideWindowMillis)
    return GuideCellModel(programme, (left - start).toFloat() / GuideWindowMillis, (right - left).toFloat() / GuideWindowMillis)
}
private fun floorGuideWindow(now: Long) = now / 1_800_000L * 1_800_000L
private fun guideTime(millis: Long): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(millis))


@Composable
private fun GuideSearchEntry(initial: String, onSubmit: (String) -> Unit, onClose: () -> Unit) {
    RokuTextEntry("Search Live TV", "Use your remote or a connected keyboard.", initial = initial, maxLength = 128, onDone = { onSubmit(it.trim()) }, onCancel = onClose)
}
