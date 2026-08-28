# Live playback policy and diagnostics

Air exposes three backend-neutral live intents:

| Policy | Android Media3 behavior | Contract meaning |
| --- | --- | --- |
| `LowLatency` | Request a 3-second target live offset | Prefer proximity to the live edge; stalls may increase on ordinary HLS or slow providers. |
| `Balanced` | Do not override Media3 or manifest live configuration | Preserve the protocol and engine defaults. |
| `Resilient` | Request a 10-second target live offset and use Media3 streaming thresholds of 10 seconds minimum, 15 seconds maximum, 1 second startup, and 5 seconds after rebuffer | Prefer a roughly 10-second safety margin where the live window and available throughput permit it. |

These are requests, not guarantees. `PlaybackStatistics.liveEdgeOffsetMillis`
measures how far playback is from the live edge, while
`bufferedAheadMillis` measures media already available after the current
position. A ten-second live offset with two seconds buffered ahead is not
reported as a ten-second buffer.

Android uses `MediaItem.LiveConfiguration` and Media3's `DefaultLoadControl`; it
does not download or concatenate HLS segments in application code. `Balanced`
and `LowLatency` retain Media3's default `LoadControl`. `Balanced` intentionally leaves
`EXT-X-START`, `HOLD-BACK`, `PART-HOLD-BACK`, DASH service descriptions, and
Media3 live defaults authoritative.

The resilient load control prioritizes its 10-second time threshold while
Media3 has heap headroom and uses a 64 MiB allocator loading threshold. That
byte value is not an absolute heap cap: Media3 may load beyond it until the
minimum time is reached, stops early under its own heap-pressure guard, and can
be constrained by the manifest, bitrate, bandwidth, or live window. The player
starts after one second so channel changes are not forced to wait ten seconds;
the reservoir grows natively during playback. After a true rebuffer, Media3
waits for up to five seconds because its documented start rule also caps that
threshold at half the current target live offset.

Crossing between the default and resilient buffer modes replaces only the
internal Media3 player on the next `open`; the app-facing Air player and the
attached video surface remain stable. Opens within the same mode reuse the
native player. The Android backend samples diagnostic state at
2 Hz, outside the main `PlaybackState`, to avoid invalidating controls or
browsing UI. It reports live offset, buffered-ahead duration, bandwidth
estimate, playback speed, dropped video frames, rebuffers, timeline
discontinuities, successful behind-live-window recoveries, and the configured
policy/offset/minimum/maximum/allocator threshold beside the measured values.

## Recovery rules

- Media3's documented `ERROR_CODE_BEHIND_LIVE_WINDOW` path seeks to the native
  default live position and prepares again. One recovery may be in flight; a
  repeated failure before `STATE_READY` becomes a typed recoverable network
  failure rather than a retry loop.
- HLS/DASH segment retries and playlist refresh remain Media3's responsibility.
  Air does not add a second retrying downloader or segment queue.
- Media3-native timeline discontinuities are observed and counted. They are not
  converted into app seeks, player recreation, or seek bars for plain live TV.
- Terminal network errors remain recoverable facts for the future source-aware
  reconnect coordinator. Automatic source reopen/backoff is not implemented in
  the backend because Xtream/Stalker URL refresh and connection limits belong
  above the decoder and must be coordinated per source.

The acceptance gate uses `corpus/live_hls_server.py`: after the resilient margin
is established, a selected segment is delayed by two seconds with deterministic
jitter. The player must return the exact segment bytes, remain in its live
window, and show no increase in the rebuffer counter. The server also supports
one-shot HTTP failure/disconnect and manifest discontinuity scenarios without
adding an application downloader or segment queue. See `corpus/README.md` for
the exact desktop and Android setup.

The harness self-tests its own timing, bytes, bounds, redacted logs, and clean
shutdown. A no-rebuffer outcome is still a measured release result on target
hardware, not a promise made by the preset or a Python test.

### Emulator checkpoint — 2026-08-28

The opt-in Android instrumentation gate ran headlessly on the canonical
`air-tv-api36` AVD with host graphics. The native buffer reached 10,762 ms before
`segment-016.ts` entered its deterministic 2,000 ms ±250 ms delay; the observed
rebuffer delta afterward was zero. Local-loopback startup was 219 ms and the
run reported zero dropped frames. Media3 returned no `currentLiveOffset` for
this synthetic manifest, so the offset remains unknown rather than inferred.

This is one debug emulator run, not release or physical-TV evidence. The
machine-readable result and full limitations are in
[`benchmark/output/media3-android-tv-live-resilient.json`](../benchmark/output/media3-android-tv-live-resilient.json).
The committed instrumentation test skips unless `airLiveFixtureUrl` is supplied;
it verifies the reservoir existed before the delayed request rather than
mistaking the configured target for measured media.

## Evidence

- AndroidX Media3 1.11.0
  [`DefaultLoadControl`](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java)
  documents streaming-only min/max/start/rebuffer thresholds, the allocator
  target, time-over-size behavior, and its heap-headroom guard. The same source
  shows that a live start/rebuffer threshold is capped at half the target live
  offset.
- AndroidX's official
  [live-streaming sample](https://github.com/androidx/media/blob/release/docsamples/src/main/java/androidx/media3/docsamples/exoplayer/LiveStreaming.java)
  documents per-item target offsets and the `seekToDefaultPosition()` plus
  `prepare()` behind-window recovery path.
- [OwnTV](https://github.com/ahXN00/OwnTV) revision
  `0f8724b4951734f043ecdc85ce70311eb3d752f6` was inspected as a public
  functional reference. It independently confirms the useful separation
  between live target offset, startup/rebuffer gates, and measured buffer/drop
  diagnostics. Air retains its own smaller contract and implementation.
- Context7 did not return matching AVFoundation live-buffer documentation from
  its Apple documentation index. Apple policy mapping therefore remains
  unimplemented until official Apple API behavior can be verified on an Apple
  host; the common contract does not claim it today.
