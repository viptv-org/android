package org.viptv.app

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val DiscoverCanvas = Color(0xFF101112)
private val DiscoverSurface = Color(0xFF202224)
private val DiscoverSelected = Color(0xFF303234)
private val DiscoverMuted = Color(0xFFA6A8AA)

/**
 * Canonical Discover: every catalog/filter comes from the server declaration.
 * AppController translates the selected values into [CatalogDiscoverRequest] and
 * retains server-issued cursors; this composable never invents a page or filter.
 */
@Composable
internal fun DiscoverScreen(state: AppState, controller: AppController) {
    val ui = state.discoverUi
    val allCatalogs = ui.catalogs
    val typeCatalogs = allCatalogs.filter { it.key.type == ui.selectedType }
    val selectedCatalog = allCatalogs.firstOrNull { it.key == ui.selectedCatalogKey }
        ?: typeCatalogs.firstOrNull()
    val initialCard = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<DiscoverPicker?>(null) }
    var textEntry by remember { mutableStateOf<DiscoverField?>(null) }
    var textValue by remember { mutableStateOf("") }
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var clientPage by remember { mutableIntStateOf(0) }

    fun dismissModal() {
        picker = null
        textEntry = null
        returnFocus?.let { requester -> scope.launch { kotlinx.coroutines.yield(); requester.requestFocus() } }
    }

    val fields = buildList {
        if (selectedCatalog?.supportsSearch == true || selectedCatalog?.filters?.any { it.kind == CatalogFilterKind.Search } == true) {
            add(DiscoverField("search", "Search", CatalogFilterKind.Search, required = false))
        }
        selectedCatalog?.filters?.filter { it.kind != CatalogFilterKind.Search }?.forEach { filter ->
            add(DiscoverField(filter.name, filter.name, filter.kind, filter.required, filter.options, filter.defaultValue))
        }
    }
    val chipIdentities = buildList {
        add("type")
        selectedCatalog?.let { add("catalog:${it.key.stableId}") }
        fields.forEach { add("field:${it.key}") }
    }
    val chipFocus = remember(chipIdentities) { chipIdentities.associateWith { FocusRequester() } }
    val shownItems = ui.items.drop(clientPage * PAGE_SIZE).take(PAGE_SIZE)
    val hasLocalNext = (clientPage + 1) * PAGE_SIZE < ui.items.size

    LaunchedEffect(ui.requestedSkip, ui.items, ui.loading, ui.error) {
        clientPage = 0
        when {
            ui.error != null -> retryFocus.requestFocus()
            ui.items.isNotEmpty() && !ui.loading -> initialCard.requestFocus()
            else -> chipFocus.getValue("type").requestFocus()
        }
    }

    Box(Modifier.fillMaxSize().background(DiscoverCanvas)) {
        Text("Discover", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(100.dp, 54.dp).width(1096.dp))
        Text(
            selectedCatalog?.let { catalog -> "${catalog.name} · ${catalog.key.addonId}" } ?: "Loading catalogs…",
            color = DiscoverMuted,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(100.dp, 126.dp).width(1096.dp),
        )
        LazyRow(
            Modifier.offset(100.dp, 178.dp).width(1096.dp).height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "type") {
                DiscoverChipButton(
                    label = "Browse",
                    value = ui.selectedType.replaceFirstChar(Char::uppercase),
                    modifier = Modifier.width(256.dp).height(48.dp).focusRequester(chipFocus.getValue("type")),
                ) { picker = DiscoverPicker.Type; returnFocus = chipFocus.getValue("type") }
            }
            selectedCatalog?.let { catalog ->
                item(key = "catalog:${catalog.key.stableId}") {
                    DiscoverChipButton(
                        label = "Catalog",
                        value = catalog.name,
                        modifier = Modifier.width(256.dp).height(48.dp).focusRequester(chipFocus.getValue("catalog:${catalog.key.stableId}")),
                    ) { picker = DiscoverPicker.Catalog; returnFocus = chipFocus.getValue("catalog:${catalog.key.stableId}") }
                }
            }
            items(fields, key = { "field:${it.key}" }) { field ->
                val value = ui.selectedFilters[field.key] ?: field.defaultValue ?: if (field.required) field.options.firstOrNull().orEmpty() else "Any"
                DiscoverChipButton(
                    label = field.label,
                    value = value.ifBlank { "Enter value" },
                    modifier = Modifier.width(256.dp).height(48.dp).focusRequester(chipFocus.getValue("field:${field.key}")),
                ) {
                    returnFocus = chipFocus.getValue("field:${field.key}")
                    if (field.isTextEntry) {
                        textValue = ui.selectedFilters[field.key] ?: field.defaultValue.orEmpty()
                        textEntry = field
                    } else picker = DiscoverPicker.Filter(field)
                }
            }
        }

        when {
            ui.loading -> Text("Loading catalog…", color = DiscoverMuted, fontSize = 20.sp, modifier = Modifier.offset(100.dp, 304.dp))
            ui.error != null -> {
                Text(ui.error, color = DiscoverMuted, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.offset(100.dp, 304.dp).width(1096.dp))
                DiscoverChipButton("Retry", "Retry", Modifier.offset(512.dp, 374.dp).width(256.dp).height(48.dp).focusRequester(retryFocus)) {
                    selectedCatalog?.let { controller.setDiscoverCatalog(it.key) } ?: controller.openDiscover()
                }
            }
            ui.items.isEmpty() -> Text("Nothing is available for these filters.", color = DiscoverMuted, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.offset(100.dp, 350.dp).width(1096.dp))
            else -> shownItems.forEachIndexed { index, media ->
                val column = index % 4
                val row = index / 4
                DiscoverCard(
                    media,
                    Modifier.offset((100 + column * 280).dp, (248 + row * 220).dp),
                    if (index == 0) initialCard else null,
                    onActivate = { controller.open(media) },
                )
            }
        }

        if (clientPage > 0 || ui.previousSkips.isNotEmpty()) {
            DiscoverChipButton("Previous page", "Previous", Modifier.offset(100.dp, 670.dp).width(180.dp).height(40.dp)) {
                if (clientPage > 0) clientPage-- else controller.changeDiscoverPage(-1)
            }
        }
        if (hasLocalNext || ui.nextSkip != null) {
            DiscoverChipButton("Next page", "Next", Modifier.offset(1016.dp, 670.dp).width(180.dp).height(40.dp)) {
                if (hasLocalNext) clientPage++ else controller.changeDiscoverPage(1)
            }
        }

        picker?.let { active ->
            DiscoverChoicePanel(active, allCatalogs, ui.selectedType, controller, ::dismissModal)
        }
        textEntry?.let { field ->
            DiscoverTextEntry(field, textValue, onValue = { textValue = it }, onApply = { value ->
                controller.setDiscoverFilter(field.key, value.trim().takeIf(String::isNotEmpty))
                dismissModal()
            }, onClose = ::dismissModal)
        }
    }
}

