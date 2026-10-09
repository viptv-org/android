package org.viptv.app

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

/** Execute only on an owned QA emulator; no credentials, source URLs or media corpus are needed. */
class TorrentRuntimeWorkerIntegrationTest {
    @Test fun privateChildLoadsSharedRuntimeAndTransfersMetainfoSizedControl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "runtime-qa-${UUID.randomUUID()}").apply { mkdir() }
        val worker = TorrentRuntimeWorker(context)
        try {
            assertTrue(worker.ownedPid > 0 && worker.ownedPid != Process.myPid())
            assertFalse(File("/proc/self/maps").readText().contains("libtorrent_runtime"))
            val opened = worker.call(JSONObject().put("op", "open").put("config", JSONObject()
                .put("cache_dir", directory.path).put("cache_bytes", 64 * 1024 * 1024).put("readahead_bytes", 4 * 1024 * 1024)), 8_000)
            assertTrue(opened.getBoolean("ok"))
            // This request exceeds Binder's transaction buffer, but only FDs cross Binder.
            val large = worker.call(JSONObject().put("op", "observe").put("padding", "a".repeat(2 * 1024 * 1024)))
            assertFalse(large.getBoolean("ok"))
            assertEquals("invalid_control", large.getString("error"))
            val invalid = worker.call(JSONObject().put("op", "prepare").put("authority_ms", 20_000)
                .put("source", JSONObject().put("magnet", "invalid")))
            assertFalse(invalid.getBoolean("ok"))
            assertTrue(worker.call(JSONObject().put("op", "shutdown"), 2_000).getBoolean("ok"))
        } finally {
            assertTrue(worker.terminate())
            directory.deleteRecursively()
        }
    }
}
