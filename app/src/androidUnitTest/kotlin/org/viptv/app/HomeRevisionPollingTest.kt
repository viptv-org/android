package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRevisionPollingTest {
    @Test fun unchangedChecksDoNotReloadAndChangedRevisionReloadsOnce() = runBlocking {
        var remote = "r1"
        var rendered: String? = "r1"
        var reloads = 0
        val polling = HomeRevisionPolling({ remote }, { rendered }, { null }, { true }) {
            reloads++
            rendered = remote
            true
        }
        repeat(3) { assertEquals(HomeRevisionCheck.Unchanged, polling.check()) }
        assertEquals(0, reloads)
        remote = "r2"
        assertEquals(HomeRevisionCheck.Refreshed, polling.check())
        assertEquals(HomeRevisionCheck.Unchanged, polling.check())
        assertEquals(1, reloads)
    }

    @Test fun backgroundRouteExitAndOldScopeRejectLateRevisionAndReentryChecksAgain() = runBlocking {
        var visible = true
        var rendered: String? = "r1"
        var reloads = 0
        val response = CompletableDeferred<String?>()
        val polling = HomeRevisionPolling({ response.await() }, { rendered }, { null }, { visible }) {
            reloads++
            rendered = "r2"
            true
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { polling.check() }
        visible = false // background, route exit, or replaced account/profile
        response.complete("r2")
        assertEquals(HomeRevisionCheck.ScopeLost, pending.await())
        assertEquals(0, reloads)
        visible = true
        assertEquals(HomeRevisionCheck.Refreshed, polling.check())
        assertEquals(1, reloads)
    }

    @Test fun loadStartRevisionDetectsChangeEvenWhenCheckBeginsAfterCatalogFetch() = runBlocking {
        var remote = "r1"
        var rendered: String? = null
        val loadGate = CompletableDeferred<Unit>()
        val load = launch(start = CoroutineStart.UNDISPATCHED) {
            val atStart = remote // Mirrors loadHome reading before gateway.home.
            loadGate.await()
            rendered = atStart
        }
        remote = "r2" // Committed while the old catalog request is in flight.
        var reloads = 0
        val polling = HomeRevisionPolling({ remote }, { rendered }, { load }, { true }) {
            reloads++
            rendered = remote
            true
        }
        val pending = async { polling.check() }
        loadGate.complete(Unit)
        assertEquals(HomeRevisionCheck.Refreshed, pending.await())
        assertEquals("r2", rendered)
        assertEquals(1, reloads)
    }

    @Test fun failedRefreshRetriesAndChangeDuringRefreshGetsFollowup() = runBlocking {
        var remote = "r2"
        var rendered: String? = "r1"
        var attempts = 0
        val polling = HomeRevisionPolling({ remote }, { rendered }, { null }, { true }) {
            attempts++
            if (attempts == 1) false else {
                rendered = remote
                if (attempts == 2) remote = "r3"
                true
            }
        }
        assertEquals(HomeRevisionCheck.RetryLater, polling.check())
        assertEquals("r1", rendered)
        assertEquals(HomeRevisionCheck.Refreshed, polling.check())
        assertEquals(HomeRevisionCheck.Refreshed, polling.check())
        assertEquals("r3", rendered)
        assertEquals(3, attempts)
    }
}
