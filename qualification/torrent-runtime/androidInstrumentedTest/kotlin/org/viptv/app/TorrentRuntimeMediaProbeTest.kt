package org.viptv.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.graphics.SurfaceTexture
import android.view.TextureView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test
import org.viptv.video.AndroidMedia3BackendFactory
import org.viptv.video.PlaybackEvent
import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackOptions
import org.viptv.video.PlaybackSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in real source test. Input and numeric results stay in the private owned QA app. */
class TorrentRuntimeMediaProbeTest {
    @Test fun verifiedRuntimeBytesDecodeSeekAndRetireOnMedia3() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.noBackupFilesDir, "torrent-runtime-qa-source.json")
        val source = JSONObject(sourceFile.readText())
        val resultFile = File(context.noBackupFilesDir, "torrent-runtime-qa-result.json")
        val result = JSONObject().put("passed", false)
        val started = SystemClock.elapsedRealtime()
        val worker = TorrentRuntimeWorker(context)
        val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()
        var handle: String? = null
        val running = AtomicBoolean(true)
        val presentedFrames = AtomicLong()
        val firstFrame = CompletableDeferred<Unit>()
        var pendingSeek: CompletableDeferred<Unit>? = null
        val frameObserver = launch {
            player.events.collect { event ->
                if (event is PlaybackEvent.SeekCompleted) pendingSeek?.complete(Unit)
                if (event is PlaybackEvent.FirstFrame) firstFrame.complete(Unit)
                if (event is PlaybackEvent.Failed) firstFrame.completeExceptionally(IllegalStateException("decoder_failed"))
            }
        }
        var renewal: kotlinx.coroutines.Job? = null
        try {
            val directory = File(context.noBackupFilesDir, "torrent-runtime-qa-cache").apply { mkdir() }
            assertTrue(worker.call(JSONObject().put("op", "open").put("config", JSONObject()
                .put("cache_dir", directory.path).put("cache_bytes", 128 * 1024 * 1024).put("readahead_bytes", 8 * 1024 * 1024)), 8_000).getBoolean("ok"))
            handle = worker.call(JSONObject().put("op", "prepare").put("source", source).put("authority_ms", 60_000)).getString("handle")
            val owned = handle
            renewal = launch(Dispatchers.IO) {
                while (running.get()) {
                    delay(10_000)
                    if (running.get()) assertTrue(worker.call(JSONObject().put("op", "renew").put("handle", owned).put("authority_ms", 60_000)).getBoolean("ok"))
                }
            }
            var media: String? = null
            withTimeout(120_000) {
                while (media == null) {
                    val reply = withContext(Dispatchers.IO) { worker.call(JSONObject().put("op", "observe").put("handle", owned)) }
                    val progress = reply.getJSONObject("progress")
                    if (progress.optString("error").isNotEmpty()) throw IllegalStateException("runtime_" + progress.getString("stage"))
                    if (progress.getBoolean("ready")) {
                        media = reply.getString("media_url")
                        result.put("selected_index", progress.getInt("file_index")).put("selected_length", progress.getLong("length"))
                    } else delay(100)
                }
            }
            result.put("endpoint_ms", SystemClock.elapsedRealtime() - started)
            ActivityScenario.launch(TorrentRuntimeQaActivity::class.java).use { scenario ->
                lateinit var activity: TorrentRuntimeQaActivity
                scenario.onActivity { activity = it }
                withTimeout(10_000) { while (!activity.texture.isAvailable) delay(20) }
                withContext(Dispatchers.Main.immediate) {
                    player.attach(activity.texture)
                    val delegate = activity.texture.surfaceTextureListener
                    activity.texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { delegate?.onSurfaceTextureAvailable(surface, width, height) }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { delegate?.onSurfaceTextureSizeChanged(surface, width, height) }
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = delegate?.onSurfaceTextureDestroyed(surface) ?: true
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { delegate?.onSurfaceTextureUpdated(surface); presentedFrames.incrementAndGet() }
                    }
                }
                player.open(PlaybackSource(requireNotNull(media), kindHint = PlaybackKind.OnDemand,
                    options = PlaybackOptions(openTimeoutMillis = (120_000 - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1), httpReadTimeoutMillis = 60_000)), true)
                withTimeout((120_000 - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1)) { firstFrame.await() }
                result.put("first_frame_ms", SystemClock.elapsedRealtime() - started)
                assertTrue(worker.call(JSONObject().put("op", "first_frame").put("handle", owned)).getBoolean("ok"))
                delay(2_000)
                val seekStarted = SystemClock.elapsedRealtime()
                pendingSeek = CompletableDeferred()
                player.seekTo(120_000)
                withTimeout(60_000) {
                    pendingSeek!!.await()
                    while (player.state.value.positionMillis < 119_000 || player.state.value.isBuffering) delay(100)
                }
                // A position discontinuity can precede decoding. Require fresh texture
                // updates after landing and buffering have actually settled.
                val landedFrames = presentedFrames.get()
                withTimeout(60_000) { while (presentedFrames.get() < landedFrames + 3) delay(20) }
                result.put("forward_seek_ms", SystemClock.elapsedRealtime() - seekStarted)
                    .put("presented_frames", presentedFrames.get())
                pendingSeek = CompletableDeferred()
                player.seekTo(0)
                withTimeout(60_000) {
                    pendingSeek!!.await()
                    while (player.state.value.positionMillis > 5_000 || player.state.value.isBuffering) delay(100)
                }
                val backwardFrames = presentedFrames.get()
                withTimeout(60_000) { while (presentedFrames.get() < backwardFrames + 3) delay(20) }
                result.put("passed", true)
                withContext(Dispatchers.Main.immediate) { player.stop(); player.close() }
            }
        } finally {
            running.set(false); renewal?.cancel(); frameObserver.cancel()
            withContext(Dispatchers.Main.immediate) { player.close() }
            handle?.let { runCatching { worker.call(JSONObject().put("op", "close").put("handle", it), 1_750) } }
            result.put("worker_settled", worker.terminate())
            resultFile.writeText(result.toString())
            sourceFile.delete()
        }
        assertTrue(result.getBoolean("passed"))
        assertEquals(true, result.getBoolean("worker_settled"))
    }
}
