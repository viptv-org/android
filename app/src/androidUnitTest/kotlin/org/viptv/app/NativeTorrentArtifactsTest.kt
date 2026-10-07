package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeTorrentArtifactsTest {
    @Test fun missingAbiAndFailedInitializationRemainUnavailable() {
        for (failure in listOf(
            UnsatisfiedLinkError("private loader detail"),
            ExceptionInInitializerError("private initialization detail"),
            NoClassDefFoundError("private class detail"),
            IllegalStateException("private ABI detail"),
        )) {
            var attempts = 0
            val availability = NativeTorrentArtifactAvailability { attempts++; throw failure }
            assertFalse(availability.isLoaded())
            assertFalse(availability.isLoaded())
            assertEquals(1, attempts)
        }
    }

    @Test fun successfulLoadingIsMeasuredOnlyOnce() {
        var attempts = 0
        val availability = NativeTorrentArtifactAvailability { attempts++ }
        assertTrue(availability.isLoaded())
        assertTrue(availability.isLoaded())
        assertEquals(1, attempts)
    }
}
