package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DiscoverScreen(state:AppState,controller:AppController) {
    val ui=state.discoverUi
    val catalog=ui.catalogs.firstOrNull {it.key==ui.selectedCatalogKey}
    var choices by remember {mutableStateOf<List<Pair<String,()->Unit>>?>(null)}
    var choiceTitle by remember {mutableStateOf("")}
    var entry by remember {mutableStateOf<CatalogFilter?>(null)}
    var localPage by remember {mutableIntStateOf(0)}
    val initial=remember {FocusRequester()}
    val filterFocus=remember {FocusRequester()}
    LaunchedEffect(ui.requestedSkip,ui.selectedCatalogKey,ui.selectedFilters) {localPage=0}
    LaunchedEffect(ui.items,localPage) {if(ui.items.isNotEmpty())initial.requestFocus() else filterFocus.requestFocus()}
    fun choose(title:String,values:List<Pair<String,()->Unit>>) {choiceTitle=title;choices=values}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuLabel(state.selectedProfile?.name.orEmpty(),930,34,284,18,color=RokuMuted,align=androidx.compose.ui.text.style.TextAlign.End)
        RokuLabel("Discover",100,54,1096,44,bold=true)
        RokuLabel(catalog?.name.orEmpty(),100,126,1096,18,color=RokuMuted)
        LazyRow(Modifier.offset(100.dp,178.dp).size(1096.dp,48.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            item {
                TvButton("${ui.selectedType.replaceFirstChar(Char::uppercase)}  ▾",{choose("Browse",ui.catalogs.map {it.key.type}.distinct().map {type->type.replaceFirstChar(Char::uppercase) to {controller.setDiscoverType(type)}})},Modifier.size(256.dp,48.dp).focusRequester(filterFocus))
            }
            item {
                TvButton("${catalog?.name?:"Catalog"}  ▾",{choose("Catalogs",ui.catalogs.filter {it.key.type==ui.selectedType}.map {item->item.name to {controller.setDiscoverCatalog(item.key)}})},Modifier.size(256.dp,48.dp))
            }
            catalog?.let { selected ->
                val filters=buildList {
                    if(selected.supportsSearch && selected.filters.none {it.kind==CatalogFilterKind.Search}) add(CatalogFilter("search",CatalogFilterKind.Search,required=false))
                    addAll(selected.filters)
                }
                itemsIndexed(filters) {_,filter ->
                    TvButton("${ui.selectedFilters[filter.name]?:filter.name.replaceFirstChar(Char::uppercase)}${if(filter.options.isNotEmpty())"  ▾" else ""}",{
                        if(filter.options.isEmpty())entry=filter else choose(filter.name.replaceFirstChar(Char::uppercase),buildList {
                            if(!filter.required)add("Any" to {controller.setDiscoverFilter(filter.name,null)})
                            filter.options.forEach {value->add(value to {controller.setDiscoverFilter(filter.name,value)})}
                        })
                    },Modifier.size(256.dp,48.dp))
                }
            }
        }
        val shown=ui.items.drop(localPage*8).take(8)
        LazyVerticalGrid(columns=GridCells.Fixed(4),modifier=Modifier.offset(100.dp,248.dp).size(1096.dp,412.dp),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
            itemsIndexed(shown,key={index,item->"$index:${item.type}:${item.id}"}) {index,media->RokuArtworkCard(media,if(index==0)Modifier.focusRequester(initial)else Modifier,onActivate={controller.open(media)},height=192)}
        }
        if(ui.loading) RokuLabel("Loading…",250,360,780,24,align=androidx.compose.ui.text.style.TextAlign.Center)
        else if(ui.error!=null) {
            RokuLabel(ui.error,250,304,780,24,lines=3,align=androidx.compose.ui.text.style.TextAlign.Center)
            TvButton("Try again",{controller.openDiscover()},Modifier.offset(540.dp,450.dp).size(200.dp,56.dp))
        } else if(shown.isEmpty()) RokuLabel("No titles found",250,304,780,32,bold=true,align=androidx.compose.ui.text.style.TextAlign.Center)
        Row(Modifier.offset(100.dp,670.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            if(localPage>0||ui.previousSkips.isNotEmpty())TvButton("Previous",{if(localPage>0)localPage-- else controller.changeDiscoverPage(-1)},Modifier.size(180.dp,40.dp))
            if((localPage+1)*8<ui.items.size||ui.nextSkip!=null)TvButton("Next",{if((localPage+1)*8<ui.items.size)localPage++ else controller.changeDiscoverPage(1)},Modifier.size(180.dp,40.dp))
        }
        choices?.let {RokuChoiceSheet(choiceTitle,it,{choices=null})}
        entry?.let {filter->RokuDiscoverEntry(filter,ui.selectedFilters[filter.name].orEmpty(),{value->controller.setDiscoverFilter(filter.name,value.takeIf {it.isNotBlank()});entry=null},{entry=null})}
    }
}

@Composable
private fun RokuDiscoverEntry(filter:CatalogFilter,initial:String,onApply:(String)->Unit,onClose:()->Unit) {
    var value by remember(filter.name) {mutableStateOf(initial)}
    val first=remember {FocusRequester()}
    BackHandler(onBack=onClose)
    LaunchedEffect(Unit){first.requestFocus()}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuLabel(filter.name.replaceFirstChar(Char::uppercase),180,112,1000,44,bold=true)
        RokuLabel("Enter a value",180,182,1000,23,color=RokuMuted)
        RokuLabel(value,180,242,1000,28)
        Column(Modifier.offset(180.dp,300.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ("abcdefghijklmnopqrstuvwxyz0123456789".map {it.toString()}+listOf("Space","Delete","Clear")).chunked(10).forEachIndexed {row,keys->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {keys.forEachIndexed {column,key->TvButton(key,{
                    value=when(key){"Space"->value+" ";"Delete"->value.dropLast(1);"Clear"->"";else->value+key}.take(256)
                },Modifier.size(if(key.length>1)128.dp else 64.dp,48.dp).then(if(row==0&&column==0)Modifier.focusRequester(first)else Modifier))}}
            }
        }
        Row(Modifier.offset(180.dp,608.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TvButton("Done",{if(!filter.required||value.isNotBlank())onApply(value)},Modifier.size(240.dp,56.dp))
            TvButton("Cancel",onClose,Modifier.size(240.dp,56.dp))
        }
    }
}
