package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvRemoteInputTest {
    @Test fun discoveryDecodesTheRealNativeResultEnvelope() {
        val candidates = RemoteInput.discoveryCandidates("192.168.1")
        assertEquals(508, candidates.length())
        assertEquals("192.168.1.1:7345", candidates.getJSONObject(0).getString("host"))
        assertEquals("192.168.1.1:9000", candidates.getJSONObject(1).getString("host"))
        assertEquals("192.168.1.254:9000", candidates.getJSONObject(507).getString("host"))
    }
    @Test fun backReturnsOneSetupStepWithoutLosingThePairedTvSettings() {
        assertEquals("manual", RemoteInput.parent("pin", "manual", false))
        assertEquals("search", RemoteInput.parent("pin", "search", false))
        assertEquals("remote", RemoteInput.parent("pin", "remote", true))
        assertEquals("search", RemoteInput.parent("manual", "manual", false))
        assertEquals("intro", RemoteInput.parent("search", "manual", false))
        assertEquals("settings", RemoteInput.parent("intro", "search", true))
        assertEquals("", RemoteInput.parent("intro", "search", false))
        assertEquals("settings", RemoteInput.parent("forget", "search", true))
    }
    @Test fun addressesAreLocalAndCanonical() {
        assertEquals("https://192.168.1.8:7345", RemoteInput.origin("192.168.1.8"))
        assertEquals("https://10.0.0.8:9000", RemoteInput.origin("https://10.0.0.8:9000"))
        for (bad in listOf("", "8.8.8.8", "127.0.0.1", "192.168.1.999", "tv.example.com", "192.168.1.8/path", "192.168.1.8:443", "192.168.1.8@evil.com", "172.32.0.1")) assertNull(RemoteInput.origin(bad), bad)
    }
    @Test fun swipeUsesOneDominantAxisAndThreshold() {
        assertNull(RemoteInput.swipe(12f, 4f, 32f))
        assertEquals("RIGHT", RemoteInput.swipe(80f, 12f, 32f))
        assertEquals("LEFT", RemoteInput.swipe(-80f, 12f, 32f))
        assertEquals("UP", RemoteInput.swipe(12f, -80f, 32f))
        assertEquals("DOWN", RemoteInput.swipe(12f, 80f, 32f))
    }
}
