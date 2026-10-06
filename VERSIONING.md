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
revisions. Main pushes and manual dispatch produce sideloading artifacts;
there is no automatic release, package-publishing or deployment workflow.

Upstream `v0.1.x`/`v0.2.x` tags identify the independently versioned
`@get-air/video` TypeScript package, not this repository's Android builds.
Import provenance and licensing are recorded in [README.md](README.md).
