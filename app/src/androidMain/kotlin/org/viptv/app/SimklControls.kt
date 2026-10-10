package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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


@Composable internal fun SimklTitleActions(media:Media,controller:AppController) {
    var open by remember(media.id) {mutableStateOf(false)}
    val context=androidx.compose.ui.platform.LocalContext.current
    AppIconButton("plus","Watchlist status",{open=true})
    val raw=org.json.JSONObject(media.normalizedJson(false)).optJSONObject("raw")
    val link=raw?.optString("simkl_url")?.takeIf {it.startsWith("https://simkl.com/")}
    if(link!=null) AppIconButton("info","View on SIMKL",{runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(link)))}.onFailure {controller.fail(it)}})
    if(open) ChoiceDialog("${media.name} · Watchlist",listOfNotNull(
        "Plan to watch" to {controller.addToSimklWatchlist(media,"plantowatch");open=false},
        if(media.type!="movie") "Watching" to {controller.addToSimklWatchlist(media,"watching");open=false} else null,
        if(media.type!="movie") "On hold" to {controller.addToSimklWatchlist(media,"hold");open=false} else null,
        "Completed" to {controller.addToSimklWatchlist(media,"completed");open=false},
        "Dropped" to {controller.addToSimklWatchlist(media,"dropped");open=false}
    ),{open=false})
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
        item {AppChip("Previous day",{controller.setDiscoverFilter("month",null);controller.setDiscoverFilter("date",day.minusDays(1).toString())})}
        item {AppChip("Today",{controller.setDiscoverFilter("month",null);controller.setDiscoverFilter("date",today.toString())})}
        item {AppChip(day.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")),{picking=true})}
        item {AppChip("Next day",{controller.setDiscoverFilter("month",null);controller.setDiscoverFilter("date",day.plusDays(1).toString())})}
        item {AppChip("Previous month",{controller.setDiscoverFilter("date",null);controller.setDiscoverFilter("month",month.minusMonths(1).toString())})}
        item {AppChip(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),{controller.setDiscoverFilter("date",null);controller.setDiscoverFilter("month",month.toString())})}
        item {AppChip("Next month",{controller.setDiscoverFilter("date",null);controller.setDiscoverFilter("month",month.plusMonths(1).toString())})}
        item {AppChip("All dates",{controller.setDiscoverFilter("date",null);controller.setDiscoverFilter("month",null)})}
    }
    if(picking) TextEntry("Calendar date","Enter a date, for example 2026-10-10",day.toString(),onDone={value->if(runCatching {java.time.LocalDate.parse(value)}.isSuccess){controller.setDiscoverFilter("month",null);controller.setDiscoverFilter("date",value);picking=false}},onCancel={picking=false})
}
