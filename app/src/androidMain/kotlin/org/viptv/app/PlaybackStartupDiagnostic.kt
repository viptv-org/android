package org.viptv.app

import kotlinx.coroutines.CancellationException

/** Development execution timings. No source identity, URL or request payload enters the log. */
internal enum class PlaybackStartupStage { Control, Acquisition, Player, Total }

internal suspend fun <T> measurePlaybackStartup(stage: PlaybackStartupStage, block: suspend () -> T): T {
    if (!BuildConfig.PLAYBACK_DIAGNOSTICS) return block()
    val start = System.nanoTime()
    var outcome = "failed"
    return try {
        block().also { outcome = if (it is Boolean && !it) "failed" else "ready" }
    } catch (cancelled: CancellationException) {
        outcome = "cancelled"
        throw cancelled
    } finally {
        val elapsed = (System.nanoTime() - start) / 1_000_000
        runCatching { android.util.Log.i("PlaybackStartupDiagnostic", "stage=${stage.name.lowercase()} elapsed_ms=$elapsed outcome=$outcome") }
    }
}
