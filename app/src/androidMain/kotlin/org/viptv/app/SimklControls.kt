package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable internal fun SimklSearchControls(controller: AppController) {
    var values by remember { mutableStateOf(emptyMap<String,String>()) }
    var entry by remember { mutableStateOf<String?>(null) }
    val labels = linkedMapOf("category" to "Category: movie / tv / anime", "genre" to "Genre", "year_min" to "From year", "year_max" to "To year", "rating_min" to "Minimum SIMKL rating", "rank_max" to "Maximum rank", "sort" to "Sort: relevance / title / rank / release-date / last-air-date")
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)) {
        labels.forEach { (key,label) -> item(key=key) { AppChip(values[key]?.let { "$label: $it" } ?: label,{entry=key}) } }
        item { AppChip("Reset filters",{values=emptyMap();controller.setSimklSearchFilters(values)}) }
        item { AppChip("Next results",{controller.nextSimklSearchPage()}) }
    }
    entry?.let { key -> TextEntry(labels.getValue(key),"Leave blank for all",onDone={value -> values=values.toMutableMap().apply { if(value.isBlank()) remove(key) else put(key,value.trim()) };controller.setSimklSearchFilters(values);entry=null},onCancel={entry=null}) }
}

internal fun AppController.setSimklSearchFilters(filters:Map<String,String>) {
    simklSearchFilters=filters
    search(_state.value.searchQuery)
}
internal fun AppController.nextSimklSearchPage() {
    simklSearchFilters=simklSearchFilters+("skip" to ((simklSearchFilters["skip"]?.toIntOrNull() ?: 0)+50).toString())
    search(_state.value.searchQuery)
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

internal fun AppController.addToSimklWatchlist(media:Media) {
    val profile=_state.value.selectedProfile?.id ?: return
    scope.launch { runCatching { gateway.simklWatchlist(profile,media,"plantowatch") }.onSuccess { _state.value=_state.value.copy(message="Added to watchlist") }.onFailure { fail(it) } }
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
    LaunchedEffect(profile){runCatching {controller.gateway.simklInfo(profile)}.onSuccess{info=it}.onFailure{info=it.message ?: "SIMKL unavailable"}}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        VText("SIMKL · This profile",22)
        VText(info,14)
        AppButton("Link / manage in account web UI",{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(origin)))})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppChip("Sync",{scope.launch {runCatching {controller.gateway.simklSync(profile);controller.gateway.simklInfo(profile)}.onSuccess{info=it}.onFailure{info=it.message ?: "Sync failed"}}})
            AppChip("Custom lists",{scope.launch {runCatching {controller.gateway.simklLists(profile,page)}.onSuccess{lists=it;page++}.onFailure{info=it.message ?: "Lists unavailable"}}})
            if(selected!=null) AppChip("Next list page",{scope.launch {runCatching {controller.gateway.simklList(profile,selected!!,listPage)}.onSuccess{items=items+it;listPage++}.onFailure{info=it.message ?: "List unavailable"}}})
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {lists.forEach { (id,name)->item(key=id){AppChip(name,{selected=id;listPage=1;scope.launch {runCatching {controller.gateway.simklList(profile,id,listPage)}.onSuccess{items=it;listPage++}.onFailure{info=it.message ?: "List unavailable"}}})}}}
        if(items.isNotEmpty()) MediaGrid(items,onClick={controller.activateCard(it)},onHold={controller.addToSimklWatchlist(it)})
    }
}