private const val PAGE_SIZE = 8

private data class DiscoverField(
    val key: String,
    val label: String,
    val kind: CatalogFilterKind,
    val required: Boolean,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
) {
    val isTextEntry: Boolean get() = kind == CatalogFilterKind.Search || kind == CatalogFilterKind.FreeText || options.isEmpty()
}

private sealed interface DiscoverPicker {
    data object Type : DiscoverPicker
    data object Catalog : DiscoverPicker
    data class Filter(val field: DiscoverField) : DiscoverPicker
}

@Composable
private fun DiscoverChipButton(label: String, value: String, modifier: Modifier, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (focused) Color.White else DiscoverSelected)
            .onFocusChanged { focused = it.hasFocus }.focusable().clickable(onClick = onActivate),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = if (focused) DiscoverCanvas else DiscoverMuted, fontSize = 12.sp, modifier = Modifier.offset(18.dp, 4.dp).width(200.dp))
        Text(value, color = if (focused) DiscoverCanvas else Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(18.dp, 20.dp).width(220.dp))
    }
}

@Composable
private fun DiscoverChoicePanel(
    active: DiscoverPicker,
    catalogs: List<DiscoverCatalog>,
    selectedType: String,
    controller: AppController,
    onClose: () -> Unit,
) {
    val choices: List<Pair<String, () -> Unit>> = when (active) {
        DiscoverPicker.Type -> catalogs.map { it.key.type }.distinct().sorted().map { type -> type.replaceFirstChar(Char::uppercase) to { controller.setDiscoverType(type) } }
        DiscoverPicker.Catalog -> catalogs.filter { it.key.type == selectedType }.map { catalog ->
            "${catalog.name} · ${catalog.key.addonId}" to { controller.setDiscoverCatalog(catalog.key) }
        }
        is DiscoverPicker.Filter -> buildList {
            val field = active.field
            if (!field.required) add("Any" to { controller.setDiscoverFilter(field.key, null) })
            field.options.forEach { option -> add(option to { controller.setDiscoverFilter(field.key, option) }) }
        }
    }
    val title = when (active) {
        DiscoverPicker.Type -> "Browse"
        DiscoverPicker.Catalog -> "Catalogs"
        is DiscoverPicker.Filter -> active.field.label
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(active) { first.requestFocus() }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0xD8101112)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(12.dp)).background(DiscoverSurface).padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.width(656.dp).height(364.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(choices, key = { index, choice -> "$index:${choice.first}" }) { index, choice ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier.width(656.dp).height(52.dp).then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .clip(RoundedCornerShape(10.dp)).background(if (focused) Color.White else Color.Transparent)
                            .onFocusChanged { focused = it.hasFocus }.focusable().clickable { choice.second(); onClose() },
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(choice.first, color = if (focused) DiscoverCanvas else Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.dp)) }
                }
            }
            DiscoverChipButton("Close", "Close", Modifier.width(180.dp).height(48.dp), onClose)
        }
    }
}

