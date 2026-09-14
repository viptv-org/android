package org.viptv.app

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The query label, inline keyboard and two source-labelled 236-pixel shelves. */
@Composable
internal fun SearchScreen(state: AppState, controller: AppController) {
    val fieldFocus = remember { FocusRequester() }
    val resultFocus = remember { FocusRequester() }
    val hasResults = state.searchSections.any { it.items.isNotEmpty() }
    val sectionsScroll = rememberLazyListState()
    val firstRowScroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstSection = state.searchSections.indexOfFirst { it.items.isNotEmpty() }
    fun focusResults() { if (hasResults) scope.launch { sectionsScroll.scrollToItem(firstSection); firstRowScroll.scrollToItem(0); withFrameNanos {}; resultFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(RokuCanvas)) {
        Text("Search",color=Color.White,fontSize=44.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(100.dp,54.dp))
        Text(state.searchQuery.ifEmpty { "Search movies and shows" },color=RokuWhite,fontSize=26.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(100.dp,164.dp).size(304.dp,44.dp))
        SearchKeyboard(state.searchQuery,controller::search,Modifier.offset(96.dp,216.dp),onResults=::focusResults,firstFocus=fieldFocus)
        Text("Type here or use a connected keyboard. Play/Pause opens results.",color=RokuMuted,fontSize=20.sp,modifier=Modifier.offset(100.dp,572.dp).size(304.dp,64.dp))
        LazyColumn(Modifier.offset(456.dp,164.dp).size(740.dp,484.dp).clipToBounds(),verticalArrangement=Arrangement.spacedBy(8.dp),state=sectionsScroll) {
            itemsIndexed(state.searchSections,key={index,section->"${section.source}:$index"}) { sectionIndex,section ->
                Column(Modifier.height(236.dp)) {
                    Text(section.source,color=RokuWhite,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.height(36.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(20.dp),state=if(sectionIndex==firstSection) firstRowScroll else rememberLazyListState()) {
                        itemsIndexed(section.items,key={_,item->item.type+":"+item.id}) { index,item ->
                            Box(Modifier.onPreviewKeyEvent { event ->
                                if(event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_INFO,KeyEvent.KEYCODE_MENU) && item.type!="live") { if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount==0) controller.toggleMyList(item); true } else if(index==0 && event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_LEFT) { fieldFocus.requestFocus(); true } else false
                            }) { RokuArtworkCard(item,modifier=if(sectionIndex==state.searchSections.indexOfFirst { it.items.isNotEmpty() } && index==0) Modifier.focusRequester(resultFocus) else Modifier,onActivate={controller.activateCard(item)}) }
                        }
                    }
                }
            }
        }
        Text(state.searchStatus,color=RokuMuted,fontSize=20.sp,modifier=Modifier.offset(456.dp,660.dp).size(740.dp,36.dp))
    }
}

/** Roku MiniKeyboard equivalent: outline focus and hardware text entry share the same edit path. */
@Composable
internal fun SearchKeyboard(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,onResults:()->Unit,compact:Boolean=true,firstFocus:FocusRequester?=null) {
    val letters=remember { "abcdefghijklmnopqrstuvwxyz1234567890".map(Char::toString)+listOf("Clear","Space","Delete") }
    val requesters=remember { letters.map { FocusRequester() } }
    var uppercase by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val first=firstFocus ?: requesters.first()
    LaunchedEffect(Unit) { first.requestFocus() }
    val keyWidth=if(compact) 50 else 62
    val keyHeight=if(compact) 46 else 36
    Column(modifier.offset(if(compact) 6.dp else 0.dp,if(compact) 10.dp else 0.dp).width(if(compact) 300.dp else 460.dp).onPreviewKeyEvent { event ->
        val key=event.nativeKeyEvent
        if(key.action!=KeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode==KeyEvent.KEYCODE_ENTER || key.keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER || key.keyCode==KeyEvent.KEYCODE_MEDIA_PLAY || key.keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { onResults(); true }
            key.keyCode==KeyEvent.KEYCODE_DEL -> { onValueChange(value.dropLast(1)); true }
            key.keyCode==KeyEvent.KEYCODE_CAPS_LOCK -> { uppercase=!uppercase; true }
            key.unicodeChar>=32 && key.unicodeChar!=127 -> { onValueChange((value+key.unicodeChar.toChar()).take(256)); true }
            else -> false
        }
    },verticalArrangement=Arrangement.spacedBy(0.dp)) {
        letters.chunked(6).forEachIndexed { row,keys ->
            Row(Modifier.zIndex(if(focusedIndex/6==row) 1f else 0f),horizontalArrangement=Arrangement.spacedBy(0.dp)) {
                keys.forEachIndexed { col,label ->
                    val index=row*6+col
                    var focused by remember { mutableStateOf(false) }
                    Holdable(onActivate={onValueChange(when(label) {"Space"->value+" ";"Delete"->value.dropLast(1);"Clear"->"";else->value+if(uppercase) label.uppercase() else label }.take(256))},onHold=null,
                        modifier=Modifier.size(if(row==6) (keyWidth*2).dp else keyWidth.dp,keyHeight.dp).focusRequester(if(index==0) first else requesters[index]).onFocusChanged { focused=it.hasFocus; if(it.hasFocus) focusedIndex=index }.zIndex(if(focused) 1f else 0f).background(Color(0xFF303234)).border(0.5.dp,Color(0xFF27292B)).onPreviewKeyEvent { event ->
                            if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT && col==keys.lastIndex) { onResults(); true } else false
                        }) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                            if(row==6) SearchKeyGlyph(label) else Text(if(uppercase) label.uppercase() else label,color=RokuWhite,fontSize=20.sp,fontWeight=FontWeight.Bold)
                            if(focused) Canvas(Modifier.matchParentSize()) { drawRoundRect(RokuWhite,topLeft=Offset(-6.dp.toPx(),-7.dp.toPx()),size=Size(size.width+12.dp.toPx(),size.height+14.dp.toPx()),cornerRadius=CornerRadius(5.dp.toPx()),style=Stroke(1.5.dp.toPx())) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchKeyGlyph(label:String) {
    Canvas(Modifier.size(20.dp)) {
        val ink=RokuWhite
        val unit=size.width/20f
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(ink,Offset(x1*unit,y1*unit),Offset(x2*unit,y2*unit),1.2f*unit)
        when(label) {
            "Space" -> { line(3f,10f,3f,13f);line(3f,13f,17f,13f);line(17f,13f,17f,10f) }
            "Clear" -> { line(4f,6f,16f,6f);line(6f,6f,7f,18f);line(7f,18f,14f,18f);line(14f,18f,15f,6f);line(8f,4f,12f,4f);line(8f,4f,8f,6f);line(12f,4f,12f,6f);line(9f,8f,9f,16f);line(12f,8f,12f,16f) }
            else -> {
                val outline=Path().apply {moveTo(2*unit,11*unit);lineTo(7*unit,6*unit);lineTo(18*unit,6*unit);lineTo(18*unit,16*unit);lineTo(7*unit,16*unit);close()}
                drawPath(outline,ink,style=Stroke(1.2f*unit));line(10f,9f,14f,13f);line(10f,13f,14f,9f)
            }
        }
    }
}
