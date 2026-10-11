# Android version identifiers

The playback library uses group `org.viptv`. Its Gradle version comes from
`VERSION_NAME`, defaulting to `0.1.0-SNAPSHOT` in `build.gradle.kts`.
The Android app's `versionCode` and `versionName` are declared separately in
`app/build.gradle.kts`; do not assume the library and app identifiers are coupled.

Use Semantic Versioning for public playback contracts:

- `MAJOR` changes a public playback contract incompatibly.
- `MINOR` adds backward-compatible contracts or capabilities.
- `PATCH` fixes behavior without changing the supported public surface.

Build artifacts identify their exact source revision and pinned core/design
revisions. Main pushes and manual dispatch produce sideloading artifacts; there
is no package-publishing or deployment workflow.

## App releases

App releases are beta GitHub releases of the optimized `performance` APK,
signed with the committed development key (see
[app/signing/README.md](app/signing/README.md)). There is one channel: the
GitHub `latest` release.

A tag `vMAJOR.MINOR.PATCH` on a commit in `main` drives the release build with
`-PviptvVersion=MAJOR.MINOR.PATCH`:

- `versionName` is `MAJOR.MINOR.PATCH`.
- `versionCode` is `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`; MINOR and PATCH
  stay below 1000 and MAJOR at most 2000.
- `BuildConfig.UPDATE_MANIFEST_URL` points at
  `https://github.com/viptv-org/android/releases/latest/download/update.json`.

Builds without the property keep `versionCode 1` / `0.1.0` and an empty update
manifest URL, so development APKs never update themselves and any release
installs over them.

The release carries `viptv-android-<version>.apk`, `SHA256SUMS`, `build.json`
and `update.json`:

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
    "size": 102074287,
    "sha256": "<hex>"
  }
}
```

`scripts/package-apk.py` writes it and fails when the APK's versionCode or
versionName does not match the tag. Because the signing key is public, the
`sha256` is what the updater trusts; never publish an APK whose checksum is not
in the release's own `update.json`.

To cut a release, with the owner's approval:

1. Pick the next version; never reuse or move a published tag.
2. `git tag vX.Y.Z <main commit>` and `git push origin vX.Y.Z`.
3. Wait for the `Build Android APK` run for the tag; its `release` job publishes
   the release with generated notes and the beta / update-your-backend note.
4. Confirm `latest/download/update.json` returns the new version and that the
   downloaded APK matches its `sha256`.

The backend has no version contract with the app. Release notes tell users to
update the backend to its latest version.

## Upstream tags

Upstream `v0.1.x`/`v0.2.x` tags identify the independently versioned
`@get-air/video` TypeScript package, not this repository's Android builds. App
releases therefore start at `v0.3.0`.
Import provenance and licensing are recorded in [README.md](README.md).
