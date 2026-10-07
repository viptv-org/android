package org.viptv.app

import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.viptv_core.normalize

/** Transient scope receipt; identities never determine the random cache directory or bridge scope. */
internal class NativeTorrentScopeEpoch internal constructor(
    internal val scope: String,
    internal val coordinator: NativeTorrentCoordinator,
) {
    override fun toString() = "NativeTorrentScopeEpoch(<redacted>)"
}

/** Rust compares authorization facts; Kotlin closes/creates the exclusively owned cache as effects. */
internal class NativeTorrentScopeOwner(
    private val openCache: () -> NativeTorrentCache,
    private val createCoordinator: (NativeTorrentCache) -> NativeTorrentCoordinator,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val serial = Mutex()
    private var previous: JSONObject? = null
    private var epoch: NativeTorrentScopeEpoch? = null
    private var unavailable = false

    /** Facts: serverOrigin/accountId/profileId/deviceAuthorizationEpoch, never access tokens or generation. */
    suspend fun adopt(current: JSONObject?, revoked: Boolean = false): NativeTorrentScopeEpoch? = serial.withLock {
        if (unavailable) throw NativeTorrentCoordinatorUnavailable()
        val supplied = current?.let { JSONObject(it.toString()) }
        val decision = try { decide(previous, supplied, revoked) } catch (_: Exception) { "reject" }
        if (decision == "keep") {
            val receipt = epoch ?: throw NativeTorrentCoordinatorUnavailable()
            if (!receipt.coordinator.cache.isAvailable) { unavailable = true; throw NativeTorrentCoordinatorUnavailable() }
            return@withLock receipt
        }
        val prior = epoch
        if (prior != null) {
            val settled = withContext(NonCancellable) { prior.coordinator.closeScope() }
            if (!settled) { unavailable = true; throw NativeTorrentCoordinatorUnavailable() }
        }
        previous = null
        epoch = null
        val admission = try { decide(null, supplied, revoked) } catch (_: Exception) { "reject" }
        if (decision == "reject" || admission != "create") return@withLock null
        // Complete ownership bookkeeping even if the caller cancels during the
        // blocking manager constructor; returning from a coroutine cannot orphan its lock.
        withContext(NonCancellable + io) {
            val cache = try { openCache() } catch (_: Exception) { throw NativeTorrentCoordinatorUnavailable() }
            if (!cache.isAvailable) { unavailable = true; throw NativeTorrentCoordinatorUnavailable() }
            val coordinator = try { createCoordinator(cache) } catch (_: Exception) {
                if (!cache.closeScope()) unavailable = true
                throw NativeTorrentCoordinatorUnavailable()
            }
            val receipt = NativeTorrentScopeEpoch(UUID.randomUUID().toString(), coordinator)
            previous = supplied
            epoch = receipt
            receipt
        }
    }

    private fun decide(prior: JSONObject?, next: JSONObject?, revoked: Boolean): String =
        normalize("nativeTorrent", JSONObject().put("operation", "authorizationScope")
            .put("previous", prior ?: JSONObject.NULL).put("current", next ?: JSONObject.NULL)
            .put("revoked", revoked).toString(), "").trim('"')

    override fun toString() = "NativeTorrentScopeOwner(<redacted>)"
}
