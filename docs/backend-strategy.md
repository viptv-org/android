# Air playback backend strategy

Air owns the player contract, state machine, capability routing, controls, and
test corpus. Platform engines are replaceable adapters. No engine object crosses
the public common API.

## Decision

| Target | Preferred engine | Fallback | Why |
| --- | --- | --- | --- |
| Android / Android TV | Media3 | optional MPV adapter | Native `MediaCodec`/surface path, lifecycle and DRM integration; MPV is reserved for formats the device Media3 stack rejects. |
| Windows, Linux, macOS desktop | bundled MPV | platform-native adapter only when measured better | Broad Matroska, codec, embedded-track, ASS/SSA, live, and hardware-decoding coverage with one behavioral surface. |
| iOS / tvOS | AVFoundation | optional MPV experiment outside the default artifact | Best power, thermal, PiP, and system integration. Unsupported MKV/audio/subtitle combinations must fail explicitly or use a separately validated remux/MPV route. |
| Browser / Wasm | native video + MSE adapter | Shaka-style adaptive adapter | Browser codec/container support is authoritative. MKV is not promised; HLS/DASH can use MSE where the browser supports it. |

Implemented now: Android/Android TV Media3, Apple AVFoundation, Browser/Wasm,
the common capability router, and the JVM MPV session/track/live engine with a
null-output corpus gate. The JAWT surface remains an internal proof only.

### Desktop fork base — 2026-08-28

Air will use `open-ani/mediamp` revision `4aae5fa` as the fork base for the
bundled desktop MPV runtime and Compose/Skia surface, while retaining Air's
smaller `com.getair.video` contract as the only app-facing API. This is a source
fork with retained history/license, not copied fragments or a second public
player model.

The choice followed a live Linux gate, not repository metadata. The pinned
runtime built FFmpeg 8.0.1, dav1d 1.5.4 and mpv 0.41.0, created a Skiko-shared
GLX producer context and triple-buffered OpenGL ring, and rendered Air's H.264,
HEVC and AV1 Matroska fixtures through the production Compose surface. H.264
also proved two audio tracks, embedded SRT/ASS, pause/play/seek/EOF/replay and
native frame capture. Detailed evidence and unresolved fork work are in
[`mediamp-evaluation-2026-08-28.md`](mediamp-evaluation-2026-08-28.md).

This selects a base, not unconditional support. The fork-side `mediamp-air`
artifact now implements Air's API, maps confirmed audio/subtitle/video tracks,
and converts mpv seekability plus demux-cache ranges into plain-live versus DVR
timelines. It emits Kotlin 2.1 metadata and passed an independent Kotlin 2.1
consumer compile. Linux frame-preview tests still cannot treat headless Xvfb as
a live Skia/GLX environment. Native mpv logs now redact URLs, sensitive headers,
bearer tokens, and media paths before bounded delivery. GitHub Packages must
still aggregate host-built runtimes; and AMD/Intel use `vaapi-copy`, so their
decode-to-GL path is not yet end-to-end zero-copy.

The router probes installed adapters lazily in application priority order, so
an optional native fallback is not loaded when the lightweight platform engine
already fits. It never treats a README claim as a runtime capability. A requirement
such as MKV + AV1 + DTS + ASS can therefore choose MPV while an ordinary H.264
HLS live channel remains on Media3.

## Upstream research snapshot

Research was performed against these exact revisions. Ideas may be reimplemented
behind Air's API only when their license permits it; copied code requires notices
and a provenance note in the commit.

