# Air player performance budgets

These are release gates, not current claims. Measurements use release builds,
the same corpus, and target-class hardware. Report median, p95, device, output
mode, codec, resolution, backend, and whether decoding/presentation were
hardware accelerated.

## Interaction and presentation

| Metric | Budget |
| --- | --- |
| D-pad command to visible control response | p95 ≤ 50 ms |
| Overlay animation frame time at 60 Hz | p95 ≤ 16.7 ms; p99 ≤ 25 ms |
| Move/resize inline surface into in-app PiP | no media reopen, no decoder restart, ≤ 1 missed display frame |
| Enter/leave optional app fullscreen | no media reopen and position discontinuity < 1 frame |
| Surface reattachment | first resumed frame ≤ 100 ms after the new surface is ready |

## Playback startup

| Source | First decoded/presented frame |
| --- | --- |
| Local H.264 fixture | p50 ≤ 250 ms; p95 ≤ 500 ms |
| Local HEVC/AV1 fixture | p50 ≤ 400 ms; p95 ≤ 800 ms |
| LAN MPEG-TS live | p50 ≤ 500 ms; p95 ≤ 1,000 ms |
| Internet HLS, excluding DNS/TLS/server time | player overhead p95 ≤ 300 ms |

The timer starts when `open` enters the backend and ends only when the first
frame is confirmed presented—not merely when metadata or tracks are parsed.

## Steady-state playback

- 1080p60 hardware route: dropped/repeated frames < 0.25% over 30 minutes.
- 4K60 hardware route: dropped/repeated frames < 0.5% over 30 minutes.
- Audio/video drift stays within ±20 ms after seek, track switch, and one hour of
  continuous playback.
- Track switching begins audible/visible output within 500 ms and does not
  recreate the player session.
- A live reconnect uses bounded exponential backoff, never grows an unbounded
  queue, and restores the surface without exposing a seek bar for plain live.

## Memory and copies

- GPU presentation uses exactly three producer slots. The newest completed
  frame wins; stale unpublished frames are dropped.
- The preferred desktop path performs zero per-frame CPU pixel copies.
- Android `SurfaceView` remains the lowest-overhead path; `TextureView` is used
  when movable compositing is required and its cost is measured separately.
- CPU fallback is explicitly reported as degraded, defaults to at most 1080p30,
  and keeps at most two CPU frames alive.
- No decoded-frame, subtitle-packet, network, or event queue is unbounded.
- Closing a player releases native textures/decoders and returns within 500 ms.

## Capability truthfulness

Hardware decode, GPU presentation, HDR output, audio passthrough, and composited
overlay support are independent runtime facts. A backend fails the gate if it
reports any one based only on source recognition, decoder creation, or a README
claim.

## Required reports

Each production backend emits an opt-in local benchmark report containing only
non-sensitive counters: engine/version, device/OS, source fixture ID, dimensions,
codec/container, startup stages, frame/drop counts, memory, CPU/GPU time,
surface transitions, and confirmed capabilities. URLs, headers, tokens, account
IDs, titles, and provider data are forbidden.
