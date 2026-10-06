package org.viptv.app

import kotlinx.coroutines.Job
import org.viptv.core.wire.HomeRevisionDecision

internal enum class HomeRevisionCheck { Unchanged, Refreshed, RetryLater, Unsupported, ScopeLost }

/** One non-overlapping check; the caller owns the foreground/Home timer and cancels its job on exit. */
internal class HomeRevisionPolling(
    private val revision: suspend () -> String?,
    private val renderedRevision: () -> String?,
    private val activeLoad: () -> Job?,
    private val valid: () -> Boolean,
    private val refresh: suspend () -> Boolean,
) {
    suspend fun check(): HomeRevisionCheck {
        if (!valid()) return CoreLifecycle.homeRevision(false, null, renderedRevision()).checkResult()
        val current = revision()
        if (current == null) return CoreLifecycle.homeRevision(true, null, renderedRevision()).checkResult()
        activeLoad()?.join()
        val decision = CoreLifecycle.homeRevision(valid(), current, renderedRevision())
        if (decision != HomeRevisionDecision.REFRESH) return decision.checkResult()
        val refreshed = refresh()
        return CoreLifecycle.homeRevision(valid(), current, renderedRevision(), refreshed).checkResult()
    }
}

private fun HomeRevisionDecision.checkResult(): HomeRevisionCheck = when (this) {
    HomeRevisionDecision.UNCHANGED -> HomeRevisionCheck.Unchanged
    HomeRevisionDecision.REFRESHED -> HomeRevisionCheck.Refreshed
    HomeRevisionDecision.RETRYLATER -> HomeRevisionCheck.RetryLater
    HomeRevisionDecision.UNSUPPORTED -> HomeRevisionCheck.Unsupported
    HomeRevisionDecision.SCOPELOST -> HomeRevisionCheck.ScopeLost
    HomeRevisionDecision.REFRESH -> error("Refresh effect required")
}
