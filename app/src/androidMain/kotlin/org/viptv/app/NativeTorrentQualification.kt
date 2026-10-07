package org.viptv.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

/** Measured development cohort facts; absent or inapplicable receipts stay unavailable. */
internal class NativeTorrentQualification(private val context: Context) {
    private val cohortMatches: Boolean by lazy {
        try {
            if (!BuildConfig.DEBUG || context.packageName != "org.viptv.app") return@lazy false
            val mode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (mode?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) return@lazy false
            val receipt = context.assets.open("native-torrent/scoped-qualification.json").bufferedReader().use {
                JSONObject(it.readText())
            }
            if (receipt.getString("decision") != "scoped_experimental_sticky_quarantine_v1") return@lazy false
            val platform = receipt.getJSONObject("platform")
            val abi = Build.SUPPORTED_ABIS.firstOrNull { (it == "arm64-v8a" || it == "x86_64") == Process.is64Bit() }
            if (platform.getString("processAbi") != abi || platform.getInt("apiLevel") != Build.VERSION.SDK_INT ||
                platform.getString("model") != Build.MODEL) return@lazy false
            val cohort = receipt.getJSONObject("cohort")
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                listOf("libplayback_gateway_ffi.so" to "gatewayLibrarySha256", "libviptv_core.so" to "coreLibrarySha256").all { (library, field) ->
                    val entry = apk.getEntry("lib/$abi/$library") ?: return@all false
                    val digest = MessageDigest.getInstance("SHA-256")
                    apk.getInputStream(entry).use { input ->
                        val bytes = ByteArray(65536)
                        while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) } == cohort.getString(field)
                }
            }
        } catch (_: Exception) { false }
    }

    fun isAvailable(): Boolean {
        if (!cohortMatches || !NativeTorrentArtifacts.isLoaded()) return false
        // elapsedRealtime is the Android clock that includes device suspension.
        val first = SystemClock.elapsedRealtimeNanos()
        val millis = SystemClock.elapsedRealtime()
        val second = SystemClock.elapsedRealtimeNanos()
        return first >= 0 && second >= first && millis >= 0 &&
            millis >= first / 1_000_000 && millis <= second / 1_000_000
    }
}
