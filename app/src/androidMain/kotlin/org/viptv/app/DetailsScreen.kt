package org.viptv.app

import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun DetailsScreen(media:Media,controller:AppController) {
    if(media.type=="series") RokuSeriesDetails(media,controller) else RokuMovieDetails(media,controller)
}

@Composable
private fun RokuMovieDetails(media:Media,controller:AppController) {
    val first=remember {FocusRequester()}
    val density=LocalDensity.current
    var synopsisHeight by remember(media.id) {mutableIntStateOf(0)}
    var info by remember(media.id) {mutableStateOf(false)}
    val infoControl=remember {FocusRequester()}
    val scope=rememberCoroutineScope()
    val state by controller.state.collectAsState()
    val saved=state.favorites.any {it.type==media.type&&it.id==media.id}
    fun closeInfo() {info=false;scope.launch {withFrameNanos {};infoControl.requestFocus()}}
    LaunchedEffect(media.id) {first.requestFocus()}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuDetailAtmosphere(media.backdrop)
        if(!media.poster.isNullOrBlank()) RokuRemoteImage(media.poster,236,354,large=true,contentScale=ContentScale.Fit,modifier=Modifier.offset(112.dp,126.dp).size(236.dp,354.dp))
        RokuLabel(media.name,380,126,804,46,bold=true,marquee=true)
        RokuLabel(rokuFacts(media),380,198,706,22,lines=2,color=RokuMuted)
        if(!media.description.isNullOrBlank()) Text(media.description.orEmpty(),color=Color(0xFFD5D6D7),fontSize=23.sp,lineHeight=33.sp,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false),lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Top,LineHeightStyle.Trim.Both)),maxLines=4,overflow=TextOverflow.Ellipsis,onTextLayout={synopsisHeight=with(density){it.size.height.toDp().value.toInt()}},modifier=Modifier.offset(380.dp,276.dp).width(804.dp).heightIn(max=128.dp))
        Row(Modifier.offset(380.dp,(276+synopsisHeight+28).dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TvButton(if(media.positionMillis>0)"Resume at ${detailResumeTime(media.positionMillis)}"else"Choose source",{controller.chooseSources(media,media.positionMillis>0)},Modifier.size(192.dp,56.dp).focusRequester(first),onHold=if(media.positionMillis>0){{controller.chooseSources(media)}}else null)
            if(media.positionMillis>0)TvButton("Choose source",{controller.chooseSources(media)},Modifier.size(192.dp,56.dp))
            TvButton(if(saved)"Remove from My List"else"+ My List",{controller.toggleMyList(media)},Modifier.size(192.dp,56.dp))
            TvButton("More info",{info=true},Modifier.size(192.dp,56.dp).focusRequester(infoControl))
        }
        Text(media.credits.orEmpty(),fontSize=22.sp,lineHeight=32.sp,color=RokuMuted,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(380.dp,(276+synopsisHeight+108).dp).size(804.dp,104.dp))
        if(info)RokuFullDetails(media,::closeInfo)
    }
}

