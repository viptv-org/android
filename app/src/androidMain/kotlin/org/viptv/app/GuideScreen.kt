package org.viptv.app

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun GuideScreen(state: AppState, channel: LiveChannel?, controller: AppController) {
    val tv = LocalTv.current
    val ui = state.guideUi
    val first = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val rows = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var search by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<LiveChannel, GuideProgramme?>?>(null) }
    val selected = ui.channels.firstOrNull { it.id == ui.selectedChannelId } ?: channel
    val programme = selected?.let { ui.schedulesByChannelId[it.id]?.firstOrNull { item -> item.startMillis <= now && item.endMillis > now } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(ui.channels.isNotEmpty()) { if (tv) { withFrameNanos {}; runCatching { first.requestFocus() } } }
    LaunchedEffect(rows, ui.channels.size, ui.channelTotal, state.loading) {
        snapshotFlow { rows.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.distinctUntilChanged().collect { last ->
            if (last >= ui.channels.size - 5 && ui.channels.isNotEmpty() && ui.channels.size + ui.channelOffset < ui.channelTotal && !state.loading) controller.appendGuidePage()
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
        } else ScreenHeader("Live TV") { VText("● " + clockLabel(now), 12, color = C.statusLive, bold = true) }
        LazyRow(Modifier.fillMaxWidth().padding(bottom = measure(30, 22)), horizontalArrangement = Arrangement.spacedBy(measure(14, 8))) {
            item { AppChip("All", { controller.setGuideFilter(LiveChannelFilter.AllUs) }, ui.channelFilter == LiveChannelFilter.AllUs, Modifier.then(if (tv && ui.channels.isEmpty()) Modifier.focusRequester(first).focusProperties { left = rail } else Modifier)) }
            item { AppChip("My channels", { controller.setGuideFilter(LiveChannelFilter.MyChannels) }, ui.channelFilter == LiveChannelFilter.MyChannels) }
            item { AppChip("Recent", { controller.setGuideFilter(LiveChannelFilter.Recent) }, ui.channelFilter == LiveChannelFilter.Recent) }
            items(ui.categories, key = { it.id }) { category -> AppChip(category.name, { controller.setGuideFilter(LiveChannelFilter.Category(category.id)) }, ui.channelFilter == LiveChannelFilter.Category(category.id)) }
            item { AppChip("Search channels", { search = true }) }
        }
        if (tv) Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            VText("Channels", 22, Modifier.width(272.dp), C.textSecondary)
            repeat(4) { offset -> VText(clockLabel(ui.windowStartMillis + offset * 30 * 60_000L), 20, Modifier.weight(1f), C.textSecondary) }
            AppChip("Earlier", { controller.shiftGuideWindow(-1) })
            AppChip("Now", controller::followGuideNow)
            AppChip("Later", { controller.shiftGuideWindow(1) })
        }
        if (ui.channels.isEmpty()) EmptyState(if (state.loading) "Finding channels…" else "No channels here yet.", "Choose another category or search.", "live", retry = if (state.loading) null else controller::retryGuidePage)
        else LazyColumn(state = rows, contentPadding = PaddingValues(bottom = measure(0, 160)), verticalArrangement = Arrangement.spacedBy(measure(10, 16))) {
            itemsIndexed(ui.channels, key = { _, item -> item.id }) { index, item ->
                val schedule = ui.schedulesByChannelId[item.id].orEmpty()
                val current = schedule.firstOrNull { it.startMillis <= now && it.endMillis > now }
                if (tv) Row(Modifier.fillMaxWidth().height(94.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    var focused by remember(item.id) { mutableStateOf(false) }
                    Holdable({ controller.watchGuideChannel(item) }, { detail = item to current },
                        Modifier.width(266.dp).fillMaxHeight().then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { left = rail }.onFocusChanged { focused = it.isFocused; if (focused) controller.selectGuideChannel(item) }
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
    if (search) TextEntry("Search live TV", "Search channels and programmes.", onDone = { controller.setGuideSearch(it); search = false }, onCancel = { search = false })
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