| Project | Revision | License/use | What Air keeps |
| --- | --- | --- | --- |
| [open-ani/mediamp](https://github.com/open-ani/mediamp) | `4aae5fa2956b5c0530704e0cd218aa75502584c6` | Apache-2.0; selected desktop fork base | Orthogonal lifecycle/play-intent/buffering state; bundled FFmpeg/mpv runtime; GLX/D3D/Metal surface ring; explicit surface providers; session-aware events. Air keeps its own public contract. |
| [kdroidFilter/ComposeMediaPlayer](https://github.com/kdroidFilter/ComposeMediaPlayer) | `67ae1dce6ae4924de19bd3b0b96d66c4758dc921` | MIT | Small common control surface, native surfaces, Media3/AVPlayer/Media Foundation/GStreamer seams, external subtitle UX. |
| [Chaintech ComposeMultiplatformMediaPlayer](https://github.com/Chaintech-Network/ComposeMultiplatformMediaPlayer) | `6ba2905779bddeedc56c251ab87d97264de3af92` | Apache-2.0 | Shaka/MSE adaptive-web lessons, HLS quality/audio/caption selection, PiP behavior. Its required system VLC desktop runtime is rejected. |
| [SuvioMedia/KMediaPlayer](https://github.com/SuvioMedia/KMediaPlayer) | `343a965fed4cf4b57e514f58e895d6e5967e472b` | Proprietary/source-visible | Architecture observation only: strict capability evidence, optional extensions, and transactional fallback. No implementation code may be copied or depended on. |

## Boilerplate boundary

A platform adapter supplies only two things:

1. `VideoBackendFactory`: stable ID, runtime probe, player creation.
2. `VideoBackend`: open/control/track commands plus level-triggered backend facts.

Every open receives a monotonically increasing `PlaybackSessionId`, and every
backend event must echo it. Shared state ignores callbacks from replaced or
stopped sessions, eliminating old-player position/end/error races.

Everything else remains shared: source redaction, lifecycle, live-vs-DVR seek
rules, typed track selection, capability matching, fallback decisions, and UI.
Adapters must not implement app controls, persistence, navigation, or their own
parallel public state model.

## Live buffering policy

Air's common contract will expose intent, never backend tuning constants, through three live profiles:
`LowLatency`, `Balanced`, and `Resilient`. Resilient targets an approximately
10-second safety margin where the manifest and engine allow it. The common state
must report live-edge offset and buffered-ahead duration separately because
being ten seconds behind live does not prove that ten seconds of media is ready.

Adapters must use their native live path. Android maps intent to Media3
`MediaItem.LiveConfiguration` and `DefaultLoadControl`, while still honoring HLS
`EXT-X-START`, `PART-HOLD-BACK`, and `HOLD-BACK` when the user has not overridden
latency. MPV uses bounded demux/cache controls, Apple uses AVPlayer's native
forward-buffer and stalling policy, and web uses native/MSE buffering. No Air
adapter owns a second HLS downloader, segment concatenator, or unbounded packet
queue.

A profile change must not recreate the app-facing player or surface. If a
backend cannot safely apply it to an active session, it reports that limitation
and applies the preference on the next open.

Android's resilient preset uses Media3 1.11.0's streaming-only load-control
setters: 10 seconds minimum, 15 seconds maximum, 1 second startup, 5 seconds
after rebuffer, time-priority while Media3 has heap headroom, and a 64 MiB
allocator loading threshold. Low-latency and balanced playback retain the
native default load control. These are configured targets, not evidence that a
particular stream reached them; advanced statistics expose the configured
values beside measured live offset and buffered-ahead duration.

## Acceptance gates

An adapter is not production-supported until it passes:

- the generated H.264, HEVC, and AV1 Matroska corpus;
- two embedded audio tracks and independent audio selection;
- embedded SRT/ASS plus external SRT/VTT/ASS;
- MPEG-TS and HLS event/live playback;
- independent live-offset and buffered-ahead telemetry for low-latency,
  balanced, and resilient profiles;
- a jitter fixture with two-second segment delays, repeated playlist refresh,
  discontinuities, bounded reconnect, and behind-live-window recovery;
- repeated open/stop/open and surface detach/reattach without stale events;
- hardware-decode/render evidence, startup latency, dropped frames, memory, CPU,
  and thermal checks on real target hardware;
- URL/header redaction and cancellation/error mapping tests.

Codec/container support remains a runtime probe result, not a compile-time
promise.

## Surface and presentation contract

Presentation is owned by the app, never the decoder backend. One live player
session must remain usable while its surface is laid out inline, moved and
resized as in-app picture-in-picture, or placed in an optional fullscreen app
layout. A backend must never force fullscreen or create a second playback
window. Controls and arbitrary Compose content must be compositable above the
video in every mode.

The production capability gate therefore requires `supportsMovableSurface`,
`supportsSurfaceReattachment`, and `supportsCompositedOverlays`. Android Media3
offers both the lowest-overhead `SurfaceView` path and a `TextureView` path for
movable/transformable in-app PiP. Desktop MPV must publish a Compose-drawable
frame/texture surface; the JAWT `wid` child-window proof is intentionally
internal because heavyweight native children cannot guarantee Compose overlay
z-order. Apple and Web adapters must use their interop-behind-Compose paths.

Fullscreen, inline, and in-app PiP are UI layout states, not commands on
`VideoPlayer`.
