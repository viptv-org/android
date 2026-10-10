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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.viptv.app.theme.ViptvColor as C
import org.viptv.app.theme.ViptvDimen
import kotlinx.coroutines.flow.first
import coil.compose.AsyncImage
import org.viptv.app.hero.TvHeroBackdrop

/** The displayed episode number is metadata, not a row position. Preserve backend row order. */
internal fun episodeIndexForNumber(episodes: List<Media>, number: Int): Int =
    episodes.indexOfFirst { it.episode == number }

@Composable internal fun DetailsScreen(media: Media, controller: AppController) {
    val tv = LocalTv.current
    val app by controller.state.collectAsState()
    val entryId = (app.route as? Route.Details)?.entryId ?: 0L
    val presentation = remember(media) { CoreModels.presentation(media) }
    val playEpisode = remember(media) { CoreModels.initialEpisode(media) }
    val focusEpisode = remember(media, playEpisode) {
        media.episodes.firstOrNull { it.season == media.season && it.episode == media.episode }
            ?: playEpisode
    }
    val seasons = remember(media.episodes) { media.episodes.map { it.season ?: 1 }.distinct().sorted() }
    var season by rememberSaveable(media.id, media.season, media.episode, entryId) { mutableIntStateOf(media.season ?: focusEpisode?.season ?: seasons.firstOrNull() ?: 1) }
    var seasonPicker by remember { mutableStateOf(false) }

    var jumpEntry by remember { mutableStateOf(false) }
    var jumpOrigin by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val jumpFocus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    val inputMode = LocalInputModeManager.current
    var jumpFocused by remember { mutableStateOf(false) }
    var focusedEpisodeIndex by remember { mutableIntStateOf(-1) }
    var jumpRequest by remember(media.id) { mutableIntStateOf(0) }
    var jumpIndex by remember(media.id) { mutableIntStateOf(-1) }
    var jumpCancelRequest by remember(media.id) { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(false) }
    val episodes = remember(media, season) { media.episodes.filter { (it.season ?: 1) == season } }
    // Keep the parent artwork facts small; the full series may contain thousands of episodes.
    val episodeArtworkContext = remember(media.id, media.name, media.poster, media.backdrop, media.thumbnail) {
        Media(media.id, media.type, name = media.name, poster = media.poster, backdrop = media.backdrop, thumbnail = media.thumbnail)
    }
    val target = playEpisode?.withArtworkFrom(media) ?: media.takeUnless { it.type == "series" && it.episode == null }
    val previewKey = target?.let { SourcePreviewPolicy.key(app.selectedProfile?.id, it) }
    val sourceSummary = app.sourceSummary?.takeIf { it.key == previewKey }
    DisposableEffect(previewKey) {
        if (target != null) controller.previewSources(target)
        onDispose { if (target != null) controller.releaseSourcePreview(target) }
    }
    val retryDetail = !app.loading && app.message != null && media.episodes.isEmpty()
    val saved = app.favorites.any { it.id == media.id && it.type == media.type }
    val initial = LocalContentFocus.current
    val rail = LocalRailFocus.current
    // Save a logical return target, never a FocusRequester from a disposed route.
    var returnToPlay by rememberSaveable(media.id, previewKey, entryId) { mutableStateOf(false) }
    val targetPresentation = remember(target) { target?.let(CoreModels::presentation) }
    val label = targetPresentation?.primaryActionLabel ?: "No episodes available"
    val episodeFocus = remember(episodes) { episodes.map { FocusRequester() } }
    val episodeScroll = rememberLazyListState()
    val pageScroll = rememberLazyListState()
    val scrolledHeader by remember { derivedStateOf { pageScroll.firstVisibleItemIndex > 0 || pageScroll.firstVisibleItemScrollOffset > 300 } }
    val returningToEpisode = media.type == "series" && media.season != null && media.episode != null
    var selectedEpisode by rememberSaveable(media.id, season, media.episode, entryId) {
        mutableIntStateOf(episodes.indexOfFirst { it.episode == media.episode }.coerceAtLeast(0))
    }
    var restoreEpisodes by rememberSaveable(media.id, media.season, media.episode, entryId) { mutableStateOf(returningToEpisode) }
    var pendingSavedEpisode by rememberSaveable(media.id, media.season, media.episode, entryId) { mutableStateOf(returningToEpisode) }
    var seasonEntryIndex by rememberSaveable(media.id, media.season, media.episode, entryId) { mutableIntStateOf(-1) }
    LaunchedEffect(media.id, entryId) {
        if (tv) {
            withFrameNanos {}
            if (returnToPlay && target != null) {
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { initial.requestFocus() }
                returnToPlay = false
            } else if (!restoreEpisodes) runCatching { initial.requestFocus() }
        }
    }
    LaunchedEffect(media.id, media.episode, restoreEpisodes, season, episodes, app.dialog, seasonPicker, entryId) {
        if (tv && restoreEpisodes && !pendingSavedEpisode && app.dialog == null && !seasonPicker && episodes.isNotEmpty()) {
            val index = seasonEntryIndex.takeIf { it in episodes.indices } ?: selectedEpisode.coerceIn(episodes.indices)
            selectedEpisode = index
            seasonEntryIndex = -1
            if (returningToEpisode) pageScroll.scrollToItem(1)
            episodeScroll.scrollToItem(index); withFrameNanos {}
            inputMode.requestInputMode(InputMode.Keyboard)
            var attempts = 0
            while (focusedEpisodeIndex != index && attempts++ < 12) {
                withFrameNanos {}
                runCatching { episodeFocus[index].requestFocus() }
            }
        }
    }
    LaunchedEffect(media.id, media.season, media.episode, season, episodes, entryId) {
        if (tv && pendingSavedEpisode && episodes.isNotEmpty()) {
            val index = episodeIndexForNumber(episodes, media.episode ?: -1).takeIf { it >= 0 }
                ?: selectedEpisode.coerceIn(episodes.indices)
            selectedEpisode = index
            pageScroll.scrollToItem(1)
            episodeScroll.scrollToItem(index)
            withFrameNanos {}
            if (pendingSavedEpisode) {
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { episodeFocus[index].requestFocus() }
                pendingSavedEpisode = false
            }
        }
    }
    LaunchedEffect(media.id, season, jumpRequest) {
        if (tv && jumpOrigin == (media.id to season) && jumpIndex in episodes.indices && jumpRequest > 0) {
            val index = jumpIndex
            focusedEpisodeIndex = -1
            pageScroll.scrollToItem(1)
            episodeScroll.scrollToItem(index)
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            var attempts = 0
            while (focusedEpisodeIndex != index && !jumpEntry && jumpOrigin == (media.id to season) && attempts++ < 60) {
                snapshotFlow { windowInfo.isWindowFocused }.first { it }
                withFrameNanos {}
                if (windowInfo.isWindowFocused) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    runCatching { episodeFocus[index].requestFocus() }
                }
            }
            if (focusedEpisodeIndex == index) jumpIndex = -1
        }
    }
    LaunchedEffect(jumpCancelRequest) {
        if (tv && jumpCancelRequest > 0) {
            jumpFocused = false
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            var attempts = 0
            while (!jumpFocused && !jumpEntry && jumpOrigin == (media.id to season) && attempts++ < 60) {
                snapshotFlow { windowInfo.isWindowFocused }.first { it }
                withFrameNanos {}
                if (windowInfo.isWindowFocused) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    runCatching { jumpFocus.requestFocus() }
                }
            }
        }
    }
    LaunchedEffect(media.id, season) {
        if (jumpEntry && jumpOrigin != (media.id to season)) jumpEntry = false
    }
    fun changeSeason(value: Int, atEnd: Boolean = false) {
        pendingSavedEpisode = false
        seasonEntryIndex = if (atEnd) media.episodes.count { (it.season ?: 1) == value } - 1 else 0
        focusedEpisodeIndex = -1
        season = value
        restoreEpisodes = tv
        seasonPicker = false
    }
    fun play() {
        if (target != null) controller.chooseSources(target, target.positionMillis > 0)
        else if (tv && episodeFocus.isNotEmpty()) episodeFocus.first().requestFocus()
    }
    Box(Modifier.fillMaxSize()) {
        if (tv) {
            val focusedEpisode = episodes.getOrNull(focusedEpisodeIndex)
            // Blank, not null, for a focused episode without a still so the backdrop still waits for focus to settle.
            val episodeStill = remember(focusedEpisode) { focusedEpisode?.let { CoreModels.presentation(it).episodeImage.orEmpty() } }
            TvHeroBackdrop(media, episodeStill,
                preloadItems = org.viptv.app.hero.neighbouringHeroItems(episodes, focusedEpisodeIndex),
                preloadEpisodes = true)
        }
        LazyColumn(Modifier.fillMaxSize(), state = pageScroll, contentPadding = if (tv) PaddingValues(start = 104.dp, top = 96.dp, bottom = 54.dp) else PaddingValues(bottom = 200.dp)) {
            item {
                if (!tv) Box(Modifier.fillMaxWidth().height(300.dp)) {
                    Artwork(presentation.heroImage, null, Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, LocalGround.current))))
                    if (!presentation.titleLogo.isNullOrBlank()) Artwork(presentation.titleLogo, media.name, Modifier.align(Alignment.BottomStart).padding(20.dp).size(280.dp, 88.dp), ContentScale.Fit, Alignment.CenterStart)
                }
                Column(Modifier.then(if (!tv) Modifier.padding(horizontal = 20.dp) else Modifier.width(1000.dp)), verticalArrangement = Arrangement.spacedBy(measure(22, 14))) {
                    if (tv && !presentation.titleLogo.isNullOrBlank()) Artwork(presentation.titleLogo, media.name, Modifier.size(310.dp, 90.dp), ContentScale.Fit, Alignment.CenterStart, trimTransparency = true)
                    else if (tv || presentation.titleLogo.isNullOrBlank()) VText(media.name, if (tv) 56 else 34, display = true, lines = 2)
                    VText(mediaFacts(media), if (tv) 22 else 14, color = C.textSecondary, lines = if (tv) 1 else 2)
                    VText(media.description.orEmpty(), if (tv) 26 else 16, Modifier.widthIn(max = if (tv) 780.dp else Dp.Infinity), color = C.textBody, lines = if (tv) 2 else 12)
                    if (!tv && !media.credits.isNullOrBlank()) VText(media.credits.orEmpty(), 14, color = C.textSecondary, lines = 3)
                }
                if (tv) Row(Modifier.padding(top = 32.dp, bottom = if (episodes.isEmpty()) 40.dp else 108.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (target != null) AppButton(label, ::play, Modifier.widthIn(min = 280.dp).focusRequester(initial).focusProperties { left = rail }, "play",
                        onHold = { returnToPlay = true; pendingSavedEpisode = false; restoreEpisodes = false; controller.chooseSources(target) }, tvAccent = targetPresentation?.primaryAction == "resume")
                    if (target == null && retryDetail)
                        AppButton("Try again", { controller.open(media, controller.detailReturnRoute, showWhileLoading = true) }, Modifier.focusRequester(initial))
                    else if (target == null) VText(label, 22, Modifier.align(Alignment.CenterVertically), C.textSecondary)
                    if(media.type!="live") SimklTitleActions(media,controller)
                    AppIconButton(if (saved) "check" else "plus", if (saved) "Remove from My List" else "Add to My List", { controller.toggleMyList(media) }, Modifier.then(if (target == null && !retryDetail) Modifier.focusRequester(initial) else Modifier))
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(Modifier.fillMaxWidth().padding(start = if (tv) 0.dp else 20.dp, end = if (tv) 96.dp else 20.dp, top = if (tv) 0.dp else 28.dp, bottom = if (tv) 0.dp else 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(measure(24, 14))) {
                        if (!tv) item { AppChip("Season $season", { seasonPicker = true }, selected = true) }
                        if (tv) item { AppChip("Episode #", { jumpOrigin = media.id to season; jumpEntry = true },
                            modifier = Modifier.focusRequester(jumpFocus).onFocusChanged { jumpFocused = it.isFocused }
                                .semantics { contentDescription = "Jump to episode number" }) }
                        if (tv) items(seasons) { value -> AppChip("Season $value", { changeSeason(value) }, selected = value == season) }
                        item { VText(episodes.size.toString() + " episodes", if (tv) 22 else 13, color = C.textTertiary) }
                    }
                }
                if (tv) item {
                    LazyRow(state = episodeScroll, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(4.dp)) {

                        itemsIndexed(episodes, key = { _, item -> item.id }) { index, episode ->
                            EpisodeCard(episode, Modifier.width(360.dp).focusRequester(episodeFocus[index]),
                                onClick = { pendingSavedEpisode = false; selectedEpisode = index; restoreEpisodes = true; controller.chooseSources(episode.withArtworkFrom(media)) },
                                onHold = {
                                    pendingSavedEpisode = false
                                    selectedEpisode = index
                                    restoreEpisodes = true
                                    controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, episode)
                                },
                                onFocused = { selectedEpisode = index; focusedEpisodeIndex = index }, artworkContext = episodeArtworkContext)
                        }

                    }
                } else itemsIndexed(episodes, key = { _, item -> item.id }) { _, episode ->
                    EpisodeCard(episode, Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth(),
                        onClick = { controller.chooseSources(episode.withArtworkFrom(media), episode.positionMillis > 0) },
                        onHold = { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, episode) }, artworkContext = episodeArtworkContext)
                }
            }
        }
        if (!tv) {
            if (scrolledHeader) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(112.dp).background(Brush.verticalGradient(listOf(LocalGround.current, LocalGround.current.copy(alpha = .9f), Color.Transparent))))
            AppIconButton("back", "Back", controller::back, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(44.dp))
        }
        if (!tv) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, LocalGround.current, LocalGround.current))).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (target != null) TitleSourceControl(sourceSummary) { controller.chooseSources(target) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if(media.type!="live") SimklTitleActions(media,controller)
                    AppIconButton(if (saved) "check" else "plus", "My List", { controller.toggleMyList(media) }, Modifier.size(ViptvDimen.sizeButtonPhoneDetail))
                if (target != null) AppButton(label, ::play, Modifier.weight(1f).height(ViptvDimen.sizeButtonPhoneDetail), "play", primary = true)
                else if (retryDetail)
                    AppButton("Try again", { controller.open(media, controller.detailReturnRoute, showWhileLoading = true) }, Modifier.weight(1f).height(ViptvDimen.sizeButtonPhoneDetail))
                else VText(label, 14, Modifier.weight(1f).align(Alignment.CenterVertically), C.textSecondary)
                AppIconButton("more", "More info", { info = true }, Modifier.size(ViptvDimen.sizeButtonPhoneDetail))
            }
        }
        if (seasonPicker) ChoiceDialog("Season", seasons.map { value -> "Season $value" to { changeSeason(value) } }, { seasonPicker = false })
        if (jumpEntry) TextEntry("Jump to episode", "Enter an available episode number in Season $season.", numeric = true,
            fieldLabel = "Episode number", doneLabel = "Go",
            validate = { entered ->
                val number = entered.toIntOrNull()
                if (jumpOrigin != (media.id to season) || number == null || episodeIndexForNumber(episodes, number) < 0) "Episode not found in this season." else null
            }, onDone = { entered ->
                if (jumpOrigin == (media.id to season)) {
                    jumpIndex = episodeIndexForNumber(episodes, entered.toInt())
                    pendingSavedEpisode = false
                    selectedEpisode = jumpIndex
                    jumpEntry = false
                    jumpRequest++
                } else jumpEntry = false
            }, onCancel = { jumpEntry = false; jumpCancelRequest++ })
        if (info) FullInfo(media.name, listOf(mediaFacts(media), media.description.orEmpty(), media.credits.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n"), { info = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EpisodeCard(media: Media, modifier: Modifier, onClick: () -> Unit, onHold: () -> Unit, onFocused: (() -> Unit)? = null, artworkContext: Media? = null) {
    val tv = LocalTv.current
    val enriched = remember(media, artworkContext) { artworkContext?.let { media.withArtworkFrom(it) } ?: media }
    var failed by remember(media.id, enriched.thumbnail, enriched.poster, enriched.backdrop) { mutableStateOf(emptySet<String>()) }
    val card = remember(enriched, failed) { CoreModels.card(enriched, true, failed) }
    val episodeProgress = remember(media) { SharedPresentation.episode(media) }
    val watching = episodeProgress.watching
    val progress = episodeProgress.progress.toFloat()
    val image = card.image
    var focused by remember { mutableStateOf(false) }
    val closeRail = LocalCloseRail.current
    Holdable(onClick, onHold, modifier.onFocusChanged { focused = it.isFocused; if (focused) { closeRail(); onFocused?.invoke() } }) {
        if (tv) Column {
            Box(Modifier.size(360.dp, 200.dp).testTag("episode-artwork").clip(RoundedCornerShape(16.dp)).background(C.surfaceN2).border(if (focused) 4.dp else 0.dp, if (focused) C.fillWhite else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (image.isNullOrBlank()) VText(card.title, 24, Modifier.padding(12.dp), C.textSecondary, bold = true, lines = 2, align = TextAlign.Center)
                else SizedArtwork(image, card.title, Modifier.fillMaxSize(), fit = if (card.imageRole == "logo") ContentScale.Fit else ContentScale.Crop,
                    onError = { failed = failed + image })
                if (watching) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(14.dp).testTag("episode-progress"))
                if (watching) Box(Modifier.align(Alignment.TopStart).padding(14.dp)
                    .height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 12.dp).testTag("episode-watching-badge"), contentAlignment = Alignment.Center) {
                    VText("WATCHING", 16, bold = true)
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                VText("EPISODE " + media.episode, 18, color = C.textSecondary, bold = true)
                if (media.watched) EpisodeWatchedBadge()
            }
            VText(media.episodeTitle ?: media.name, 24, Modifier.padding(top = 8.dp), bold = true, lines = 1)
            VText(media.description.orEmpty(), 20, Modifier.padding(top = 14.dp), C.textSecondary, lines = 2)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp, 76.dp).testTag("episode-artwork").clip(RoundedCornerShape(12.dp)).background(C.surfaceN2), contentAlignment = Alignment.Center) {
                if (image.isNullOrBlank()) VText(card.title, 13, Modifier.padding(8.dp), C.textSecondary, bold = true, lines = 2, align = TextAlign.Center)
                else SizedArtwork(image, card.title, Modifier.fillMaxSize(), fit = if (card.imageRole == "logo") ContentScale.Fit else ContentScale.Crop,
                    onError = { failed = failed + image })
                if (watching) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag("episode-progress"))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    VText("Episode " + media.episode, 12, color = C.textTertiary, lines = 2)
                    if (media.watched) EpisodeWatchedBadge()
                }
                VText(media.episodeTitle ?: media.name, 15, bold = true, lines = 2)
                VText(media.description.orEmpty(), 13, color = C.textSecondary, lines = 2)
            }
            Holdable(onHold, modifier = Modifier.size(44.dp)) { VIcon("more", "Episode options") }
        }
    }
}

@Composable private fun EpisodeWatchedBadge() {
    val tv = LocalTv.current
    Row(Modifier.testTag("episode-watched-badge").height(if (tv) 32.dp else 24.dp)
        .clip(RoundedCornerShape(50)).background(C.surfaceN3).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        VIcon("check", modifier = Modifier.size(if (tv) 20.dp else 14.dp), color = LocalAccent.current)
        VText("Watched", if (tv) 18 else 11, color = C.textPrimary, bold = true, lines = 1)
    }
}