@Composable
private fun RokuSeriesDetails(media:Media,controller:AppController) {
    val seasons=media.episodes.mapNotNull {it.season}.distinct().sorted().ifEmpty {listOf(1)}
    // MainScene.episodeProgress chooses newest progress across the whole series.
    // A completed episode advances only to an already released, unwatched episode.
    val entryEpisode = remember(media.id, media.season, media.episode, media.episodes) {
        val ordered = media.episodes.sortedWith(compareBy<Media> { it.season ?: Int.MAX_VALUE }.thenBy { it.episode ?: Int.MAX_VALUE })
        fun watched(item: Media): Boolean = item.watched ||
            (item.durationMillis?.let { it > 0 && item.positionMillis.toDouble() / it >= .95 } == true)
        val latest = ordered.filter { (it.updatedAtMillis ?: 0) > 0 }.maxByOrNull { it.updatedAtMillis ?: 0 }
        when {
            latest != null && watched(latest) -> ordered.drop(ordered.indexOf(latest) + 1).firstOrNull {
                !watched(it) && it.season != 0 && (it.releasedAtMillis == null || it.releasedAtMillis <= System.currentTimeMillis())
            } ?: latest
            latest != null -> latest
            else -> ordered.firstOrNull { it.season == media.season && it.episode == media.episode && media.episode != null }
        }
    }
    var season by rememberSaveable(media.id) {mutableIntStateOf(entryEpisode?.season ?: media.season ?: seasons.first())}
    var focusIntent by remember(media.id) {mutableStateOf("initial")}
    var pickedSeason by remember {mutableStateOf(false)}
    var picker by remember {mutableStateOf(false)}
    var info by remember {mutableStateOf(false)}
    val state by controller.state.collectAsState()
    val saved=state.favorites.any {it.type==media.type&&it.id==media.id}
    val rail=LocalRokuRailFocus.current
    val episodes=media.episodes.filter {(it.season?:1)==season}.sortedBy {it.episode}
    val episodeFocus=remember(episodes) {episodes.map {FocusRequester()}}
    val grid=rememberLazyGridState()
    val scope=rememberCoroutineScope()
    val infoControlFocus=remember {FocusRequester()}
    val seasonFocus=remember {FocusRequester()}
    LaunchedEffect(focusIntent,season,picker,info) {
        if(picker || info || focusIntent.isEmpty()) return@LaunchedEffect
        when(focusIntent) {
            "season" -> seasonFocus.requestFocus()
            "info" -> infoControlFocus.requestFocus()
            else -> if(episodes.isEmpty()) seasonFocus.requestFocus() else {
                val target = if(focusIntent == "initial") episodes.indexOfFirst {it.id == entryEpisode?.id}.coerceAtLeast(0) else 0
                grid.scrollToItem(target / 4 * 4)
                withFrameNanos {}
                episodeFocus[target].requestFocus()
            }
        }
        focusIntent = ""
    }
    fun closeInfo() {info=false;focusIntent="info"}
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        RokuDetailAtmosphere(media.backdrop)
        RokuLabel(media.name,112,74,900,36,bold=true,marquee=true)
        RokuLabel(rokuFacts(media),112,132,900,22,color=RokuMuted)
        Row(Modifier.offset(112.dp,188.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            RokuSeriesChip(if(season==0)"Specials"else"Season $season",{pickedSeason=false;picker=true},Modifier.size(256.dp,48.dp).focusRequester(seasonFocus),dropdown=true)
            RokuSeriesChip(if(saved)"Remove from My List"else"+ My List",{controller.toggleMyList(media)},Modifier.size(256.dp,48.dp))
            RokuSeriesChip("More info",{info=true},Modifier.size(256.dp,48.dp).focusRequester(infoControlFocus))
        }
        RokuLabel("${episodes.size} episodes",976,198,220,22,color=RokuMuted,align=androidx.compose.ui.text.style.TextAlign.End)
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
        if(picker) RokuChoiceSheet("Choose season",seasons.map {value->(if(value==0)"Specials"else"Season $value") to {season=value;pickedSeason=true}}, {picker=false;focusIntent=if(pickedSeason)"episodes"else"season"})
        if(info)RokuFullDetails(media,::closeInfo)
    }
}

