@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.viptv.app

import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.ViptvColor as C

internal fun AppController.activateHero(media: Media, queue: Boolean) {
    val action = MediaCardPolicy.primary(queue, media)
    when {
        media.type == "live" -> activateCard(media)
        action == MediaCardAction.PlayQueuedNext -> playQueuedNext(media)
        action == MediaCardAction.ResumeExactSource -> chooseHeroSources(media, queue, resume = true)
        HomeHeroPrimaryPolicy.choosesManualSource(action, queue, media) -> chooseHeroSources(media, queue)
        else -> open(media)
    }
}

internal fun AppController.chooseHeroSources(media: Media, queue: Boolean, resume: Boolean = false) =
    chooseSources(media, resume, SourceReturn.Home, queueEpisodeReturn = queue && SourceReturnPolicy.parentSeries(media) != null)

@Composable internal fun HomeScreen(state: AppState, controller: AppController, list: LazyListState = rememberLazyListState()) {
    val tv = LocalTv.current
    val shelves = state.shelves.filter { it.items.isNotEmpty() }
    val queue = shelves.firstOrNull { it.isQueueShelf }
    val firstShelf = shelves.firstOrNull()
    val featured = if (tv) firstShelf?.items.orEmpty().take(1) else shelves.firstOrNull { !it.isQueueShelf && it.items.firstOrNull()?.type != "live" }?.items.orEmpty().take(5).ifEmpty { queue?.items.orEmpty().take(1) }
    val heroShelf = firstShelf
    val heroKey = state.homeFocus.heroMediaKey ?: state.homeFocus.mediaKey.takeIf { state.homeFocus.shelfTitle == firstShelf?.id }
    val hero = if (tv) firstShelf?.items?.firstOrNull { HomeFocusPolicy.mediaKey(it) == heroKey }
        ?: featured.firstOrNull() else featured.firstOrNull()
    val initial = LocalContentFocus.current
    val rail = LocalRailFocus.current
    val showHero = tv && (state.homeFocus.surface == HomeFocusSurface.Hero || state.homeFocus.shelfIndex == 0)
    LaunchedEffect(showHero, state.homeFocus.shelfIndex) {
        // Enter the top region smoothly; moving between its cards must not restart the scroll.
        val motion = tween<Float>(280, easing = FastOutSlowInEasing)
        if (showHero) {
            if (list.firstVisibleItemIndex == 0) list.animateScrollBy(-list.firstVisibleItemScrollOffset.toFloat(), motion)
            else list.animateScrollToItem(0)
        } else if (tv && state.homeFocus.surface == HomeFocusSurface.Card && state.homeFocus.shelfIndex == 1) {
            // Ease the first downward boundary too, revealing only the clipped row/caption.
            val target = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == state.homeFocus.shelfTitle }
            val remaining = target?.let { it.offset + it.size - list.layoutInfo.viewportEndOffset } ?: 0
            if (remaining > 0) list.animateScrollBy(remaining.toFloat(), motion)
        }
    }
    LaunchedEffect(state.homeFocus.restoreRequest, tv) {
        if (tv && state.homeFocus.mediaKey != null && state.homeFocus.surface == HomeFocusSurface.Card) {
            val row = shelves.indexOfFirst { it.id == state.homeFocus.shelfTitle }
            if (row >= 0) list.scrollToItem(if (row == 0) 0 else row + 1)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().onFocusChanged { controller.homeContentFocused = it.hasFocus }.onPreviewKeyEvent {
            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) controller.recordHomeDirectionalInput()
            false
        }, contentPadding = PaddingValues(start = measure(0, 16), end = measure(0, 16), top = measure(0, 8), bottom = measure(54, 164)),
            verticalArrangement = Arrangement.spacedBy(measure(36, 20))) {
            item(key = "featured") {
                if (tv) {
                    if (hero != null) Box(Modifier.fillMaxWidth().height(664.dp)) {
                        HeroBackdrop(hero)
                        Box(Modifier.padding(start = 192.dp, end = 96.dp, top = 54.dp)) { TelevisionHero(hero, heroShelf?.isQueueShelf == true, heroShelf?.let(shelves::indexOf)?.coerceAtLeast(0) ?: 0, heroShelf?.id.orEmpty(), state, controller, initial, rail) }
                    }
                    else EmptyState(if (state.homeLoading) "Starting VIPTV…" else "Your library is ready", "Browse Discover to find something to watch.", "home", Modifier.height(540.dp))
                } else {
                    // AND-042-HOME: no header bar; the rounded hero is the first element.
                    if (featured.isEmpty() && state.homeLoading) PhoneHomeSkeleton(shelves.isEmpty())
                    else if (featured.isEmpty()) EmptyState("Your library is empty", "Browse Discover to find movies and series.", "home")
                    else {
                        val pager = rememberPagerState(pageCount = { featured.size })
                        HorizontalPager(pager, pageSpacing = 16.dp, key = { featured[it].id }) { index ->
                            LaunchedEffect(featured[index].id, state.homeLoading) { controller.enrichVisibleHomeItem(featured[index]) }
                            PhoneHero(featured[index], state, controller)
                        }
                        if (featured.size > 1) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(featured.size) { index -> Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == index) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (pager.currentPage == index) C.textPrimary else C.fillDot)) }
                        }
                    }
                }
            }
            itemsIndexed(shelves, key = { _, shelf -> shelf.id }) { row, shelf ->
                Box(Modifier.padding(start = measure(192, 0))) { ShelfRow(shelf, row, state, controller) }
            }
        }
    }
}

