# Air Video

Air-owned Kotlin Multiplatform playback contracts for Android, JVM desktop,
iOS/macOS, Windows, Linux, JavaScript, and Wasm. The library deliberately does
not expose backend objects. Applications own controls and policy; backend
adapters own decoding, rendering, and runtime capability probes.

The first backend benchmark is `open-ani/mediamp` (Apache-2.0), with selected
MIT ideas from `kdroidFilter/ComposeMediaPlayer`. No backend is considered
supported until it passes Air's media corpus on real target hardware.

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest
```
