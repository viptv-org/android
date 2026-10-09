package org.viptv.app

import android.os.Build
import android.os.Process
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.playback_gateway_ffi.defaultTorrentOptions
import uniffi.viptv_core.CoreBridge
import org.junit.Assume.assumeTrue

/** Run on each supported process ABI; loading and runtime defaults are measured separately. */
class NativeTorrentArtifactLoadTest {
    @Test fun nativeCapabilityIsAvailableWithoutAnEnablementReceipt() {
        assertTrue(NativeTorrentRuntime.isAvailable())
    }

    @Test fun legacyComparisonFacadeAndCoreStillLoadAlongsideWorkerArtifacts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Legacy transport is an explicit debug comparison only",java.io.File(context.applicationInfo.nativeLibraryDir,"libplayback_gateway_ffi.so").isFile)
        val expectedAbi = InstrumentationRegistry.getArguments().getString("nativeTorrentExpectedAbi")
        assumeTrue("Explicit process ABI required for native loading evidence", expectedAbi != null)
        assertTrue(expectedAbi in setOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        assertTrue(expectedAbi in Build.SUPPORTED_ABIS)
        assertEquals(expectedAbi != "armeabi-v7a", Process.is64Bit())
        val processAbi = when (System.getProperty("os.arch")) {
            "arm", "armv7l", "armv8l", "aarch64" -> if (Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"
            "x86_64", "amd64" -> "x86_64"
            else -> "unsupported"
        }
        assertEquals(expectedAbi, processAbi)
        assertTrue(NativeTorrentArtifacts.isLoaded())
        assertEquals(2_147_483_648uL, defaultTorrentOptions().maxCacheBytes)
        CoreBridge().use { core -> assertTrue(core.view().isNotEmpty()) }
        assertTrue(NativeTorrentRuntime.isAvailable())
    }

    @Test fun ownedFixtureCleartextIsRestrictedToLiteralIpv4Loopback() {
        assumeTrue("Loopback-only policy belongs to the isolated native fixture APK",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".nativefixture"))
        val policy = NetworkSecurityPolicy.getInstance()
        assertFalse(policy.isCleartextTrafficPermitted)
        assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
        for (host in listOf("localhost", "::1", "127.0.0.2", "192.168.0.1", "example.com")) {
            assertFalse(policy.isCleartextTrafficPermitted(host))
        }
    }
}
