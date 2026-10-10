package org.viptv.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.*
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.viptv.app.hero.TvHeroBackdrop
import org.viptv.app.hero.DetailHeroScrim
import org.viptv.app.theme.ViptvColor as C

/** Whole-hero entry focus and bounded tab panes keep long series usable on a remote. */
@Composable internal fun DetailsScreen(media: Media, controller: AppController) {
    val tv = LocalTv.current
    val app by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val heroFocus = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val watchFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val detailScroll = rememberLazyListState()
    val series = media.type == "series"
    val tabs = remember(series) { (if (series) listOf("Episodes") else emptyList()) + listOf("Also watched", "More like this", "Details") }
    val tabFocus = remember(tabs) { List(tabs.size) { FocusRequester() } }
    var paneOpen by rememberSaveable(media.id) { mutableStateOf(false) }
    BackHandler(paneOpen && app.dialog == null && app.pinPrompt == null) { paneOpen = false; scope.launch { withFrameNanos {}; runCatching { heroFocus.requestFocus() } } }
    var tab by rememberSaveable(media.id) { mutableStateOf(if (series) "Episodes" else "Details") }
    var season by rememberSaveable(media.id) { mutableIntStateOf(media.season ?: 1) }
    var jump by remember { mutableStateOf(false) }
    val episodes = remember(media.episodes, season) { media.episodes.filter { (it.season ?: 1) == season } }
    val seasons = remember(media.episodes) { media.episodes.map { it.season ?: 1 }.distinct().sorted() }
    val episodeScroll = rememberLazyListState()
    val art = remember(media) { CoreModels.presentation(media) }
    val playEpisode by produceState<Media?>(null, media) { value = withContext(Dispatchers.Default) { CoreModels.initialEpisode(media) } }
    val lightParent = remember(media.id, media.name, media.poster, media.backdrop) { media.copy(episodes = emptyList()) }
    val target = remember(playEpisode, lightParent, series) { playEpisode?.withArtworkFrom(lightParent) ?: media.takeUnless { series } }
    val targetArt = remember(target) { target?.let(CoreModels::presentation) }
    val sourceKey = target?.let { SourcePreviewPolicy.key(app.selectedProfile?.id, it) }
    DisposableEffect(sourceKey) {
        target?.let { controller.previewSources(it) }
        onDispose { target?.let { controller.releaseSourcePreview(it) } }
    }
    fun watch() { target?.let { controller.chooseSources(it, resume = targetArt?.primaryAction == "resume") } }
    LaunchedEffect(paneOpen, tab) { if (tv && paneOpen) { withFrameNanos {}; runCatching { tabFocus[tabs.indexOf(tab).coerceAtLeast(0)].requestFocus() } } }
    val entry = (app.route as? Route.Details)?.entryId
    LaunchedEffect(media.id, entry) { if (tv) { withFrameNanos {}; runCatching { if (paneOpen) tabFocus[tabs.indexOf(tab).coerceAtLeast(0)].requestFocus() else heroFocus.requestFocus() } } }
    Box(Modifier.fillMaxSize()) {
        if (tv) TvHeroBackdrop(media, fullScreen = true)
        else {
            Artwork(art.heroImage, null, Modifier.fillMaxSize())
            DetailHeroScrim(Modifier.fillMaxSize(), compact = true)
        }
        if (paneOpen) Box(Modifier.fillMaxSize().background(LocalGround.current.copy(alpha = .96f)))
        Column(Modifier.fillMaxSize().padding(start = measure(64, 16), end = measure(64, 16), top = measure(48, 56), bottom = measure(54, 32)), verticalArrangement = Arrangement.spacedBy(measure(24, 16))) {
            if (!paneOpen) {
            var heroSelected by remember { mutableStateOf(false) }
            val heroModifier = Modifier.fillMaxWidth().weight(1f)
                .onFocusChanged { heroSelected = it.hasFocus }
                .then(if (tv) Modifier.focusRequester(heroFocus).focusProperties { left = rail; down = tabFocus.first(); right = watchFocus } else Modifier)
            val heroContent: @Composable BoxScope.() -> Unit = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                    Column(Modifier.widthIn(max = if (tv) 1100.dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxWidth().padding(bottom = measure(60, 16)), verticalArrangement = Arrangement.spacedBy(measure(18, 12))) {
                        TitleArtwork(media.name, art.titleLogo, Modifier.fillMaxWidth().height(measure(118, 82)), if (tv) 54 else 30)
                        VText(mediaFacts(media), if (tv) 22 else 14, color = C.textSecondary, lines = 2)
                        HeroAiring(media)
                        Box(Modifier.widthIn(max = if (tv) 654.dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxWidth(if (tv) 1f else .667f).height(measure(110, 70))) {
                            if (media.description.isNullOrBlank() && app.loading) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth(if (it == 2) .7f else .94f).height(measure(18, 12))) } }
                            else VText(media.description?.takeIf { it.isNotBlank() } ?: "No description available.", if (tv) 26 else 16, color = C.textBody, lines = if (tv) 3 else 3)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppButton(if (series && target != null) "${if (targetArt?.primaryAction == "resume") "Continue" else "Watch"} S${target?.season ?: 1} · E${target?.episode ?: 1}" else targetArt?.primaryActionLabel ?: if (app.loading) "Loading episodes…" else "Episodes", { if (target != null) watch() else { tab = "Episodes"; paneOpen = true } },
                                Modifier.then(if (tv) Modifier.width(320.dp).focusRequester(watchFocus).focusProperties { up = heroFocus; down = tabFocus.first(); left = heroFocus } else Modifier.weight(1f)), "play", primary = true, tvAccent = true)
                            SimklSaveAction(media, app.favorites.any { it.id == media.id }, controller)
                        }
                        Box(Modifier.height(measure(24, 18))) { app.sourceSummary?.takeIf { it.key == sourceKey && it.count > 0 }?.let { VText("${it.count} sources available", if (tv) 18 else 12, color = C.textTertiary) } }
                    }
                }
            }
            if (tv) Holdable(::watch, modifier = heroModifier, showIndication = false, onHold = { target?.let { controller.chooseSources(it) } }) { heroContent() }
            else Box(heroModifier) { heroContent() }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(measure(20, 8))) {
                tabs.forEachIndexed { index, label ->
                    AppChip(label, { tab = label; paneOpen = true }, paneOpen && tab == label,
                        Modifier.focusRequester(tabFocus[index]).onFocusChanged { if (tv && it.isFocused && paneOpen) tab = label }
                            .focusProperties { up = if (paneOpen) FocusRequester.Cancel else heroFocus; if (!paneOpen) down = FocusRequester.Cancel else if (tab == "Episodes" && episodes.isNotEmpty()) down = episodeFocus else if (tab == "Details") down = detailFocus; if (index == 0 && tv) left = rail })
                }
            }
            if (paneOpen) Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    "Episodes" -> {
                        if (episodes.isEmpty()) {
                            if (app.loading) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(measure(154, 100))) } }
                            else EmptyState("Episodes unavailable", "Retry the title details or choose another title.", "list", retry = { controller.open(media, controller.detailReturnRoute, showWhileLoading = true) })
                        } else if (tv) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                            Column(Modifier.width(290.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                AppChip("Jump to episode", { jump = true })
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(seasons) { number -> AppButton("Season $number", { season = number; scope.launch { episodeScroll.scrollToItem(0) } }, Modifier.fillMaxWidth(), selected = season == number) } }
                            }
                            LazyColumn(state = episodeScroll, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode -> DetailEpisodeRow(episode, lightParent, controller,
                                    Modifier.then(if (index == episodeScroll.firstVisibleItemIndex) Modifier.focusRequester(episodeFocus) else Modifier).focusProperties { up = if (index == 0) tabFocus.first() else FocusRequester.Default }) }
                            }
                        } else Column {
                            FilterTabs(seasons.map { "Season $it" }, "Season $season", { season = it.removePrefix("Season ").toInt(); scope.launch { episodeScroll.scrollToItem(0) } })
                            LazyColumn(state = episodeScroll, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
                                items(episodes, key = { it.id }) { DetailEpisodeRow(it, lightParent, controller) }
                            }
                        }
                    }
                    "Details" -> LazyColumn(state = detailScroll, modifier = Modifier.fillMaxSize().then(if (tv) Modifier.focusRequester(detailFocus).focusProperties { up = tabFocus[tabs.indexOf("Details")]; left = rail }.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.DirectionDown -> { scope.launch { detailScroll.animateScrollBy(300f) }; true }
                                Key.DirectionUp -> { if (detailScroll.firstVisibleItemIndex == 0 && detailScroll.firstVisibleItemScrollOffset == 0) tabFocus[tabs.indexOf("Details")].requestFocus() else scope.launch { detailScroll.animateScrollBy(-300f) }; true }
                                else -> false
                            }
                        }.focusable() else Modifier), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { SimklDetailFacts(media) }
                        item { VText(media.description.orEmpty(), if (tv) 26 else 16, color = C.textBody) }
                        item { VText(media.credits.orEmpty(), if (tv) 22 else 14, color = C.textSecondary) }
                        item { VText("Metadata: SIMKL · Logo artwork: MetaHub", if (tv) 18 else 12, color = C.textTertiary) }
                    }
                    else -> DetailsRelated(media, if (tab == "Also watched") "users_recommendations" else "similar", controller)
                }
            }
        }
        if (!tv) AppIconButton("back", "Back", controller::back, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(44.dp))
    }
    if (jump) TextEntry("Jump to episode", "Enter an episode number in Season $season", numeric = true,
        validate = { if (episodeIndexForNumber(episodes, it.toIntOrNull() ?: -1) < 0) "Episode not found" else null },
        onDone = { number -> val index = episodeIndexForNumber(episodes, number.toInt()); jump = false; scope.launch { episodeScroll.scrollToItem(index); withFrameNanos {}; if (tv) runCatching { episodeFocus.requestFocus() } } }, onCancel = { jump = false })
}

