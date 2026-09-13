# VIPTV Android video

Android TV VIPTV app and Android/Android TV playback contracts backed by AndroidX Media3. `:app` is the native Jetpack Compose Android TV client; the root module is the Android-only playback library it uses. Product controls, focus, screens and playback policy follow the pinned design repository contract.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ and Android TV only.
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

This workspace uses hosted `app-review.yml`; keep local Gradle and emulators stopped because of the owner's memory constraint. CI verifies the core snapshot, builds its host library for JVM tests plus arm64-v8a/armeabi-v7a/x86_64 native libraries, runs the player/app unit suites and produces a debug APK. No cross-repository private token is required to build the pinned source.

On a separate suitably provisioned development machine, use Node 22+, Rust with Android targets, cargo-ndk 4.1.2, NDK 27.2.12479018, JDK 17 and SDK Platform 36. Run `scripts/prepare-core.sh` before Gradle so the host test library and packaged JNI libraries match the generated bindings. Real-device acceptance is recorded separately in TESTING.md.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
