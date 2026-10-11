package org.viptv.app

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** What the last update check learned about the latest GitHub release (AND-APP-UPDATE-001). */
internal sealed interface UpdateStatus {
    data object Disabled : UpdateStatus
    data object Unknown : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data object CheckFailed : UpdateStatus
    data class Available(val manifest: UpdateManifest) : UpdateStatus
}

/** The update flow started by Update now; [None] when no flow dialog is showing. */
internal sealed interface UpdateFlow {
    data object None : UpdateFlow
    data class Permission(val systemScreen: Boolean) : UpdateFlow
    data class Downloading(val versionName: String, val percent: Int) : UpdateFlow
    data object Installing : UpdateFlow
    data class Failed(val reason: UpdateFailure) : UpdateFlow
}

internal enum class UpdateFailure { Network, Damaged, Rejected }

internal fun shouldPromptForUpdate(status: UpdateStatus, dismissedVersionCode: Int): Boolean =
    status is UpdateStatus.Available && status.manifest.versionCode != dismissedVersionCode

/**
 * Checks the latest release, downloads its verified APK and hands it to the system installer.
 * Owned by [ViptvModel] so a rotation or server change does not interrupt a download.
 */
internal class AppUpdater(private val application: Application) {
    private val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL
    private val preferences = application.getSharedPreferences("viptv.updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directory = File(application.filesDir, "updates")
    private val client by lazy {
        AppUpdateClient(manifestUrl, directory, OkHttpClient.Builder()
            .dns(org.viptv.video.AndroidMediaDns(application))
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .build())
    }
    private var check: Job? = null
    private var download: Job? = null
    private var awaitingPermission = false
    private val installAction = application.packageName + ".UPDATE_INSTALL_STATUS"

    var status by mutableStateOf<UpdateStatus>(if (manifestUrl.isEmpty()) UpdateStatus.Disabled else rememberedAvailable() ?: UpdateStatus.Unknown); private set
    var flow by mutableStateOf<UpdateFlow>(UpdateFlow.None); private set
    var prompt by mutableStateOf(false); private set

    private val installStatus = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm == null) flow = UpdateFlow.Failed(UpdateFailure.Rejected)
                    else runCatching { application.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { flow = UpdateFlow.Failed(UpdateFailure.Rejected) }
                }
                PackageInstaller.STATUS_SUCCESS -> flow = UpdateFlow.None
                PackageInstaller.STATUS_FAILURE_ABORTED -> flow = UpdateFlow.None
                else -> flow = UpdateFlow.Failed(UpdateFailure.Rejected)
            }
        }
    }

    init {
        if (manifestUrl.isNotEmpty()) ContextCompat.registerReceiver(application, installStatus, IntentFilter(installAction), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** A signed-in, non-player screen is showing: run the throttled quiet launch check. */
    fun onLaunchScreen() {
        if (status == UpdateStatus.Disabled || check != null) return
        if (!isUpdateCheckDue(preferences.getLong("checked", -1).takeIf { it >= 0 }, System.currentTimeMillis())) {
            prompt = shouldPromptForUpdate(status, preferences.getInt("dismissed", 0))
            return
        }
        runCheck(quiet = true)
    }

    fun checkNow() {
        if (status != UpdateStatus.Disabled && check == null) runCheck(quiet = false)
    }

    fun dismissPrompt() {
        (status as? UpdateStatus.Available)?.let { preferences.edit().putInt("dismissed", it.manifest.versionCode).apply() }
        prompt = false
    }

    fun update() {
        prompt = false
        if (status !is UpdateStatus.Available || download != null) return
        if (!canInstall()) {
            flow = UpdateFlow.Permission(permissionScreen() != null)
            return
        }
        startDownload()
    }

    fun openPermissionSettings(activity: Activity) {
        val screen = permissionScreen()
        if (screen == null) { flow = UpdateFlow.Permission(systemScreen = false); return }
        awaitingPermission = true
        try { activity.startActivity(screen) } catch (_: ActivityNotFoundException) { flow = UpdateFlow.Permission(systemScreen = false) }
    }

    /** Try again from the Fire TV instructions, or return from the system permission screen. */
    fun retryPermission() {
        awaitingPermission = false
        if (canInstall()) startDownload() else flow = UpdateFlow.Permission(permissionScreen() != null)
    }

    fun onForeground() {
        if (awaitingPermission && flow is UpdateFlow.Permission) retryPermission()
    }

    fun cancel() {
        awaitingPermission = false
        download?.cancel()
        download = null
        flow = UpdateFlow.None
    }

    fun retry() {
        flow = UpdateFlow.None
        if (status is UpdateStatus.Available) update() else checkNow()
    }

    fun close() {
        scope.cancel()
        if (manifestUrl.isNotEmpty()) runCatching { application.unregisterReceiver(installStatus) }
    }

    private fun runCheck(quiet: Boolean) {
        val previous = status
        if (!quiet) status = UpdateStatus.Checking
        check = scope.launch {
            val result = try {
                evaluateUpdate(client.fetchManifest(), BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
            status = when (result) {
                null -> if (quiet) previous else UpdateStatus.CheckFailed
                is UpdateCheck.Available -> UpdateStatus.Available(result.manifest)
                is UpdateCheck.UpToDate, is UpdateCheck.Unsupported -> UpdateStatus.UpToDate
            }
            remember(status)
            if (quiet) prompt = shouldPromptForUpdate(status, preferences.getInt("dismissed", 0))
            check = null
        }
    }

    private fun startDownload() {
        val manifest = (status as? UpdateStatus.Available)?.manifest ?: return
        flow = UpdateFlow.Downloading(manifest.versionName, 0)
        download = scope.launch {
            try {
                val apk = client.download(manifest) { downloaded, total ->
                    val percent = (downloaded * 100 / total).toInt()
                    if ((flow as? UpdateFlow.Downloading)?.percent != percent) flow = UpdateFlow.Downloading(manifest.versionName, percent)
                }
                flow = UpdateFlow.Installing
                withContext(Dispatchers.IO) { install(apk) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                flow = UpdateFlow.Failed(when (error) {
                    is UpdateDamagedException -> UpdateFailure.Damaged
                    is IOException -> UpdateFailure.Network
                    else -> UpdateFailure.Rejected
                })
            } finally {
                download = null
            }
        }
    }

    private fun install(apk: File) {
        val installer = application.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(application.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(application, id, Intent(installAction).setPackage(application.packageName), PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            session.commit(callback.intentSender)
        }
    }

    private fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || application.packageManager.canRequestPackageInstalls()

    private fun permissionScreen(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + application.packageName))
        return intent.takeIf { it.resolveActivity(application.packageManager) != null }
    }

    /** Keeps a known newer release across restarts so Settings and the prompt survive the check throttle. */
    private fun remember(value: UpdateStatus) {
        when (value) {
            is UpdateStatus.Available -> preferences.edit().putString("available", updateManifestJson(value.manifest)).apply()
            UpdateStatus.UpToDate -> {
                preferences.edit().remove("available").apply()
                directory.listFiles()?.forEach(File::delete)
            }
            else -> Unit
        }
    }

    private fun rememberedAvailable(): UpdateStatus? {
        val manifest = preferences.getString("available", null)?.let { runCatching { parseUpdateManifest(it) }.getOrNull() } ?: return null
        return (evaluateUpdate(manifest, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT) as? UpdateCheck.Available)?.let { UpdateStatus.Available(it.manifest) }
    }
}
