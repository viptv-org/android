# 05 — Update check and verified download

Blocked by: 01
Status: done
Owner: Claude
Triage: ready-for-agent
Repositories: android
Implementation commits: see "Add verified update check and download core" on feat/app-updates

## Acceptance

- Parse `update.json` schema 1 with kotlinx.serialization; reject unknown schema,
  non-HTTPS or non-`github.com/viptv-org/android` APK URLs, and malformed sha256.
- Decide availability from installed versionCode, `minSdk` and `Build.VERSION.SDK_INT`.
- Pure throttle: a launch check is due after 6 hours or when the clock moved
  backwards. Persisting the timestamp and wiring launch/on-demand checks is ticket 06.
- Download with OkHttp to app-private storage with progress and cancellation;
  verify size and sha256 before exposing the file; delete partial or bad files.
- JVM unit tests cover parsing, availability, URL rejection, throttling, and
  real OkHttp downloads against the repo's in-process `FixtureServer`.

## Evidence

`app/src/androidMain/kotlin/org/viptv/app/AppUpdates.kt` with
`AppUpdatesTest` (7 tests, 0 failures) on 2026-10-11. Full
`:testDebugUnitTest :app:testDebugUnitTest` and
`-PviptvVersion=0.3.0 :app:assemblePerformance :app:lintPerformance` BUILD
SUCCESSFUL. The code is not yet reachable from the app, so R8 likely removes it;
ticket 06 must confirm `UpdateManifest` deserializes in the optimized APK.