@Composable internal fun HeroBackdrop(media: Media) {
    val presentation = remember(media) { CoreModels.presentation(media) }
    val ground = LocalGround.current
    Box(Modifier.fillMaxWidth().height(950.dp)) {
        Artwork(presentation.heroImage, null, Modifier.fillMaxSize().blur(72.dp).alpha(.6f))
        Artwork(presentation.heroImage, null, Modifier.align(Alignment.TopEnd).width(1120.dp).height(720.dp))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(ground, ground.copy(alpha = .92f), Color.Transparent))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, ground), startY = 440f)))
    }
}

@Composable private fun TelevisionHero(media: Media, queue: Boolean, shelfIndex: Int, shelfId: String, state: AppState, controller: AppController, initial: FocusRequester, rail: FocusRequester) {
    val hero = remember(media) { CoreModels.presentation(media) }
    val saved = state.favorites.any { it.id == media.id && it.type == media.type }
    LaunchedEffect(media.id) { if (state.homeFocus.mediaKey == null || (state.homeFocus.surface == HomeFocusSurface.Hero && controller.homeContentFocused)) { withFrameNanos {}; runCatching { initial.requestFocus() } } }
    LaunchedEffect(media.id, state.homeFocus.restoreRequest) {
        val requested = state.homeFocus
        if (requested.restoreRequest > 0 && requested.surface == HomeFocusSurface.Hero &&
            requested.mediaKey == HomeFocusPolicy.mediaKey(media)) {
            withFrameNanos {}
            val current = controller.state.value.homeFocus
            if (current.restoreRequest == requested.restoreRequest && current.surface == requested.surface &&
                current.mediaKey == requested.mediaKey && HomeFocusPolicy.mayRestore(current, requested.inputEpoch)) {
                runCatching { initial.requestFocus() }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(610.dp)) {
        VText(if (queue) "CONTINUE WATCHING" else if (media.type == "live") "LIVE NOW" else "FEATURED", 20, Modifier.offset(y = 96.dp), C.textSecondary, bold = true)
        if (hero.titleLogo.isNullOrBlank()) VText(media.name, 56, Modifier.offset(y = 142.dp).width(800.dp), display = true, lines = 2)
        else Artwork(hero.titleLogo, media.name, Modifier.offset(y = 142.dp).size(410.dp, 118.dp), ContentScale.Fit)
        VText(hero.episodeLabel, 24, Modifier.offset(y = 286.dp).width(420.dp), bold = true, lines = 1)
        if (media.positionMillis > 0 && (media.durationMillis ?: 0) > 0) {
            ProgressLine(hero.progress.toFloat(), Modifier.offset(444.dp, 298.dp).width(180.dp))
            VText(formatTime(media.positionMillis) + " of " + ((media.durationMillis ?: 0) / 60000) + " min", 24, Modifier.offset(644.dp, 286.dp), C.textSecondary)
        }
        VText(mediaFacts(media), 22, Modifier.offset(y = 336.dp).width(950.dp), C.textSecondary, lines = 1)
        VText(media.description.orEmpty(), 26, Modifier.offset(y = 394.dp).width(760.dp), C.textBody, lines = 2)
        Row(Modifier.offset(y = 496.dp).onFocusChanged { if (it.hasFocus) controller.recordHomeFocus(shelfIndex, shelfId, media, HomeFocusSurface.Hero) }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AppButton(hero.primaryActionLabel, { controller.activateHero(media, queue) }, Modifier.width(228.dp).focusRequester(initial).focusProperties { left = rail },
                "play", tvAccent = hero.primaryAction == "resume", onHold = { controller.chooseHeroSources(media, queue) },
                onFocused = { controller.recordHomeFocus(shelfIndex, shelfId, media, HomeFocusSurface.Hero) })
            AppButton("Details", { controller.open(media) }, Modifier.width(228.dp))
            AppIconButton(if (saved) "check" else "plus", if (saved) "Remove from My List" else "Add to My List", { controller.toggleMyList(media) })
        }
    }
}

@Composable private fun PhoneHero(media: Media, state: AppState, controller: AppController) {
    val saved = state.favorites.any { it.id == media.id && it.type == media.type }
    val hero = remember(media) { CoreModels.presentation(media) }
    Box(Modifier.fillMaxWidth().height(410.dp).clip(RoundedCornerShape(28.dp)).background(C.surfaceN1)) {
        Artwork(hero.heroImage, media.name, Modifier.fillMaxWidth().height(270.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Transparent, C.surfaceN1))))
        VText("FEATURED", 11, Modifier.padding(14.dp).clip(CircleShape).background(C.fillBadgeGlass).padding(horizontal = 12.dp, vertical = 7.dp), bold = true)
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Holdable({ controller.open(media) }, modifier = Modifier.fillMaxWidth()) {
                if (hero.titleLogo.isNullOrBlank()) VText(media.name, 30, Modifier.fillMaxWidth(), display = true, lines = 2)
                else Artwork(hero.titleLogo, media.name, Modifier.fillMaxWidth().height(64.dp), ContentScale.Fit, Alignment.CenterStart)
            }
            VText(mediaFacts(media), 13, color = C.textSecondary, lines = 1)
            VText(media.description.orEmpty(), 15, color = C.textBody, lines = 2)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(hero.primaryActionLabel, { controller.activateHero(media, false) }, Modifier.weight(1f), "play", primary = true)
                AppIconButton(if (saved) "check" else "plus", if (saved) "Remove from My List" else "Add to My List", { controller.toggleMyList(media) })
            }
        }
    }
}

