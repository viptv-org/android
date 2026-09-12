package org.viptv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val DetailCanvas = Color(0xFF101112)
private val DetailSurface = Color(0xFF202224)
private val DetailWhite = Color(0xFFF5F5F5)
private val DetailMuted = Color(0xFFC5C6C7)

/**
 * Shared title-detail surface. The controller owns navigation and sources; this screen keeps only
 * title-local, recoverable presentation state such as the selected season and transient dialogs.
 */
@Composable
internal fun DetailsScreen(media: Media, controller: AppController) {
    if (media.type == "series") SeriesDetails(media, controller) else MovieDetails(media, controller)
}

@Composable
private fun SeriesDetails(media: Media, controller: AppController) {
    val seasons = media.episodes.mapNotNull(Media::season).distinct().sorted().ifEmpty { listOf(media.season ?: 1) }
    val defaultSeason = media.season?.takeIf { it in seasons } ?: seasons.first()
    var selectedSeason by rememberSaveable(media.id) { mutableStateOf(defaultSeason) }
    val activeSeason = selectedSeason.takeIf { it in seasons } ?: defaultSeason

    var seasonPickerOpen by remember(media.id) { mutableStateOf(false) }
    var moreInfoOpen by remember(media.id) { mutableStateOf(false) }
    var focusTarget by rememberSaveable(media.id) { mutableStateOf("episode") }
    val seasonFocus = remember { FocusRequester() }
    val moreInfoFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val modalFocus = remember { FocusRequester() }
    val episodes = media.episodes.filter { (it.season ?: defaultSeason) == activeSeason }
    // Preserve the Roku entry rule: return to the newest watched episode, otherwise
    // offer the first unwatched episode after it.  The source picker remains manual.
    val preferredEpisode = episodes
        .sortedWith(compareBy<Media> { it.season ?: activeSeason }.thenBy { it.episode ?: Int.MAX_VALUE })
        .let { ordered ->
            val latestWatchedIndex = ordered.indexOfLast { it.positionMillis > 0 }
            when {
                latestWatchedIndex >= 0 -> ordered.drop(latestWatchedIndex + 1)
                    .firstOrNull { it.positionMillis <= 0 } ?: ordered[latestWatchedIndex]
                else -> ordered.firstOrNull()
            }
        }

    BackHandler(enabled = seasonPickerOpen || moreInfoOpen) {
        if (seasonPickerOpen) {
            seasonPickerOpen = false
            focusTarget = "season"
        } else {
            moreInfoOpen = false
            focusTarget = "info"
        }
    }
    LaunchedEffect(focusTarget, seasonPickerOpen, moreInfoOpen, preferredEpisode?.id) {
        if (!seasonPickerOpen && !moreInfoOpen) {
            when (focusTarget) {
                "season" -> seasonFocus.requestFocus()
                "info" -> moreInfoFocus.requestFocus()
                else -> if (preferredEpisode != null) episodeFocus.requestFocus() else seasonFocus.requestFocus()
            }
        }
    }
    LaunchedEffect(seasons, defaultSeason) {
        if (selectedSeason !in seasons) selectedSeason = defaultSeason
    }
    LaunchedEffect(seasonPickerOpen, moreInfoOpen, activeSeason) {
        if (seasonPickerOpen || moreInfoOpen) modalFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(DetailCanvas)) {
        Text(
            media.name,
            color = DetailWhite,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(112.dp, 74.dp).width(900.dp),
        )
        Text(
            media.type.uppercase(),
            color = DetailMuted,
            fontSize = 18.sp,
            modifier = Modifier.offset(112.dp, 132.dp).width(706.dp),
        )
        Row(Modifier.offset(112.dp, 188.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DetailButton(
                "Season $activeSeason",
                onActivate = { seasonPickerOpen = true },
                modifier = Modifier.width(256.dp).height(48.dp).focusRequester(seasonFocus),
            )
            DetailButton("My List", { controller.toggleMyList(media) }, Modifier.width(256.dp).height(48.dp))
            DetailButton(
                "More info",
                onActivate = { moreInfoOpen = true },
                modifier = Modifier.width(256.dp).height(48.dp).focusRequester(moreInfoFocus),
            )
        }
        Text(
            "Episodes",
            color = DetailWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.offset(976.dp, 198.dp).width(220.dp),
        )
        if (episodes.isEmpty()) {
            Text(
                "No episodes are available for Season $activeSeason.",
                color = DetailMuted,
                fontSize = 18.sp,
                modifier = Modifier.offset(112.dp, 310.dp).width(1096.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.offset(112.dp, 262.dp).width(1096.dp).height(330.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeDetailCard(
                        episode = episode,
                        controller = controller,
                        focusRequester = if (episode.id == preferredEpisode?.id) episodeFocus else null,
                    )
                }
            }
        }
        if (seasonPickerOpen) {
            DetailModal("Choose a season") {
                LazyColumn(Modifier.width(792.dp).heightIn(max = 424.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons, key = { it }) { season ->
                        DetailButton(
                            "Season $season",
                            onActivate = {
                                selectedSeason = season
                                seasonPickerOpen = false
                                focusTarget = "season"
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .then(if (season == activeSeason) Modifier.focusRequester(modalFocus) else Modifier),
                            selected = season == activeSeason,
                        )
                    }
                }
            }
        }
        if (moreInfoOpen) {
            DetailModal("More information") {
                Text(
                    media.description ?: "No additional information is available for this title.",
                    color = DetailMuted,
                    fontSize = 19.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(792.dp).padding(top = 16.dp),
                )
                DetailButton(
                    "Back",
                    onActivate = {
                        moreInfoOpen = false
                        focusTarget = "info"
                    },
                    modifier = Modifier.padding(top = 28.dp).width(180.dp).height(52.dp).focusRequester(modalFocus),
                )
            }
        }
    }
}

@Composable
private fun MovieDetails(media: Media, controller: AppController) = Box(Modifier.fillMaxSize().background(DetailCanvas)) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(media.id, media.type) { initialFocus.requestFocus() }
    if (!media.poster.isNullOrBlank()) {
        AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(620.dp))
        Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(listOf(DetailCanvas.copy(alpha = .32f), DetailCanvas.copy(alpha = .90f), DetailCanvas))))
    }
    Box(Modifier.offset(112.dp, 126.dp).width(236.dp).height(354.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
        Text(media.name.take(1), color = DetailMuted, fontSize = 42.sp)
        if (!media.poster.isNullOrBlank()) AsyncImage(model = media.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Column(Modifier.offset(380.dp, 126.dp).width(804.dp)) {
        Text(media.name, color = DetailWhite, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(media.type.uppercase(), color = DetailMuted, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp).height(48.dp))
        Text(media.description ?: "No description available.", color = DetailMuted, fontSize = 20.sp, modifier = Modifier.padding(top = 28.dp).height(128.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailButton(if (media.positionMillis > 0) "Resume" else "Play", { controller.chooseSources(media, media.positionMillis > 0) }, Modifier.width(192.dp).height(56.dp).focusRequester(initialFocus))
            DetailButton("Choose source", { controller.chooseSources(media) }, Modifier.width(192.dp).height(56.dp))
            DetailButton("My List", { controller.toggleMyList(media) }, Modifier.width(192.dp).height(56.dp))
        }
    }
}

@Composable
private fun EpisodeDetailCard(episode: Media, controller: AppController, focusRequester: FocusRequester?) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    Holdable(
        onActivate = { controller.chooseSources(episode) },
        onHold = { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, media = episode) },
        modifier = Modifier.fillMaxWidth().height(330.dp).then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(2.dp, DetailWhite, RoundedCornerShape(8.dp)) else Modifier),
        onInfo = { controller.requestDialog(DialogKind.EpisodeManage, episode.episodeTitle ?: episode.name, media = episode) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242628)), contentAlignment = Alignment.Center) {
                Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = DetailMuted, fontSize = 20.sp)
                if (!episode.poster.isNullOrBlank()) AsyncImage(model = episode.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text("S${episode.season ?: 0} E${episode.episode ?: 0}", color = DetailMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Text(episode.episodeTitle ?: episode.name.ifBlank { "Episode ${episode.episode ?: ""}" }, color = DetailWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            Text(episode.description ?: "", color = DetailMuted, fontSize = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).height(94.dp))
        }
    }
}

@Composable
private fun DetailModal(title: String, content: @Composable () -> Unit) = Box(
    Modifier.fillMaxSize().background(Color(0xDC080909)),
    contentAlignment = Alignment.Center,
) {
    Column(
        Modifier.width(880.dp).background(Color(0xFF191B1D), RoundedCornerShape(12.dp)).padding(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = DetailWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun DetailButton(
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> DetailWhite
        selected -> Color(0xFF303234)
        else -> DetailSurface
    }
    Holdable(
        onActivate = onActivate,
        onHold = null,
        modifier = modifier.onFocusChanged { focused = it.hasFocus }
            .background(fill, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
    ) {
        Text(
            label,
            color = if (focused) DetailCanvas else DetailWhite,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
