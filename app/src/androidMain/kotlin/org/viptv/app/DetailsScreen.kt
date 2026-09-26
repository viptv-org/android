package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.viptv.app.theme.ViptvColor as C

@Composable internal fun DetailsScreen(media: Media, controller: AppController) {
    val tv = LocalTv.current
    val app by controller.state.collectAsState()
    val presentation = remember(media) { CoreModels.presentation(media) }
    val initialEpisode = remember(media) { CoreModels.initialEpisode(media) }
    val seasons = remember(media.episodes) { media.episodes.map { it.season ?: 1 }.distinct().sorted() }
    var season by rememberSaveable(media.id) { mutableIntStateOf(initialEpisode?.season ?: seasons.firstOrNull() ?: 1) }
    var seasonPicker by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val episodes = remember(media, season) { media.episodes.filter { (it.season ?: 1) == season } }
    val target = initialEpisode?.withArtworkFrom(media) ?: media.takeUnless { it.type == "series" && it.episode == null }
    val saved = app.favorites.any { it.id == media.id && it.type == media.type }
    val initial = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val targetPresentation = remember(target) { target?.let(CoreModels::presentation) }
    val label = targetPresentation?.primaryActionLabel ?: "No episodes available"
    val episodeFocus = remember(episodes) { episodes.map { FocusRequester() } }
    val episodeScroll = rememberLazyListState()
    val pageScroll = rememberLazyListState()
    val scrolledHeader by remember { derivedStateOf { pageScroll.firstVisibleItemIndex > 0 || pageScroll.firstVisibleItemScrollOffset > 300 } }
    var selectedEpisode by rememberSaveable(media.id, season) { mutableIntStateOf(0) }
    var restoreEpisodes by rememberSaveable(media.id) { mutableStateOf(false) }
    LaunchedEffect(media.id) { if (tv) { withFrameNanos {}; if (!restoreEpisodes) runCatching { initial.requestFocus() } } }
    LaunchedEffect(restoreEpisodes, season) {
        if (tv && restoreEpisodes && episodes.isNotEmpty()) {
            val index = selectedEpisode.coerceIn(episodes.indices)
            episodeScroll.scrollToItem(index); withFrameNanos {}; runCatching { episodeFocus[index].requestFocus() }
        }
    }
    fun play() {
        if (target != null) controller.chooseSources(target, target.positionMillis > 0)
        else if (tv && episodeFocus.isNotEmpty()) episodeFocus.first().requestFocus()
    }
    Box(Modifier.fillMaxSize()) {
        if (tv) HeroBackdrop(media)
        LazyColumn(Modifier.fillMaxSize(), state = pageScroll, contentPadding = if (tv) PaddingValues(start = 192.dp, end = 96.dp, top = 96.dp, bottom = 54.dp) else PaddingValues(bottom = 200.dp)) {
            item {
                if (!tv) Box(Modifier.fillMaxWidth().height(352.dp)) {
                    Artwork(presentation.heroImage, null, Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, LocalGround.current))))
                    if (!presentation.titleLogo.isNullOrBlank()) Artwork(presentation.titleLogo, media.name, Modifier.align(Alignment.BottomStart).padding(20.dp).size(280.dp, 88.dp), ContentScale.Fit, Alignment.CenterStart)
                }
                Column(Modifier.then(if (!tv) Modifier.padding(horizontal = 20.dp) else Modifier.width(1000.dp)), verticalArrangement = Arrangement.spacedBy(measure(22, 14))) {
                    if (tv && !presentation.titleLogo.isNullOrBlank()) Artwork(presentation.titleLogo, media.name, Modifier.size(310.dp, 90.dp), ContentScale.Fit)
                    else if (tv || presentation.titleLogo.isNullOrBlank()) VText(media.name, if (tv) 56 else 34, display = true, lines = 2)
                    VText(mediaFacts(media), if (tv) 22 else 14, color = C.textSecondary, lines = if (tv) 1 else 2)
                    VText(media.description.orEmpty(), if (tv) 26 else 16, Modifier.widthIn(max = if (tv) 780.dp else Dp.Infinity), color = C.textBody, lines = if (tv) 2 else 12)
                    if (!tv && !media.credits.isNullOrBlank()) VText(media.credits.orEmpty(), 14, color = C.textSecondary, lines = 3)
                }
                if (tv) Row(Modifier.padding(top = 32.dp, bottom = if (episodes.isEmpty()) 40.dp else 108.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (target != null) AppButton(label, ::play, Modifier.widthIn(min = 280.dp).focusRequester(initial).focusProperties { left = rail }, "play",
                        onHold = { controller.chooseSources(target) }, tvAccent = targetPresentation?.primaryAction == "resume")
                    if (target != null) AppButton("Choose source", { controller.chooseSources(target) })
                    else VText(label, 22, Modifier.align(Alignment.CenterVertically), C.textSecondary)
                    AppButton("My List", { controller.toggleMyList(media) }, Modifier.then(if (target == null) Modifier.focusRequester(initial) else Modifier), icon = if (saved) "check" else "plus")
                    AppButton("More info", { info = true }, icon = "info")
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = if (tv) 0.dp else 20.dp, vertical = if (tv) 0.dp else 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(24, 14))) {
                        AppChip("Season $season", { seasonPicker = true }, selected = true)
                        VText(episodes.size.toString() + " episodes", if (tv) 22 else 13, color = C.textTertiary)
                    }
                }
                if (tv) item {
                    LazyRow(state = episodeScroll, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {
                        itemsIndexed(episodes, key = { _, item -> item.id }) { index, episode ->
                            EpisodeCard(episode, Modifier.width(360.dp).focusRequester(episodeFocus[index]),
                                onClick = { selectedEpisode = index; restoreEpisodes = true; controller.chooseSources(episode.withArtworkFrom(media)) },
                                onHold = { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, episode) },
                                onFocused = { selectedEpisode = index })
                        }
                    }
                } else itemsIndexed(episodes, key = { _, item -> item.id }) { _, episode ->
                    EpisodeCard(episode, Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth(),
                        onClick = { controller.chooseSources(episode.withArtworkFrom(media), episode.positionMillis > 0) },
                        onHold = { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, episode) })
                }
            }
        }
        if (!tv) {
            if (scrolledHeader) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(112.dp).background(Brush.verticalGradient(listOf(LocalGround.current, LocalGround.current.copy(alpha = .9f), Color.Transparent))))
            AppIconButton("back", "Back", controller::back, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(44.dp))
        }
        if (!tv) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, LocalGround.current, LocalGround.current))).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (target != null) Holdable({ controller.chooseSources(target) }, modifier = Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(C.surfaceN1).border(1.dp, C.lineOutline, RoundedCornerShape(18.dp))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    VIcon("list", modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { VText("Playback source", 12, color = C.textTertiary); VText("Choose source", 14, bold = true) }
                    VIcon("down")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIconButton(if (saved) "check" else "plus", "My List", { controller.toggleMyList(media) })
                if (target != null) AppButton(label, ::play, Modifier.weight(1f), "play", primary = true)
                else VText(label, 14, Modifier.weight(1f).align(Alignment.CenterVertically), C.textSecondary)
                AppIconButton("more", "More info", { info = true })
            }
        }
        if (seasonPicker) ChoiceDialog("Season", seasons.map { value -> "Season $value" to { season = value; selectedEpisode = 0; restoreEpisodes = tv; seasonPicker = false } }, { seasonPicker = false })
        if (info) FullInfo(media.name, listOf(mediaFacts(media), media.description.orEmpty(), media.credits.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n"), { info = false })
    }
}

