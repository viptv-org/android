@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.viptv.app

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import org.viptv.app.hero.TvHeroBackdrop
import org.viptv.app.hero.neighbouringHeroItems
import org.viptv.app.theme.ViptvColor as C

internal fun AppController.activateHero(media: Media, queue: Boolean) {
    when (SharedPresentation.home(media, queue).heroPrimaryAction) {
        "play" -> activateCard(media)
        "next" -> playQueuedNext(media)
        "resume" -> chooseHeroSources(media, queue, resume = true)
        "sources" -> chooseHeroSources(media, queue)
        else -> open(media)
    }
}

internal fun AppController.chooseHeroSources(media: Media, queue: Boolean, resume: Boolean = false) =
    chooseSources(media, resume, SourceReturn.Home, queueEpisodeReturn = queue && SourceReturnPolicy.parentSeries(media) != null)

@Composable internal fun HomeScreen(state: AppState, controller: AppController, list: LazyListState = rememberLazyListState()) {
    val tv = LocalTv.current
    val shelves = remember(state.shelves, tv) { state.shelves.filter { it.items.isNotEmpty() && (!tv || (it.title != "Recently watched live TV" && it.contentType != "live" && it.items.any { media -> media.type != "live" })) } }
    val queue = shelves.firstOrNull { it.isQueueShelf }
    val firstShelf = shelves.firstOrNull()
    val phoneHeroShelf = shelves.firstOrNull { !it.isQueueShelf && it.items.firstOrNull()?.type != "live" } ?: queue
    val featured = if (tv) firstShelf?.items.orEmpty().take(1) else phoneHeroShelf?.items.orEmpty().take(if (phoneHeroShelf?.isQueueShelf == true) 1 else 5)
    val heroShelf = shelves.firstOrNull { it.id == state.homeFocus.shelfTitle } ?: firstShelf
    val hero = if (tv) heroShelf?.items?.firstOrNull { HomeFocusPolicy.mediaKey(it) == state.homeFocus.mediaKey }
        ?: heroShelf?.items?.firstOrNull() else featured.firstOrNull()
    val heroNeighbours = remember(heroShelf?.items, hero) {
        neighbouringHeroItems(heroShelf?.items.orEmpty(), heroShelf?.items?.indexOf(hero) ?: -1)
    }
    val shortcuts = remember { FocusRequester() }
    val shelfFocus = remember(shelves.map { it.id }) { shelves.map { FocusRequester() } }
    var rowRequest by remember { mutableIntStateOf(0) }
    var requestedRow by remember { mutableIntStateOf(0) }
    var pendingRowId by remember { mutableStateOf<String?>(null) }
    var requestedRowId by remember { mutableStateOf<String?>(null) }
    var rowDirection by remember { mutableIntStateOf(1) }
    val rowEntrance = remember { Animatable(1f) }
    val context = LocalContext.current
    val animateRows = remember(context) { systemAnimationsEnabled(context) }
    val rowTravel = with(LocalDensity.current) { 32.dp.toPx() }
    val rowInputMode = LocalInputModeManager.current
    LaunchedEffect(rowRequest, shelves.map { it.id }) {
        val target = shelves.indexOfFirst { it.id == requestedRowId }
        if (tv && rowRequest > 0 && target in shelves.indices) {
            val request = rowRequest
            rowEntrance.snapTo(if (animateRows) 0f else 1f)
            list.scrollToItem(target)
            withFrameNanos {}
            rowInputMode.requestInputMode(InputMode.Keyboard)
            var attempts = 0
            while ((attempts == 0 || controller.state.value.homeFocus.shelfTitle != shelves[target].id || !controller.homeContentFocused) && attempts++ < 6) {
                runCatching { shelfFocus[target].requestFocus() }
                withFrameNanos {}
            }
            if (animateRows) rowEntrance.animateTo(1f, tween(180, easing = EaseOutCubic))
            if (rowRequest == request) pendingRowId = null
        }
    }
    val restoreFocus = remember(state.homeFocus.restoreRequest) {
        state.homeFocus.takeIf { it.restoreRequest > 0 && it.surface == HomeFocusSurface.Card }
    }
    LaunchedEffect(state.homeFocus.shelfTitle, state.homeFocus.restoreRequest, tv, queue?.id) {
        if (tv) {
            val row = shelves.indexOfFirst { it.id == state.homeFocus.shelfTitle }
            if (state.homeFocus.mediaKey == null) list.scrollToItem(0)
            else if (row >= 0) list.scrollToItem(row)
        }
    }
    when (tv) {
        true -> {
            Box(Modifier.fillMaxSize()) {
                if (hero != null) {
                    LaunchedEffect(hero.id, state.selectedProfile?.id) {
                        kotlinx.coroutines.delay(180)
                        controller.enrichVisibleHomeItem(hero)
                    }
                    TvHeroBackdrop(hero, preloadItems = heroNeighbours, fullScreen = true, copyAtBottom = false)
                    Box(Modifier.padding(start = 104.dp, top = 54.dp).height(626.dp), contentAlignment = Alignment.CenterStart) { TelevisionHero(hero, heroShelf?.isQueueShelf == true) }
                } else EmptyState(if (state.homeLoading) "Starting VIPTV…" else "Your library is ready", "Browse Discover to find something to watch.", "home", Modifier.height(600.dp))
                Box(Modifier.fillMaxSize().drawWithCache {
                    val radius = size.width * .74f
                    val center = Offset(size.width * .85f, 0f)
                    val shade = Brush.radialGradient(0f to Color.Black.copy(alpha = .84f), .55f to Color.Black.copy(alpha = .76f), 1f to Color.Transparent, center = center, radius = radius)
                    onDrawBehind { scale(scaleX = 1f, scaleY = .18f, pivot = center) {
                        drawRect(shade, size = Size(size.width, maxOf(size.height, radius)))
                    } }
                })
                Box(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 40.dp).widthIn(max = 1000.dp)) { SimklHomeShortcuts(controller, shortcuts, shelfFocus.firstOrNull()) }
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(400.dp).align(Alignment.BottomStart)
                    .onFocusChanged { controller.homeContentFocused = it.hasFocus }
                    .onPreviewKeyEvent {
                        val key = it.nativeKeyEvent
                        val direction = when (key.keyCode) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
                        if (key.action == KeyEvent.ACTION_DOWN) {
                            controller.recordHomeDirectionalInput()
                            if (direction != 0 && shelves.isNotEmpty()) {
                                val currentRow = pendingRowId?.let { id -> shelves.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: shelves.indexOfFirst { shelf -> shelf.id == controller.state.value.homeFocus.shelfTitle }.coerceAtLeast(0)
                                if (direction < 0 && currentRow == 0) {
                                    runCatching { shortcuts.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                                rowDirection = direction
                                requestedRow = (currentRow + direction).coerceIn(shelves.indices)
                                if (requestedRow != currentRow) { requestedRowId = shelves[requestedRow].id; pendingRowId = requestedRowId; rowRequest++ }
                            }
                        }
                        direction != 0
                    }, userScrollEnabled = false) {
                    itemsIndexed(shelves, key = { _, shelf -> shelf.id }) { row, shelf ->
                        Box(Modifier.height(400.dp).padding(start = 104.dp, end = 20.dp, top = 16.dp)) {
                            ShelfRow(shelf, row, state.homeLoading, restoreFocus?.takeIf { it.shelfTitle == shelf.id }, controller, shelfFocus[row], Modifier.graphicsLayer {
                                translationY = rowDirection * rowTravel * (1f - rowEntrance.value)
                                alpha = .65f + .35f * rowEntrance.value
                            }, contentEntry = row == list.firstVisibleItemIndex)
                        }
                    }
                }
                if (shelves.size > 1) CarouselPosition(shelves.size, list.firstVisibleItemIndex,
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 90.dp).width(4.dp).height(220.dp))
            }
        }
        false -> {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().onFocusChanged { controller.homeContentFocused = it.hasFocus }.onPreviewKeyEvent {
                    if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) controller.recordHomeDirectionalInput()
                    false
                }, contentPadding = PaddingValues(start = measure(0, 16), end = measure(0, 16), top = measure(0, 8), bottom = measure(54, 164)),
                    verticalArrangement = Arrangement.spacedBy(measure(36, 20))) {
                    item(key = "home-header") { ScreenHeader("Home", trailing = { PhoneTabActions(state, controller) }) }
                    item(key = "featured") {
                        if (featured.isEmpty() && state.homeLoading) PhoneHomeSkeleton(shelves.isEmpty())
                        else if (featured.isEmpty()) EmptyState("Your library is empty", "Browse Discover to find movies and series.", "home")
                        else {
                            val pager = rememberPagerState(pageCount = { featured.size })
                            HorizontalPager(pager, pageSpacing = 16.dp, key = { featured[it].id }) { index ->
                                LaunchedEffect(featured[index].id, state.homeLoading) { controller.enrichVisibleHomeItem(featured[index]) }
                                PhoneHero(featured[index], phoneHeroShelf?.isQueueShelf == true, state, controller)
                            }
                            if (featured.size > 1) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                                repeat(featured.size) { index -> Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == index) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (pager.currentPage == index) C.textPrimary else C.fillDot)) }
                            }
                        }
                    }
                    item(key = "simkl-shortcuts") { SimklHomeShortcuts(controller) }
                    itemsIndexed(shelves, key = { _, shelf -> shelf.id }) { row, shelf ->
                        Box(Modifier.padding(start = measure(104, 0))) {
                            ShelfRow(shelf, row, state.homeLoading, restoreFocus?.takeIf { it.shelfTitle == shelf.id }, controller)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CarouselPosition(count: Int, selected: Int, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Carousel ${selected + 1} of $count" }) {
        val gap = minOf(8.dp.toPx(), size.height / (count * 2f))
        val unit = minOf(6.dp.toPx(), (size.height - gap * (count - 1)) / (count + 2f))
        val total = unit * (count + 2) + gap * (count - 1)
        var y = (size.height - total) / 2f
        for (index in 0 until count) {
            val length = if (index == selected) unit * 3f else unit
            drawRoundRect(if (index == selected) C.textPrimary else C.textSecondary.copy(alpha = .45f),
                Offset(0f, y), Size(size.width, length), CornerRadius(size.width / 2f))
            y += length + gap
        }
    }
}

@Composable private fun TelevisionHero(media: Media, queue: Boolean) {
    val hero = remember(media) { CoreModels.presentation(media) }
    val actions = remember(media, queue) { SharedPresentation.home(media, queue) }
    Column(Modifier.width(950.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleArtwork(media.name, hero.titleLogo, Modifier.width(800.dp).height(118.dp), 56)
        Box(Modifier.height(34.dp)) { if (hero.episodeLabel.isNotBlank()) VText(hero.episodeLabel, 24, bold = true, lines = 1) }
        if (actions.showHeroProgress) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressLine(hero.progress.toFloat(), Modifier.width(300.dp))
            VText(formatTime(media.positionMillis) + " of " + ((media.durationMillis ?: 0) / 60000) + " min", 22, color = C.textSecondary)
        }
        Box(Modifier.height(32.dp)) {
            if (mediaFacts(media).isNotBlank()) VText(mediaFacts(media), 22, color = C.textSecondary, lines = 1)
            else SkeletonBlock(Modifier.width(420.dp).height(20.dp), radius = 8.dp)
        }
        HeroDescription(media.description, Modifier.width(675.dp).height(180.dp), television = true)
    }
}

@Composable private fun PhoneHero(media: Media, queue: Boolean, state: AppState, controller: AppController) {
    val saved = state.favorites.any { it.id == media.id && it.type == media.type }
    val hero = remember(media) { CoreModels.presentation(media) }
    val actions = remember(media, queue) { SharedPresentation.home(media, queue) }
    Box(Modifier.fillMaxWidth().height(410.dp).clip(RoundedCornerShape(28.dp)).background(C.surfaceN1)) {
        Artwork(hero.heroImage, media.name, Modifier.fillMaxWidth().height(270.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Transparent, C.surfaceN1))))
        VText("FEATURED", 11, Modifier.padding(14.dp).clip(CircleShape).background(C.fillBadgeGlass).padding(horizontal = 12.dp, vertical = 7.dp), bold = true)
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Holdable({ controller.open(media) }, modifier = Modifier.fillMaxWidth()) {
                TitleArtwork(media.name, hero.titleLogo, Modifier.fillMaxWidth().height(82.dp), 30)
            }
            VText(mediaFacts(media), 13, color = C.textSecondary, lines = 1)
            HeroDescription(media.description, Modifier.fillMaxWidth().height(42.dp), television = false)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(actions.heroPrimaryActionLabel, { controller.activateHero(media, queue) }, Modifier.weight(1f), "play", primary = true)
                AppIconButton(if (saved) "check" else "plus", if (saved) "Remove from My List" else "Add to My List", { controller.toggleMyList(media) })
            }
        }
    }
}

