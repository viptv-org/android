package org.viptv.app

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.EventListener
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object TrimLogoPadding : Transformation {
    override val cacheKey = "viptv-logo-alpha-bounds-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        var left = input.width
        var top = input.height
        var right = -1
        var bottom = -1
        for (y in 0 until input.height) for (x in 0 until input.width) {
            if ((pixels[y * input.width + x] ushr 24) > 8) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        if (right < left || (left == 0 && top == 0 && right == input.width - 1 && bottom == input.height - 1)) return input
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}

/** Public artwork transport only; Core retains image choice and fallback ownership. */
internal object ArtworkImages {
    private val publicHosts = setOf("images.metahub.space", "live.metahub.space", "image.tmdb.org",
        "artworks.thetvdb.com", "assets.fanart.tv", "images.fanart.tv", "static.tvmaze.com",
        "i.imgur.com", "upload.wikimedia.org", "simkl.in", "cdn.simkl.in", "simkl.com")
    private val sizes = intArrayOf(96, 160, 240, 320, 480, 640, 960, 1280)
    fun dimension(value: Int): Int = sizes.firstOrNull { it >= value.coerceAtLeast(1) } ?: sizes.last()

    fun transport(url: String, width: Int, height: Int, crop: Boolean, blur: Int = 0): String {
        val source = url.toHttpUrlOrNull() ?: return url
        // Private/local artwork and credential-bearing URLs never leave the app.
        if (source.host !in publicHosts || source.username.isNotEmpty() || source.password.isNotEmpty() ||
            source.query != null || source.fragment != null || source.port !in setOf(80, 443)) return url
        return "https://wsrv.nl/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("url", source.toString())
            .addQueryParameter("w", dimension(width).toString())
            .addQueryParameter("h", dimension(height).coerceAtMost(960).toString())
            .addQueryParameter("fit", if (crop) "cover" else "inside")
            .addQueryParameter("output", "webp").addQueryParameter("q", "65")
            .addQueryParameter("we", "true").addQueryParameter("maxage", "7d")
            .apply { if (blur > 0) addQueryParameter("blur", blur.coerceAtMost(24).toString()) }
            .build().toString()
    }

    fun request(context: Context, url: String, width: Int, height: Int, crop: Boolean, blur: Int = 0): ImageRequest =
        ImageRequest.Builder(context).data(transport(url, width, height, crop, blur))
            .size(dimension(width), dimension(height).coerceAtMost(960)).crossfade(120).build()
}

/** One bounded image pool/cache for every native screen, independent of playback/control IO. */
class ViptvApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(120)
        .bitmapFactoryMaxParallelism(2)
        .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
        .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.125).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork-v1")).maxSizeBytes(64L * 1024 * 1024).build() }
        .eventListenerFactory {
            object : EventListener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    if (BuildConfig.PLAYBACK_DIAGNOSTICS) android.util.Log.i("ArtworkDiagnostic", "cache=${result.dataSource.name.lowercase()}")
                }
            }
        }
        .okHttpClient {
            OkHttpClient.Builder().dns(org.viptv.video.AndroidMediaDns(this)).dispatcher(okhttp3.Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 })
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .eventListenerFactory { call ->
                    val proxy = call.request().url.host == "wsrv.nl"
                    object : okhttp3.EventListener() {
                        override fun callStart(call: okhttp3.Call) {
                            if (BuildConfig.PLAYBACK_DIAGNOSTICS) android.util.Log.i("ArtworkDiagnostic", "network_start=true proxy=$proxy")
                        }
                        override fun responseBodyEnd(call: okhttp3.Call, byteCount: Long) {
                            if (BuildConfig.PLAYBACK_DIAGNOSTICS) android.util.Log.i("ArtworkDiagnostic", "network_bytes=$byteCount proxy=$proxy")
                        }
                    }
                }.build()
        }.build()
}

@Composable internal fun SizedArtwork(url: String, description: String?, modifier: Modifier,
    fit: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center,
    onSuccess: () -> Unit = {}, onError: () -> Unit = {}, trimTransparency: Boolean = false) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 320
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 240
        val request = remember(url, width, height, fit, context, trimTransparency) {
            ArtworkImages.request(context, url, width, height, fit == ContentScale.Crop).let { request ->
                if (trimTransparency) request.newBuilder().allowHardware(false).transformations(TrimLogoPadding).build() else request
            }
        }
        AsyncImage(request, description, Modifier.fillMaxSize(), contentScale = fit, alignment = alignment,
            onSuccess = { onSuccess() }, onError = { onError() })
    }
}