@Composable private fun DetailEpisodeRow(episode: Media, parent: Media, controller: AppController, modifier: Modifier = Modifier) {
    val tv = LocalTv.current
    val enriched = remember(episode, parent) { episode.withArtworkFrom(parent) }
    var failed by remember(episode.id) { mutableStateOf(emptySet<String>()) }
    val card = remember(enriched, failed) { CoreModels.card(enriched, true, failed) }
    var focused by remember { mutableStateOf(false) }
    Holdable({ controller.chooseSources(enriched, resume = CoreModels.presentation(enriched).primaryAction == "resume") }, { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, episode) },
        modifier.fillMaxWidth().then(if (tv) Modifier.height(210.dp) else Modifier).onFocusChanged { focused = it.isFocused }.background(C.surfaceN2, RoundedCornerShape(16.dp)).border(if (tv && focused) 3.dp else 0.dp, if (focused) C.textPrimary else Color.Transparent, RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().padding(measure(16, 12)), horizontalArrangement = Arrangement.spacedBy(measure(24, 12)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(measure(300, 100)).height(measure(168, 66)).clip(RoundedCornerShape(12.dp)).background(C.surfaceN3)) {
                card.image?.let { SizedArtwork(it, null, Modifier.fillMaxSize(), onError = { failed = failed + it }) }
                card.progress?.takeIf { it > 0 }?.let { ProgressLine(it.toFloat(), Modifier.align(Alignment.BottomCenter).padding(8.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VText("E${episode.episode} · ${episode.episodeTitle ?: episode.name}", if (tv) 24 else 15, bold = true, lines = 1, marquee = focused)
                if (episode.watched) VText("Watched", if (tv) 18 else 12, color = C.textTertiary)
                VText(episode.description.orEmpty(), if (tv) 20 else 13, color = C.textSecondary, lines = 3)
            }
        }
    }
}

@Composable private fun DetailsRelated(media: Media, key: String, controller: AppController) {
    val items = remember(media.id, media.coreItem, key) {
        val raw = org.json.JSONObject(media.normalizedJson(false)).optJSONObject("raw")?.optJSONArray(key)
        (0 until (raw?.length() ?: 0)).mapNotNull { index -> raw?.optJSONObject(index)?.let { runCatching { CoreModels.media(it) }.getOrNull() } }
    }
    if (items.isEmpty()) EmptyState("No recommendations yet", "SIMKL has no recommendations for this title.", "discover")
    else MediaGrid(items, onClick = { controller.open(it) }, onHold = { controller.requestDialog(DialogKind.MyListManage, it.name, it) })
}

@Composable private fun HeroAiring(media: Media) {
    val next = remember(media) { org.json.JSONObject(media.normalizedJson(false)).optJSONObject("raw")?.optJSONObject("next_airing")?.optString("released")?.takeIf { it.isNotBlank() && it != "null" } }
    next?.let { value ->
        val time = runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")) }.getOrDefault(value)
        VText("Next episode · $time", if (LocalTv.current) 20 else 13, color = C.textSecondary, lines = 1)
    }
}
