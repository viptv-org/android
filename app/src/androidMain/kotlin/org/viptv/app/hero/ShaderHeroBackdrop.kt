package org.viptv.app.hero

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import coil.size.Precision
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.viptv.app.CoreModels
import org.viptv.app.HeroBackdrop
import org.viptv.app.LocalGround
import org.viptv.app.Media
import org.viptv.app.systemAnimationsEnabled
import org.viptv.app.SharedPresentation

/**
 * TV-042 backdrop with shader transitions between images and a category-matched
 * edge fade. Shows [media]'s hero image, or [focusImage] (the focused episode's
 * still) when that decodes sharp enough to fill the art; the edge pool always
 * follows [media]. [focusImage] is null when no episode is the subject and blank
 * when the subject episode has no still; either way an episode subject waits for
 * focus to settle. Uses the static compositor ([HeroBackdrop]) when the system
 * has animations disabled or GLES is unavailable.
 */
@Composable internal fun ShaderHeroBackdrop(
    media: Media,
    focusImage: String? = null,
    preloadItems: List<Media> = emptyList(),
    preloadEpisodes: Boolean = false,
) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    val motion = remember(context) { systemAnimationsEnabled(context) }
    if (!motion || unavailable) {
        HeroBackdrop(media)
        return
    }
    val density = LocalDensity.current
    val ground = LocalGround.current
    val library = remember(context) { HeroShaderLibrary.fromAssets(context.assets) }
    val policy = remember(library) { HeroMotionPolicy(library.index) }
    val renderer = remember(library) { HeroGlRenderer(library, ground.toArgb()) { unavailable = true } }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    SideEffect { renderer.ground = ground.toArgb() }
    // One core decision per title, never per frame or per episode focus.
    val edgePool = remember(policy, media.type, media.genres) { SharedPresentation.heroEdgePool(media.type, media.genres, policy.edgeIds) }
    val artW = with(density) { ART_WIDTH.dp.roundToPx() }
    val artH = with(density) { ART_HEIGHT.dp.roundToPx() }
    val scope = rememberCoroutineScope()
    val artwork = remember(context, artW, artH) {
        HeroArtPreloader(scope, { url: String -> loadArt(context, url, artW, artH) }, { it.allocationByteCount })
    }
    DisposableEffect(artwork) { onDispose { artwork.close() } }
    LaunchedEffect(artW, artH) { renderer.setArt(artW, artH, artW / ART_WIDTH.toFloat()) }

    val base = remember(media) { CoreModels.presentation(media).heroImage }
    var shown by remember(artwork) { mutableStateOf<String?>(null) }
    LaunchedEffect(base, focusImage, artwork) {
        // Moving quickly through episode cards should not queue a transition per card.
        if (focusImage != null) delay(FOCUS_SETTLE_MS)
        val focused = focusImage?.takeIf { it.isNotBlank() && it != shown }?.let { artwork.load(it) }
            ?.takeIf { it.width >= artW * MIN_FILL }
        val (url, bitmap) = when {
            focused != null -> focusImage to focused
            focusImage == shown && !focusImage.isNullOrBlank() -> return@LaunchedEffect
            base.isNullOrBlank() || base == shown -> return@LaunchedEffect
            else -> base to (artwork.load(base) ?: return@LaunchedEffect)
        }
        shown = url
        renderer.show(bitmap, policy.nextTransition(renderer.transition), policy.nextEdge(edgePool, renderer.edge))
    }

    LaunchedEffect(base, focusImage, preloadItems, preloadEpisodes, artwork) {
        val current = focusImage?.takeIf { it.isNotBlank() } ?: base
        artwork.setWindow(current, emptyList())
        // Keep native projections off the remote-input thread; resolve only the
        // two adjacent items already present in this screen's row.
        val urls = withContext(Dispatchers.Default) {
            preloadItems.take(2).mapNotNull { item ->
                val presentation = CoreModels.presentation(item)
                if (preloadEpisodes) presentation.episodeImage else presentation.heroImage
            }
        }
        artwork.setWindow(current, urls)
    }

    Box(Modifier.fillMaxWidth().height(BACKDROP_HEIGHT.dp)) {
        AndroidView({ TextureView(it).apply { isOpaque = true; surfaceTextureListener = renderer } }, Modifier.matchParentSize())
        // Readability scrims: the text column on the left, and the lower fade into the shelves.
        // The art's own left edge is the shader's edge fade, so this scrim clears before the
        // art's fade zone ends and leaves the subject undimmed.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to ground, 0.22f to ground.copy(alpha = .9f), 0.4f to ground.copy(alpha = .35f), 0.52f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, ground), startY = with(density) { LOWER_FADE_START.dp.toPx() })))
    }
}

/** 16:9, matching the backdrops, so cover-fit crops nothing. */
private const val ART_WIDTH = 1280
private const val FOCUS_SETTLE_MS = 120L
/** Art narrower than this share of the art width would visibly blur when stretched. */
private const val MIN_FILL = 0.6f
private const val ART_HEIGHT = 720
private const val BACKDROP_HEIGHT = 950
/** The lower fade runs from transparent here (logical px) to ground at the backdrop's bottom edge. */
internal const val LOWER_FADE_START = 440

/** Decodes without upscaling, so [Bitmap.getWidth] reflects the art's real resolution. */
private suspend fun loadArt(context: Context, url: String, width: Int, height: Int): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).size(width, height).precision(Precision.INEXACT).allowHardware(false).build()
    return ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        ?.takeIf { Build.VERSION.SDK_INT < 26 || it.config != Bitmap.Config.HARDWARE }
}
