package org.viptv.app.hero

import org.viptv.app.BuildConfig
import org.viptv.app.homeTimingLog
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.content.Context
import coil.size.Precision
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.viptv.app.CoreModels
import org.viptv.app.LocalGround
import org.viptv.app.Media

/**
 * Static TV artwork with one cached ambient image and readable scrims. The focused
 * episode still waits for focus to settle and must be sharp enough to fill the art.
 * Replacement artwork appears only after decoding; rapid input cancels stale work.
 */
@Composable internal fun TvHeroBackdrop(
    media: Media,
    focusImage: String? = null,
    preloadItems: List<Media> = emptyList(),
    preloadEpisodes: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ground = LocalGround.current
    val artW = with(density) { ART_WIDTH.dp.roundToPx() }
    val artH = with(density) { ART_HEIGHT.dp.roundToPx() }
    val scope = rememberCoroutineScope()
    val artwork = remember(context, artW, artH) {
        HeroArtPreloader(scope, { url: String -> loadArt(context, url, artW, artH) }, { it.sharp.allocationByteCount + it.ambient.allocationByteCount })
    }
    DisposableEffect(artwork) { onDispose { artwork.close() } }

    val base = remember(media) { CoreModels.presentation(media).heroImage }
    var shown by remember(artwork) { mutableStateOf<String?>(null) }
    var displayed by remember(artwork) { mutableStateOf<HeroArtwork?>(null) }
    LaunchedEffect(base, focusImage, artwork) {
        if (base.isNullOrBlank() && focusImage.isNullOrBlank()) {
            shown = null
            displayed = null
            return@LaunchedEffect
        }
        // Decode the settled subject without delaying remote focus itself.
        if (focusImage != null) delay(FOCUS_SETTLE_MS)
        val focused = focusImage?.takeIf { it.isNotBlank() && it != shown }?.let { artwork.load(it) }
            ?.takeIf { it.sharp.width >= artW * MIN_FILL }
        val (url, bitmap) = when {
            focused != null -> focusImage to focused
            focusImage == shown && !focusImage.isNullOrBlank() -> return@LaunchedEffect
            base.isNullOrBlank() || base == shown -> return@LaunchedEffect
            else -> base to (artwork.load(base) ?: return@LaunchedEffect)
        }
        shown = url
        displayed = bitmap
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

    val copyFade = remember(ground) {
        Brush.horizontalGradient(0f to ground, 0.22f to ground.copy(alpha = .9f),
            0.4f to ground.copy(alpha = .35f), 0.65f to Color.Transparent)
    }
    val lowerFadeStart = with(density) { LOWER_FADE_START.dp.toPx() }
    val lowerFade = remember(ground, lowerFadeStart) {
        Brush.verticalGradient(listOf(Color.Transparent, ground), startY = lowerFadeStart)
    }
    Box(Modifier.fillMaxWidth().height(BACKDROP_HEIGHT.dp)) {
        displayed?.let { artwork ->
            val ambient = remember(artwork) { artwork.ambient.asImageBitmap() }
            val sharp = remember(artwork) { artwork.sharp.asImageBitmap() }
            Image(ambient, null, Modifier.fillMaxSize().alpha(.6f), contentScale = ContentScale.Crop)
            Image(sharp, null, Modifier.align(Alignment.TopEnd).width(ART_WIDTH.dp).height(ART_HEIGHT.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val left = Brush.horizontalGradient(0f to Color.Transparent, .3f to Color.Black, 1f to Color.Black)
                    val bottom = Brush.verticalGradient(0f to Color.Black, .58f to Color.Black, 1f to Color.Transparent)
                    onDrawWithContent {
                        drawContent()
                        drawRect(left, blendMode = BlendMode.DstIn)
                        drawRect(bottom, blendMode = BlendMode.DstIn)
                    }
                }, contentScale = ContentScale.Crop)
        }
        // Static fades keep the copy readable and join the art to the shelves.
        Box(Modifier.matchParentSize().background(copyFade))
        Box(Modifier.matchParentSize().background(lowerFade))
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

private class HeroArtwork(val sharp: Bitmap, val ambient: Bitmap)

/** Decodes without upscaling, so the bitmap width reflects the artwork's real resolution. */
private suspend fun loadArt(context: Context, url: String, width: Int, height: Int): HeroArtwork? {
    val timingStart = System.nanoTime() / 1_000_000
    val request = ImageRequest.Builder(context).data(url).size(width, height).precision(Precision.INEXACT).allowHardware(false).build()
    val sharp = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        ?.takeIf { Build.VERSION.SDK_INT < 26 || it.config != Bitmap.Config.HARDWARE } ?: return null
    if (BuildConfig.PLAYBACK_DIAGNOSTICS) homeTimingLog("event=hero_image_decode elapsed_ms=${(System.nanoTime() / 1_000_000) - timingStart}")
    return withContext(Dispatchers.Default) {
        // Keep blur/decode allocation off the input thread. The foreground stays sharp.
        val ambientWidth = minOf(160, sharp.width)
        val ambientHeight = (sharp.height.toLong() * ambientWidth / sharp.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(sharp, ambientWidth, ambientHeight, true)
        val pixels = IntArray(ambientWidth * ambientHeight)
        small.getPixels(pixels, 0, ambientWidth, 0, 0, ambientWidth, ambientHeight)
        val blurred = blurAmbientPixels(pixels, ambientWidth, ambientHeight, 8)
        val ambient = Bitmap.createBitmap(blurred, ambientWidth, ambientHeight, Bitmap.Config.ARGB_8888)
        if (small !== sharp) small.recycle()
        HeroArtwork(sharp, ambient)
    }.also {
        if (BuildConfig.PLAYBACK_DIAGNOSTICS) homeTimingLog("event=hero_art_ready elapsed_ms=${(System.nanoTime() / 1_000_000) - timingStart} at_ms=${(System.nanoTime() / 1_000_000)}")
    }
}
