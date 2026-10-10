package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import org.viptv.app.theme.ViptvColor as C
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable internal fun SimklSearchControls(state:AppState,controller:AppController) {
    val values=state.simklSearchFilters
    var entry by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<Pair<String,List<Pair<String,String>>>?>(null) }
    val categories=listOf("All" to "", "Movies" to "movie", "TV shows" to "tv", "Anime" to "anime", "Live TV" to "live")
    val sorts=listOf("Best match" to "relevance", "Most popular" to "rank", "Recently aired" to "last-air-date", "Newest release" to "release-date", "Title A–Z" to "title")
    val genres=listOf("All genres" to "", "Action" to "action", "Adventure" to "adventure", "Animation" to "animation", "Comedy" to "comedy", "Crime" to "crime", "Documentary" to "documentary", "Drama" to "drama", "Fantasy" to "fantasy", "Horror" to "horror", "Mystery" to "mystery", "Romance" to "romance", "Science fiction" to "science-fiction", "Thriller" to "thriller")
    val numeric=linkedMapOf("year_min" to "From year", "year_max" to "To year", "rating_min" to "Minimum rating", "rank_max" to "Top ranked: maximum rank")
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)) {
        item { AppChip(categories.firstOrNull {it.second==values["category"]}?.first ?: "All types",{choice="category" to categories}) }
        item { AppChip(genres.firstOrNull {it.second==values["genre"]}?.first ?: "Genre",{choice="genre" to genres}) }
        item { AppChip(sorts.firstOrNull {it.second==values["sort"]}?.first ?: "Best match",{choice="sort" to sorts}) }
        numeric.forEach { (key,label)->item(key=key){AppChip(values[key]?.let { "$label: $it" } ?: label,{entry=key})} }
        item { AppChip("Reset filters",{controller.setSimklSearchFilters(emptyMap())}) }
        if(state.searchHasMore) item { AppChip("Load more",{controller.nextSimklSearchPage()}) }
    }
    choice?.let { (key,options)->ChoiceDialog(if(key=="category") "Media type" else if(key=="genre") "Genre" else "Sort results",options.map { (label,value)->label to {controller.setSimklSearchFilters(values.toMutableMap().apply {if(value.isBlank()) remove(key) else put(key,value)});choice=null} },{choice=null}) }
    entry?.let { key->TextEntry(numeric.getValue(key),"Leave blank to clear",values[key].orEmpty(),onDone={value->controller.setSimklSearchFilters(values.toMutableMap().apply {if(value.isBlank()) remove(key) else put(key,value.trim())});entry=null},onCancel={entry=null}) }
}
internal fun AppController.setSimklSearchFilters(filters:Map<String,String>) {
    simklSearchFilters=filters.filterKeys {it!="skip"}
    search(_state.value.searchQuery)
}
internal fun AppController.nextSimklSearchPage() {
    if(!_state.value.searchHasMore) return
    simklSearchFilters=simklSearchFilters+("skip" to ((simklSearchFilters["skip"]?.toIntOrNull() ?: 0)+50).toString())
    appendSimklSearch()
}

internal fun AppController.sendSimklEvent(action:String,media:Media?=(_state.value.route as? Route.Player)?.media) {
    val item=media ?: return
    if(item.type=="live") return
    val profile=_state.value.selectedProfile?.id ?: return
    if(action=="start" && simklActiveItem!=item.id) { simklPlaybackSession=UUID.randomUUID().toString();simklActiveItem=item.id }
    if(simklActiveItem!=item.id) return
    if(action=="pause") simklPaused=true
    if(action=="start") simklPaused=false
    val session=simklPlaybackSession
    val position=if(action=="complete") titleDurationMillis() ?: absolutePositionMillis() else absolutePositionMillis()
    val duration=titleDurationMillis() ?: item.durationMillis
    if(action in listOf("stop","complete")) simklActiveItem=null
    // Keep real player events ordered; routine progress saves never enter this queue.
    val prior=simklEventJob
    simklEventJob=scope.launch {
        prior?.join()
        runCatching { gateway.simklPlayback(profile,item,session,action,position,duration) }
    }
}

