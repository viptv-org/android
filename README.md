# Air Video

Air-owned Kotlin Multiplatform playback contracts for Android, JVM desktop,
iOS/macOS, Windows, Linux, JavaScript, and Wasm. The library deliberately does
not expose backend objects. Applications own controls and policy; backend
adapters own decoding, rendering, and runtime capability probes.

The implementation research covers mediamp, ComposeMediaPlayer,
ComposeMultiplatformMediaPlayer, and KMediaPlayer. Air reimplements useful
permissively licensed ideas behind its own API; KMediaPlayer is proprietary and
is architecture observation only. The exact revisions, engine policy, legal
boundary, and acceptance gates are documented in
[`docs/backend-strategy.md`](docs/backend-strategy.md).

`corpus/generate.sh` creates copyright-free fixtures, and
`benchmark/run-mpv-linux.sh` records the initial Linux MPV engine baseline in an
ignored local report. Wrapper/API mapping and hardware-rendering measurements
remain separate gates.

`DefaultVideoPlayer` is the shared state machine. Backends report level-triggered
facts through `VideoBackend`; the common boundary enforces live seek rejection,
track selection, buffering, lifecycle, typed failures, and redacted sources.
`VideoBackendRouter` adds strict runtime capability selection so an ordinary
stream stays on the lightweight platform engine while unsupported MKV/codec/
track combinations can move to an explicitly installed fallback.

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest
```
