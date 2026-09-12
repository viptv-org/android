package org.viptv.app

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

/** Two focus regions in the canonical Search canvas; query text is never a focus target. */
@Composable
internal fun SearchScreen(state: AppState, controller: AppController) {
    val query = state.searchQuery
    val keys = remember { ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").map(Char::toString) + listOf("Space", "Delete", "Clear") }
    val keyFocus = remember { keys.map { FocusRequester() } }
    var lastKey by remember { mutableIntStateOf(0) }
    val resultFocus = remember { FocusRequester() }
    val hasResults = state.searchSections.any { it.items.isNotEmpty() }
    fun results() { if (hasResults) resultFocus.requestFocus() }
    fun edit(value: String) { controller.search(value.take(256)) }
    LaunchedEffect(Unit) { keyFocus.first().requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xFF101112))) {
        Text("Search", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(100.dp, 54.dp))
        Text(query.ifEmpty { "Search" }, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(100.dp, 164.dp).size(304.dp, 44.dp))
        Column(Modifier.offset(96.dp, 216.dp).width(308.dp).onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.action == KeyEvent.ACTION_DOWN && key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { results(); true }
            else if (key.action == KeyEvent.ACTION_DOWN && key.keyCode == KeyEvent.KEYCODE_DEL) { edit(query.dropLast(1)); true }
            else if (key.action == KeyEvent.ACTION_DOWN && key.unicodeChar in 32..126) { edit(query + key.unicodeChar.toChar()); true }
            else false
        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.chunked(6).forEachIndexed { row, entries ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entries.forEachIndexed { col, label ->
                        val index = row * 6 + col
                        var focused by remember { mutableStateOf(false) }
                        Holdable(
                            onActivate = { edit(when (label) { "Space" -> query + " "; "Delete" -> query.dropLast(1); "Clear" -> ""; else -> query + label.lowercase() }) },
                            onHold = null,
                            modifier = Modifier.width(if (row == 6) 100.dp else 48.dp).height(42.dp)
                                .focusRequester(keyFocus[index])
                                .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) lastKey = index }
                                .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && col == entries.lastIndex) { results(); true } else false
                                },
                        ) { Text(label, color = Color.White, fontSize = if (row == 6) 16.sp else 20.sp) }
                    }
                }
            }
        }
        Text("Type here with your remote. Play/Pause opens results.", color = Color(0xFFA6A8AA), fontSize = 18.sp, modifier = Modifier.offset(100.dp, 572.dp).size(304.dp, 64.dp))
        LazyColumn(Modifier.offset(456.dp, 164.dp).size(740.dp, 484.dp).clipToBounds(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.searchSections, key = { index, section -> "${section.source}:$index" }) { sectionIndex, section ->
                Column(Modifier.height(236.dp)) {
                    Text(section.source, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.height(36.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        itemsIndexed(section.items, key = { _, item -> item.type + ":" + item.id }) { index, item ->
                            Box(Modifier.onPreviewKeyEvent { event ->
                                if (index == 0 && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { keyFocus[lastKey].requestFocus(); true } else false
                            }) { MediaCard(item, controller, if (sectionIndex == 0 && index == 0) resultFocus else null) }
                        }
                    }
                }
            }
        }
        Text(state.searchStatus, color = Color(0xFFA6A8AA), fontSize = 18.sp, modifier = Modifier.offset(456.dp, 660.dp).size(740.dp, 36.dp))
    }
}
