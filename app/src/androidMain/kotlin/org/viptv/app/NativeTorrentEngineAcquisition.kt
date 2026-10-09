package org.viptv.app

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import uniffi.viptv_core.normalize

/** Core owns grant/selection rules; this adapter executes the shared worker protocol. */
internal fun beginNativeTorrentAcquisition(cache: NativeTorrentCache, control: NativePlaybackControl, remainingBudgetMillis: () -> Long): NativeTorrentAcquisitionEffect {
    val manager = cache.managerForAdmission() as? NativeTorrentEngineCacheManager ?: throw NativeTorrentCoordinatorUnavailable()
    val source = control.withAuthorizedGrant { bridge, facts ->
        val input = bridge.privateInputValue(facts)
        JSONObject().apply {
            when (bridge.privateInputKind(facts)) {
                "magnet" -> put("magnet", input)
                "metainfo" -> put("metainfo", input)
                else -> throw NativeTorrentCoordinatorUnavailable()
            }
            putOpt("file_index", bridge.privateFileIndex(facts)?.toInt())
            putOpt("archive_index", bridge.privateArchiveIndex(facts)?.toInt())
            putOpt("expected_file_size", bridge.privateExpectedFileSize(facts)?.toLong())
            put("trackers", JSONArray(bridge.privateTrackers(facts)))
        }
    }
    if (remainingBudgetMillis() <= 0) throw NativeTorrentFailure("native_acquisition_timeout")
    val (epoch, handle) = manager.prepare(source, control.remainingMillis()?.coerceAtMost(60_000) ?: throw NativeTorrentCoordinatorUnavailable())
    val effect = RuntimeHandle(manager, epoch, handle, control, remainingBudgetMillis)
    return object : NativeTorrentAcquisitionEffect {
        override fun waitReady(): NativeTorrentHandleEffect = effect.awaitReady()
        override fun cancel() = effect.stop()
        override fun cancelAndJoin() = effect.stopAndJoin()
        override fun close() = effect.close()
        override fun toString() = "NativeTorrentAcquisitionEffect(<redacted>)"
    }
}

private class RuntimeHandle(
    private val manager: NativeTorrentEngineCacheManager,
    private val epoch: Long,
    private val handle: String,
    private val control: NativePlaybackControl,
    private val startupRemaining: () -> Long,
) : NativeTorrentHandleEffect {
    private val stopped = AtomicBoolean()
    private val stopStarted = AtomicBoolean()
    private val stopFinished = java.util.concurrent.CountDownLatch(1)
    @Volatile private var settled = false
    @Volatile private var firstFrame = false
    @Volatile private var capability: NativeTorrentCapability? = null
    private var lastAuthority = 0L
    private var monitor: java.util.concurrent.Future<*>? = null
    private var lastStage: String? = null
    fun awaitReady(): NativeTorrentHandleEffect {
        while (!stopped.get()) {
            val reply = observe()
            val progress = reply.getJSONObject("progress")
            if (progress.getBoolean("ready")) {
                control.withAuthorizedGrant { bridge, facts ->
                    bridge.bindResolution(progress.getInt("file_index").toUInt(),
                        progress.opt("archive_index").let { if (it == null || it == JSONObject.NULL) null else (it as Number).toInt().toUInt() },
                        progress.getLong("length").toULong(), facts)
                }
                capability = NativeTorrentCapability.runtimeValidated(reply.getString("media_url"))
                monitor = effects.submit {
                    try { while (!stopped.get()) { observe(); Thread.sleep(200) } }
                    catch (error: Exception) {
                        if (!stopped.get()) control.failRuntime((error as? NativeTorrentFailure)?.reason ?: "native_playback_failed")
                    }
                }
                return this
            }
            Thread.sleep(100)
        }
        throw NativeTorrentCoordinatorUnavailable()
    }
    private fun observe(): JSONObject {
        if (!firstFrame && startupRemaining() <= 0) throw NativeTorrentFailure("native_acquisition_timeout")
        val remaining = control.remainingMillis(requireForeground = false)?.coerceAtMost(60_000)?.takeIf { it > 0 } ?: throw NativeTorrentFailure("native_authorization_expired")
        val deadline = android.os.SystemClock.elapsedRealtime() + remaining
        if (deadline > lastAuthority + 1_000) {
            manager.command(epoch, handle, "renew", remaining)
            lastAuthority = deadline
        }
        val reply = manager.command(epoch, handle, "observe")
        val progress = reply.getJSONObject("progress")
        val stage = progress.getString("stage")
        if (stage != lastStage) { control.progress(stage); lastStage = stage }
        val error = progress.optString("error")
        if (error.isNotEmpty()) {
            val projection = try { JSONObject(normalize("torrentRuntime", JSONObject().put("operation", "failure").put("stage", stage).put("reason", error).toString(), "")) }
                catch (_: Exception) { throw NativeTorrentFailure("native_playback_failed") }
            throw NativeTorrentFailure(projection.getString("code"))
        }
        return reply
    }
    override fun validatedCapability(): NativeTorrentCapability = capability ?: throw NativeTorrentCoordinatorUnavailable()
    override fun firstFrame() {
        if (stopped.get() || firstFrame) return
        if (startupRemaining() <= 0) throw NativeTorrentFailure("native_acquisition_timeout")
        manager.command(epoch, handle, "first_frame")
        firstFrame = true
    }
    override fun stop() {
        stopped.set(true)
        if (stopStarted.compareAndSet(false, true)) effects.execute {
            try { settled = manager.stop(epoch, handle) } finally { stopFinished.countDown() }
        }
    }
    override fun stopAndJoin(): Boolean { stop(); return stopFinished.await(2_000, TimeUnit.MILLISECONDS) && settled }
    override fun close() { stop(); monitor?.cancel(true) }
    override fun toString() = "NativeTorrentHandleEffect(<redacted>)"
    companion object { private val effects = Executors.newCachedThreadPool { Thread(it, "torrent-runtime-handle").apply { isDaemon = true } } }
}
