package org.viptv.app

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(Modifier.fillMaxSize().background(Color(0xFF101112))) {
        Text("Search",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.offset(112.dp,34.dp))
        Text(state.searchQuery.ifEmpty { "Search movies and shows" },color=Color(0xFFF5F5F5),fontSize=26.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(100.dp,164.dp).size(304.dp,44.dp))
        SearchKeyboard(state.searchQuery,controller::search,Modifier.offset(96.dp,216.dp),onResults={if(hasResults) resultFocus.requestFocus()},firstFocus=fieldFocus)
        Text("Type here or use a connected keyboard. Play/Pause opens results.",color=Color(0xFFA6A8AA),fontSize=20.sp,modifier=Modifier.offset(100.dp,572.dp).size(304.dp,64.dp))
        LazyColumn(Modifier.offset(456.dp,164.dp).size(740.dp,484.dp).clipToBounds(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.searchSections,key={index,section->"${section.source}:$index"}) { sectionIndex,section ->
                Column(Modifier.height(236.dp)) {
                    Text(section.source,color=Color(0xFFF5F5F5),fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.height(36.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        itemsIndexed(section.items,key={_,item->item.type+":"+item.id}) { index,item ->
                            Box(Modifier.onPreviewKeyEvent { event ->
                                if(index==0 && event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_LEFT) { fieldFocus.requestFocus(); true } else false
                            }) { RokuArtworkCard(item,modifier=if(sectionIndex==state.searchSections.indexOfFirst { it.items.isNotEmpty() } && index==0) Modifier.focusRequester(resultFocus) else Modifier,onActivate={controller.open(item)}) }
                        }
                    }
                }
            }
        }
        Text(state.searchStatus,color=Color(0xFFA6A8AA),fontSize=20.sp,modifier=Modifier.offset(456.dp,660.dp).size(740.dp,36.dp))
    }
}

/** Roku MiniKeyboard equivalent: outline focus and hardware text entry share the same edit path. */
@Composable
internal fun SearchKeyboard(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,onResults:()->Unit,compact:Boolean=true,firstFocus:FocusRequester?=null) {
    val letters=remember { "abcdefghijklmnopqrstuvwxyz0123456789".map(Char::toString)+listOf("Space","Delete","Clear") }
    val requesters=remember { letters.map { FocusRequester() } }
    var uppercase by remember { mutableStateOf(false) }
    val first=firstFocus ?: requesters.first()
    LaunchedEffect(Unit) { first.requestFocus() }
    val keyWidth=if(compact) 48 else 62
    val keyHeight=if(compact) 42 else 36
    Column(modifier.width(if(compact) 308.dp else 460.dp).onPreviewKeyEvent { event ->
        val key=event.nativeKeyEvent
        if(key.action!=KeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode==KeyEvent.KEYCODE_ENTER || key.keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER || key.keyCode==KeyEvent.KEYCODE_MEDIA_PLAY || key.keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { onResults(); true }
            key.keyCode==KeyEvent.KEYCODE_DEL -> { onValueChange(value.dropLast(1)); true }
            key.keyCode==KeyEvent.KEYCODE_CAPS_LOCK -> { uppercase=!uppercase; true }
            key.unicodeChar>=32 && key.unicodeChar!=127 -> { onValueChange((value+key.unicodeChar.toChar()).take(256)); true }
            else -> false
        }
    },verticalArrangement=Arrangement.spacedBy(6.dp)) {
        letters.chunked(6).forEachIndexed { row,keys ->
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                keys.forEachIndexed { col,label ->
                    val index=row*6+col
                    var focused by remember { mutableStateOf(false) }
                    Holdable(onActivate={onValueChange(when(label) {"Space"->value+" ";"Delete"->value.dropLast(1);"Clear"->"";else->value+if(uppercase) label.uppercase() else label }.take(256))},onHold=null,
                        modifier=Modifier.size(if(row==6) (keyWidth*2+4).dp else keyWidth.dp,keyHeight.dp).focusRequester(if(index==0) first else requesters[index]).onFocusChanged { focused=it.hasFocus }.then(if(focused) Modifier.border(2.dp,Color(0xFFF5F5F5),RoundedCornerShape(8.dp)) else Modifier).onPreviewKeyEvent { event ->
                            if(event.nativeKeyEvent.action==KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT && col==keys.lastIndex) { onResults(); true } else false
                        }) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(if(uppercase) label.uppercase() else label,color=Color(0xFFF5F5F5),fontSize=if(row==6) 16.sp else 22.sp) }
                    }
                }
            }
        }
    }
}
