# 06 — Install flow and update UI

Blocked by: 04
Status: in-review
Owner: Claude
Triage: ready-for-agent
Repositories: android
Implementation commits: c54e510, e0ccf12

## Acceptance

- Wire `AppUpdateClient` with the app's DNS (`AndroidMediaDns`), redirects
  enabled, and `filesDir/updates`; persist the last check time in app prefs and
  check on launch when `isUpdateCheckDue`, and on demand from Settings; skip
  everything when `BuildConfig.UPDATE_MANIFEST_URL` is empty.
- Confirm in the optimized APK that `UpdateManifest` deserializes after R8.
- Manifest adds `REQUEST_INSTALL_PACKAGES`.
- On API 26+, route to `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package when
  `canRequestPackageInstalls()` is false, and resume the flow on return.
- Install through a `PackageInstaller` session; handle `STATUS_PENDING_USER_ACTION`,
  success and each failure status with the design's copy.
- Settings row and launch prompt follow the design spec; nothing appears or
  installs while the player is active.
- Remote-only navigation works on TV.

## Evidence

Unit: `AppUpdatesTest` 9 tests; full suites 404 tests, 0 failures. `-PviptvVersion=0.3.0 :app:assemblePerformance :app:lintPerformance` passes (lint error `ContextCastToActivity` fixed; `<queries>` added for the settings intent). Device evidence and the Cancel race fix are in TESTING.md, "In-app updates (AND-APP-UPDATE-001) — 2026-10-11".

Development builds now use versionCode one above the newest reachable release tag (`X.Y.Z-dev`) so `adb install -r` of a development APK works over an installed release; verified with a temporary local `v0.3.0` tag (3001 / `0.3.0-dev`) and without tags (1 / `0.1.0`).
