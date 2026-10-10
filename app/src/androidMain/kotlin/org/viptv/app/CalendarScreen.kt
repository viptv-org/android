package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import org.viptv.app.theme.ViptvColor as C

/** A month-and-agenda calendar, rather than a discovery grid with extra filter rows. */
@Composable internal fun CalendarScreen(state: AppState, controller: AppController, catalog: DiscoverCatalog) {
    val tv = LocalTv.current
    val ui = state.discoverUi
    val today = LocalDate.now()
    val selected = runCatching { LocalDate.parse(ui.selectedFilters["date"]) }.getOrDefault(today)
    val month = YearMonth.from(selected)
    val dayFocus = remember(month) { List(month.lengthOfMonth()) { FocusRequester() } }
    var expandedMonth by rememberSaveable { mutableStateOf(false) }
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val weekFocus = remember(weekStart) { List(7) { FocusRequester() } }
    val headerFocus = LocalContentFocus.current
    val agendaFocus = remember { FocusRequester() }
    val type = if (catalog.key.id.startsWith("anime-")) "Anime" else if (catalog.key.type == "movie") "Movies" else "TV shows"
    val mine = catalog.key.id.contains("my-calendar")
    LaunchedEffect(Unit) { if (tv) { withFrameNanos {}; runCatching { headerFocus.requestFocus() } } }
    Column(Modifier.fillMaxSize().padding(start = measure(104, 16), end = measure(64, 16), top = measure(40, 12))) {
        ScreenHeader("Calendar", if (tv) null else controller::back, trailing = { PhoneTabActions(state, controller) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(measure(32, 12)), verticalAlignment = Alignment.CenterVertically) {
            FilterTabs(listOf("Movies", "TV shows", "Anime"), type, { controller.switchCalendar(type = when(it) { "Movies" -> "movie"; "Anime" -> "anime"; else -> "series" }) }, Modifier.weight(1f))
            if (tv) AppChip(if (mine) "My library" else "All releases", { controller.switchCalendar(mine = !mine) }, selected = mine)
        }
        if (!tv) FilterTabs(listOf("All releases", "My library"), if (mine) "My library" else "All releases", { controller.switchCalendar(mine = it == "My library") }, Modifier.padding(top = 8.dp))
        if (tv) Row(Modifier.fillMaxSize().padding(top = 32.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(600.dp)) {
                CalendarMonthHeader(month, selected, today, controller, headerFocus, dayFocus[selected.dayOfMonth - 1])
                CalendarDays(month, selected, controller, dayFocus, headerFocus, agendaFocus.takeIf { ui.items.isNotEmpty() })
            }
            Column(Modifier.weight(1f)) {
                CalendarDayTitle(selected, ui.items.size, ui.loading, ui.nextSkip != null)
                CalendarAgenda(ui, controller, agendaFocus, dayFocus[selected.dayOfMonth - 1])
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 164.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { CalendarMonthHeader(month, selected, today, controller, headerFocus, if (expandedMonth) dayFocus[selected.dayOfMonth - 1] else weekFocus[selected.dayOfWeek.value - 1], { expandedMonth = !expandedMonth }, expandedMonth) }
            item {
                if (expandedMonth) CalendarDays(month, selected, controller, dayFocus, headerFocus, null, { date -> controller.setCalendarPeriod(date = date.toString()); expandedMonth = false })
                else CalendarWeek(selected, controller, weekFocus)
            }
            item { CalendarDayTitle(selected, ui.items.size, ui.loading, ui.nextSkip != null) }
            if (ui.loading && ui.items.isEmpty()) item { CalendarLoading() }
            else if (ui.items.isEmpty()) item { CalendarEmpty(ui, controller) }
            else items(ui.items, key = { it.id }) { CalendarEntry(it, controller) }
            if (ui.nextSkip != null) item { AppButton("Load more", controller::appendDiscoverPage, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable private fun CalendarMonthHeader(month: YearMonth, selected: LocalDate, today: LocalDate, controller: AppController, entry: FocusRequester, below: FocusRequester, toggleMonth: (() -> Unit)? = null, expanded: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = measure(16, 0)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AppIconButton("back", if (expanded) "Previous month" else "Previous week", { controller.setCalendarPeriod(date = (if (expanded) selected.minusMonths(1) else selected.minusWeeks(1)).toString()) }, Modifier.size(measure(64, 44)))
        if (toggleMonth == null) VText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 30, Modifier.weight(1f), bold = true, align = androidx.compose.ui.text.style.TextAlign.Center)
        else Holdable(toggleMonth, modifier = Modifier.weight(1f).height(44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { VText(month.format(DateTimeFormatter.ofPattern("MMM yyyy")), 17, bold = true); VIcon("right", if (expanded) "Show week" else "Show month", Modifier.size(18.dp).rotate(if (expanded) 270f else 90f)) }
        }
        AppIconButton("right", if (expanded) "Next month" else "Next week", { controller.setCalendarPeriod(date = (if (expanded) selected.plusMonths(1) else selected.plusWeeks(1)).toString()) }, Modifier.size(measure(64, 44)))
        AppChip("Today", { controller.setCalendarPeriod(date = today.toString()) }, modifier = Modifier.focusRequester(entry).focusProperties { down = below })
    }
}

@Composable private fun CalendarDays(month: YearMonth, selected: LocalDate, controller: AppController, focus: List<FocusRequester>, above: FocusRequester, agenda: FocusRequester?, choose: ((LocalDate) -> Unit)? = null) {
    val tv = LocalTv.current
    val rail = LocalRailFocus.current
    val offset = month.atDay(1).dayOfWeek.value - 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(measure(10, 4))) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { VText(it, if (tv) 20 else 12, Modifier.weight(1f), C.textTertiary, align = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(measure(10, 4))) {
                repeat(7) { column ->
                    val number = week * 7 + column - offset + 1
                    if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                    else {
                        val date = month.atDay(number)
                        var focused by remember(date) { mutableStateOf(false) }
                        val chosen = date == selected
                        val background = if (chosen || focused) C.textPrimary else C.surfaceN2
                        Holdable({ if (choose != null) choose(date) else controller.setCalendarPeriod(date = date.toString()) }, modifier = Modifier.weight(1f).height(measure(64, 44))
                            .focusRequester(focus[number - 1]).focusProperties {
                                up = if (number > 7) focus[number - 8] else above
                                down = if (number + 7 <= month.lengthOfMonth()) focus[number + 6] else agenda ?: above
                                if (column == 0 && tv) left = rail
                                if (column == 6 && agenda != null) right = agenda
                            }.onFocusChanged { focused = it.isFocused }.background(background, RoundedCornerShape(12.dp))
                            .border(if (tv && focused) 3.dp else 0.dp, if (focused) C.textPrimary else Color.Transparent, RoundedCornerShape(12.dp))) {
                            VText(number.toString(), if (tv) 24 else 15, color = if (chosen || focused) C.onLight else C.textPrimary, bold = chosen || focused)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CalendarDayTitle(date: LocalDate, count: Int, loading: Boolean, more: Boolean) {
    VText(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), if (LocalTv.current) 30 else 20, Modifier.padding(bottom = 8.dp), bold = true)
    VText(if (loading) "Loading releases…" else "$count${if (more) "+" else ""} scheduled releases", if (LocalTv.current) 20 else 13, Modifier.padding(bottom = 16.dp), C.textTertiary)
}

@Composable private fun CalendarAgenda(ui: DiscoverUiState, controller: AppController, first: FocusRequester, month: FocusRequester) {
    if (ui.loading && ui.items.isEmpty()) CalendarLoading()
    else if (ui.items.isEmpty()) CalendarEmpty(ui, controller)
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(ui.items, key = { it.id }) { media -> CalendarEntry(media, controller, Modifier.then(if (media === ui.items.first()) Modifier.focusRequester(first) else Modifier).focusProperties { left = month }) }
        if (ui.nextSkip != null) item { AppButton("Load more", controller::appendDiscoverPage) }
    }
}

@Composable private fun CalendarEntry(media: Media, controller: AppController, modifier: Modifier = Modifier) {
    val tv = LocalTv.current
    val presentation = remember(media) { CoreModels.presentation(media) }
    val time = media.releasedAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a z")) }
    var focused by remember(media.id) { mutableStateOf(false) }
    Holdable({ controller.open(media) }, modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.background(C.surfaceN2, RoundedCornerShape(16.dp))
        .border(if (tv && focused) 3.dp else 0.dp, if (focused) C.textPrimary else Color.Transparent, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().padding(measure(16, 12)), horizontalArrangement = Arrangement.spacedBy(measure(20, 12)), verticalAlignment = Alignment.CenterVertically) {
            Artwork(presentation.posterImage, media.name, Modifier.width(measure(88, 56)).height(measure(120, 80)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VText(media.name, if (tv) 24 else 16, bold = true, lines = 2)
                if (!media.episodeTitle.isNullOrBlank()) VText(media.episodeTitle.orEmpty(), if (tv) 20 else 13, color = C.textSecondary, lines = 2)
                val coordinate = if (media.episode != null) "S${media.season ?: 1} · E${media.episode}" else "Movie release"
                VText(listOfNotNull(time, coordinate).joinToString(" · "), if (tv) 18 else 12, color = C.textTertiary)
            }
            VIcon("right", "Open title")
        }
    }
}

@Composable private fun CalendarLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(measure(140, 100))) } }
}
@Composable private fun CalendarEmpty(ui: DiscoverUiState, controller: AppController) {
    EmptyState(if (ui.error == null) "Nothing scheduled" else "Calendar unavailable", ui.error ?: "Choose another day or switch to All releases.", "discover", retry = if (ui.error != null) ({ controller.openDiscover() }) else null)
}

/** Phone starts with the useful week and agenda; the month remains one tap away. */
@Composable private fun CalendarWeek(selected: LocalDate, controller: AppController, focus: List<FocusRequester>) {
    val start = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { column ->
            val day = start.plusDays(column.toLong())
            val chosen = day == selected
            Holdable({ controller.setCalendarPeriod(date = day.toString()) }, modifier = Modifier.weight(1f).height(64.dp).focusRequester(focus[column])
                .background(if (chosen) C.textPrimary else C.surfaceN2, RoundedCornerShape(12.dp))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VText(day.format(DateTimeFormatter.ofPattern("EEEEE")), 11, color = if (chosen) C.onLight else C.textTertiary)
                    VText(day.dayOfMonth.toString(), 17, color = if (chosen) C.onLight else C.textPrimary, bold = chosen)
                }
            }
        }
    }
}
