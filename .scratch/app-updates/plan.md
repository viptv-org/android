# Android GitHub releases and in-app updates

Ship tagged Android builds as GitHub releases and let installed apps update
themselves from the latest release. This is beta auto-updating for sideloaded
phones, Android TV and Fire TV; Play Store distribution is out of scope.

## Owner decisions (2026-10-11)

- Releases keep the committed development key (`app/signing/viptv-development.p12`).
  No new key and no rotation, so existing installs update in place and keep
  sign-in, and the Fire TV rollback APK stays installable.
- One channel: the GitHub `latest` release. Releases are regular (not
  prerelease) GitHub releases with 0.x versions and beta wording in the notes.
- No Play Store variant is planned, so the updater ships in every release build.
- No backend versioning. Release notes tell users to update to the latest backend.
- App releases start at `v0.3.0`; `VERSIONING.md` reserves `v0.1.x`/`v0.2.x` names for the upstream `@get-air/video` package.

## Release pipeline

- The released APK is the existing `performance` variant (optimized,
  non-debuggable, same key) so the release is the build already qualified on
  devices. Adding a separate `release` build type is not needed.
- A tag `vMAJOR.MINOR.PATCH` sets `-PviptvVersion=MAJOR.MINOR.PATCH`.
  `versionName` is that string; `versionCode` is
  `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH` (MINOR and PATCH < 1000).
  Untagged builds are `X.Y.Z-dev` with versionCode one above the newest
  reachable release tag (`0.1.0` / 1 without tags), so they install over a
  release with `adb install -r`; their updater is disabled.
- `build.yml` also runs for `v*` tags. On a tag it verifies the APK's
  versionCode, names the APK `viptv-android-<version>.apk`, writes
  `update.json`, and a `release` job publishes the GitHub release.

### `update.json` (schema 1)

```json
{
  "schema": 1,
  "versionCode": 3000,
  "versionName": "0.3.0",
  "minSdk": 24,
  "revision": "<full commit sha>",
  "releaseUrl": "https://github.com/viptv-org/android/releases/tag/v0.3.0",
  "apk": {
    "name": "viptv-android-0.3.0.apk",
    "url": "https://github.com/viptv-org/android/releases/download/v0.3.0/viptv-android-0.3.0.apk",
    "size": 123456789,
    "sha256": "<hex>"
  }
}
```

The app reads `https://github.com/viptv-org/android/releases/latest/download/update.json`.
That redirect always targets the newest non-prerelease release and is not
subject to the unauthenticated REST API rate limit.

## In-app updater

- Check on launch at most every 6 hours, and on demand from Settings.
- An update is available when `versionCode` is greater than the installed one
  and `minSdk` is supported.
- Download the APK with OkHttp into app-private storage and verify size and
  sha256 from `update.json`. The signing key is public, so this checksum (fetched
  over HTTPS from GitHub) is the integrity check; Android still enforces that
  the APK is signed by the same certificate as the installed app.
- Install with a `PackageInstaller` session (`REQUEST_INSTALL_PACKAGES`). On API
  26+, when `canRequestPackageInstalls()` is false, open
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package. Handle
  `STATUS_PENDING_USER_ACTION` by launching the system confirmation.
- Never prompt or install during playback.
- UX states (up to date, available, downloading with progress, needs
  permission, ready to install, failed) are specified in `design` first.

## Open device question

Whether Fire OS opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for the app, or users
must enable it under Developer options > Install unknown apps. Verify on the
owner's Fire TV before finalizing the design copy.

## Tickets

See `issues/`. Order: versioning, release workflow, docs/policy, design spec,
updater core, updater install/UI, device validation, first release.
