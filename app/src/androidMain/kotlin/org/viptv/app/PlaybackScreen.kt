package org.viptv.app

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.getair.video.PlaybackStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private enum class PlayerTrackMenu { Audio, Subtitles }

/** PlayerOverlay's two remote rows, with media/session policy delegated to the controller. */
@Composable
internal fun PlaybackScreen(media:Media,chromeVisible:Boolean,seekPreview:SeekPreview?,serverTracks:PlaybackTrackChoices,controller:AppController) {
    val playback by controller.player.state.collectAsState()
    val app by controller.state.collectAsState()
    val scope=rememberCoroutineScope()
    val live=media.type=="live"
    var row by remember(media.id) { mutableIntStateOf(if(live) 1 else 0) }
    var button by remember(media.id) { mutableIntStateOf(if(live) 3 else 1) }
    var scrubKey by remember { mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN) }
    var repeats by remember { mutableIntStateOf(0) }
    var justCommittedSeek by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf<PlayerTrackMenu?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val focus=remember { FocusRequester() }
    val position=controller.absolutePositionMillis()
    val duration=controller.titleDurationMillis() ?: playback.timeline?.durationMillis ?: 0
    val next=media.type=="series" && !live
    val order=if(live) listOf(3,4,5) else if(next) listOf(0,1,2,6,3,4,5) else listOf(0,1,2,3,4,5)
    fun cancelSeek() { seekJob?.cancel(); controller.cancelSeek(); scrubKey=KeyEvent.KEYCODE_UNKNOWN; repeats=0 }
    fun seek(delta:Long,key:Int) {
        if(live || duration<=0) return
        seekJob?.cancel()
        repeats=if(scrubKey==key) repeats+1 else 0
        scrubKey=key
        val multiplier=when { repeats>=15->60;repeats>=9->15;repeats>=5->6;repeats>=2->3;else->1 }
        controller.previewSeek(delta*multiplier);row=0
    }
    fun release() { scrubKey=KeyEvent.KEYCODE_UNKNOWN;repeats=0;seekJob?.cancel();seekJob=scope.launch {delay(800);if(controller.state.value.seekPreview!=null) {justCommittedSeek=true;controller.commitSeek()}} }
    fun toggle() { cancelSeek();if(!live) {if(playback.isPlaying) controller.pausePlayback() else controller.resumePlayback()} }
    fun activate(index:Int) {
        when(index) {
            0->{seek(-10_000,KeyEvent.KEYCODE_DPAD_CENTER);release()}
            1->toggle()
            2->{seek(30_000,KeyEvent.KEYCODE_DPAD_CENTER);release()}
            3->{cancelSeek();menu=PlayerTrackMenu.Audio;notice=null}
            4->{cancelSeek();menu=PlayerTrackMenu.Subtitles;notice=null}
            5->controller.exitPlayback()
            6->controller.nextEpisode(media)
        }
    }
    LaunchedEffect(media.id,position,duration,playback.isPlaying,playback.status) {controller.maybeAutoNext(media,position,duration.takeIf {it>0},playback.isPlaying,playback.status==PlaybackStatus.Ended)}
    LaunchedEffect(menu) {controller.setPlayerMenuOpen(menu!=null);if(menu==null) focus.requestFocus()}
    DisposableEffect(controller) {onDispose {seekJob?.cancel();controller.setPlayerMenuOpen(false)}}
    BackHandler(menu!=null) {if(notice!=null) notice=null else menu=null}
    val buffering=playback.status==PlaybackStatus.Opening || playback.isBuffering
    val shown=chromeVisible || buffering
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {event ->
        val key=event.nativeKeyEvent;val code=key.keyCode
        if(code==KeyEvent.KEYCODE_BACK) {
            seekJob?.cancel()
            if(menu!=null) {if(key.action==KeyEvent.ACTION_UP) {if(notice!=null) notice=null else menu=null};return@onPreviewKeyEvent true}
            return@onPreviewKeyEvent false
        }
        if(menu!=null) return@onPreviewKeyEvent false
        if(key.action==KeyEvent.ACTION_UP) {if(code==scrubKey) release();return@onPreviewKeyEvent true}
        if(key.action!=KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
        controller.showPlayerChrome()
        if(code !in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) justCommittedSeek=false
        when(code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if(key.repeatCount==0) toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if(!live && key.repeatCount==0) controller.resumePlayback()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if(!live && key.repeatCount==0) controller.pausePlayback()
            KeyEvent.KEYCODE_DPAD_DOWN -> {cancelSeek();row=1}
            KeyEvent.KEYCODE_DPAD_UP -> if(!live) row=0
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val left=code==KeyEvent.KEYCODE_DPAD_LEFT
                if(!shown && !live) row=0
                if(row==1) {val current=order.indexOf(button).coerceAtLeast(0);button=order[(current+if(left) order.size-1 else 1)%order.size]}
                else seek(if(left) -10_000 else 10_000,code)
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-60_000,code)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(60_000,code)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {seek(-10_000,code)}
            KeyEvent.KEYCODE_INFO,KeyEvent.KEYCODE_MENU -> {cancelSeek();row=1;button=3}
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER -> if(key.repeatCount==0) {
                if(seekPreview!=null && row==0) {seekJob?.cancel();justCommittedSeek=true;controller.commitSeek()}
                else if(justCommittedSeek) justCommittedSeek=false
                else if(shown) {if(row==0) toggle() else activate(button)}
            }
            else -> return@onPreviewKeyEvent false
        }
        true
    }.focusRequester(focus).focusable()) {
        AndroidView(factory={SurfaceView(it).also(controller.player::attach)},modifier=Modifier.fillMaxSize())
        if(shown) {
            PlayerAsset("player-gradient-top.png",Modifier.size(1280.dp,210.dp))
            PlayerAsset("player-gradient-bottom.png",Modifier.offset(y=382.dp).size(1280.dp,338.dp))
            val liveChannel=app.liveChannels.firstOrNull {it.id==media.id} ?: app.guideUi.channels.firstOrNull {it.id==media.id}
            val programme=(app.guideUi.schedulesByChannelId[media.id] ?: app.guide).firstOrNull {it.startMillis<=System.currentTimeMillis() && it.endMillis>System.currentTimeMillis()}
            if(live) {
                val logo=liveChannel?.logo ?: media.poster
                if(!logo.isNullOrBlank()) {
                    Box(Modifier.offset(64.dp,34.dp).size(84.dp,56.dp).background(Color(0xE8242628),RoundedCornerShape(12.dp)))
                    AsyncImage(logo,liveChannel?.name,contentScale=ContentScale.Fit,modifier=Modifier.offset(72.dp,40.dp).size(68.dp,48.dp))
                }
                Text(media.name,color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(if(logo.isNullOrBlank()) 64.dp else 164.dp,42.dp).size(1018.dp,36.dp))
            } else {
                PlayerAsset("viptv-mark.png",Modifier.offset(64.dp,40.dp).size(36.dp,32.dp))
                Text(media.episodeTitle ?: media.name,color=RokuWhite,fontSize=26.sp,fontWeight=FontWeight.Bold,maxLines=1,modifier=Modifier.offset(112.dp,36.dp).size(700.dp,40.dp))
            }
            Text(if(buffering) "LOADING" else if(live) "● LIVE" else if(playback.isPlaying) "PLAYING" else "PAUSED",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.End,modifier=Modifier.offset(1048.dp,36.dp).size(168.dp,40.dp))
            Text(if(live) "ON NOW  ·  LIVE TV" else "NOW PLAYING",color=Color(0xFFC5C6C7),fontSize=18.sp,modifier=Modifier.offset(64.dp,460.dp).size(1100.dp,26.dp))
            Text(if(live) programme?.title ?: media.name else media.name,color=RokuWhite,fontSize=32.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(64.dp,486.dp).width(1090.dp))
            Text(playerContext(media,seekPreview),color=Color(0xFFBFC1C3),fontSize=20.sp,modifier=Modifier.offset(64.dp,524.dp).size(1152.dp,30.dp))
            if(!live) {
                val fraction=if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f
                val preview=if(duration>0) ((seekPreview?.targetMillis ?: position).toFloat()/duration).coerceIn(0f,1f) else 0f
                Box(Modifier.offset(64.dp,572.dp).size(1152.dp,6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF5A5C5E)))
                if(fraction>0) Box(Modifier.offset(64.dp,572.dp).size((1152*fraction).dp,6.dp).clip(RoundedCornerShape(3.dp)).background(RokuWhite))
                if(seekPreview!=null) Box(Modifier.offset((64+1152*fraction-1).dp,569.dp).size(3.dp,10.dp).background(RokuMuted))
                if(row==0 && duration>0) PlayerAsset("player-circle.png",Modifier.offset((64+1152*preview-8).dp,567.dp).size(16.dp))
            } else if(programme!=null) {
                val fraction=((System.currentTimeMillis()-programme.startMillis).toFloat()/(programme.endMillis-programme.startMillis)).coerceIn(0f,1f)
                Box(Modifier.offset(64.dp,572.dp).size(1152.dp,6.dp).background(Color(0xFF5A5C5E),RoundedCornerShape(3.dp)))
                Box(Modifier.offset(64.dp,572.dp).size((1152*fraction).dp,6.dp).background(RokuWhite,RoundedCornerShape(3.dp)))
            }
            Text(if(live) if(programme!=null) "ON NOW" else "LIVE" else formatTime(seekPreview?.targetMillis ?: position),color=RokuWhite,fontSize=20.sp,modifier=Modifier.offset(64.dp,584.dp).size(576.dp,32.dp))
            Text(if(live) if(programme!=null) "${(programme.endMillis-System.currentTimeMillis()+59_999)/60_000} min left" else "Live broadcast" else formatTime(duration),color=RokuMuted,fontSize=20.sp,textAlign=TextAlign.End,modifier=Modifier.offset(640.dp,584.dp).size(576.dp,32.dp))
            order.forEach {index ->
                val x=if(live) mapOf(3 to 64,4 to 608,5 to 1152).getValue(index) else listOf(64,144,224,992,1072,1152,304)[index]
                val focused=row==1 && button==index && menu==null
                val glyph=when(index) {0->"rewind";1->if(playback.isPlaying) "pause" else "play";2,6->"forward";3->"audio";4->"captions";else->"exit"}
                Box(Modifier.offset(x.dp,624.dp).size(64.dp).background(if(focused) RokuWhite else Color.Transparent,RoundedCornerShape(12.dp)).clickable {row=1;button=index;activate(index)},contentAlignment=Alignment.Center) {
                    PlayerAsset("ui-nav-player-$glyph.png",Modifier.size(28.dp),if(focused) RokuCanvas else RokuWhite,when(index) {0->"Rewind 10 seconds";1->if(playback.isPlaying) "Pause" else "Resume";2->"Forward 30 seconds";3->"Audio";4->"Captions";5->"Exit";else->"Next episode"})
                }
            }
        }
        if(buffering) RokuSpinner(Modifier.offset(610.dp,330.dp).size(60.dp))
        menu?.let {active ->
            PlayerTrackDialog(active,if(active==PlayerTrackMenu.Audio) serverTracks.audio else serverTracks.subtitles,active==PlayerTrackMenu.Subtitles && serverTracks.subtitlesSupported,notice,
                onAudio={controller.selectAudioTrack(it);menu=null},onText={controller.selectSubtitleTrack(it);menu=null},onUnavailable={notice="This track is not supported on this TV."},onClose={menu=null})
        }
    }
}

