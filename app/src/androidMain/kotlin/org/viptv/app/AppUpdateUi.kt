package org.viptv.app

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

// Copy and states follow design specs/behavior/app-updates.md (AND-APP-UPDATE-001).

internal fun aboutRowDetail(status: UpdateStatus, version: String): String =
    "Version $version" + if (status is UpdateStatus.Available) " · Update available" else ""

internal fun aboutUpdateLine(status: UpdateStatus): String? = when (status) {
    UpdateStatus.Disabled -> "Updates are off for development builds."
    UpdateStatus.Unknown -> null
    UpdateStatus.Checking -> "Checking for updates…"
    UpdateStatus.UpToDate -> "You're up to date."
    UpdateStatus.CheckFailed -> "Couldn't check for updates. Check your connection and try again."
    is UpdateStatus.Available -> "VIPTV ${status.manifest.versionName} is available."
}

internal fun updateFailureText(reason: UpdateFailure): String = when (reason) {
    UpdateFailure.Network -> "Check your connection and try again."
    UpdateFailure.Damaged -> "The download was incomplete or damaged. Try again."
    UpdateFailure.Rejected -> "Android didn't install the update. Try again, or install it from the release page."
}

internal const val ALLOW_UPDATES_TEXT = "Allow VIPTV to install apps, then come back to finish the update."
internal const val FIRE_TV_ALLOW_UPDATES_TEXT =
    "On Fire TV, open Settings › My Fire TV › Developer options › Install unknown apps and turn on VIPTV, then come back."

/** About VIPTV: version, update status and the first action kept in one slot so TV focus survives a check. */
@Composable internal fun AboutDialog(updates: AppUpdater, onDismiss: () -> Unit) {
    val status = updates.status
    val description = listOfNotNull("VIPTV " + BuildConfig.VERSION_NAME + "\n\nYour shows, channels and progress. All in one place.", aboutUpdateLine(status)).joinToString("\n\n")
    val options = when (status) {
        UpdateStatus.Disabled -> listOf("Close" to onDismiss)
        is UpdateStatus.Available -> listOf("Update now" to { onDismiss(); updates.update() }, "Close" to onDismiss)
        else -> listOf("Check for updates" to updates::checkNow, "Close" to onDismiss)
    }
    ChoiceDialog("About VIPTV", options, onDismiss, description)
}

/** Launch prompt and update flow dialogs; the shell never shows them over the player. */
@Composable internal fun AppUpdateDialogs(updates: AppUpdater) {
    val activity = LocalActivity.current
    when (val flow = updates.flow) {
        UpdateFlow.None -> (updates.status as? UpdateStatus.Available)?.takeIf { updates.prompt }?.let { available ->
            ChoiceDialog("Update available", listOf("Update now" to updates::update, "Later" to updates::dismissPrompt), updates::dismissPrompt,
                "VIPTV ${available.manifest.versionName} is ready to install. Update your VIPTV server to its latest version too.")
        }
        is UpdateFlow.Permission -> ChoiceDialog("Allow updates",
            if (flow.systemScreen) listOf("Open settings" to { activity?.let(updates::openPermissionSettings); Unit }, "Cancel" to updates::cancel)
            else listOf("Try again" to updates::retryPermission, "Cancel" to updates::cancel),
            updates::cancel, if (flow.systemScreen) ALLOW_UPDATES_TEXT else FIRE_TV_ALLOW_UPDATES_TEXT)
        is UpdateFlow.Downloading -> ChoiceDialog("Downloading update", listOf("Cancel" to updates::cancel), updates::cancel,
            "VIPTV ${flow.versionName} · ${flow.percent}%")
        UpdateFlow.Installing -> Unit
        is UpdateFlow.Failed -> ChoiceDialog("Couldn't update VIPTV", listOf("Try again" to updates::retry, "Close" to updates::cancel), updates::cancel,
            updateFailureText(flow.reason))
    }
}

internal fun AppUpdater.dialogVisible(): Boolean = flow !is UpdateFlow.None && flow != UpdateFlow.Installing || prompt && status is UpdateStatus.Available
