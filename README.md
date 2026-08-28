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
Release performance gates are defined in
[`docs/performance-budgets.md`](docs/performance-budgets.md); they are budgets,
not claims based on wrapper documentation.

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

The selected desktop fork base is `open-ani/mediamp` at `4aae5fa`. Its bundled
Linux runtime and production Compose GLX texture ring built and rendered Air's
H.264, HEVC and AV1 Matroska fixtures on the local RX 7900 XT. Air keeps this
repository's smaller backend-neutral API in front of that fork. See
[`docs/mediamp-evaluation-2026-08-28.md`](docs/mediamp-evaluation-2026-08-28.md)
for the evidence and the Linux preview/redaction/VAAPI-copy gaps that remain.

### Browser / Wasm

`BrowserVideoBackendFactory` owns or accepts one native `HTMLVideoElement`.
The element can move anywhere behind the Compose scene without reopening the
session. The backend maps browser play/pause/buffering/position/seek/end/error
events into Air's session-tagged contract, preserves live/DVR hints, installs
external WebVTT tracks, and derives codec/container support from runtime
`canPlayType` results. Sources requiring private request headers fail before
browser networking; applications must use an authorized URL or a scoped proxy.

Browser/system PiP is reported separately from Air's in-app PiP layout and is
not claimed merely because the surface is movable.

### Apple

`AppleAvFoundationBackendFactory` uses a nonvisual `AVPlayer` and attaches it to
an app-owned `AVPlayerLayer`. The same session and decoder survive when that
layer moves between inline playback, an arbitrary in-app PiP rectangle, and an
optional fullscreen layout. Air still owns controls and never invokes a forced
fullscreen controller.

The adapter maps native ready/failure/end, transport, buffering, position,
seekable live ranges, audio selections, and embedded legible selections into
the shared session-safe contract. It deliberately does not claim Matroska,
external subtitle sidecars, manual video rendition selection, FairPlay setup,
HDR, or system PiP. Those remain measured capability/fallback decisions rather
than AVFoundation assumptions.

```kotlin
val player = AppleAvFoundationBackendFactory().createApplePlayer()
val layer = player.createVideoLayer()

// Put `layer` in any app-owned UIView/NSView layer hierarchy. Moving or resizing
// that host view does not reopen the source.
```

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest testReleaseUnitTest
./scripts/test-mpv-jvm-integration.sh
CHROME_BIN=/path/to/chrome ./gradlew wasmJsBrowserTest
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