internal fun AppController.addToSimklWatchlist(media:Media,status:String="plantowatch") {
    val profile=_state.value.selectedProfile?.id ?: return
    scope.launch { runCatching { gateway.simklWatchlist(profile,media,status) }.onSuccess { _state.value=_state.value.copy(message="Watchlist updated") }.onFailure { fail(it) } }
}

@Composable internal fun SimklSettings(state:AppState,controller:AppController,origin:String) {
    val profile=state.selectedProfile?.id ?: return
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    var info by remember(profile) { mutableStateOf("Loading SIMKL connection…") }
    var lists by remember(profile) { mutableStateOf(emptyList<Pair<String,String>>()) }
    var items by remember(profile) { mutableStateOf(emptyList<Media>()) }
    var selected by remember(profile) { mutableStateOf<String?>(null) }
    var page by remember(profile) { mutableIntStateOf(1) }
    var listPage by remember(profile) { mutableIntStateOf(1) }
    var statuses by remember(profile) {mutableStateOf(false)}
    var statusFilter by remember(profile) {mutableStateOf("all")}
    LaunchedEffect(profile){runCatching {controller.gateway.simklInfo(profile)}.onSuccess{info=it}.onFailure{info=it.message ?: "SIMKL unavailable"}}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        VText("SIMKL · This profile",22)
        VText(info,14)
        VText("Link this profile in your account settings: $origin",14)
        AppButton("Open account settings",{runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(origin)))}.onFailure {info="Open $origin on your phone or computer to link this profile."}})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppChip("Watchlist",{scope.launch {runCatching {controller.gateway.simklWatchlistItems(profile)}.onSuccess {items=it;statuses=true;selected=null}.onFailure{info=it.message ?: "Watchlist unavailable"}}})
            AppChip("Sync",{scope.launch {runCatching {controller.gateway.simklSync(profile);controller.gateway.simklInfo(profile)}.onSuccess{info=it}.onFailure{info=it.message ?: "Sync failed"}}})
            AppChip("Custom lists",{scope.launch {runCatching {controller.gateway.simklLists(profile,page)}.onSuccess{lists=(lists+it).distinctBy {entry->entry.first};page++}.onFailure{info=it.message ?: "Lists unavailable"}}})
            if(selected!=null) AppChip("Next list page",{scope.launch {runCatching {controller.gateway.simklList(profile,selected!!,listPage)}.onSuccess{items=(items+it).distinctBy {entry->HomeFocusPolicy.mediaKey(entry)};listPage++}.onFailure{info=it.message ?: "List unavailable"}}})
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {lists.forEach { (id,name)->item(key=id){AppChip(name,{selected=id;listPage=1;scope.launch {runCatching {controller.gateway.simklList(profile,id,listPage)}.onSuccess{items=it;statuses=false;listPage++}.onFailure{info=it.message ?: "List unavailable"}}})}}}
        if(statuses) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("all" to "All", "plantowatch" to "Plan to watch", "watching" to "Watching", "hold" to "On hold", "completed" to "Completed", "dropped" to "Dropped").forEach { (id,name)->item(key=id){AppChip(name,{statusFilter=id},statusFilter==id)}}}
        val visible=if(!statuses || statusFilter=="all") items else items.filter {org.json.JSONObject(it.normalizedJson(false)).optJSONObject("raw")?.optString("watchlist_status")==statusFilter}
        if(visible.isNotEmpty()) MediaGrid(visible,onClick={controller.activateCard(it)},onHold={controller.addToSimklWatchlist(it)})
    }
}