@Composable
private fun PlayerAsset(name:String,modifier:Modifier,tint:Color?=null,description:String?=null) {
    AsyncImage("file:///android_asset/roku/images/$name",description,contentScale=ContentScale.FillBounds,colorFilter=tint?.let { ColorFilter.tint(it) },modifier=modifier)
}

@Composable
private fun PlayerTrackDialog(menu:PlayerTrackMenu,tracks:List<PlaybackTrack>,canDisable:Boolean,notice:String?,onAudio:(PlaybackTrack)->Unit,onText:(PlaybackTrack?)->Unit,onUnavailable:()->Unit,onClose:()->Unit) {
    var page by remember(menu) {mutableIntStateOf(0)}
    var selected by remember(menu,page) {mutableIntStateOf(0)}
    val focus=remember {FocusRequester()}
    val pageCount=max(1,(tracks.size+4)/5)
    val entries=buildList<Pair<String,()->Unit>> {
        if(canDisable) add("Off" to {onText(null)})
        tracks.drop(page.coerceIn(0,pageCount-1)*5).take(5).forEach {track ->
            val name=track.title.ifBlank {track.language ?: "Track ${track.inputIndex+1}"}
            add((if(!track.selectable || !track.supported) "Unavailable · $name" else if(track.selected) "Playing · $name" else name) to {if(!track.selectable || !track.supported) onUnavailable() else if(menu==PlayerTrackMenu.Audio) onAudio(track) else onText(track)})
        }
        if(page+1<pageCount) add("More tracks" to {page++})
        if(page>0) add("Previous tracks" to {page--})
        add("Back to player" to onClose)
    }
    val panelHeight=146+minOf(entries.size,7)*62
    val panelTop=(720-panelHeight)/2
    LaunchedEffect(menu,page) {focus.requestFocus()}
    Box(Modifier.fillMaxSize().background(Color(0xDC080909)).onPreviewKeyEvent {event ->
        val key=event.nativeKeyEvent
        if(key.keyCode==KeyEvent.KEYCODE_BACK) false
        else if(key.action!=KeyEvent.ACTION_DOWN) true
        else {when(key.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP->selected=(selected-1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN->selected=(selected+1).coerceAtMost(entries.lastIndex)
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER->if(key.repeatCount==0) entries[selected.coerceAtMost(entries.lastIndex)].second()
        };true}
    }.focusRequester(focus).focusable()) {
        Box(Modifier.offset(200.dp,panelTop.dp).size(880.dp,panelHeight.dp).background(Color(0xFF191B1D),RoundedCornerShape(12.dp)))
        Text(if(menu==PlayerTrackMenu.Audio) "Audio tracks" else "Subtitles",color=RokuWhite,fontSize=36.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(244.dp,(panelTop+32).dp).size(792.dp,54.dp))
        val first=max(0,selected-6)
        entries.drop(first).take(7).forEachIndexed {slot,entry ->
            val active=first+slot==selected
            Box(Modifier.offset(244.dp,(panelTop+94+slot*62).dp).size(792.dp,52.dp).background(if(active) RokuWhite else Color.Transparent,RoundedCornerShape(10.dp)).clickable(onClick=entry.second),contentAlignment=Alignment.CenterStart) {
                Text(entry.first,color=if(active) RokuCanvas else RokuWhite,fontSize=20.sp,maxLines=1,modifier=Modifier.padding(start=18.dp))
            }
        }
        Text(notice ?: when { tracks.isEmpty() -> if(menu==PlayerTrackMenu.Audio) "This stream supplies no selectable audio tracks." else "This stream supplies no selectable subtitles."; menu==PlayerTrackMenu.Audio -> "Choose any available audio track. Language labels are informational."; !canDisable -> "Subtitles are unavailable for this output. Listed tracks cannot currently be displayed."; else -> "Select a supported text track. Image subtitles cannot be displayed." },color=RokuMuted,fontSize=16.sp,maxLines=2,modifier=Modifier.offset(244.dp,(panelTop+panelHeight-50).dp).size(792.dp,44.dp))
    }
}

private fun formatTime(millis:Long):String {
    val total=max(0,millis/1000);val h=total/3600;val m=total%3600/60;val s=total%60
    return if(h>0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)
}
private fun playerContext(media:Media,seek:SeekPreview?):String = if(seek!=null) "Seeking to ${formatTime(seek.targetMillis)}…" else buildString {
    media.season?.let {append("Season $it")};media.episode?.let {if(isNotEmpty()) append(" · ");append("Episode $it")}
    media.episodeTitle?.takeIf {it!=media.name}?.let {if(isNotEmpty()) append(" · ");append(it)}
}
