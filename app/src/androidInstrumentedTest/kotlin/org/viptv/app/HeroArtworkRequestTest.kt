package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.Options
import coil.size.Size
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Counts real Coil work; the fetcher deliberately returns no image bytes. */
@RunWith(AndroidJUnit4::class)
class HeroArtworkRequestTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ambientAndSharpHeroShareOneBoundedRequest() = withBackdrop { probe, _ ->
        assertEquals("Ambient blur and sharp art must reuse one request", 1, probe.starts.get())
        assertEquals(listOf(Size(1120, 720)), probe.sizes.toList())
    }

    @Test fun decodeSizeTracksActualScreenDensity() = withBackdrop(density = 2f / 3) { probe, _ ->
        assertEquals(1, probe.starts.get())
        assertEquals(listOf(Size(747, 480)), probe.sizes.toList())
    }

    @Test fun changingBackdropStartsOnlyOneReplacementRequest() = withBackdrop { probe, update ->
        update(fixture.copy(backdrop = "https://example.invalid/replacement"))
        compose.waitUntil(5_000) { probe.starts.get() == 2 && probe.errors.get() == 2 }
        assertEquals(2, probe.sizes.size)
    }

    @Test fun metadataChangesKeepTheExistingBackdropRequest() = withBackdrop { probe, update ->
        update(fixture.copy(name = "Updated title", description = "Updated description"))
        compose.waitForIdle()
        assertEquals(1, probe.starts.get())
    }

    @Test fun missingBackdropStartsNoImageRequests() = withBackdrop(initial = fixture.copy(backdrop = null)) { probe, _ ->
        assertEquals(0, probe.starts.get())
        assertEquals(0, probe.sizes.size)
    }

    private val fixture = Media("hero", "movie", name = "Fixture", backdrop = "https://example.invalid/artwork")
    private class Probe {
        val starts = AtomicInteger()
        val errors = AtomicInteger()
        val sizes = CopyOnWriteArrayList<Size>()
    }

    private fun withBackdrop(density: Float = 1f, initial: Media = fixture, action: (Probe, (Media) -> Unit) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Coil.imageLoader(context)
        val probe = Probe()
        var media by mutableStateOf(initial)
        val loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<String> {
                override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher = object : Fetcher {
                    override suspend fun fetch(): FetchResult = error("Text-only fixture: no image bytes")
                }
            })
        }.eventListenerFactory {
            object : EventListener {
                override fun onStart(request: ImageRequest) { probe.starts.incrementAndGet() }
                override fun resolveSizeEnd(request: ImageRequest, size: Size) { probe.sizes.add(size) }
                override fun onError(request: ImageRequest, result: ErrorResult) { probe.errors.incrementAndGet() }
            }
        }.build()
        try {
            Coil.setImageLoader(loader)
            compose.setContent {
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(density, 1f)) {
                    ViptvTheme(false, Color.White) { Box(Modifier.fillMaxSize()) {
                        HeroBackdrop(media)
                    } }
                }
            }
            if (initial.backdrop != null) compose.waitUntil(5_000) { probe.starts.get() > 0 && probe.errors.get() == probe.starts.get() }
            compose.waitForIdle()
            action(probe) { value -> compose.runOnIdle { media = value } }
            println("HERO_ARTWORK requests=${probe.starts.get()} resolvedSizes=${probe.sizes.joinToString()}")
        } finally {
            Coil.setImageLoader(previous)
            loader.shutdown()
        }
    }
}
