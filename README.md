# VIPTV Android

Native phone and Android TV VIPTV app and playback contracts backed by AndroidX Media3. `:app` is the adaptive Jetpack Compose client; the root module is the Android-only playback library it uses. Product controls, focus, screens and playback policy follow the pinned design repository contract.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ phones and Android TV. Native TV mode selects the remote layout; phones retain touch, system text entry and rotation.
- `:app` phone username/password sign-in, TV device pairing/refresh, profiles, Home/Discover/source picker, exact-source Resume, and Media3 direct playback.
- Media3 playback, headers, external subtitles, track selection, live/DVR semantics, and runtime capability reporting.
- No desktop, Apple, browser, JavaScript, WebAssembly, MPV, AVFoundation, or package publishing.

## Shared application core

`viptv-org/core` owns response normalization, session restoration and shared presentation/playback rules. The app calls its native UniFFI library and generated kotlinx.serialization models. Compose, Media3, Android networking and credential storage remain platform code. Hero artwork is a landscape role supplied by the core, never a poster fallback.

`CORE_REF` and `vendor/core/lock.json` pin a committed Rust source/bindings snapshot. Do not edit vendor files. Make shared changes in core, regenerate and commit there, then run:

```sh
node scripts/core-sync.mjs sync ../core
node scripts/core-sync.mjs check
```

The same core revision must be adopted by TV-web for shared behavior changes. Installed apps still need rebuilding and delivery.

`TORRENT_REF` pins a separate archive-free gateway Kotlin/Android native snapshot.
Gradle verifies its checksums and packages its dependency notices alongside the
three supported ABI libraries without replacing core or JNA. See
[native artifact adoption](docs/NATIVE_TORRENT_ARTIFACTS.md) for source generation,
import, APK alignment and actual ABI loading checks. Artifact presence or loading
does not establish native playback qualification or distribution clearance.

## Backend compatibility and live guide

Live browsing requires the backend v2 next/previous cursor contract. Do not use
this app against a backend with the older live contract. Backend changes must
preserve the v2 wire contract or use a new protocol version; an Android build
alone does not authorize a backend migration or production deployment.

The guide retains at most three channel pages and fetches bounded viewport
schedules. Categories use one replacement page of up to 200, with independent
cancellation/retry and profile/catalog scope guards.

### Category UI integration

Keep the existing row, fixed All/My channels/Recent/Search actions and visuals;
do not add paging buttons or reserve provider IDs. `guideUi.categoryPage`
contains the revision, next/previous cursors, saved viewport, loading/error and
edge focus. `guideUi.categories` is the current bounded list.

- Report the rendered revision and first/last **category IDs** through
  `onGuideCategoryViewport(revision, firstId, lastId, offset, allowPaging = false)`.
  Fixed tab indices are not category indices. Capture the revision belonging to
  the rendered row, not a newer state from a stale callback.
- When `awaitingAnchor` is true, restore `focusIndex` (first on forward, last on
  backward) before acknowledging the viewport. Stale/unrestored callbacks are
  ignored to prevent page oscillation. Returning without replacement preserves
  the saved visible index/offset.
- Record exact namespaced row keys/index/offset (including fixed controls) and
  actual TV focus through `onGuideCategoryRowViewport`.
- Use `changeGuideCategoryPage(delta, renderedRevision)` at intentional remote
  boundaries and `retryGuideCategories()` for retry. Search-Right advances and
  All-Left reverses. Ordinary provider-to-Search/Recent focus moves and Search
  activation remain available; paging must not consume their navigation.
- Phone paging requires continued drag beyond the row ends. Wait for drag/fling
  settlement and closed overlays before restoring the provider anchor.

These callbacks update category state only. They must not reset channel filters,
programme time, schedules or unrelated playback. Do not invent a default catalog
override from response metadata.

## Validation and builds

[DEVELOPMENT.md](DEVELOPMENT.md) documents host selection, local native builds,
Windows tools and the Linux workspace's serve-avd TV emulator. On the Linux
workspace, read the workspace root's private `.local-device-testing.md` before
installing an APK or running TV UI/input checks.

Use JDK 17, SDK Platform 36, Node 22+, Rust, cargo-ndk and an Android NDK. The
hosted workflow installs NDK 28.2.13676358. With the Android SDK/NDK environment
configured:

```sh
node scripts/core-sync.mjs check
node scripts/design-sync.mjs check
scripts/prepare-core.sh host
./gradlew --no-daemon :testDebugUnitTest :app:testDebugUnitTest
scripts/prepare-core.sh android
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

Hosted `.github/workflows/build.yml` builds the pinned native core for host tests
and all three Android ABIs, runs library/app unit tests and lint, and packages a
universal phone/TV debug APK with revision/pin metadata and SHA256SUMS. Main
pushes and manual dispatch produce sideloading artifacts only. There are no PR
gates, automatic releases, package/image publishing or deployments; retain local
checks. Production delivery requires separate authorization.

A passing build does not qualify physical playback hardware. Emulator and
physical evidence and outstanding qualification limits are recorded in
[TESTING.md](TESTING.md).

### Dated evidence

These records describe specific revisions and conditions, not the current
checkout or deployment:

- [Hosted Core8 artifact — 2026-09-30](docs/history/2026-09-30-hosted-core8-artifact.md)
- [Development v2 qualification — 2026-10-01](docs/history/2026-10-01-development-qualification.md)
- [Undated backend cutover handoff, preserved 2026-10-06](docs/history/2026-10-06-backend-cutover-handoff.md)

## Design and native preview

`DESIGN_REF` pins AND-037 and the current shared visual system. The generated tokens, local Onest/Bricolage fonts, licensed Lucide assets and the TV hero shaders (`app/src/androidMain/assets/hero`, TV-042) are checked by `scripts/design-sync.mjs`; never edit generated files. To adopt a new committed design:

```sh
node scripts/design-sync.mjs sync ../design <full-commit-sha>
```

Phone and TV share cards, buttons, sheets, profile/avatar UI and controllers. TV uses a uniformly scaled 1920×1080 frame; phone layouts use native density, font scaling and safe/keyboard insets. Profile selection enters Home immediately after server confirmation. Home publishes saved rows before optional metadata, bounds provider concurrency and reuses loaded rows when returning. Series, source and player navigation retain their originating route. Live uses the direct channel path and omits transport/seek controls.

[qualification/README.md](qualification/README.md) describes isolated HTTPS emulator fixtures, device pairing, native input and private visual captures. The normal APK trusts system CAs. Fixture trust is an explicit debug build option and must not be present in an APK delivered for normal use. Debug-only preview origins do not alter release routing. Production deployment and store publication are separate actions.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
