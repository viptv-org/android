package org.viptv.app

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.viptv.app.hero.TvHeroBackdrop
import java.util.concurrent.ConcurrentHashMap

/** Exercises decode, ambient generation and the displayed Compose image, without app storage. */
class HeroArtworkSwapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingAndFailedReplacementsKeepTheSharpImageAndLatestSelectionWins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Coil.imageLoader(context)
        val replies = ConcurrentHashMap<String, CompletableDeferred<Int>>()
        fun reply(id: String) = replies.getOrPut("https://example.invalid/$id") { CompletableDeferred() }
        reply("first").complete(android.graphics.Color.RED)
        var media by mutableStateOf(Media("first", "movie", name = "First", backdrop = "https://example.invalid/first"))
        val loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<android.net.Uri> {
                override fun create(data: android.net.Uri, options: Options, imageLoader: ImageLoader) = object : Fetcher {
                    override suspend fun fetch(): FetchResult {
                        val color = replies.getOrPut(data.toString()) { CompletableDeferred() }.await()
                        check(color != 0) { "Fixture artwork failure" }
                        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                        return DrawableResult(BitmapDrawable(context.resources, bitmap), false, DataSource.MEMORY)
                    }
                }
            })
        }.build()
        fun selected(id: String) = compose.runOnIdle {
            media = media.copy(id = id, backdrop = "https://example.invalid/$id")
        }
        fun color(): Color {
            val image = compose.onRoot().captureToImage()
            return image.toPixelMap()[(image.width * .9f).toInt(), (image.height * .15f).toInt()]
        }
        fun isRed() = color().let { it.red > .9f && it.blue < .1f }
        fun isBlue() = color().let { it.blue > .9f && it.red < .1f }
        try {
            Coil.setImageLoader(loader)
            compose.setContent {
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) { Box(Modifier.fillMaxSize()) { TvHeroBackdrop(media) } }
                }
            }
            compose.waitUntil(10_000) { isRed() }
            selected("pending")
            compose.waitForIdle()
            assertTrue("Pending decode must retain the previous image", isRed())
            selected("failed")
            reply("failed").complete(0)
            compose.waitForIdle()
            assertTrue("Failed decode must retain the previous image", isRed())
            selected("latest")
            reply("latest").complete(android.graphics.Color.BLUE)
            compose.waitUntil(10_000) { isBlue() }
            reply("pending").complete(android.graphics.Color.GREEN)
            compose.waitForIdle()
            assertTrue("A stale decode must not replace the latest selected art", isBlue())
        } finally {
            Coil.setImageLoader(previous)
            loader.shutdown()
        }
    }
}