@Composable internal fun SimklCalendarControls(state:AppState,controller:AppController,catalog:DiscoverCatalog) {
    val calendar=catalog.key.id.contains("calendar") || catalog.key.id.contains("new-episodes") || catalog.key.id.contains("premieres") || catalog.key.id.contains("upcoming")
    if(!calendar) return
    val filters=state.discoverUi.selectedFilters
    val today=java.time.LocalDate.now()
    val day=runCatching {java.time.LocalDate.parse(filters["date"])}.getOrDefault(today)
    val month=runCatching {java.time.YearMonth.parse(filters["month"])}.getOrDefault(java.time.YearMonth.from(day))
    var picking by remember {mutableStateOf(false)}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=8.dp)) {
        item {AppChip("Previous day",{controller.setCalendarPeriod(date = day.minusDays(1).toString())})}
        item {AppChip("Today",{controller.setCalendarPeriod(date = today.toString())})}
        item {AppChip(day.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")),{picking=true})}
        item {AppChip("Next day",{controller.setCalendarPeriod(date = day.plusDays(1).toString())})}
        item {AppChip("Previous month",{controller.setCalendarPeriod(month = month.minusMonths(1).toString())})}
        item {AppChip(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),{controller.setCalendarPeriod(month = month.toString())})}
        item {AppChip("Next month",{controller.setCalendarPeriod(month = month.plusMonths(1).toString())})}
        item {AppChip("All dates",{controller.setCalendarPeriod()})}
    }
    SimklCalendarMonth(month, day, controller)
    if(picking) TextEntry("Calendar date","Enter a date, for example 2026-10-10",day.toString(),onDone={value->if(runCatching {java.time.LocalDate.parse(value)}.isSuccess){controller.setCalendarPeriod(date = value);picking=false}},onCancel={picking=false})
}

