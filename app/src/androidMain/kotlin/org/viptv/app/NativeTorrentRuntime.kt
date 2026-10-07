package org.viptv.app

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.security.NetworkSecurityPolicy

/** Native capability follows bundled process ABI and runtime facts in every build. */
internal object NativeTorrentRuntime {
    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val supportedProcess = Build.SUPPORTED_ABIS.any {
            when (it) {
                "armeabi-v7a" -> !Process.is64Bit()
                "arm64-v8a", "x86_64" -> Process.is64Bit()
                else -> false
            }
        }
        if (!supportedProcess || !NativeTorrentArtifacts.isLoaded() ||
            !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1")) return false
        // This clock includes device suspension; grant deadlines must share it.
        val first = SystemClock.elapsedRealtimeNanos()
        val millis = SystemClock.elapsedRealtime()
        val second = SystemClock.elapsedRealtimeNanos()
        return first >= 0 && second >= first && millis >= 0 &&
            millis >= first / 1_000_000 && millis <= second / 1_000_000
    }
}
