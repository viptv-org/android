# 01 — Derive app version from the release tag

Blocked by: none
Status: done
Owner: Claude
Triage: ready-for-agent
Repositories: android
Implementation commits: 8dffc54

## Acceptance

- `-PviptvVersion=MAJOR.MINOR.PATCH` sets `versionName` and
  `versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`; malformed values or
  MINOR/PATCH >= 1000 fail the build.
- Without the property, builds keep `versionCode 1` / `0.1.0`.
- `BuildConfig.UPDATE_MANIFEST_URL` is the latest-release `update.json` URL only
  for versioned builds and empty otherwise, so unversioned builds never self-update.
- Signing README and build comment describe the development key as also signing
  beta GitHub releases (owner decision in ../plan.md).

## Evidence

Local run on 2026-10-11 (worktree `feat/app-updates`, sequential Gradle):
`prepare-core.sh host`, `:testDebugUnitTest :app:testDebugUnitTest` BUILD
SUCCESSFUL; `prepare-core.sh android`, `-PviptvVersion=0.2.0 :app:assemblePerformance
:app:lintPerformance` BUILD SUCCESSFUL; native-torrent and ffmpeg APK checks pass.
Generated `BuildConfig.UPDATE_MANIFEST_URL` is the latest-release URL; `aapt2`
reports versionCode 2000 / versionName 0.2.0; `apksigner` certificate SHA-256
`7ba1a1ae…550d` matches the existing development APK.
