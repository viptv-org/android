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

Implemented now: the Android/Android TV Media3 adapter and common capability
router. Desktop MPV, Apple, and browser adapters remain gated work.

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
| [open-ani/mediamp](https://github.com/open-ani/mediamp) | `4aae5fa2956b5c0530704e0cd218aa75502584c6` | Apache-2.0 | Orthogonal lifecycle/play-intent/buffering state; Media3, MPV, AVKit, browser modules; explicit surface providers; session-aware backend events. |
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

## Acceptance gates

An adapter is not production-supported until it passes:

- the generated H.264, HEVC, and AV1 Matroska corpus;
- two embedded audio tracks and independent audio selection;
- embedded SRT/ASS plus external SRT/VTT/ASS;
- MPEG-TS and HLS event/live playback;
- repeated open/stop/open and surface detach/reattach without stale events;
- hardware-decode/render evidence, startup latency, dropped frames, memory, CPU,
  and thermal checks on real target hardware;
- URL/header redaction and cancellation/error mapping tests.

Codec/container support remains a runtime probe result, not a compile-time
promise.
