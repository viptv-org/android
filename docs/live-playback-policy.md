# Live playback policy and diagnostics

Air exposes three backend-neutral live intents:

| Policy | Android Media3 behavior | Contract meaning |
| --- | --- | --- |
| `LowLatency` | Request a 3-second target live offset | Prefer proximity to the live edge; stalls may increase on ordinary HLS or slow providers. |
| `Balanced` | Do not override Media3 or manifest live configuration | Preserve the protocol and engine defaults. |
| `Resilient` | Request a 10-second target live offset | Prefer a roughly 10-second safety margin where the live window permits it. |

These are requests, not guarantees. `PlaybackStatistics.liveEdgeOffsetMillis`
measures how far playback is from the live edge, while
`bufferedAheadMillis` measures media already available after the current
position. A ten-second live offset with two seconds buffered ahead is not
reported as a ten-second buffer.

Android uses `MediaItem.LiveConfiguration`; it does not download or concatenate
HLS segments in application code. `Balanced` intentionally leaves
`EXT-X-START`, `HOLD-BACK`, `PART-HOLD-BACK`, DASH service descriptions, and
Media3 defaults authoritative. The Android backend samples diagnostic state at
2 Hz, outside the main `PlaybackState`, to avoid invalidating controls or
browsing UI. It reports live offset, buffered-ahead duration, bandwidth
estimate, playback speed, dropped video frames, rebuffers, timeline
discontinuities, and successful behind-live-window recoveries.

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

The acceptance gate remains the jitter corpus: after the resilient margin is
established, a two-second delayed segment must not cause a rebuffer. That is a
measured release result on target hardware, not a promise made by the preset.

## Evidence

- AndroidX Media3 1.11 API and official live sample document per-item target
  offsets and the `seekToDefaultPosition()` plus `prepare()` behind-window
  recovery path.
- [OwnTV](https://github.com/ahXN00/OwnTV) revision
  `0f8724b4951734f043ecdc85ce70311eb3d752f6` was inspected as a public
  functional reference. It independently confirms the useful separation
  between live target offset, startup/rebuffer gates, and measured buffer/drop
  diagnostics. Air retains its own smaller contract and implementation.
- Context7 did not return matching AVFoundation live-buffer documentation from
  its Apple documentation index. Apple policy mapping therefore remains
  unimplemented until official Apple API behavior can be verified on an Apple
  host; the common contract does not claim it today.
