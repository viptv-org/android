package org.viptv.app

import android.content.Context
import android.os.SystemClock
import java.io.File
import org.json.JSONObject

internal fun openNativeTorrentCache(context: Context): NativeTorrentCache = NativeTorrentCache.open(
    context.applicationContext.noBackupFilesDir,
    { directory, maxBytes -> NativeTorrentEngineCacheManager(directory, maxBytes) { TorrentRuntimeWorker(context.applicationContext) }.also { it.initialize() } },
    nowNanos = { SystemClock.elapsedRealtimeNanos() },
    retainContent = true,
)

/** A warm worker owns one bounded reusable namespace; a dead child never owns a new handle. */
internal class NativeTorrentEngineCacheManager(
    private val directory: File,
    private val maxBytes: Long,
    private val createWorker: () -> TorrentRuntimePort,
) : NativeTorrentCacheManager {
    @Volatile private var worker: TorrentRuntimePort? = null
    @Volatile private var epoch = 0L
    private var failedSettlement = false
    @Synchronized fun initialize() {
        if (worker?.isAlive() != true) {
            if (worker?.terminate() == false) { failedSettlement = true; throw NativeTorrentCacheUnavailable() }
            worker = createWorker()
            try { worker!!.call(JSONObject().put("op", "open").put("config", JSONObject()
                .put("cache_dir", directory.path).put("cache_bytes", maxBytes).put("readahead_bytes", 32 * 1024 * 1024)), 8_000).also(::requireOK) }
                catch (_: Exception) {
                    if (!worker!!.terminate()) failedSettlement = true
                    throw NativeTorrentCacheUnavailable()
                }
            epoch++
        }
    }
    @Synchronized fun prepare(source: JSONObject, authorityMillis: Long): Pair<Long, String> {
        initialize()
        val reply = worker!!.call(JSONObject().put("op", "prepare").put("source", source).put("authority_ms", authorityMillis))
        requireOK(reply)
        val handle = reply.getString("handle")
        if (!Regex("[1-9][0-9]{0,18}").matches(handle)) throw NativeTorrentCoordinatorUnavailable()
        return epoch to handle
    }
    @Synchronized fun command(generation: Long, handle: String, operation: String, authority: Long? = null): JSONObject {
        if (generation != epoch || worker?.isAlive() != true) throw NativeTorrentCoordinatorUnavailable()
        val reply = worker!!.call(JSONObject().put("op", operation).put("handle", handle).putOpt("authority_ms", authority))
        requireOK(reply)
        return reply
    }
    @Synchronized fun stop(generation: Long, handle: String): Boolean {
        if (generation != epoch) return true
        if (worker?.isAlive() != true) return worker?.terminate() != false
        return try {
            val reply = worker!!.call(JSONObject().put("op", "close").put("handle", handle), 1_750)
            reply.optBoolean("ok") || (reply.optBoolean("terminate") && worker!!.terminate())
        } catch (_: Exception) { worker!!.terminate() }
    }
    @Synchronized override fun hasFailedSettlement() = failedSettlement
    @Synchronized override fun closeAfterSettlement() {
        val child = worker ?: return
        runCatching { child.call(JSONObject().put("op", "shutdown"), 1_750) }
        if (!child.terminate()) { failedSettlement = true; throw NativeTorrentCacheUnavailable() }
        worker = null
    }
    private fun requireOK(reply: JSONObject) {
        if (reply.optInt("version") != 2 || !reply.optBoolean("ok")) throw NativeTorrentCoordinatorUnavailable()
    }
    override fun toString() = "NativeTorrentEngineCacheManager(<redacted>)"
}
