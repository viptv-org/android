# Android and Android TV playback contract

This document specifies the playback library in `src/` (package `org.viptv.video`): what it
promises to the `:app` module and to any other embedding Android code. It is derived from the
implementation and its tests; where they disagree, the code is the bug.

## Scope and ownership

- **This library owns:** adapting AndroidX Media3 (ExoPlayer, HLS, DASH) to one session-scoped
  `VideoPlayer`; source opening; the status state machine; timeline/seek facts; track lists and
  selection; subtitle sideloading and preferences; live/DVR policy; runtime capability reporting;
  typed errors; redaction.
- **Not owned here:** screens, focus, remote keys, overlays, wording, resume prompts, next-episode
  policy, live-channel chrome and every other product UX decision. Those come from
  [`viptv-org/design`](https://github.com/viptv-org/design) at the commit pinned in `DESIGN_REF`
  (mirrored under `design-contract/`). Shared non-UI rules (source identity, resume/progress,
  continuation) come from `viptv-org/core` at `CORE_REF`. Source discovery, delivery mode,
  remux/transcode and lease lifetime are owned by the backend and orchestrated by `:app`.
- **Platform boundary:** Android API 24+ phones and Android TV only. No Apple, desktop, browser,
  WebAssembly, JavaScript or MPV implementation lives here. Public types contain no Media3 types;
  only the Android player subtype exposes Android view types (`SurfaceView`, `TextureView`).

## Public API

| Type | Contract |
|---|---|
| `PlaybackSource` | `uri`, optional `mimeType`, `headers`, `title`, `externalSubtitles`, `kindHint`, `options`, `startPositionMillis`. `headers` and `externalSubtitles` are copied at construction, so later caller mutation cannot change an open session. Not a data class: no `equals`/`copy` that could spread a URI. |
| `PlaybackOptions` | `livePolicy` (default `Balanced`), `preferredAudioLanguage`, `preferredSubtitleLanguage`, `subtitlesEnabled` (`null` = leave Media3's default). |
| `ExternalSubtitleSource` | Sideloaded text subtitle: `id`, `uri`, `mimeType`, `language`, `label`, `isDefault`, `isForced`. |
| `VideoPlayer` | Level-triggered `StateFlow`s: `state`, `capabilities`, `audioTracks`, `subtitleTracks`, `videoTracks`, `statistics`, `subtitleCues`. One-off `events`. Commands: `open` (suspending), `play`, `pause`, `seekTo`, `selectAudioTrack`/`selectSubtitleTrack`/`selectVideoTrack`, `stop`, `close`. |
| `VideoBackendFactory` | Stable lowercase `id`, `probe()` returning runtime capabilities, `create()`. |
| `VideoBackendRouter` | Probes factories lazily in caller priority order, caches probes (`invalidateProbes`/`refreshProbes` re-probe), and returns `BackendSelection.Selected` for the first backend with no `BackendRejection`, otherwise `Unavailable` with every candidate's rejections. A throwing probe becomes `ProbeFailed`; cancellation propagates. IDs must be unique and match `[a-z0-9][a-z0-9._-]{0,63}`. |
| `AndroidMedia3BackendFactory` | `id = "media3"`. `openTimeoutMillis` (default 20 000) and `AndroidMedia3ResilientBufferConfig`. `createAndroidPlayer()` builds on the main thread (blocking up to 10 s when called elsewhere). |
| `AndroidMedia3VideoPlayer` | `VideoPlayer` plus `attach(SurfaceView)`, `attach(TextureView)`, `detachSurface()`. State is published on `Dispatchers.Main.immediate`. |

`PlaybackState` carries `status`, `playWhenReady`, `isPlaying`, `isBuffering`, `positionMillis`,
`bufferedPositionMillis`, `timeline`, the three selected track IDs and `error`.

`events` (`Ended`, `SeekCompleted`, `Failed`) has no replay and a small buffer: a late subscriber
misses earlier events and an overflowing event is dropped. `state` is authoritative; events are
notifications only.

## Status state machine

```
Idle --open--> Opening --native READY--> Ready --native ENDED--> Ended
                  |                        |                        |
                  +--failure--> Error <----+----playback failure----+
any non-Released --stop--> Idle        any --close--> Released (terminal)
```

- `Idle` is the initial state and the state after `stop()`.
- `open()` publishes `Opening` (`isBuffering = true`, requested `playWhenReady`, statistics reset),
  then `Ready` with timeline, tracks and selections once Media3 first reaches `STATE_READY`.
- Open failure publishes `Error` with the typed `PlaybackError`, emits `PlaybackEvent.Failed`, and
  rethrows. A `PlaybackFailure` carries its typed error; any other throwable becomes
  `Internal`, non-recoverable.
- A failure after `Ready` publishes `Error` (`isPlaying = false`) and emits `Failed`.
- Native end publishes `Ended` (`isPlaying = playWhenReady = false`) and emits `PlaybackEvent.Ended`.
- `play`, `pause`, `seekTo` and track selection act only in `Ready` or `Ended`; otherwise they
  are ignored (`seekTo` returns `false`, selection returns `NotSupported`).
- `Released` is terminal: `open()` throws `IllegalStateException`, every command is a no-op.

**`stop` vs `close`:** `stop()` ends the current session, cancels a pending open, stops and clears
the native media, empties tracks and statistics and returns to `Idle`; the player and its surface
attachment remain reusable. `close()` additionally releases the ExoPlayer and its video output,
cancels internal sampling, and moves to `Released`. Callers close exactly once, when the player's
owner is destroyed.

## Timeline and seeking

`PlaybackTimeline(kind, durationMillis, seekableRange, liveEdgeMillis)` enforces:

- `Live` never has a `seekableRange` (construction fails).
- `OnDemand` always has a `durationMillis` (construction fails otherwise); durations are `>= 0`.
- `SeekableRange` has `0 <= startMillis <= endMillis`.
- `canSeek = kind != Live && (durationMillis != null || seekableRange != null)`;
  `showSeekBar == canSeek`. A `SeekableLive` window that is not yet known cannot seek.

Media3 mapping (`media3Timeline`): an explicit `kindHint` wins over the manifest — a `Live` hint
stays non-seekable even when Media3 reports a seekable window. Without a hint, `isLive && isSeekable`
is `SeekableLive` with range `[0, windowDuration]`, `isLive` alone is `Live`, anything else is
`OnDemand` (duration `0` when unknown). An empty timeline is `OnDemand` with duration `0`.

Live and seekable-live positions and ranges are relative to the current native window. An
`OnDemand` hint uses a session clock instead: the window's `positionInFirstPeriodMs` is added to
native positions and ranges and subtracted on seek, so a rolling server-managed VOD HLS window does
not move the reported position. `startPositionMillis` is applied only with an `OnDemand` hint;
otherwise Media3 starts at its default (live edge) position.

`seekTo` clamps to the seekable range, else to `[0, duration]`, sends the native seek, updates the
reported position optimistically and returns `true`. `PlaybackEvent.SeekCompleted` follows only for
seeks requested through this API.

## Tracks

- Audio, subtitle and video tracks are independent lists. Tracks Media3 reports as unsupported on
  the device are omitted.
- Track IDs are opaque strings valid only for the current session and track snapshot; callers never
  parse or persist them.
- Labels fall back from the stream label to language to `"<Type> <n>"`; video labels prefer a
  non-blank stream label, then `<height>p`, then codec.

`TrackSelectionResult`:

| Result | Meaning | State effect |
|---|---|---|
| `Selected(id)` | Backend applied the selection synchronously. | `selected*TrackId` updated now. |
| `Requested(id)` | Backend accepted an asynchronous request (`null` = disable). | Unchanged until the native track change arrives; the confirmed selection may differ. |
| `Disabled` | Track type turned off synchronously. | Selected ID becomes `null`. |
| `NotFound(id)` | ID not in the current list or of a different track type. | None. |
| `NotSupported` | Not `Ready`/`Ended`, or capability flag is false. | None. |

The Media3 backend always answers `Requested` (or `NotFound`): selection becomes Media3 track
selection overrides and is confirmed by the next track snapshot.

## Errors

`PlaybackError(code, message, recoverable, suggestedBackend)`. Messages are user-safe and never
contain URLs, headers or credentials. `recoverable` means retrying the same source or another
delivery may succeed; it is not an instruction to retry automatically.

| Condition | Code | Recoverable |
|---|---|---|
| HTTP 401/403 (access refused), 404/410 (missing/expired), 429 (rate limited) | `Source` | no |
| Other HTTP status | `Network` | yes |
| Connection failure/timeout, bad HTTP status without a response code | `Network` | yes |
| Open did not reach `READY` within `openTimeoutMillis` | `Network` | yes |
| Fell behind the live window and automatic recovery was not possible | `Network` | yes |
| Unsupported container or manifest | `UnsupportedContainer` | yes |
| Unsupported codec | `UnsupportedCodec` | yes |
| Decoder init/decode failure | `Decode` | yes |
| File not found / no permission, or the source could not be constructed | `Source` | no |
| Anything else | `Internal` | no |

`suggestedBackend = "mpv"` on container/codec/decode errors is a hint for multi-backend hosts.
Android has no MPV backend; `:app` must not treat it as an available fallback.

## Session replacement safety

- Every `open()` allocates a new `PlaybackSessionId`. Backend events carry it, and events whose ID
  is not the active session (replaced, stopped or released) are dropped.
- Opens are serialized. The open runs as a child of the caller: cancelling the caller cancels the
  native open, stops the backend and returns to `Idle` without publishing `Error` or `Failed`.
- `stop()` and `close()` cancel a pending open immediately (the caller's `open()` throws
  `CancellationException`). A failure that arrives for an open whose session already ended is
  reported to that caller only as cancellation, never as `Error` or `Failed`.
- The Media3 backend clears its session before reconfiguring, ignores callbacks from a replaced
  ExoPlayer instance or after release, and removes the opening listener on cancellation.
- Changing between buffer modes (see Live) recreates the ExoPlayer and reattaches the retained
  `SurfaceView`/`TextureView`; the replaced instance is released and its callbacks are ignored.

## Subtitles

- Sideloaded subtitles become Media3 subtitle configurations carrying `id`, MIME type, language,
  label and the default/forced selection flags. A subtitle track whose native format ID matches a
  sideloaded `id` reports `external = true`.
- Sideloaded requests use the same HTTP data source, and therefore the same `headers`, as the media.
- Track `isDefault`/`isForced` come from the stream or sideload flags.
- `preferredAudioLanguage` and `preferredSubtitleLanguage` become Media3 preferred languages for the
  session. `subtitlesEnabled = false` disables the text track type, `true` enables it, `null` leaves
  Media3's default (forced/default flags may still select a track).
- Reported subtitle formats are the text formats Media3 parses: `vtt`, `srt`, `ssa`, `ass`, `ttml`,
  `tx3g`, `cea608`, `cea708`. Bitmap formats (PGS, DVB, VobSub) are not claimed.
- `subtitleCues` is the level-triggered set of text cues active now (`SubtitleCue`: plain `text`,
  optional `line`/`position` viewport fractions, `alignment`). The Media3 backend maps `onCues`
  for the active session only; bitmap and blank cues are dropped, styling spans are flattened,
  numbered lines map onto a 15-row caption grid and Media3's default bottom row reports `null`
  (renderer default placement). Cues clear on open, stop, close, a track change, Off, and any
  native track update that deselects text. Late cues from a replaced session are dropped.
- A merged sideload's native format ID is prefixed with its source index (`1:<id>`); it still
  reports `external = true`. Extraction-parsed tracks report their original subtitle MIME type.
- The app draws cues over the video viewport in Compose: default cues sit bottom-centred with a
  6% inset and rise above visible player chrome; positioned cues keep their fractions. Size:
  Small 0.75x, System default (device caption font scale) or Large 1.35x of 5.33% of the video
  height. Appearance: System default (device caption colours/edge), Text with shadow, or White
  text on black.

## Live and DVR

`LivePlaybackPolicy` is intent; measured statistics are authoritative.

| Policy | Target live offset | Load control |
|---|---|---|
| `LowLatency` | 3 000 ms | Media3 default |
| `Balanced` (default) | manifest/Media3 default | Media3 default |
| `Resilient` | `targetLiveOffsetMillis` (10 000) | streaming buffer min 10 000 / max 15 000 ms, start 1 000 ms, after rebuffer 5 000 ms, 64 MiB allocation threshold, time prioritized over size |

`AndroidMedia3ResilientBufferConfig` rejects values Media3 would silently cap (target offset below
the minimum buffer or below twice either start threshold). An `OnDemand` hint always uses
`Balanced` and no live configuration.

- `SeekableLive` exposes its window range and is seekable; plain `Live` never is (see Timeline).
- **Behind-live-window recovery:** for any source not hinted `OnDemand`, the first
  `ERROR_CODE_BEHIND_LIVE_WINDOW` of an attempt seeks to the default (live edge) position and
  re-prepares instead of failing; a second one before `READY` is reached again fails as `Network`.
  Each recovery increments `behindLiveWindowRecoveryCount`.
- **Statistics are diagnostic only.** `PlaybackStatistics` (live-edge offset, buffered-ahead,
  throughput estimate, dropped frames, rebuffers, recoveries, discontinuities, speed, and the
  configured policy targets) lives outside `PlaybackState`, is sampled about every 500 ms, is
  session-scoped and reset by `open`/`stop`. Configured targets are requests, not measurements.
  Product behavior must not branch on statistics.

## Runtime capability reporting

`PlayerCapabilities` is produced by `probe()` from the device at runtime, through a pure mapping
(`media3Capabilities`) that is unit-tested. Each claim has one of three bases:

| Claim | Basis |
|---|---|
| `videoCodecs`, `audioCodecs` | Runtime: decoders advertised by `MediaCodecList`. |
| `maxVideoWidth`/`maxVideoHeight`, `supportsHevcSdr` | Runtime: decoder video capabilities; one limit for both H.264 and HEVC Main (the intersection when both exist). `null`/`false` when unknown. |
| `hardwareAcceleratedVideoCodecs`, `hardwareAcceleration` | Runtime on API 29+: `isHardwareAccelerated && !isSoftwareOnly`. Reported as `Decode` (or `None`); never `DecodeAndRender`, which no API proves. Below API 29 or with no decoder list: empty / `Unknown`. |
| `drmSchemes` | Runtime: `MediaDrm.isCryptoSchemeSupported` (Widevine, ClearKey). |
| `supportsPictureInPicture` | Runtime: API 26+ **and** `FEATURE_PICTURE_IN_PICTURE`. The embedding activity must still opt in. |
| `containers`, `adaptiveProtocols` (`hls`, `dash`), `subtitleFormats`, track selection, external subtitles, `supportsLive`, live policies, movable surface, composited overlays | Structural: bundled Media3 extractors/modules and this library's wiring. |
| `supportsSeekableLive`, `supportsSurfaceReattachment`, `supportsHdr`, `supportsAudioPassthrough` | Conservative `false`: no platform API proves them; raised only with device evidence (see matrix). |
| `supportsPlaybackRate` | `false`: the API has no rate control. |

Codec, HDR, DRM and UHD support are never inferred from dependency presence. A per-source timeline
can still report `SeekableLive` when the capability flag is `false`; the flag is a device-level
claim for backend selection.

## Direct play first

The library plays exactly the URL it is given with the given headers; it never asks for remux or
transcoding. Cross-protocol redirects are refused, so headers are not replayed across a scheme
change. When direct playback fails it returns a typed error; `:app` sends the measured capabilities
(codecs, size limit, HEVC SDR) to the backend, and only the backend decides a remux/transcode
delivery. A container extension alone is never a reason to request conversion.

## Privacy

- `PlaybackSource.toString()` prints `uri=<redacted>` and `headers=<redacted>`;
  `ExternalSubtitleSource.toString()` prints `uri=<redacted>`; `BackendSelection.Selected` prints
  only the factory ID.
- Source URIs, headers, cookies, licenses and local paths never enter error messages, events,
  statistics, state or exceptions.
- Diagnostic logging contains only Media3 numeric error codes, observed HTTP status, or the
  closed `media3_open_timeout` code and configured deadline in milliseconds. No telemetry or
  analytics transport is present; statistics stay in process for the embedding app. The opt-in
  instrumented measurement test never logs its fixture URL.

## Device validation matrix

A host unit test proves mapping logic only. Before a capability is claimed in a release note,
ticket or product decision, record the evidence below in `TESTING.md` (device model, Android
version, build SHA). Emulator evidence never qualifies codec, HDR, DRM, PiP or remote claims.

| Claim | Minimum evidence |
|---|---|
| State machine, timeline mapping, session tagging, cancellation, redaction, error mapping, capability mapping | Host unit tests (`:testDebugUnitTest`). |
| Direct H.264/AAC VOD decode, seek, stop/close, header-bearing requests | Emulator or device playback of a real header-protected source. |
| Codec list, `maxVideoWidth/Height`, HEVC SDR, hardware decode | Physical phone and physical Android TV: probe output plus sustained playback at the claimed size per codec. |
| HDR, Dolby Vision, audio passthrough | Physical HDR/passthrough-capable TV with matching content; flags stay `false` until then. |
| DRM (Widevine/ClearKey) | Physical device license and playback of protected content. |
| Live policies, behind-window recovery | Connected `AndroidMedia3LiveResilienceMeasurementTest` against `corpus/live_hls_server.py` on emulator, then a real live provider on a physical TV. |
| `SeekableLive` / DVR seeking | Physical TV seeking within a real DVR window; then `supportsSeekableLive` may become `true`. |
| Surface reattachment (background/foreground, surface recreate) | Physical phone and TV resuming video on a recreated surface without re-open; then `supportsSurfaceReattachment` may become `true`. |
| Track switching (audio/video) | Device playback with multiple renditions, confirmed by the next track snapshot. |
| Subtitle display | Connected `AndroidMedia3SubtitleCueTest` (corpus, emulator) for in-stream default, forced and sideloaded WebVTT/SRT cues and Off clearing, plus an in-app visual check on a physical phone and TV. Dated cue-test results belong in `TESTING.md`. |
| Picture-in-picture | Physical phone entering PiP with the activity opted in. |
| Remote/media keys, focus | Physical Android TV remote (owned by the design contract). |
