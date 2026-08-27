# Air video repository guidance

- This repository owns Air's backend-neutral Kotlin Multiplatform playback contract. Compose controls and application playback policy do not belong in this module.
- Keep platform types from Media3, AVFoundation/AVKit, mpv, VLC, GStreamer, and browser APIs out of `commonMain` public signatures.
- State is level-triggered `StateFlow`; one-off completion/error/session events use `Flow`. Commands are ordinary functions unless they must await opening or backend work.
- Every backend callback carries the `PlaybackSessionId` supplied to `open`; never accept an unscoped late event from a replaced or stopped source.
- Live, seekable-live, and on-demand timelines are distinct. A plain live timeline must never expose a seek bar or accept seek commands.
- Model audio, subtitle, and video tracks independently. Selection must return a typed result rather than silently succeeding.
- Capabilities are runtime backend/device facts, not README promises. Unsupported containers/codecs/tracks fail with typed errors and a useful fallback recommendation.
- Sources, headers, licenses, cookies, and credential-bearing URLs must never appear in `toString`, logs, analytics, or error messages.
- Backends may adapt permissively licensed code from ComposeMediaPlayer (MIT) and mediamp (Apache-2.0) with required notices. Do not copy or depend on proprietary KMediaPlayer additions.
- Record the exact upstream revision and provenance for adapted code. Architecture observations alone do not justify copying source; `docs/backend-strategy.md` is the current research ledger.
- Run `./gradlew jvmTest jsNodeTest wasmJsNodeTest` for portable contract changes. Apple, Windows, Android-device, and physical playback validation remain separate gates.