/** Home carousels keep the selected card at their leading edge until scrolling reaches its limit. */
internal class HomeCarouselScroll(private val leadingInset: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(220, easing = FastOutSlowInEasing)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - leadingInset
}

@Composable private fun ShelfRow(shelf: HomeShelf, index: Int, homeLoading: Boolean, restoreFocus: HomeFocusSnapshot?, controller: AppController, entryFocus: FocusRequester? = null, rowDecoration: Modifier = Modifier, contentEntry: Boolean = index == 0) {
    val tv = LocalTv.current
    val rail = LocalRailFocus.current
    val initial = LocalContentFocus.current
    val horizontal = rememberLazyListState()
    val leadingInset = 0f
    val carouselScroll = remember(leadingInset) { HomeCarouselScroll(leadingInset) }
    val focuses = remember(shelf.items.map { it.id }) { shelf.items.map { FocusRequester() } }
    var firstPlaced by remember { mutableStateOf(false) }
    var initialHandled by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(tv, index, firstPlaced, windowFocused, homeLoading, shelf.isQueueShelf) {
        if (tv && index == 0 && firstPlaced && windowFocused && !initialHandled && (!homeLoading || shelf.isQueueShelf)) {
            initialHandled = true
            if (controller.state.value.homeFocus.mediaKey == null) {
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { focuses.firstOrNull()?.requestFocus() }
            }
        }
    }

    LaunchedEffect(restoreFocus?.restoreRequest) {
        if (tv && restoreFocus != null) {
            fun stillRequested(): Boolean {
                val current = controller.state.value.homeFocus
                return current.restoreRequest == restoreFocus.restoreRequest &&
                    current.surface == restoreFocus.surface && current.mediaKey == restoreFocus.mediaKey &&
                    current.shelfTitle == restoreFocus.shelfTitle && HomeFocusPolicy.mayRestore(current, restoreFocus.inputEpoch)
            }
            if (!stillRequested()) return@LaunchedEffect
            val target = shelf.items.indexOfFirst { HomeFocusPolicy.mediaKey(it) == restoreFocus.mediaKey }
            if (target >= 0) {
                horizontal.scrollToItem(target)
                withFrameNanos {}
                if (stillRequested()) runCatching { focuses[target].requestFocus() }
            }
        }
    }
    Column {
        if (tv) VText(shelf.title.uppercase(), 20, Modifier.padding(bottom = 18.dp), color = C.textSecondary, bold = true, lines = 1)
        if (!tv) Row(Modifier.fillMaxWidth().padding(end = measure(96, 0), bottom = measure(18, 12)), verticalAlignment = Alignment.CenterVertically) {
            VText(if (shelf.isQueueShelf) "Continue watching" else if (tv) shelf.title else PhonePresentationPolicy.shelfHeading(shelf), if (tv) 32 else 20, Modifier.weight(1f), display = true, lines = 1)
            if (!tv && shelf.isQueueShelf) Holdable({ controller.openContinueWatching() }, modifier = Modifier.height(44.dp).padding(start = 12.dp)) { VText("See all", 13, color = C.textSecondary) }
        }
        // Phone rows start exactly on the heading edge (AND-042-CARDS).
        CompositionLocalProvider(LocalBringIntoViewSpec provides if (tv) carouselScroll else LocalBringIntoViewSpec.current) {
            LazyRow(state = horizontal, modifier = Modifier.fillMaxWidth().then(rowDecoration).focusGroup(), horizontalArrangement = Arrangement.spacedBy(measure(36, if (shelf.items.firstOrNull()?.type == "live") 10 else 12)), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(shelf.items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { column, media ->
                    val action = { controller.activateCard(media, shelf.isQueueShelf, SourceReturn.Home) }
                    val hold = { if (shelf.isQueueShelf) controller.requestQueueManage(media) else controller.requestDialog(DialogKind.MyListManage, media.name, media) }
                    if (shelf.isQueueShelf) LaunchedEffect(media.id, controller.state.value.selectedProfile?.id) { controller.enrichVisibleHomeItem(media) }
                    if (!tv && shelf.isQueueShelf) QueueCard(media, action, hold)
                    else if (!tv && media.type == "live") LiveLogoTile(media, action, hold)
                    else MediaCard(media, Modifier.focusRequester(focuses[column]).then(if (column == horizontal.firstVisibleItemIndex && entryFocus != null) Modifier.focusRequester(entryFocus) else Modifier).onGloballyPositioned { if (column == 0) firstPlaced = true }.then(if (contentEntry && column == horizontal.firstVisibleItemIndex && tv) Modifier.focusRequester(initial) else Modifier).then(if (column == 0 && tv) Modifier.focusProperties { left = rail } else Modifier),
                        shelf.isQueueShelf, action, hold, onFocused = { controller.recordHomeFocus(index, shelf.id, media) })
                }
            }
        }
    }
}

@Composable internal fun QueueCard(media: Media, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val card = remember(media) { CoreModels.card(media, true) }
    Holdable(onClick, onHold, modifier.width(292.dp).height(96.dp).clip(RoundedCornerShape(22.dp)).background(C.surfaceN1)) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(card.image, null, Modifier.size(58.dp, 76.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                VText(card.title, 15, bold = true, lines = 1)
                VText(PhonePresentationPolicy.cardContext(media), 12, color = C.textSecondary, lines = 1)
                ProgressLine(card.progress?.toFloat() ?: 0f)
            }
            Holdable(onHold, modifier = Modifier.size(44.dp).clip(CircleShape).background(C.surfaceN3)) { VIcon("more", "More options", color = LocalAccent.current) }
        }
    }
}

