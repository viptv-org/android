package org.viptv.app

import kotlinx.coroutines.Job

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
        if (!valid()) return HomeRevisionCheck.ScopeLost
        val current = revision() ?: return HomeRevisionCheck.Unsupported
        activeLoad()?.join()
        if (!valid()) return HomeRevisionCheck.ScopeLost
        if (current == renderedRevision()) return HomeRevisionCheck.Unchanged
        if (!refresh()) return HomeRevisionCheck.RetryLater
        return if (valid()) HomeRevisionCheck.Refreshed else HomeRevisionCheck.ScopeLost
    }
}
