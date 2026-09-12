# VIPTV Android video

Android TV VIPTV app and Android/Android TV playback contracts backed by AndroidX Media3. `:app` is the native Jetpack Compose Android TV client; the root module is the Android-only playback library it uses. Product controls, focus, screens and playback policy follow the pinned design repository contract.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ and Android TV only.
- `:app` device pairing/refresh, profiles, Home/Discover/source picker, exact-source Resume, and Media3 direct playback.
- Media3 playback, headers, external subtitles, track selection, live/DVR semantics, and runtime capability reporting.
- No desktop, Apple, browser, JavaScript, WebAssembly, MPV, AVFoundation, package publishing, or inherited automation.

## Local validation

Install JDK 17 and Android SDK Platform 36. Use `./gradlew :app:testDebugUnitTest :app:assembleDebug` for the app and `./gradlew testDebugUnitTest` for the playback library. Run connected tests only on an emulator or physical Android/TV device. The Android tooling document in the design repo records that this workspace has no KVM; no emulator or hardware acceptance is claimed.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
