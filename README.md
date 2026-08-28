# Air Video

Air's video repository is migrating the existing `@get-air/video`
TypeScript/React controller into an Air-owned Kotlin Multiplatform player for
Android, JVM desktop, iOS/macOS, Windows, Linux, JavaScript, and Wasm. The
legacy implementation and fixtures remain in this history as the behavioral
oracle until KMP parity is proven.

The KMP API deliberately exposes no backend objects. Applications own controls,
focus, in-app picture-in-picture layout, optional fullscreen layout, and
playback policy. Replaceable adapters own decoding, rendering, runtime
capability probes, and platform surface attachment.

The exact upstream research revisions and engine gates are documented in
[`docs/backend-strategy.md`](docs/backend-strategy.md). The movable,
overlay-capable surface design is in
[`docs/surface-architecture.md`](docs/surface-architecture.md).

## Kotlin Multiplatform status

`DefaultVideoPlayer` is the shared session-safe state machine. It enforces live
seek rejection, independent audio/subtitle/video selection, buffering,
lifecycle, typed failures, stale-event rejection, and redacted sources.
`VideoBackendRouter` selects adapters from measured runtime capabilities.

### Android / Android TV

`AndroidMedia3BackendFactory` uses Media3 1.11.0 with HLS and DASH, runtime
`MediaCodec`/DRM probes, request headers, external subtitles, independent track
overrides, plain-live/DVR timelines, and typed MPV fallback errors. Media3 types
remain in `androidMain`.

```kotlin
val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()

AndroidView(
    factory = { TextureView(it).also(player::attach) },
    modifier = Modifier.fillMaxSize(),
)

player.open(
    PlaybackSource(
        uri = streamUrl,
        mimeType = "application/x-mpegURL",
        headers = streamHeaders,
        kindHint = PlaybackKind.Live,
    ),
)
```

Use `SurfaceView` for the lowest-overhead stable full-size path or
`TextureView` when one session must move, resize, clip, or sit beneath Compose
overlays. Fullscreen and in-app PiP are app layouts, never forced backend modes.
A plain live timeline never exposes a seek bar.

### Desktop MPV checkpoints

The internal JVM MPV JSON-IPC engine passes the generated H.264/HEVC/AV1
Matroska, MPEG-TS, HLS event/live, multitrack, and external SRT/VTT/ASS corpus.
The JAWT/`wid` experiment proves native rendering but is intentionally not the
production surface because heavyweight child windows cannot guarantee Compose
overlay z-order.

The production desktop design is a bounded triple-buffered GPU texture stream
from libmpv into the normal Compose/Skia scene, with CPU readback only as an
explicit degraded fallback.

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest testReleaseUnitTest
./scripts/test-mpv-jvm-integration.sh
```

## Legacy TypeScript/React reference

The existing `@get-air/video` package remains buildable during migration. It
owns explicit HTML, Tizen AVPlay, webOS/Vizio, Promise/Effect, and React/TV
controller behavior. It does not automatically select client decoders or
transcoders.

```ts
import { createVideoClient } from '@get-air/video'

const client = createVideoClient({ adapters })
const player = await client.attach(video, {
  source,
  backend: ['html', 'tauri', 'transcode'],
})
```

Legacy backends report live state and moving DVR bounds through `player.media`.
Non-seekable live media rejects seeking; React/TV controls expose a Go Live
action only for seekable live windows.

```bash
npm ci
npm run ci
```

[Legacy API](docs/api.md) · [Legacy platforms](docs/platforms.md) ·
[Versioning](VERSIONING.md) · [Contributing](CONTRIBUTING.md)

KMP releases publish Maven artifacts to GitHub Packages. Legacy npm releases
use GitHub Actions trusted publishing with provenance. No local publishing token
belongs in this repository.
