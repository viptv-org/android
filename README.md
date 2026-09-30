# VIPTV Android

## Backend v2 development handoff

`refactor/android-backend-cutover` is isolated from the owner's active UI checkout.
It targets backend `refactor/backend-v2` at `2c2eca2` or later compatible revisions,
with shared core `fba95c8`. Do not install this candidate against the older
production backend: live browsing requires the v2 next/previous cursor contract.
Further backend work must preserve this v2 wire contract or use a new protocol
version; no production migration/deployment is implied by this handoff.

Networking/controller changes remove legacy live discovery/playback/catalog calls,
retain only three channel pages, and fetch bounded viewport schedules. Layouts
are unchanged. The small `GuideScreen.kt` integration diff reports the viewport,
restores its saved position/focus and corrects search copy. Review that diff when
merging UI work; the owner checkout has not been switched or overwritten.

Category controller logic now supports next/previous replacement pages of 200,
with separate cancellation/retry and scope guards. The owner-facing
`GuideScreen.kt` callbacks are deliberately not wired by this controller-only
pass; see the integration notes below. Remaining gates include visible category
traversal beyond its first 200 entries, Android
TV/physical-device qualification, real backend/gateway integration, and the
remaining quality/legacy cleanup across the organization. See TESTING.md.

### Category integration for the UI owner

Keep the existing row, fixed All/My channels/Recent/Search actions and visuals;
do not add paging buttons or reserve provider IDs. `guideUi.categoryPage` exposes
the page revision, next/previous tokens, saved viewport, loading/error and desired
edge focus. Existing `guideUi.categories` remains the current bounded list.

- Report the rendered revision and first/last **category IDs** through
  `onGuideCategoryViewport(revision, firstId, lastId, offset)`; fixed tab indices
  are not category indices. Capture the revision belonging to the rendered row,
  not a newer state from a stale callback.
- When `awaitingAnchor` is true, restore the page's `focusIndex` (first on forward,
  last on backward) using existing list/focus mechanics before acknowledging its
  viewport. Old/unrestored callbacks are ignored, preventing page oscillation.
  Preserve the saved visible index/offset when returning without replacement.
- Use `changeGuideCategoryPage(delta, renderedRevision)` at the intentional
  remote category boundary and `retryGuideCategories()` for an existing retry
  action. Keep fixed actions, especially Search, independently reachable; do not
  consume their navigation merely to advance provider categories.

These methods update category state only. They must not reset channel filters,
programme time, schedules or unrelated playback. No default catalog override is
invented from response metadata. Your root Android checkout is not modified.

Actions delivery: main pushes and manual builds produce sideloading artifacts
(Android universal APK; desktop Windows/Linux installers; Roku ZIP; TV WGT/IPK).
Other repositories have no Actions workflows. Local checks remain; previous
CI/release-publication descriptions below are historical. No automatic deploys.

Native phone and Android TV VIPTV app and playback contracts backed by AndroidX Media3. `:app` is the adaptive Jetpack Compose client; the root module is the Android-only playback library it uses. Product controls, focus, screens and playback policy follow the pinned design repository contract.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ phones and Android TV. Native TV mode selects the remote layout; phones retain touch, system text entry and rotation.
- `:app` phone username/password sign-in, TV device pairing/refresh, profiles, Home/Discover/source picker, exact-source Resume, and Media3 direct playback.
- Media3 playback, headers, external subtitles, track selection, live/DVR semantics, and runtime capability reporting.
- No desktop, Apple, browser, JavaScript, WebAssembly, MPV, AVFoundation, package publishing, or inherited automation.

## Shared application core

`viptv-org/core` owns response normalization, session restoration and shared presentation/playback rules. The app calls its native UniFFI library and generated kotlinx.serialization models. Compose, Media3, Android networking and credential storage remain platform code. Hero artwork is a landscape role supplied by the core, never a poster fallback.

`CORE_REF` and `vendor/core/lock.json` pin a committed Rust source/bindings snapshot. Do not edit vendor files. Make shared changes in core, regenerate and commit there, then run:

```sh
node scripts/core-sync.mjs sync ../core
node scripts/core-sync.mjs check
```

The same core revision must be adopted by TV-web for shared behavior changes. Installed apps still need rebuilding and delivery.

## Validation and builds

Use JDK 17, SDK Platform 36, Node 22+, Rust, cargo-ndk and an Android NDK. The local acceptance build used NDK 28.2.13676358. With the Android SDK/NDK environment configured:

```sh
node scripts/core-sync.mjs check
node scripts/design-sync.mjs check
scripts/prepare-core.sh host
./gradlew --no-daemon :testDebugUnitTest :app:testDebugUnitTest
scripts/prepare-core.sh android
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

Hosted `app-review.yml` also builds the pinned native core for host tests and all three Android ABIs. A passing build does not qualify physical playback hardware. Current emulator and physical evidence is recorded separately in [TESTING.md](TESTING.md).

## Design and native preview

`DESIGN_REF` pins AND-037 and the current shared visual system. The generated tokens, local Onest/Bricolage fonts and licensed Lucide assets are checked by `scripts/design-sync.mjs`; never edit generated files. To adopt a new committed design:

```sh
node scripts/design-sync.mjs sync ../design <full-commit-sha>
```

Phone and TV share cards, buttons, sheets, profile/avatar UI and controllers. TV uses a uniformly scaled 1920×1080 frame; phone layouts use native density, font scaling and safe/keyboard insets. Profile selection enters Home immediately after server confirmation. Home publishes saved rows before optional metadata, bounds provider concurrency and reuses loaded rows when returning. Series, source and player navigation retain their originating route. Live uses the direct channel path and omits transport/seek controls.

[qualification/README.md](qualification/README.md) describes isolated HTTPS emulator fixtures, device pairing, native input and private visual captures. The normal APK trusts system CAs. Fixture trust is an explicit debug build option and must not be present in an APK delivered for normal use. Debug-only preview origins do not alter release routing. Production deployment and store publication are separate actions.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
