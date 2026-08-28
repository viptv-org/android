# Air video repository guidance

This repository is migrating the existing `@get-air/video` TypeScript/React
package into Air's backend-neutral Kotlin Multiplatform player. Keep both
implementations independently testable while the TypeScript fixtures remain the
behavioral oracle. New product behavior belongs in Kotlin unless it is needed to
preserve or clarify a legacy contract test.

## Kotlin Multiplatform player

- Compose controls and application playback policy do not belong in the core player module.
- Keep platform types from Media3, AVFoundation/AVKit, mpv, VLC, GStreamer, and browser APIs out of `commonMain` public signatures.
- The Android default is `AndroidMedia3BackendFactory`; keep its surface owner in `androidMain`, preserve per-source headers and external subtitles, and run `testReleaseUnitTest` for adapter mapping changes.
- `MpvSessionBackend` and the JAWT/`wid` surface are conformance harnesses, not the production desktop surface. Keep them internal and never claim composited desktop playback from their result; run the MPV scripts after event/track/surface changes.
- MPV surface tests must use MPV diagnostics and app-owned window state. Never use `Robot.createScreenCapture`, PipeWire, or desktop screenshot/screencast portals; automated player tests must not request screen-sharing permission.
- State is level-triggered `StateFlow`; one-off completion/error/session events use `Flow`. Commands are ordinary functions unless they must await opening or backend work.
- Every backend callback carries the `PlaybackSessionId` supplied to `open`; never accept an unscoped late event from a replaced or stopped source.
- Live, seekable-live, and on-demand timelines are distinct. A plain live timeline must never expose a seek bar or accept seek commands.
- Live policy is intent, not a buffer claim. Keep target live offset and measured buffered-ahead duration independent; Android's ten-second Resilient offset is not proven protection until a documented native LoadControl mapping passes `corpus/live_hls_server.py` on target hardware.
- HLS/DASH segment retry and playlist refresh remain native backend responsibilities. Never add an Air-owned segment downloader/queue. Use the deterministic impairment harness for delay, jitter, one-shot faults, and discontinuity gates.
- Model audio, subtitle, and video tracks independently. Selection must return a typed result rather than silently succeeding.
- Capabilities are runtime backend/device facts, not README promises. Unsupported containers/codecs/tracks fail with typed errors and a useful fallback recommendation.
- Sources, headers, licenses, cookies, and credential-bearing URLs must never appear in `toString`, logs, analytics, or error messages.
- Read public player implementations for architecture and failure modes, then independently implement Air's contracts. Record exact upstream revisions in `docs/backend-strategy.md`; do not paste implementation code.
- The desktop native/surface fork base is mediamp `4aae5fa`; keep its source history and Apache notice in the fork instead of transplanting files here. Air's API remains the only app API. The optional fork-side `mediamp-air` artifact implements this repository's contracts and emits Kotlin 2.1 metadata; mediamp implementation types remain runtime-only. Native diagnostics are sanitized before delivery. Remaining fork work is the Linux hosted/live test split, GitHub Packages runtime aggregation, and measured AMD/Intel DMA-BUF work.
- Browser/Wasm behavior requires `wasmJsBrowserTest` with a real headless browser. Node compilation/tests are not evidence for DOM media behavior, and `canPlayType` results remain runtime facts.
- Apple playback uses one nonvisual `AVPlayer` plus an app-owned `AVPlayerLayer`; moving, clipping, or resizing that layer must not replace the player item. Do not use `AVPlayerViewController` as the general surface or force fullscreen. Apple CI compilation is not physical-device evidence for codecs, HDR, external displays, PiP, or power.
- Run `./gradlew jvmTest jsNodeTest wasmJsNodeTest` for portable changes. Apple, Windows, Android-device, and physical playback validation remain separate gates.

## Legacy TypeScript reference

- The product name is **Air**; `@get-air` is the npm scope.
- Before changing or debugging Effect code, read `.agents/skills/effect-best-practices/SKILL.md` completely.
- The legacy package is DOM/TV-first and owns HTML, Tizen AVPlay, webOS/Vizio behavior, React controls, and its Promise/Effect façades.
- Promise and Effect surfaces must continue delegating to one implementation while parity fixtures are used by the migration.
- Preserve typed adapter errors, cancellation, live/DVR bounds, explicit fallback ordering, and TV focus behavior.
- Do not add new native/Tauri engines to the legacy JS package; native playback is owned by the KMP platform adapters.
- Install with the frozen npm lockfile and run the focused checks in `CONTRIBUTING.md` when legacy source changes.

## Releases

- Before release work, read `.agents/skills/air-package-publishing/SKILL.md` completely.
- KMP artifacts publish to GitHub Packages through release workflows.
- The legacy npm package may publish only from the restored `get-air/video` repository. This Kotlin-owned `air-tv/video` repository must never publish `@get-air/video` to npm.
- Never add npm, Maven, GitHub, signing, or device credentials to files or workflows.
