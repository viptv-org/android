# mediamp desktop evaluation — 2026-08-28

## Scope

Evaluated upstream `open-ani/mediamp` at
`4aae5fa2956b5c0530704e0cd218aa75502584c6` on Linux x86_64 with an AMD Radeon
RX 7900 XT. This is evidence for that host only. It is not Windows, Apple,
physical-TV, HDR, power, or packaged-release evidence.

The checkout included its pinned submodules:

- dav1d `54706fc6bc0cdecab7e9593974a4039cc038fca7`
- FFmpeg `449453a9713aa1af31a12e148fb0caba80ed02d7`
- mpv `41f6a645068483470267271e1d09966ca3b9f413`

## Reproduction

The host needs Meson, Ninja, NASM, pkg-config, OpenSSL development files, X11/
GLX development files, VAAPI development files, and `ffnvcodec-headers`.

```bash
git clone --recursive https://github.com/open-ani/mediamp.git
git checkout 4aae5fa2956b5c0530704e0cd218aa75502584c6
./gradlew :mediamp-mpv:mpvAssembleLinuxX64
./gradlew :mediamp-mpv-demo:runD3D11 \
  -Pvideo=/path/to/air/video/corpus/output/h264-multitrack.mkv \
  -PdemoScript=smoke \
  -PscreenshotDir=/tmp/mediamp-smoke
```

Repeat the live demo with `hevc.mkv` and `av1.mkv`.

## Evidence

- The native assembly completed and emitted a bundled ELF closure with local
  `$ORIGIN` runpaths rather than depending on a user-installed VLC or mpv.
- The runtime enabled Matroska, MPEG-TS, HLS, H.264, HEVC, AV1/dav1d, AAC,
  Dolby/DTS families, libass subtitle formats, NVDEC and VAAPI.
- The live Compose demo created a GLX producer context sharing Skiko's context,
  allocated a 1270×770 OpenGL ring, and logged `rendering ... via OpenGL/GLX
  surface` before media reached Ready.
- H.264, HEVC and AV1 Matroska each produced repeated non-empty native frame
  captures and completed playback/replay. H.264 enumerated two audio tracks and
  embedded SubRip/ASS tracks; its captures showed the selected embedded subtitle.
- The scripted H.264 and HEVC runs exercised Ready, pause, play, seek completion,
  EOF, `MediaEnded`, and replay without replacing the Compose surface.
- On this AMD host mpv selected VAAPI decoding with `vaapi-copy`. The Skia
  presentation ring is GPU-shared, but decoded frames cross system memory before
  OpenGL upload. Do not label this end-to-end zero-copy.

## Upstream gaps Air must own

- Linux-required smoke/preview tests hard-code Mac/Windows. When Linux was
  enabled in a disposable checkout, headless smoke creation failed because GLX
  correctly requires a live Skiko environment; frame preview then suspended
  without a timeout. Air's fork needs a live-window Linux integration test and
  a bounded preview failure/fallback path.
- Demo event rendering includes the full `UriMediaData` URI. Air must redact
  source URLs/headers in every diagnostic and adapter boundary.
- Air still needs explicit video-track/quality selection and live/DVR window
  capability mapping into `PlayerCapabilities`.
- The surface uses reflective access to Skiko internals and suppresses invisible
  API warnings. The fork must pin/test Compose/Skiko upgrades and fail capability
  probing cleanly when the reflection layout changes.
- AMD/Intel direct DMA-BUF/EGL import remains a measured future optimization;
  `vaapi-copy` is the honest current capability.

## Decision

Fork the full mediamp repository/history for the native build and surface work,
but expose it only through Air's backend adapter. Do not transplant surface files
into this repository, and do not expose mediamp's backend implementation object to
application code.
