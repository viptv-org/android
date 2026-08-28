# Air Video Kotlin Multiplatform guidance

This repository owns Air's backend-neutral Kotlin Multiplatform player. The
historical TypeScript/React `@get-air/video` implementation lives separately at
`get-air/video`; use it only as a behavioral reference and never add npm source,
manifests, locks, or publishing workflows here.

## Core boundaries

- Compose controls and application playback policy do not belong in this core library.
- Keep Media3, AVFoundation/AVKit, mpv, VLC, GStreamer, and browser platform types out of `commonMain` public signatures.
- State is level-triggered `StateFlow`; one-off completion, error, and session events use `Flow`.
- Every backend callback carries the `PlaybackSessionId` supplied to `open`; stale callbacks from replaced sessions are rejected.
- Live, seekable-live, and on-demand timelines remain distinct. Non-seekable live media never exposes or accepts seeking.
- Model audio, subtitle, and video tracks independently. Track selection returns a typed result.
- Capabilities are runtime backend/device facts, not README promises.
- Sources, headers, licenses, cookies, local paths, and credential-bearing URLs never appear in logs, errors, analytics, or `toString()`.

## Platform expectations

- Android uses `AndroidMedia3BackendFactory`; preserve request headers, external subtitles, native live policy, and runtime capability mapping.
- Apple uses one nonvisual `AVPlayer` plus an app-owned movable `AVPlayerLayer`; never force fullscreen as the general surface.
- MPV/JAWT classes are internal conformance harnesses. Production desktop surface work remains in the `air-tv/mediamp` fork.
- Browser/Wasm claims require `wasmJsBrowserTest` in a real browser. Node compilation is not DOM playback evidence.
- Never use Robot, PipeWire, or screenshot/screencast portals for automated player verification.

## Validation and releases

- JDK 17 is canonical. Run `./gradlew jvmTest jsNodeTest wasmJsNodeTest` for portable changes and the relevant host/device gates for platform changes.
- KMP artifacts publish only through `air-tv/video` GitHub Packages workflows.
- This repository must never publish the historical `@get-air/video` npm package.
- Never add npm, Maven, GitHub, signing, or device credentials to the worktree.