/** Discovery entry points use the existing chips and category screens on both form factors. */
@Composable internal fun SimklHomeShortcuts(controller: AppController, entry: FocusRequester? = null, below: FocusRequester? = null) {
    val rail = LocalRailFocus.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(measure(16, 8)), contentPadding = PaddingValues(4.dp)) {
        listOf("Calendar" to "calendar", "My calendar" to "my-calendar", "New episodes" to "new-episodes", "Premieres" to "premieres", "Upcoming" to "upcoming").forEach { (label, catalog) ->
            item(key = catalog) { AppChip(label, { controller.openSimklFeed(catalog) }, modifier = Modifier.background(Color.Black.copy(alpha = .16f), RoundedCornerShape(50))
                .then(if (catalog == "calendar" && entry != null) Modifier.focusRequester(entry) else Modifier)
                .focusProperties { if (below != null) down = below; if (catalog == "calendar") left = rail }) }
        }
    }
}
internal fun AppController.openSimklFeed(id: String) {
    scope.launch {
        try {
            val catalogs = _state.value.discoverUi.catalogs.takeIf { it.isNotEmpty() } ?: gateway.catalogs()
            val category = if (id == "premieres") "movie" else "series"
            val catalog = catalogs.firstOrNull { it.key.id == id && it.key.type == category } ?: catalogs.firstOrNull { it.key.id == id } ?: return@launch
            _state.value = _state.value.copy(discoverUi = _state.value.discoverUi.copy(catalogs = catalogs))
            setDiscoverCatalog(catalog.key)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { fail(error) }
    }
}

@Composable internal fun SimklDetailFacts(media: Media) {
    val raw = remember(media) { org.json.JSONObject(media.normalizedJson(false)).optJSONObject("raw") }
    val original = raw?.optString("original_title")?.takeIf { it.isNotBlank() && it != "null" && it != media.name }
    if (original != null) VText("Also known as $original", if (LocalTv.current) 20 else 13, color = C.textTertiary, lines = 2)
    val next = raw?.optJSONObject("next_airing")
    val tv = LocalTv.current
    val date = next?.optString("released")?.takeIf { it.isNotBlank() && it != "null" }
    val schedule = raw?.optJSONObject("airs")
    val airing = date?.let { value ->
        runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z")) }.getOrDefault(value)
    }
    if (airing != null) VText("Next episode · ${next?.optString("episodeTitle")?.takeIf { it.isNotBlank() } ?: next?.optString("name").orEmpty()} · $airing", if (tv) 22 else 14, color = org.viptv.app.theme.ViptvColor.textSecondary, lines = 3)
    else if (schedule != null) {
        val parts = listOf("day", "time", "timezone").mapNotNull { key -> schedule.optString(key).takeIf { it.isNotBlank() && it != "null" } }
        if (parts.isNotEmpty()) VText("Airs · " + parts.joinToString(" · "), if (tv) 22 else 14, color = org.viptv.app.theme.ViptvColor.textSecondary, lines = 2)
    }
    val facts = listOfNotNull(raw?.optString("status")?.takeIf { it.isNotBlank() && it != "null" }, raw?.optString("network")?.takeIf { it.isNotBlank() && it != "null" }, raw?.optJSONObject("ratings")?.optJSONObject("simkl")?.optDouble("rating")?.takeIf { !it.isNaN() }?.let { "SIMKL %.1f/10".format(it) }, raw?.optInt("rank")?.takeIf { it > 0 }?.let { "Rank #$it" })
    if (facts.isNotEmpty()) VText(facts.joinToString(" · "), if (tv) 22 else 14, color = org.viptv.app.theme.ViptvColor.textSecondary, lines = 2)
}

@Composable private fun SimklCalendarMonth(month: java.time.YearMonth, selected: java.time.LocalDate, controller: AppController) {
    var expanded by remember { mutableStateOf(false) }
    AppChip(if (expanded) "Hide month" else "Show month", { expanded = !expanded }, modifier = Modifier.padding(bottom = 8.dp))
    if (!expanded) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { VText(it, if (LocalTv.current) 20 else 12, Modifier.weight(1f).padding(start = 12.dp)) }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { column ->
                    val number = week * 7 + column - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        var focused by remember(date) { mutableStateOf(false) }
                        val tv = LocalTv.current
                        Holdable({ controller.setCalendarPeriod(date = date.toString()) }, modifier = Modifier.weight(1f).height(measure(52, 44))
                            .onFocusChanged { focused = it.isFocused }.background(if (date == selected || focused) C.surfaceN3 else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(if (tv && focused) 2.dp else 0.dp, if (tv && focused) C.textPrimary else Color.Transparent, RoundedCornerShape(8.dp))) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { VText(number.toString(), if (tv) 20 else 14, bold = date == selected || focused) }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable internal fun SimklSaveAction(media: Media, saved: Boolean, controller: AppController, modifier: Modifier = Modifier) {
    var open by remember(media.id) { mutableStateOf(false) }
    AppIconButton(if (saved) "check" else "plus", "My List and watchlist status", { open = true }, modifier)
    if (open) ChoiceDialog(media.name, listOfNotNull(
        (if (saved) "Remove from My List" else "Add to My List") to { controller.toggleMyList(media); open = false },
        "Plan to watch" to { controller.addToSimklWatchlist(media, "plantowatch"); open = false },
        if (media.type != "movie") "Watching" to { controller.addToSimklWatchlist(media, "watching"); open = false } else null,
        if (media.type != "movie") "On hold" to { controller.addToSimklWatchlist(media, "hold"); open = false } else null,
        "Completed" to { controller.addToSimklWatchlist(media, "completed"); open = false },
        "Dropped" to { controller.addToSimklWatchlist(media, "dropped"); open = false }
    ), { open = false })
}

@Composable internal fun SimklRecommendations(media: Media, controller: AppController) {
    val groups = remember(media) {
        val raw = org.json.JSONObject(media.normalizedJson(false)).optJSONObject("raw")
        listOf("users_recommendations" to "Viewers also watched", "similar" to "More like this").map { (key, label) ->
            val array = raw?.optJSONArray(key)
            label to (0 until (array?.length() ?: 0)).mapNotNull { index -> array?.optJSONObject(index)?.let { runCatching { CoreModels.media(it) }.getOrNull() } }.take(20)
        }.filter { it.second.isNotEmpty() }
    }
    Column(Modifier.padding(horizontal = measure(0, 20), vertical = measure(24, 16)), verticalArrangement = Arrangement.spacedBy(measure(24, 16))) {
        groups.forEach { (label, items) ->
            VText(label, if (LocalTv.current) 28 else 20, display = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(measure(24, 12)), contentPadding = PaddingValues(4.dp)) {
                items.forEach { item -> item(key = item.id) {
                    MediaCard(item, Modifier.width(measure(200, 116)), portrait = true, onClick = { controller.open(item) }, onHold = { controller.requestDialog(DialogKind.MyListManage, item.name, item) })
                } }
            }
        }
    }
}
