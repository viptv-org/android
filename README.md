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
The movable, overlay-capable surface design for inline playback, in-app PiP,
and optional app-owned fullscreen is documented in
[`docs/surface-architecture.md`](docs/surface-architecture.md).

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

## Android / Android TV

`AndroidMedia3BackendFactory` is the first real adapter. It uses Media3 1.11.0
with HLS and DASH modules, runtime `MediaCodec`/DRM probes, source request
headers, external subtitles, independent audio/text/video track overrides,
plain-live versus DVR timelines, and typed fallback errors. Media3 classes stay
inside `androidMain`.

```kotlin
val factory = AndroidMedia3BackendFactory(context)
val player = factory.createAndroidPlayer()

AndroidView(
    factory = { SurfaceView(it).also(player::attach) },
    modifier = Modifier.fillMaxSize(),
)

player.open(
    PlaybackSource(
        uri = streamUrl,
        mimeType = "application/x-mpegURL",
        headers = streamHeaders,
    ),
)
```

The application owns the Compose controls and surface. Use `SurfaceView` for a
stable lowest-overhead full-size path or `TextureView` when the same session must
move, resize, clip, or sit under Compose overlays for in-app PiP. Call
`detachSurface()` when removing it and `close()` when the player is no longer
needed. A plain live timeline never exposes a seek bar.

## Desktop MPV checkpoint

The JVM MPV JSON-IPC session engine is implemented and exercised headlessly
against the generated corpus through `scripts/test-mpv-jvm-integration.sh`. It
opens H.264, HEVC, and AV1 Matroska, MPEG-TS, HLS event/live, discovers and
switches embedded tracks, attaches external SRT/VTT/ASS subtitles, preserves
source headers without logging them, and honors explicit live/DVR hints.

This is deliberately not exported as the production desktop factory yet. It
uses null audio/video outputs to validate session semantics. Desktop playback
will be advertised only after the native embedded surface and hardware-render
path uses the same engine and passes the rendered corpus gate.

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest testReleaseUnitTest
./scripts/test-mpv-jvm-integration.sh
```
