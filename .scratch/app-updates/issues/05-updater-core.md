# 05 — Update check and verified download

Blocked by: 01
Status: ready
Owner: unassigned
Triage: ready-for-agent
Repositories: android
Implementation commits: pending

## Acceptance

- Parse `update.json` schema 1 with kotlinx.serialization; reject unknown schema,
  non-HTTPS or non-`github.com/viptv-org/android` APK URLs, and malformed sha256.
- Decide availability from installed versionCode, `minSdk` and `Build.VERSION.SDK_INT`.
- Check on launch at most every 6 hours (persisted timestamp) and on demand.
- Download with OkHttp to app-private storage with progress and cancellation;
  verify size and sha256 before exposing the file; delete partial or bad files.
- Disabled entirely when `BuildConfig.UPDATE_MANIFEST_URL` is empty.
- JVM unit tests cover parsing, availability, URL rejection, checksum mismatch
  and the MockWebServer-free OkHttp boundary style already used in the repo.

## Evidence

Pending.