@Composable
private fun DiscoverTextEntry(field: DiscoverField, value: String, onValue: (String) -> Unit, onApply: (String) -> Unit, onClose: () -> Unit) {
    val firstKey = remember { FocusRequester() }
    val keys = remember { ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").map(Char::toString) + listOf("Space", "Delete", "Clear") }
    var error by remember(field.key) { mutableStateOf<String?>(null) }
    fun edit(next: String) { error = null; onValue(next.take(128)) }
    fun apply() { if (field.required && value.isBlank()) error = "${field.label} is required." else onApply(value) }
    LaunchedEffect(field.key) { firstKey.requestFocus() }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0xD8101112)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).clip(RoundedCornerShape(12.dp)).background(DiscoverSurface).padding(32.dp)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN) false
                    else when {
                        native.keyCode == KeyEvent.KEYCODE_DEL -> { edit(value.dropLast(1)); true }
                        native.unicodeChar in 32..126 -> { edit(value + native.unicodeChar.toChar()); true }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(field.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(if (field.required) "Enter a value to continue." else "Leave empty to clear this filter.", color = DiscoverMuted, fontSize = 17.sp)
            Box(Modifier.width(656.dp).height(44.dp).clip(RoundedCornerShape(8.dp)).background(DiscoverSelected), contentAlignment = Alignment.CenterStart) {
                Text(value.ifBlank { "Enter ${field.label.lowercase()}" }, color = if (value.isBlank()) DiscoverMuted else Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                keys.chunked(6).forEachIndexed { row, entries ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEachIndexed { column, key ->
                            val index = row * 6 + column
                            DiscoverTextKey(key, Modifier.width(if (key.length > 1) 100.dp else 48.dp).height(34.dp).then(if (index == 0) Modifier.focusRequester(firstKey) else Modifier)) {
                                edit(when (key) {
                                    "Space" -> value + " "
                                    "Delete" -> value.dropLast(1)
                                    "Clear" -> ""
                                    else -> value + key.lowercase()
                                })
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 15.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DiscoverChipButton("Apply", "Apply", Modifier.width(180.dp).height(48.dp)) { apply() }
                DiscoverChipButton("Cancel", "Cancel", Modifier.width(180.dp).height(48.dp), onClose)
            }
        }
    }
}

@Composable
private fun DiscoverTextKey(label: String, modifier: Modifier, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(if (focused) Color.White else DiscoverSelected).onFocusChanged { focused = it.hasFocus }.focusable().clickable(onClick = onActivate), contentAlignment = Alignment.Center) {
        Text(label, color = if (focused) DiscoverCanvas else Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun DiscoverCard(media: Media, modifier: Modifier, requester: FocusRequester?, onActivate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.width(256.dp).height(192.dp).then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .onFocusChanged { focused = it.hasFocus }.then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .focusable().clickable(onClick = onActivate),
    ) {
        Box(Modifier.width(256.dp).height(144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
            Text(media.name, color = DiscoverMuted, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(220.dp))
            if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(media.name, color = if (focused) Color.White else Color(0xFFF5F5F5), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = 152.dp).width(256.dp))
        Text(media.type, color = DiscoverMuted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.offset(y = 176.dp).width(256.dp))
    }
}
