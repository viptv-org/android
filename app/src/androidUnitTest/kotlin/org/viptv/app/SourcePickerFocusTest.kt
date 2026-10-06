package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcePickerFocusTest {
    @Test fun `provider interaction during frame handoff suppresses initial result focus`() = runTest {
        var claimed = false
        var focusRequests = 0
        val frame = CompletableDeferred<Unit>()
        val handoff = launch(start = CoroutineStart.UNDISPATCHED) {
            requestInitialSourceFocusAfterFrame(
                isClaimed = { claimed },
                awaitFrame = { frame.await() },
                requestFocus = { focusRequests++; claimed = true },
            )
        }

        assertEquals(0, focusRequests)
        claimed = true
        frame.complete(Unit)
        handoff.join()

        assertEquals(0, focusRequests)
    }

    @Test fun `unclaimed initial result receives focus after the frame`() = runTest {
        var frameAwaited = false
        var focusRequests = 0

        requestInitialSourceFocusAfterFrame(
            isClaimed = { false },
            awaitFrame = { frameAwaited = true },
            requestFocus = { focusRequests++ },
        )

        assertTrue(frameAwaited)
        assertEquals(1, focusRequests)
    }
}
