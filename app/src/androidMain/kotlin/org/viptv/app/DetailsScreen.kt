package org.viptv.app

import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun DetailsScreen(media:Media,controller:AppController) {
    if(media.type=="series"&&media.episode==null) RokuSeriesDetails(media,controller) else RokuMovieDetails(media,controller)
}

@Composable
private fun RokuMovieDetails(media:Media,controller:AppController) {
    val first=remember {FocusRequester()}
    val density=LocalDensity.current
    var synopsisHeight by remember(media.id) {mutableIntStateOf(0)}
    LaunchedEffect(media.id) {first.requestFocus()}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuBackdrop(media.backdrop,620)
        Box(Modifier.fillMaxWidth().height(620.dp).background(RokuCanvas.copy(alpha=.4f)))
        if(!media.poster.isNullOrBlank()) AsyncImage(media.poster,null,contentScale=ContentScale.Fit,modifier=Modifier.offset(112.dp,126.dp).size(236.dp,354.dp))
        RokuLabel(media.name,380,126,804,46,bold=true,marquee=true)
        RokuLabel(rokuFacts(media),380,198,706,18,lines=2,color=RokuMuted)
        if(!media.description.isNullOrBlank()) Text(media.description.orEmpty(),color=Color(0xFFD5D6D7),fontSize=23.sp,maxLines=4,overflow=TextOverflow.Ellipsis,onTextLayout={synopsisHeight=with(density){it.size.height.toDp().value.toInt()}},modifier=Modifier.offset(380.dp,276.dp).width(804.dp).heightIn(max=128.dp))
        Row(Modifier.offset(380.dp,(276+synopsisHeight+28).dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TvButton(if(media.positionMillis>0)"Resume" else "Play",{controller.chooseSources(media,media.positionMillis>0)},Modifier.size(192.dp,56.dp).focusRequester(first))
            TvButton("Choose source",{controller.chooseSources(media)},Modifier.size(192.dp,56.dp))
            TvButton("My List",{controller.toggleMyList(media)},Modifier.size(192.dp,56.dp))
        }
        RokuLabel(media.credits.orEmpty(),380,276+synopsisHeight+108,804,19,lines=3,color=RokuMuted)
    }
}

