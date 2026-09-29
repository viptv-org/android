package org.viptv.app

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.URI

/** Resolve one explicit source without opening the player or retaining delivery credentials. */
internal suspend fun resolveStreamUrlForCopy(
    gateway: BackendGateway,
    source: Source,
    capabilities: PlaybackClientCapabilities,
): String {
    // Once submitted, finish this bounded HTTP exchange so even a dismissed sheet
    // can retire a late server lease. Cancellation still forbids clipboard delivery.
    val url = withContext(NonCancellable) {
        val launch = gateway.playback(source, 0, capabilities)
        try {
            val uri = try { URI(launch.url) } catch (_: Exception) { null }
            check(launch.timelineMode == "direct" && uri?.scheme in listOf("https", "http") &&
                !uri?.host.isNullOrBlank() && !uri!!.path.orEmpty().startsWith("/media/${launch.sessionId}/")) {
                "A direct stream URL is unavailable"
            }
            launch.url
        } finally {
            if (launch.sessionId.isNotBlank()) gateway.stopPlayback(launch.sessionId)
        }
    }
    currentCoroutineContext().ensureActive()
    return url
}