@Composable private fun EpisodeCard(media: Media, modifier: Modifier, onClick: () -> Unit, onHold: () -> Unit, onFocused: (() -> Unit)? = null) {
    val tv = LocalTv.current
    val card = remember(media) { CoreModels.card(media, true) }
    var focused by remember { mutableStateOf(false) }
    val closeRail = LocalCloseRail.current
    Holdable(onClick, onHold, modifier.onFocusChanged { focused = it.isFocused; if (focused) { closeRail(); onFocused?.invoke() } }) {
        if (tv) Column {
            Box(Modifier.size(360.dp, 200.dp).clip(RoundedCornerShape(16.dp)).background(C.surfaceN2).border(if (focused) 4.dp else 0.dp, if (focused) C.fillWhite else Color.Transparent, RoundedCornerShape(16.dp))) {
                Artwork(card.image, media.episodeTitle, Modifier.fillMaxSize())
                if (media.positionMillis > 0) ProgressLine(card.progress?.toFloat() ?: 0f, Modifier.align(Alignment.BottomCenter).padding(14.dp))
            }
            VText("EPISODE " + media.episode, 18, Modifier.padding(top = 14.dp), C.textSecondary, bold = true)
            VText(media.episodeTitle ?: media.name, 24, Modifier.padding(top = 8.dp), bold = true, lines = 1)
            VText(media.description.orEmpty(), 20, Modifier.padding(top = 14.dp), C.textSecondary, lines = 2)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp, 76.dp).clip(RoundedCornerShape(12.dp)).background(C.surfaceN2)) {
                Artwork(card.image, null, Modifier.fillMaxSize())
                if (media.positionMillis > 0) ProgressLine(card.progress?.toFloat() ?: 0f, Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                VText("Episode " + media.episode, 12, color = C.textTertiary)
                VText(media.episodeTitle ?: media.name, 15, bold = true, lines = 2)
                VText(media.description.orEmpty(), 13, color = C.textSecondary, lines = 2)
            }
            Holdable(onHold, modifier = Modifier.size(44.dp)) { VIcon("more", "Episode options") }
        }
    }
}