@Composable
private fun RokuSeriesDetails(media:Media,controller:AppController) {
    val seasons=media.episodes.mapNotNull {it.season}.distinct().sorted().ifEmpty {listOf(1)}
    var season by rememberSaveable(media.id) {mutableIntStateOf(media.season?:seasons.first())}
    var picker by remember {mutableStateOf(false)}
    var info by remember {mutableStateOf(false)}
    val rail=LocalRokuRailFocus.current
    val episodes=media.episodes.filter {(it.season?:1)==season}.sortedBy {it.episode}
    val episodeFocus=remember(episodes) {episodes.map {FocusRequester()}}
    val first=episodeFocus.firstOrNull()?:remember {FocusRequester()}
    val grid=rememberLazyGridState()
    val scope=rememberCoroutineScope()
    val infoFocus=remember {FocusRequester()}
    val seasonFocus=remember {FocusRequester()}
    LaunchedEffect(media.id,season) {if(episodes.isNotEmpty())first.requestFocus()else seasonFocus.requestFocus()}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuLabel(media.name,112,74,900,36,bold=true,marquee=true)
        RokuLabel(rokuFacts(media),112,132,900,18,color=RokuMuted)
        Row(Modifier.offset(112.dp,188.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            TvButton("${if(season==0)"Specials"else"Season $season"}  ▾",{picker=true},Modifier.size(256.dp,48.dp).focusRequester(seasonFocus))
            TvButton("My List",{controller.toggleMyList(media)},Modifier.size(256.dp,48.dp))
            TvButton("More info",{info=true},Modifier.size(256.dp,48.dp))
        }
        RokuLabel("${episodes.size} episodes",976,198,220,22,bold=true,align=androidx.compose.ui.text.style.TextAlign.End)
        if(episodes.isEmpty()) RokuLabel("No episodes available",250,360,780,24,align=androidx.compose.ui.text.style.TextAlign.Center)
        LazyVerticalGrid(columns=GridCells.Fixed(4),state=grid,modifier=Modifier.offset(112.dp,262.dp).size(1096.dp,330.dp).clip(androidx.compose.ui.graphics.RectangleShape),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
            itemsIndexed(episodes,key={_,episode->episode.id}) {index,episode ->
                RokuEpisode(episode.copy(episodes=media.episodes,backdrop=episode.backdrop?:media.backdrop,year=episode.year?:media.year,runtime=episode.runtime?:media.runtime,genres=episode.genres.ifEmpty {media.genres}),controller,Modifier.focusRequester(episodeFocus[index]).onFocusChanged {if(it.hasFocus)scope.launch {grid.scrollToItem(index/4*4)}}.onPreviewKeyEvent { event ->
                    val key=event.nativeKeyEvent
                    if(key.action!=KeyEvent.ACTION_DOWN) false else when {
                        key.keyCode==KeyEvent.KEYCODE_DPAD_LEFT&&index%4==0->{rail.requestFocus();true}
                        key.keyCode==KeyEvent.KEYCODE_DPAD_UP&&index<4->{seasonFocus.requestFocus();true}
                        key.keyCode==KeyEvent.KEYCODE_DPAD_UP||key.keyCode==KeyEvent.KEYCODE_DPAD_DOWN->{
                            val target=if(key.keyCode==KeyEvent.KEYCODE_DPAD_UP)index-4 else if(index/4<episodes.lastIndex/4)(index+4).coerceAtMost(episodes.lastIndex)else index
                            scope.launch {grid.scrollToItem(target/4*4);withFrameNanos {};episodeFocus[target].requestFocus()};true
                        }
                        else->false
                    }
                })
            }
        }
        if(picker) RokuChoiceSheet("Choose season",seasons.map {value->(if(value==0)"Specials"else"Season $value") to {season=value}}, {picker=false;scope.launch {withFrameNanos {};seasonFocus.requestFocus()}})
        if(info) {
            BackHandler {info=false}
            Box(Modifier.fillMaxSize().background(Color(0xF8101112))) {
                RokuLabel(media.name,100,72,1060,44,bold=true)
                RokuLabel(listOfNotNull(media.description,media.credits).joinToString("\n\n"),100,158,1060,23,lines=14)
                TvButton("Back",{info=false},Modifier.offset(100.dp,650.dp).size(180.dp,48.dp).focusRequester(infoFocus))
                LaunchedEffect(Unit){infoFocus.requestFocus()}
            }
        }
    }
}

@Composable
private fun RokuEpisode(episode:Media,controller:AppController,modifier:Modifier) {
    var focused by remember {mutableStateOf(false)}
    Holdable({controller.chooseSources(episode)},{controller.requestDialog(DialogKind.EpisodeManage,episode.episodeTitle?:episode.name,media=episode)},modifier.size(256.dp,330.dp).onFocusChanged {focused=it.hasFocus}.then(if(focused)Modifier.border(2.dp,RokuWhite,RoundedCornerShape(8.dp))else Modifier)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(256.dp,144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)),contentAlignment=Alignment.Center) {
                Text(episode.episodeTitle?:episode.name,color=RokuMuted,fontSize=19.sp,maxLines=3,modifier=Modifier.padding(12.dp))
                AsyncImage(episode.thumbnail?:episode.backdrop?:episode.poster,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                if(episode.watched) Box(Modifier.offset(160.dp,10.dp).align(Alignment.TopStart).size(86.dp,26.dp).background(RokuWhite,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {Text("WATCHED",color=RokuCanvas,fontSize=14.sp)}
                val duration=episode.durationMillis
                if(!episode.watched&&duration!=null&&duration>0&&episode.positionMillis>0) Box(Modifier.offset(8.dp,134.dp).align(Alignment.TopStart).size((240f*episode.positionMillis/duration).coerceIn(0f,240f).dp,4.dp).background(RokuWhite))
            }
            RokuLabel("EPISODE ${episode.episode?:""}",0,158,256,14,color=RokuMuted)
            RokuLabel(episode.episodeTitle?:episode.name,0,190,256,22,bold=true,marquee=focused)
            RokuLabel(episode.description.orEmpty(),0,226,256,19,lines=4,color=if(focused)Color(0xFFC5C6C7)else RokuMuted)
        }
    }
}