@Composable private fun ShelfRow(shelf: HomeShelf, index: Int, state: AppState, controller: AppController) {
    val tv = LocalTv.current
    val rail = LocalRailFocus.current
    val horizontal = rememberLazyListState()
    val focuses = remember(shelf.items.map { it.id }) { shelf.items.map { FocusRequester() } }
    LaunchedEffect(state.homeFocus.restoreRequest) {
        if (tv && state.homeFocus.surface == HomeFocusSurface.Card && state.homeFocus.shelfTitle == shelf.id) {
            val target = shelf.items.indexOfFirst { HomeFocusPolicy.mediaKey(it) == state.homeFocus.mediaKey }
            if (target >= 0) { horizontal.scrollToItem(target); withFrameNanos {}; runCatching { focuses[target].requestFocus() } }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(end = measure(96, 0), bottom = measure(18, 12)), verticalAlignment = Alignment.CenterVertically) {
            VText(if (shelf.isQueueShelf) "Continue watching" else if (tv) shelf.title else PhonePresentationPolicy.shelfHeading(shelf), if (tv) 32 else 20, Modifier.weight(1f), display = true, lines = 1)
            if (!tv && shelf.isQueueShelf) Holdable({ controller.openContinueWatching() }, modifier = Modifier.height(44.dp).padding(start = 12.dp)) { VText("See all", 13, color = C.textSecondary) }
        }
        // Phone rows start exactly on the heading edge (AND-042-CARDS).
        LazyRow(state = horizontal, modifier = Modifier.fillMaxWidth().focusGroup(), horizontalArrangement = Arrangement.spacedBy(measure(36, if (shelf.items.firstOrNull()?.type == "live") 10 else 12)), contentPadding = if (tv) PaddingValues(4.dp) else PaddingValues(vertical = 4.dp)) {
            itemsIndexed(shelf.items, key = { _, item -> HomeFocusPolicy.mediaKey(item) }) { column, media ->
                val action = { controller.activateCard(media, shelf.isQueueShelf, SourceReturn.Home) }
                val hold = { if (shelf.isQueueShelf) controller.requestQueueManage(media) else controller.requestDialog(DialogKind.MyListManage, media.name, media) }
                LaunchedEffect(media.id, state.homeLoading) { controller.enrichVisibleHomeItem(media) }
                if (!tv && shelf.isQueueShelf) QueueCard(media, action, hold)
                else if (!tv && media.type == "live") LiveLogoTile(media, action, hold)
                else MediaCard(media, Modifier.focusRequester(focuses[column]).then(if (column == 0 && tv) Modifier.focusProperties { left = rail } else Modifier),
                    shelf.isQueueShelf, action, hold, onFocused = { controller.recordHomeFocus(index, shelf.id, media) })
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
