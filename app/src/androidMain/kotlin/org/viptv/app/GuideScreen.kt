package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** Canonical five-row, two-hour EPG. Controller owns paging/cache/window state. */
@Composable
internal fun GuideScreen(state: AppState, initialChannel: LiveChannel, controller: AppController) {
    val model = state.guideUi
    val allChannels = model?.channels?.ifEmpty { state.liveChannels } ?: state.liveChannels.ifEmpty { listOf(initialChannel) }
    val schedules = model?.schedulesByChannelId ?: mapOf(initialChannel.id to state.guide)
    val selectedId = model?.selectedChannelId ?: initialChannel.id
    val now = System.currentTimeMillis()
    val pageChannels = allChannels.drop((model?.page ?: 0) * GuidePolicy.PAGE_SIZE).take(GuidePolicy.PAGE_SIZE)
    val categories = listOf("All", "Now") + pageChannels.mapNotNull(LiveChannel::category).distinct().take(2)
    var filter by remember { mutableStateOf("All") }
    val filteredChannels = pageChannels.filter { channel ->
        when (filter) {
            "All" -> true
            "Now" -> schedules[channel.id]?.any { it.startMillis <= now && it.endMillis > now } == true
            else -> channel.category == filter
        }
    }.ifEmpty { pageChannels }
    val selectedIndex = filteredChannels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val firstRow = max(0, selectedIndex - 4)
    val visible = filteredChannels.drop(firstRow).take(5)
    val window = model?.windowStartMillis ?: floorGuideWindow(System.currentTimeMillis())
    val followsNow = model?.followsNow ?: true
    var detail by remember { mutableStateOf<Pair<LiveChannel, GuideProgramme>?>(null) }
    val initialFocus = remember(initialChannel.id) { FocusRequester() }
    LaunchedEffect(initialChannel.id) { initialFocus.requestFocus() }
    if (detail != null) BackHandler { detail = null }

    Box(Modifier.fillMaxSize().background(GuideCanvas)) {
        Text("Live TV", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(104.dp, 98.dp))
        categories.forEachIndexed { index, label ->
            GuideButton(label, Modifier.offset(104.dp, (166 + index * 52).dp).width(184.dp).height(42.dp).then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier), selected = filter == label) { filter = label }
        }
        GuideButton("Now", Modifier.offset(104.dp, 390.dp).width(184.dp).height(42.dp), selected = followsNow) { controller.followGuideNow() }
        GuideButton("Earlier hour", Modifier.offset(104.dp, 442.dp).width(184.dp).height(42.dp)) { controller.shiftGuideWindow(-1) }
        GuideButton("Later hour", Modifier.offset(104.dp, 494.dp).width(184.dp).height(42.dp)) { controller.shiftGuideWindow(1) }
        GuideButton("Previous page", Modifier.offset(104.dp, 546.dp).width(184.dp).height(42.dp)) { controller.changeGuidePage(-1) }
        GuideButton("Next page", Modifier.offset(104.dp, 598.dp).width(184.dp).height(42.dp)) { controller.changeGuidePage(1) }

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
                onActivate = { programme ->
                    if (programme.startMillis <= now && programme.endMillis > now) controller.watchGuideChannel(channel)
                    else detail = channel to programme
                },
                onWatch = { controller.watchGuideChannel(channel) },
            )
        }
        if (now in window until window + GuideWindowMillis) {
            val x = 432 + ((now - window).toFloat() / GuideWindowMillis * GuideWidth).toInt()
            Box(Modifier.offset(x.dp, 150.dp).width(2.dp).height(470.dp).background(Color(0x66FFFFFF)))
        }
        Text("${if (followsNow) "Following now" else "Time shifted"} · ${visible.size} channels", color = GuideMuted, fontSize = 15.sp, modifier = Modifier.offset(432.dp, 650.dp).width(804.dp), textAlign = TextAlign.Center)
        detail?.let { (channel, programme) ->
            GuideDetail(channel, programme, onWatch = { controller.watchGuideChannel(channel) }, onClose = { detail = null })
        }
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
private fun GuideProgrammeRow(channel: LiveChannel, programmes: List<GuideProgramme>, windowStart: Long, y: Int, selected: Boolean, onFocus: () -> Unit, onActivate: (GuideProgramme) -> Unit, onWatch: () -> Unit) {
    val cells = guideCells(programmes, windowStart)
    cells.forEach { cell ->
        val x = 432 + (cell.left * GuideWidth).toInt()
        val width = max(1, (cell.width * GuideWidth).toInt() - 3)
        GuideCell(
            programme = cell.programme,
            selected = selected,
            modifier = Modifier.offset(x.dp, y.dp).width(width.dp).height(87.dp),
            onFocus = onFocus,
            onActivate = { onActivate(cell.programme) },
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
            .focusable().clickable(onClick = onActivate)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) { onWatch(); true } else false
            }.padding(10.dp),
    ) {
        Text(programme.title, color = if (focused) GuideCanvas else Color.White, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private data class GuideCellModel(val programme: GuideProgramme, val left: Float, val width: Float)
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
    Box(modifier.background(if (focused) Color.White else if (selected) Color(0xFF303234) else Color.Transparent, RoundedCornerShape(21.dp)).onFocusChanged { focused = it.hasFocus }.focusable().clickable(onClick = onActivate), contentAlignment = Alignment.Center) {
        Text(label, color = if (focused) GuideCanvas else Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GuideDetail(channel: LiveChannel, programme: GuideProgramme, onWatch: () -> Unit, onClose: () -> Unit) = Box(Modifier.fillMaxSize().background(Color(0xDC080909)), contentAlignment = Alignment.Center) {
    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(programme.startMillis, channel.id) { watchFocus.requestFocus() }
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
