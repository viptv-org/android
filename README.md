# VIPTV Android video

Android and Android TV playback contracts backed by AndroidX Media3. This is a library, not the VIPTV application: product controls, focus, screens, and playback policy belong to the platform application and must follow the design repository.

The initial import was derived from `air-tv/video` at `57551ec48d63c81d407e098214611140230739f4`. Upstream history and the included Apache-2.0 and MIT license texts are retained.

## Scope

- Android API 24+ and Android TV only.
- Media3 playback, headers, external subtitles, track selection, live/DVR semantics, and runtime capability reporting.
- No desktop, Apple, browser, JavaScript, WebAssembly, MPV, AVFoundation, package publishing, or inherited automation.

## Local validation

Install JDK 17 and Android SDK Platform 36. Use `./gradlew testDebugUnitTest` for Android unit tests, and run connected tests only on an emulator or physical Android/TV device.

## License

This imported work remains available under Apache-2.0 or MIT, at the consumer's option. See `LICENSE-APACHE` and `LICENSE-MIT`.
