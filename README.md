# VIPTV Android

Native phone and Android TV VIPTV app and playback contracts backed by AndroidX Media3. `:app` is the adaptive Jetpack Compose client; the root module is the Android-only playback library it uses. Product controls, focus, screens and playback policy follow the pinned design repository contract.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ phones and Android TV. Native TV mode selects the remote layout; phones retain touch, system text entry and rotation.
- `:app` device pairing/refresh, profiles, Home/Discover/source picker, exact-source Resume, and Media3 direct playback.
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

`DESIGN_REF` pins AND-035 and the current shared visual system. The generated tokens, local Onest/Bricolage fonts and licensed Lucide assets are checked by `scripts/design-sync.mjs`; never edit generated files. To adopt a new committed design:

```sh
node scripts/design-sync.mjs sync ../design <full-commit-sha>
```

Phone and TV share cards, buttons, sheets, profile/avatar UI and controllers. TV uses a uniformly scaled 1920×1080 frame; phone layouts use native density, font scaling and safe/keyboard insets. Profile selection enters Home immediately after server confirmation. Home publishes saved rows before optional metadata, bounds provider concurrency and reuses loaded rows when returning. Series, source and player navigation retain their originating route. Live uses the direct channel path and omits transport/seek controls.

[qualification/README.md](qualification/README.md) describes isolated HTTPS emulator fixtures, device pairing, native input and private visual captures. The normal APK trusts system CAs. Fixture trust is an explicit debug build option and must not be present in an APK delivered for normal use. Debug-only preview origins do not alter release routing. Production deployment and store publication are separate actions.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