/** AND-042-SKELETON: the phone Home hero and shelves before the first row arrives. */
@Composable private fun PhoneHomeSkeleton(withShelves: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(410.dp), 28.dp)
        if (withShelves) repeat(2) {
            Column {
                SkeletonBlock(Modifier.padding(bottom = 12.dp).width(160.dp).height(20.dp), 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { SkeletonBlock(Modifier.width(232.dp).aspectRatio(16f / 9)) }
                }
            }
        }
    }
}

/** Fixed geometry during metadata arrival; only the copy fades, never the layout. */
@Composable private fun HeroDescription(description: String?, modifier: Modifier, television: Boolean) {
    Box(modifier) {
        androidx.compose.animation.Crossfade(targetState = description?.takeIf { it.isNotBlank() },
            animationSpec = androidx.compose.animation.core.tween(180), label = "hero-details") { text ->
            if (text != null) VText(text, if (television) 26 else 15, color = C.textBody, lines = if (television) 5 else 2)
            else Column(verticalArrangement = Arrangement.spacedBy(if (television) 16.dp else 8.dp)) {
                repeat(if (television) 4 else 2) { row -> SkeletonBlock(Modifier.fillMaxWidth(if (row == (if (television) 3 else 1)) .65f else .94f).height(if (television) 18.dp else 11.dp), radius = 6.dp) }
            }
        }
    }
}
