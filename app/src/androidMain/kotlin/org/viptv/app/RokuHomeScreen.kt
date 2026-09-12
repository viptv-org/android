package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
internal fun RokuHomeScreen(state: AppState, controller: AppController) {
    val shelves = state.shelves.filter { it.items.isNotEmpty() }
    val railFocus=LocalRokuRailFocus.current
    val saved = state.homeFocus
    var row by remember { mutableIntStateOf((saved.shelfIndex ?: 0).coerceIn(0, shelves.lastIndex.coerceAtLeast(0))) }
    var column by remember { mutableIntStateOf(shelves.getOrNull(row)?.items?.indexOfFirst { HomeFocusPolicy.mediaKey(it) == saved.mediaKey }?.coerceAtLeast(0) ?: 0) }
    val hero = shelves.getOrNull(row)?.items?.getOrNull(column) ?: shelves.firstOrNull()?.items?.firstOrNull()
    val expanded = row == 0
    val scope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    var heroFocused by remember { mutableStateOf(false) }
    val requesters = remember(shelves.map { it.title to it.items.map(Media::id) }) { shelves.map { shelf -> shelf.items.map { FocusRequester() } } }
    val horizontal = remember(requesters) {shelves.map {LazyListState()}}
    fun activate(media: Media, queue: Boolean, heroAction: Boolean = false) {
        val action = MediaCardPolicy.primary(queue, media)
        when {
            action == MediaCardAction.PlayQueuedNext -> controller.playQueuedNext(media)
            action == MediaCardAction.ResumeExactSource -> controller.chooseSources(media, true, SourceReturn.Home)
            heroAction && HomeHeroPrimaryPolicy.choosesManualSource(action, queue, media) -> controller.chooseSources(media, origin=SourceReturn.Home)
            heroAction && media.type=="live" -> controller.chooseSources(media,origin=SourceReturn.Home)
            else -> controller.open(media)
        }
    }
    var restoredRequest by remember {mutableStateOf<Long?>(null)}
    LaunchedEffect(saved.restoreRequest, requesters) {
        if(restoredRequest==saved.restoreRequest)return@LaunchedEffect
        val snapshot = state.homeFocus
        if (hero != null && HomeFocusPolicy.mayRestore(snapshot, controller.state.value.homeFocus.inputEpoch)) {
            if ((snapshot.surface == HomeFocusSurface.Hero || snapshot.mediaKey == null)&&expanded) {heroFocus.requestFocus();restoredRequest=snapshot.restoreRequest}
            else {
                horizontal.getOrNull(row)?.scrollToItem(column)
                withFrameNanos {}
                if(HomeFocusPolicy.mayRestore(snapshot,controller.state.value.homeFocus.inputEpoch)) {requesters.getOrNull(row)?.getOrNull(column)?.requestFocus();restoredRequest=snapshot.restoreRequest}
            }
        }
    }
    Box(Modifier.fillMaxSize().background(RokuCanvas).onPreviewKeyEvent {
        val key = it.nativeKeyEvent
        if (key.action == KeyEvent.ACTION_DOWN && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) controller.recordHomeDirectionalInput()
        false
    }) {
        if (expanded && hero != null) {
            RokuBackdrop(hero.backdrop)
            RokuLabel(if (shelves.firstOrNull()?.isQueueShelf == true) "CONTINUE WATCHING" else if (hero.type == "live") "LIVE NOW" else "FEATURED ${hero.type.uppercase()}",100,128,650,14,color=Color(0xFFC5C6C7))
            RokuLabel(hero.name,100,166,600,44,bold=true,marquee=true)
            RokuLabel(hero.description.orEmpty(),100,228,548,20,lines=3,color=Color(0xFFD5D6D7))
            RokuLabel(rokuHeroFacts(hero),100,322,650,18,color=Color(0xFFC5C6C7))
            val queue = shelves.firstOrNull()?.isQueueShelf == true
            Row(Modifier.offset(100.dp,375.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Holdable(onActivate={activate(hero,queue,true)}, onHold=when {queue&&QueuePolicy.canManage(hero)->{{controller.requestQueueManage(hero)}};HomeHoldPolicy.opensSourcesFromHero(false,hero)->{{controller.chooseSources(hero,origin=SourceReturn.Home)}};else->null}, modifier=Modifier.width(if(QueuePolicy.hasResolvedNext(hero))236.dp else 144.dp).height(50.dp).focusRequester(heroFocus).onPreviewKeyEvent { event -> if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN&&event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_DOWN) { requesters.firstOrNull()?.getOrNull(column)?.requestFocus();true } else if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN&&event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_LEFT) {railFocus.requestFocus();true} else false }.onFocusChanged { heroFocused=it.hasFocus; if(it.hasFocus) controller.recordHomeFocus(row,shelves[row].title,hero,HomeFocusSurface.Hero) }) {
                    RokuHeroAction(when {hero.type=="live"->"Watch live";QueuePolicy.hasResolvedNext(hero)->"Play next episode";hero.positionMillis>0->"Resume";hero.type=="series"&&hero.episode==null->"Episodes";else->"Play"},heroFocused)
                }
                TvButton(if(hero.type=="live")"Guide"else"Details",{if(hero.type=="live")controller.openGuide(LiveChannel(hero.id,hero.name,hero.poster))else controller.open(hero)},Modifier.width(if(QueuePolicy.hasResolvedNext(hero))236.dp else 144.dp).height(50.dp))
            }
        }
        if(shelves.isEmpty()) {
            RokuLabel(if(state.loading) "Loading…" else "Nothing here yet",250,304,780,32,bold=true,align=TextAlign.Center)
        } else Box(Modifier.offset(92.dp,if(expanded)466.dp else 100.dp).width(1188.dp).height(if(expanded)254.dp else 620.dp).clip(androidx.compose.ui.graphics.RectangleShape)) {
            // The reference window is positioned by logical shelf, never by a scroll
            // container. Focus bring-into-view cannot displace this vertical canvas.
            shelves.withIndex().filter {it.index in row..(row+1)}.forEach { (shelfIndex,shelf) ->
                key(shelf.title) {
                Column(Modifier.offset(8.dp,((shelfIndex-row)*254+8).dp).width(1180.dp).height(240.dp)) {
                    Text(shelf.title.uppercase(),color=RokuWhite,fontSize=18.sp,fontWeight=FontWeight.Bold,style=rokuSingleLineStyle(18),modifier=Modifier.height(32.dp))
                    LazyRow(state=horizontal[shelfIndex],horizontalArrangement=Arrangement.spacedBy(24.dp),modifier=Modifier.height(200.dp)) {
                        itemsIndexed(shelf.items) { cardIndex,media ->
                            RokuArtworkCard(media,Modifier.focusRequester(requesters[shelfIndex][cardIndex]).focusProperties {canFocus=shelfIndex in row..(row+1)}.onFocusChanged { if(it.hasFocus) {row=shelfIndex;column=cardIndex;controller.recordHomeFocus(shelfIndex,shelf.title,media)} }.onPreviewKeyEvent {
                                val event=it.nativeKeyEvent
                                if(event.action==KeyEvent.ACTION_DOWN && event.keyCode==KeyEvent.KEYCODE_DPAD_LEFT && cardIndex==0) {railFocus.requestFocus();true} else if(event.action==KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)) {
                                    val target=shelfIndex+if(event.keyCode==KeyEvent.KEYCODE_DPAD_UP)-1 else 1
                                    if(target<0) {heroFocus.requestFocus();true} else if(target<shelves.size) {
                                        row=target;column=cardIndex.coerceAtMost(shelves[target].items.lastIndex)
                                        val targetColumn=column
                                        val inputEpoch=controller.state.value.homeFocus.inputEpoch
                                        scope.launch {
                                            horizontal[target].scrollToItem(targetColumn)
                                            withFrameNanos {}
                                            if(controller.state.value.homeFocus.inputEpoch==inputEpoch) requesters[target][targetColumn].requestFocus()
                                        };true
                                    } else true
                                } else false
                            },onActivate={activate(media,shelf.isQueueShelf)},onHold=if(shelf.isQueueShelf){{controller.requestQueueManage(media)}}else null)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun RokuHeroAction(label:String,focused:Boolean) {
    Box(Modifier.fillMaxSize().background(if(focused)RokuWhite else RokuSurface,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {Text(label,color=if(focused)RokuCanvas else RokuWhite,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1)}
}

@Composable
internal fun RokuBackdrop(uri:String?,height:Int=720) {
    var sharpReady by remember(uri) {mutableStateOf(false)}
    val context=LocalContext.current
    Box(Modifier.width(1280.dp).height(height.dp).clip(androidx.compose.ui.graphics.RectangleShape)) {
        if(!uri.isNullOrBlank()) {
            AsyncImage(ImageRequest.Builder(context).data(uri).size(1280,720).build(),null,onSuccess={sharpReady=true},contentScale=ContentScale.Crop,modifier=Modifier.width(1280.dp).height(720.dp))
            if(!sharpReady) AsyncImage(ImageRequest.Builder(context).data(uri).size(256,144).build(),null,contentScale=ContentScale.Crop,modifier=Modifier.width(1280.dp).height(720.dp))
            AsyncImage("file:///android_asset/roku/images/ui-hero-left.png",null,contentScale=ContentScale.FillBounds,modifier=Modifier.fillMaxSize())
            AsyncImage("file:///android_asset/roku/images/ui-hero-bottom.png",null,contentScale=ContentScale.FillBounds,modifier=Modifier.fillMaxSize())
        }
    }
}

/** PresentationContext, PresentationFacts and HeroPanel.render from the Roku source. */
internal fun rokuContext(media: Media): String = when (media.queueStatus) {
    "caught_up" -> "You're caught up"
    "upcoming" -> "Next episode not released"
    "pending", "unavailable" -> "Find next episode"
    "next" -> "Up next · S${media.season} E${media.episode}"
    else -> listOfNotNull(
        media.season?.let { "Season $it" },
        media.episode?.let { "Episode $it" },
        media.episodeTitle?.takeIf { it.isNotBlank() },
        media.positionMillis.takeIf { it > 0 }?.let { "Resume at ${rokuResumeTime(it)}" },
    ).joinToString("  ·  ")
}
private fun rokuResumeTime(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds >= 3600) "${seconds / 3600}:${((seconds / 60) % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
internal fun rokuFacts(media: Media): String = listOfNotNull(
    media.type.uppercase().takeIf { media.type in listOf("movie", "series", "live") },
    media.year, media.imdbRating?.takeIf {it.isNotBlank()}?.let {"IMDb $it"}, media.runtime,
    media.genres.take(3).joinToString(" / ").takeIf { it.isNotBlank() },
).joinToString("  ·  ")
private fun rokuHeroFacts(media: Media): String = listOfNotNull(
    media.year, media.runtime,
    media.genres.take(2).joinToString(" / ").takeIf { it.isNotBlank() },
    rokuContext(media).takeIf { it.isNotBlank() },
).joinToString("  ·  ")

/** Roku labels use their explicit font metrics, not Material body typography leading. */
private fun rokuSingleLineStyle(size: Int) = TextStyle(
    lineHeight = size.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.Both),
)

@Composable
internal fun RokuLabel(text:String,x:Int,y:Int,width:Int,size:Int,lines:Int=1,bold:Boolean=false,color:Color=RokuWhite,marquee:Boolean=false,align:TextAlign=TextAlign.Start) {
    Text(text,color=color,fontSize=size.sp,style=if(lines==1)rokuSingleLineStyle(size)else TextStyle.Default,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,maxLines=lines,overflow=TextOverflow.Ellipsis,textAlign=align,modifier=Modifier.offset(x.dp,y.dp).width(width.dp).then(if(marquee)Modifier.basicMarquee(iterations=Int.MAX_VALUE)else Modifier))
}

@Composable
internal fun RokuArtworkCard(media:Media,modifier:Modifier=Modifier,onActivate:()->Unit,onHold:(()->Unit)?=null,height:Int=200) {
    var focused by remember {mutableStateOf(false)}
    val artwork=if(media.type=="live")media.poster else media.backdrop?.takeUnless {it==media.poster}?:media.thumbnail?:media.poster.takeIf {media.posterShape=="landscape"}
    var artworkReady by remember(artwork) {mutableStateOf(false)}
    Holdable(onActivate,onHold,modifier.width(256.dp).height(height.dp).onFocusChanged {focused=it.hasFocus}) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.width(256.dp).height(144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)),contentAlignment=Alignment.Center) {
                if(!artworkReady) Text(media.name,color=RokuMuted,fontSize=19.sp,maxLines=3,textAlign=TextAlign.Center,modifier=Modifier.padding(12.dp))
                AsyncImage(artwork,null,onSuccess={artworkReady=true},onError={artworkReady=false},contentScale=if(media.type=="live")ContentScale.Fit else ContentScale.Crop,modifier=if(media.type=="live")Modifier.size(176.dp,100.dp)else Modifier.fillMaxSize())
                val duration=media.durationMillis
                if(duration!=null&&duration>0&&media.positionMillis>0) {
                    Box(Modifier.offset(8.dp,134.dp).align(Alignment.TopStart).size(240.dp,6.dp).background(Color(0xFF4A4C4E)))
                    if(media.positionMillis>0)Box(Modifier.offset(8.dp,134.dp).align(Alignment.TopStart).size((240f*media.positionMillis.toFloat()/duration).coerceIn(6f,240f).dp,6.dp).background(RokuWhite,RoundedCornerShape(3.dp)))
                }
            }
            AsyncImage(rokuAsset("ui-card-focus.png"),null,contentScale=ContentScale.FillBounds,modifier=Modifier.size(256.dp,144.dp).alpha(if(focused)1f else .35f))
            RokuLabel(media.name,0,152,256,21,bold=true,marquee=focused)
            RokuLabel(rokuContext(media).ifBlank {rokuFacts(media)},0,178,256,17,color=RokuMuted,marquee=focused)
        }
    }
}

@Composable
internal fun RokuChoiceSheet(title:String,choices:List<Pair<String,()->Unit>>,onClose:()->Unit) {
    val first=remember {FocusRequester()}
    LaunchedEffect(title) {if(choices.isNotEmpty())first.requestFocus()}
    BackHandler(onBack=onClose)
    Box(Modifier.fillMaxSize().background(Color(0xDC080909)),contentAlignment=Alignment.Center) {
        Box(Modifier.width(880.dp).height((146+choices.size.coerceAtMost(7)*62).dp).background(Color(0xFF191B1D),RoundedCornerShape(12.dp))) {
            RokuLabel(title,44,32,792,32,bold=true)
            LazyColumn(Modifier.offset(44.dp,94.dp).width(792.dp).height((choices.size.coerceAtMost(7)*62).dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                itemsIndexed(choices) {index,choice ->TvButton(choice.first,{choice.second();onClose()},Modifier.width(792.dp).height(52.dp).then(if(index==0)Modifier.focusRequester(first)else Modifier))}
            }
        }
    }
}
