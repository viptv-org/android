# Air cross-platform video surface architecture

The player session and the visual presentation are separate. A decoder backend
owns media, timing, tracks, and frame production. The app owns where the surface
is placed. Inline playback, movable/resizable in-app picture-in-picture, and
optional fullscreen are layouts of the same session—not backend modes.

The app-level shape on every Compose target is conceptually:

```kotlin
Box(modifier) {
    AirVideoSurface(player, Modifier.matchParentSize())
    PlayerOverlay(player, Modifier.matchParentSize())
}
```

Moving that `Box`, changing its size, clipping it, or placing it in a floating
layout must not reopen media. The backend must never force fullscreen or create
a second playback window.

## What the public implementations teach us

- mediamp's desktop MPV path demonstrates the fast shape: libmpv renders into a
  triple-buffered ring of platform GPU textures; Skia consumes the newest
  published texture during the normal Compose draw. There is no per-frame CPU
  copy, and the video participates in Compose z-order.
- KMediaPlayer's desktop texture host demonstrates that the application must own
  the window and fullscreen state while the video producer submits rotating GPU
  buffers into the same scene as overlays. Its software libmpv fallback also
  shows the necessary compatibility escape hatch.
- ComposeMediaPlayer's appsink/Skia paths demonstrate a portable frame-copy
  fallback and the importance of keeping old frames visible during resize, but
  continuous 4K copies are not Air's preferred path.
- Chaintech's surface is adequate for ordinary platform playback, but relying on
  an installed desktop VLC and heavyweight native views does not meet Air's
  movable overlay contract.

Air re-derives these ideas behind its own session and capability contracts.

## Platform routes

### Android and Android TV

- `SurfaceView`: lowest compositor overhead for stable full-size playback.
- `TextureView`: movable, transformable, clip-safe surface for in-app PiP and
  Compose overlays.
- Both attach to the same Media3 player. Surface changes do not reload media.

### Desktop JVM

- Production: libmpv render context publishes a triple-buffered GPU texture
  stream. Linux uses a shared GL/Vulkan-compatible texture route, Windows uses a
  shared D3D texture, and macOS uses IOSurface/Metal.
- Compose/Skia draws the newest completed texture as a normal scene node, then
  draws controls above it.
- A bounded software BGR0/BGRA frame path is retained only when GPU sharing is
  unavailable. It must report degraded capability and use frame dropping rather
  than unbounded queues.
- The JAWT `wid` experiment proves native mpv rendering but is not production:
  heavyweight child windows cannot guarantee Compose overlay z-order.

### Apple

- The default AVFoundation route uses an `AVPlayerLayer`/UIKit interop view kept
  behind the Compose scene, with bounds controlled by the composable.
- An MPV fallback, if shipped, must publish IOSurface/Metal textures into the
  same Compose scene instead of opening another window.

### Web/Wasm

- The default video element is positioned behind the Compose canvas at the
  composable's exact bounds; controls remain in Compose.
- Adaptive playback may use MSE/Shaka-style logic. A future WebCodecs/WebGPU path
  can publish textures when browser-managed video cannot meet transforms or
  color requirements.

## Performance invariants

- At most three producer frames may be in flight. The newest completed frame
  wins; stale frames are dropped.
- Playback threads never wait for a Compose draw, and Compose never waits for a
  decoder frame.
- Surface resize is debounced; the previous frame remains visible until the new
  ring publishes.
- GPU texture lifetime uses explicit publish/retire/ack generations so resize,
  reattachment, and close cannot free a texture still used by Skia.
- Hardware decode and hardware presentation are separate capability facts.
- CPU readback is a reported fallback, never silently described as the fast
  path.
- No screen-capture or PipeWire portal is part of rendering or verification.

## Capability gate

A backend can serve the app's general player surface only when it reports:

- `supportsMovableSurface`
- `supportsSurfaceReattachment`
- `supportsCompositedOverlays`

Specialized non-composited outputs may still be chosen for a fixed full-size
surface, but the router must not select them for in-app PiP or overlay-required
layouts.
