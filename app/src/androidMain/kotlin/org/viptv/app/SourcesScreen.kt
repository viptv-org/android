package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun SourcePicker(media: Media, sources: List<Source>, controller: AppController) {
    val tv = LocalTv.current
    val state by controller.state.collectAsState()
    var provider by remember(media.id) { mutableStateOf<String?>(null) }
    var quality by remember(media.id) { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    val groups = remember(sources) { sources.associateWith { SourceDisplayPolicy.providerKey(it) to SourceDisplayPolicy.providerLabel(it) } }
    val shown = sources.filter { (provider == null || groups[it]?.first == provider) && (quality == null || it.quality == quality) }
    val first = remember(media.id, provider, quality) { FocusRequester() }
    var claimed by remember(media.id, provider, quality) { mutableStateOf(false) }
    val providerLabels = groups.values.associate { it.first to it.second }
    Box(Modifier.fillMaxSize()) {
        if (tv) {
            HeroBackdrop(media)
            Column(Modifier.padding(start = 192.dp, top = 96.dp).width(760.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                VText(media.name, 56, display = true, lines = 2)
                VText(mediaFacts(media), 22, color = C.textSecondary)
                VText(media.description.orEmpty(), 26, color = C.textBody, lines = 3)
            }
        } else Artwork(CoreModels.presentation(media).heroImage, null, Modifier.fillMaxWidth().height(320.dp))
    }
    AppOverlay("Choose a source", controller::back) {
        VText(media.name + " · " + sources.size + " found", if (tv) 22 else 13, color = C.textSecondary, lines = 1)
        LazyRow(Modifier.fillMaxWidth().padding(vertical = measure(28, 18)), horizontalArrangement = Arrangement.spacedBy(measure(12, 8))) {
            item { AppChip("All", { quality = null }, quality == null) }
            items(sources.mapNotNull { it.quality }.distinct()) { value -> AppChip(value, { quality = value }, quality == value) }
            item { AppChip(provider?.let { providerLabels[it] } ?: "All providers", { picker = true }, provider != null) }
        }
        if (shown.isEmpty()) EmptyState(
            if (state.sourceLoading) "Finding sources" else if (sources.isEmpty()) "No sources available" else "No matching sources",
            if (state.sourceLoading) "Sources appear here as they arrive." else "Choose another provider or check your addons in Settings.", "list",
            retry = if (state.sourceLoading) null else { { val route = state.route as? Route.Sources; controller.chooseSources(media, route?.resume == true, route?.origin ?: SourceReturn.Details) } })
        else LazyColumn(Modifier.fillMaxWidth().then(if (tv) Modifier.weight(1f) else Modifier.heightIn(max = 440.dp)), verticalArrangement = Arrangement.spacedBy(measure(14, 12)), contentPadding = PaddingValues(4.dp)) {
            itemsIndexed(shown, key = { _, source -> source.id }) { index, source ->
                var focused by remember(source.id) { mutableStateOf(false) }
                val opening = state.preparingSourceId == source.id
                val foreground = if (tv && focused) C.onLight else C.textPrimary
                Holdable({ controller.start(media, source) }, { controller.requestDialog(DialogKind.SourceDetails, "Source details", source = source) },
                    Modifier.fillMaxWidth().height(measure(104, 86)).then(if (index == 0 && tv) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(measure(22, 18)))
                        .background(if (tv && focused) C.textPrimary else C.surfaceN2)
                        .border(1.dp, if (index == 0 && !tv) LocalAccent.current else Color.Transparent, RoundedCornerShape(measure(22, 18)))) {
                    Row(Modifier.fillMaxSize().padding(horizontal = measure(26, 14)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(20, 12))) {
                        Box(Modifier.size(measure(92, 60), measure(64, 52)).clip(RoundedCornerShape(10.dp)).background(if (focused && tv) C.lineOnAccent else C.surfaceN3), contentAlignment = Alignment.Center) {
                            VText(source.quality ?: "Auto", if (tv) 22 else 12, color = foreground, bold = true, lines = 1)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (index == 0) VText("BEST MATCH", if (tv) 18 else 10, color = if (tv && focused) C.textOnLightAccent else LocalAccent.current, bold = true)
                            VText(SourceDisplayPolicy.title(source).replace('\n', ' '), if (tv) 26 else 15, color = foreground, bold = true, lines = 1)
                            VText(if (opening) "Opening source…" else SourceDisplayPolicy.body(source).replace('\n', ' '), if (tv) 20 else 12, color = if (tv && focused) C.textOnLightSecondary else C.textSecondary, lines = 1)
                        }
                        if (opening) CircularProgressIndicator(Modifier.size(measure(28, 24)), color = foreground, strokeWidth = 3.dp)
                        else if (tv) VIcon("play", color = foreground)
                        else Holdable({ controller.requestDialog(DialogKind.SourceDetails, "Source details", source = source) }, modifier = Modifier.size(44.dp)) { VIcon("more", "Source details", Modifier.size(18.dp)) }
                    }
                }
            }
        }
        if (state.preparingSourceId != null) VText("Opening your selected source. Back cancels.", if (tv) 20 else 13, Modifier.padding(top = 12.dp), C.textSecondary)
        if (state.sourceLoading && shown.isNotEmpty()) VText("Still checking sources…", if (tv) 20 else 13, Modifier.padding(top = 12.dp), C.textTertiary)
        LaunchedEffect(shown.isNotEmpty(), picker) {
            if (tv && shown.isNotEmpty() && !claimed && !picker) { withFrameNanos {}; runCatching { first.requestFocus() }; claimed = true }
        }
    }
    if (picker) ChoiceDialog("Provider", listOf("All providers" to { provider = null; picker = false }) + providerLabels.map { (id, label) -> label to { provider = id; picker = false } }, { picker = false })
}

@Composable internal fun ActionDialog(dialog: DialogState, controller: AppController) {
    val state by controller.state.collectAsState()
    val dismiss = { controller.dismissDialog() }
    if (dialog.kind == DialogKind.SourceDetails) {
        FullInfo(dialog.title, dialog.source?.let { SourceDisplayPolicy.title(it) + "\n\n" + SourceDisplayPolicy.body(it) }.orEmpty(), dismiss)
        return
    }
    val options = buildList<Pair<String, () -> Unit>> {
        when (dialog.kind) {
            DialogKind.SignOut -> { add("Cancel" to dismiss); add("Sign out" to { controller.dismissDialog(); controller.signOut() }) }
            DialogKind.DeleteProfile -> { add("Cancel" to dismiss); add("Delete profile" to { dialog.profile?.let { controller.deleteProfile(it) }; controller.dismissDialog() }) }
            DialogKind.QueueManage -> {
                dialog.media?.let { media ->
                    if (QueuePolicy.canResume(media)) add("Resume" to { controller.resumeQueueItem(media) })
                    add("Choose source" to { controller.chooseQueueSource(media) })
                    add("Mark watched" to { controller.correctEpisode(media, true); controller.dismissDialog() })
                    add("Watch from beginning" to { controller.dismissDialog(); controller.chooseSources(media.copy(positionMillis = 0), origin = SourceReturn.Home) })
                    add("Remove from Continue Watching" to { controller.removeFromQueue(media) })
                }
                add("Cancel" to dismiss)
            }
            DialogKind.QueueRemoved -> { add("Undo" to { dialog.media?.let { controller.undoQueueRemoval(it) } }); add("Done" to dismiss) }
            DialogKind.MyListManage, DialogKind.LiveManage -> {
                dialog.media?.let { media ->
                    val saved = state.favorites.any { it.id == media.id && it.type == media.type }
                    add((if (saved) "Remove from My List" else "Add to My List") to { controller.toggleMyList(media); controller.dismissDialog() })
                    add("Details" to { controller.dismissDialog(); controller.open(media) })
                }
                add("Cancel" to dismiss)
            }
            DialogKind.EpisodeManage -> {
                dialog.media?.let { media ->
                    add("Mark watched" to { controller.correctEpisode(media, true); controller.dismissDialog() })
                    add("Mark unwatched" to { controller.correctEpisode(media, false); controller.dismissDialog() })
                    add("Watch from beginning" to { controller.dismissDialog(); controller.chooseSources(media.copy(positionMillis = 0)) })
                }; add("Cancel" to dismiss)
            }
            DialogKind.NextUnavailable -> { dialog.media?.let { media -> add("Open series" to { controller.dismissDialog(); controller.open(media) }) }; add("Done" to dismiss) }
            DialogKind.PlaybackRecovery -> {
                add("Retry" to { controller.retryPlaybackRecovery() })
                add("Choose another source" to { controller.chooseAnotherSourceForRecovery() })
                add("Back" to { controller.backFromPlaybackRecovery() })
            }
            else -> add("Done" to dismiss)
        }
    }
    ChoiceDialog(dialog.title, options, dismiss, description = dialog.detail)
}
