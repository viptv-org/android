@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AndroidMedia3BackendFactory(
    context: Context,
    private val openTimeoutMillis: Long = 20_000,
    private val resilientBufferConfig: AndroidMedia3ResilientBufferConfig =
        AndroidMedia3ResilientBufferConfig(),
) : VideoBackendFactory {
    private val applicationContext = context.applicationContext
    @Volatile private var probedCapabilities: PlayerCapabilities? = null

    init {
        require(openTimeoutMillis > 0)
    }

    override val id: String = "media3"

    override suspend fun probe(): PlayerCapabilities = withContext(Dispatchers.Default) {
        probeMedia3Capabilities()
    }.also { probedCapabilities = it }

    override fun create(): VideoPlayer = createAndroidPlayer()

    fun createAndroidPlayer(): AndroidMedia3VideoPlayer = onMainThreadBlocking {
        AndroidMedia3VideoPlayer(
            AndroidMedia3Backend(
                applicationContext,
                openTimeoutMillis,
                probedCapabilities ?: probeMedia3Capabilities(),
                resilientBufferConfig,
            ),
        )
    }
}

class AndroidMedia3VideoPlayer internal constructor(
    private val backend: AndroidMedia3Backend,
) : VideoPlayer by DefaultVideoPlayer(backend, Dispatchers.Main.immediate) {
    fun attach(surfaceView: SurfaceView) = backend.attach(surfaceView)
    fun attach(textureView: TextureView) = backend.attach(textureView)
    fun detachSurface() = backend.detachSurface()
}

private fun <T> onMainThreadBlocking(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val result = AtomicReference<Result<T>>()
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        result.set(runCatching(block))
        latch.countDown()
    }
    check(latch.await(10, TimeUnit.SECONDS)) { "Media3 player creation timed out on the main thread" }
    return checkNotNull(result.get()).getOrThrow()
}
