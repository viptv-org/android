# AND-APP-UPDATE-001 — Android in-app updates from GitHub Releases

Status: **proposed**, owner requested 2026-10-11. Android (phones, Android TV,
Fire TV) adopts it first. Release packaging, versioning and `update.json` are
owned by the Android repository (`VERSIONING.md`). Other clients are out of
scope; Roku, TV-web and desktop keep their own delivery.

## Intent

Sideloaded Android installs learn about a newer beta release and install it
from inside the app, without a store, a computer or ADB. There is one channel:
the latest GitHub release. The backend is not versioned; release notes ask
people to update their server to its latest version.

## Availability

- Only release builds (built from a version tag) check for updates.
  Development builds show the version only and never offer an update.
- An update is **available** when the latest release has a higher version code
  than the installed app and supports the device's Android version. A release
  that needs a newer Android version is never offered.
- The app downloads only the release's own APK and installs it only when its
  size and checksum match the release's `update.json`.

## When the app checks

1. **On launch:** after the first signed-in screen is shown, at most once every
   6 hours. The check runs quietly; no spinner, no error if it fails.
2. **On demand:** from **Settings › About VIPTV › Check for updates**, any time.

A check never runs, prompts or installs while the player is open. A launch
prompt that becomes due while the player is open waits until the viewer leaves
the player.

## Launch prompt

When a launch check finds an available version the viewer has not dismissed,
show the dialog:

- Title **`Update available`**
- Description **`VIPTV {version} is ready to install. Update your VIPTV server to its latest version too.`**
- Buttons **`Update now`** (focused first on TV) · **`Later`**

`Later` and Back close it and remember that version: the launch prompt does not
appear again for it. A newer release prompts again. Settings always shows an
available version.

## Settings

The **About VIPTV** row shows:

| State | Row detail |
|---|---|
| Development build | `Version {version}` |
| Up to date or unknown | `Version {version}` |
| Update available | `Version {version} · Update available` |

Choosing the row opens the **About VIPTV** dialog with the description
`VIPTV {version}` + blank line + `Your shows, channels and progress. All in one place.`
followed by one status line, and these buttons:

| State | Status line | Buttons |
|---|---|---|
| Development build | `Updates are off for development builds.` | `Close` |
| Not checked / up to date | `You're up to date.` (after a check) or none | `Check for updates` · `Close` |
| Checking | `Checking for updates…` | `Check for updates` (no effect while checking) · `Close` |
| Available | `VIPTV {new} is available.` | `Update now` · `Close` |
| Check failed | `Couldn't check for updates. Check your connection and try again.` | `Check for updates` · `Close` |

The first button keeps its position while the status changes, so TV focus
stays on it (`Check for updates` becomes `Update now` when a release is
found). On TV the first button takes focus when the dialog opens. Back closes
the dialog and returns focus to the About VIPTV row.

## Update flow

`Update now` (from the prompt or Settings) runs these steps:

1. **Permission.** If Android requires the viewer to allow VIPTV to install
   apps, show **`Allow updates`** with description
   `Allow VIPTV to install apps, then come back to finish the update.` and
   buttons **`Open settings`** · **`Cancel`**. `Open settings` opens the
   system setting for VIPTV. When the viewer returns with permission granted,
   the flow continues to the download automatically. Returning without it
   shows the same dialog again.
   - When the device has no such setting screen (some Fire TV models), the
     description instead reads
     `On Fire TV, open Settings › My Fire TV › Developer options › Install unknown apps and turn on VIPTV, then come back.`
     with buttons **`Try again`** · **`Cancel`**.
2. **Download.** Dialog **`Downloading update`**, description
   `VIPTV {new} · {percent}%`, button **`Cancel`**. Cancel or Back stops the
   download and discards it. A download already verified for that version is
   reused, so this step is skipped.
3. **Install.** The system install confirmation appears. VIPTV closes when the
   update installs; the viewer reopens it, still signed in, with settings and
   progress kept.
4. **Failure.** Dialog **`Couldn't update VIPTV`** with one description and
   buttons **`Try again`** · **`Close`**:
   - network or server error: `Check your connection and try again.`
   - checksum or size mismatch: `The download was incomplete or damaged. Try again.`
   - the viewer cancelled the system confirmation: no dialog; return to where
     they were.
   - installation rejected by Android: `Android didn't install the update. Try again, or install it from the release page.`

## Acceptance

1. **AND-APP-UPDATE-01:** a development build shows `Version 0.1.0` and
   `Updates are off for development builds.`, and never prompts.
2. **AND-APP-UPDATE-02:** a release build older than the latest release shows
   the launch prompt once after sign-in; `Later` suppresses it for that version
   across restarts; Settings still shows `· Update available`.
3. **AND-APP-UPDATE-03:** `Update now` without install permission shows
   `Allow updates`; granting it in system settings and returning continues to
   the download and the system confirmation; the updated app opens signed in.
4. **AND-APP-UPDATE-04:** the launch prompt never appears and no check runs
   while the player is open; it appears after leaving the player if due.
5. **AND-APP-UPDATE-05:** `Check for updates` on the latest release shows
   `You're up to date.`; with no network it shows the check-failed line.
6. **AND-APP-UPDATE-06:** a damaged download shows the checksum message and
   nothing is installed; cancelling the download leaves no file behind.
7. **AND-APP-UPDATE-07:** every dialog is reachable and dismissable with a TV
   remote (D-pad, OK, Back) on Android TV and Fire TV.