@Composable
private fun RokuEpisode(episode:Media,controller:AppController,modifier:Modifier) {
    var focused by remember {mutableStateOf(false)}
    var artworkReady by remember(episode.thumbnail) {mutableStateOf(false)}
    Holdable({controller.chooseSources(episode)},{controller.requestDialog(DialogKind.EpisodeManage,episode.episodeTitle?:episode.name,media=episode)},modifier.size(256.dp,330.dp).onFocusChanged {focused=it.hasFocus}) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(256.dp,144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)),contentAlignment=Alignment.Center) {
                if(!artworkReady) {
                    AsyncImage(rokuAsset("viptv-mark.png"),null,modifier=Modifier.offset(112.dp,35.dp).align(Alignment.TopStart).size(32.dp))
                    RokuLabel("Preview unavailable",12,84,232,19,color=RokuMuted,align=androidx.compose.ui.text.style.TextAlign.Center)
                }
                RokuRemoteImage(episode.thumbnail,256,144,onReady={artworkReady=true},onFailure={artworkReady=false},contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                if(episode.watched) Box(Modifier.offset(160.dp,10.dp).align(Alignment.TopStart).size(86.dp,26.dp).background(RokuWhite,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {Text("WATCHED",color=RokuCanvas,fontSize=14.sp)}
                val duration=episode.durationMillis
                if(!episode.watched&&duration!=null&&duration>0&&episode.positionMillis>0) Box(Modifier.offset(8.dp,134.dp).align(Alignment.TopStart).size((240f*episode.positionMillis/duration).coerceIn(0f,240f).dp,4.dp).background(RokuWhite))
            }
            if(focused)AsyncImage(rokuAsset("ui-card-focus.png"),null,contentScale=ContentScale.FillBounds,modifier=Modifier.size(256.dp,144.dp))
            RokuLabel("EPISODE ${episode.episode?:""}",0,158,256,22,color=RokuMuted)
            RokuLabel(episode.episodeTitle?:episode.name,0,190,256,22,bold=true,marquee=focused)
            Text(episode.description.orEmpty(),fontSize=19.sp,lineHeight=28.sp,maxLines=4,overflow=TextOverflow.Ellipsis,color=if(focused)Color(0xFFC5C6C7)else RokuMuted,modifier=Modifier.offset(0.dp,226.dp).size(256.dp,94.dp))
        }
    }
}


private fun detailResumeTime(millis:Long):String {
    val seconds=millis/1000
    return if(seconds>=3600) "${seconds/3600}:${((seconds/60)%60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}" else "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
}

@Composable
private fun RokuDetailAtmosphere(backdrop:String?) {
    if(!backdrop.isNullOrBlank())Box(Modifier.size(1280.dp,620.dp)) {
        RokuRemoteImage(backdrop,1280,720,large=true,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(RokuCanvas.copy(alpha=.52f)))
        AsyncImage(rokuAsset("ui-hero-left.png"),null,contentScale=ContentScale.FillBounds,modifier=Modifier.fillMaxSize())
        AsyncImage(rokuAsset("ui-hero-bottom.png"),null,contentScale=ContentScale.FillBounds,modifier=Modifier.fillMaxSize())
    }
}

@Composable
private fun RokuFullDetails(media:Media,onClose:()->Unit) {
    val focus=remember {FocusRequester()}
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    BackHandler(onBack=onClose)
    LaunchedEffect(Unit){focus.requestFocus()}
    Box(Modifier.fillMaxSize().background(Color(0xF8101112))) {
        RokuLabel(media.name,100,72,1060,44,bold=true)
        Text(listOf(rokuFacts(media),media.description.orEmpty(),media.credits.orEmpty()).filter {it.isNotBlank()}.joinToString("\n\n"),color=RokuWhite,fontSize=23.sp,lineHeight=32.sp,modifier=Modifier.offset(100.dp,158.dp).size(1060.dp,460.dp).focusRequester(focus).onPreviewKeyEvent {
            val key=it.nativeKeyEvent
            if(key.action==KeyEvent.ACTION_DOWN&&key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP)) {
                scope.launch {scroll.animateScrollTo((scroll.value+if(key.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)160 else -160).coerceIn(0,scroll.maxValue))};true
            }else false
        }.focusable().verticalScroll(scroll))
        RokuLabel("Back to close",100,650,1060,19,color=RokuMuted)
    }
}


@Composable
private fun RokuSeriesChip(label:String,onActivate:()->Unit,modifier:Modifier,dropdown:Boolean=false) {
    var focused by remember {mutableStateOf(false)}
    TvButton(label,onActivate,modifier.onFocusChanged {focused=it.hasFocus},content={
        Box(Modifier.fillMaxSize()) {
            val foreground=if(focused)RokuCanvas else RokuMuted
            RokuLabel(label,18,11,if(dropdown)192 else 216,21,bold=true,color=foreground,marquee=focused)
            if(dropdown)AsyncImage(rokuAsset("ui-nav-chevron.png"),null,colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(foreground),modifier=Modifier.offset(222.dp,15.dp).size(18.dp))
        }
    })
}
