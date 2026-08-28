# Air playback corpus

This corpus is generated locally from FFmpeg test sources. It contains no
third-party video, audio, artwork, or subtitles.

```bash
./corpus/generate.sh
```

Generated files are ignored by Git. `cases.json` is the capability contract
used by backend acceptance tests. A backend result must report unsupported
capabilities explicitly; opening one sample is not evidence for the entire
container or codec family.

The initial set covers:

- Matroska with H.264, HEVC, and AV1;
- two selectable audio tracks with language/default metadata;
- embedded SubRip and ASS subtitles plus external SRT, WebVTT, and ASS;
- HLS event and duration-less live playlists;
- MPEG-TS IPTV-style playback.

The generated HLS event contains at least fourteen one-second segments. That is
long enough to establish the resilient player's requested ~10-second live
offset before a later segment is impaired. This is a test-window requirement,
not a claim that offset and buffered-ahead media are the same measurement.

## Deterministic live impairment server

`live_hls_server.py` serves the generated event playlist unchanged and creates
a request-driven sliding live playlist. It requires Python 3.10 or newer, uses
only the standard library, and reads media from disk in fixed-size chunks. Run
its self-tests without FFmpeg or a player:

```bash
python3 -B -m unittest discover -s corpus/tests -p 'test_*.py' -v
```

Generate the media, then start the two-second slow-segment gate:

```bash
./corpus/generate.sh
python3 corpus/live_hls_server.py \
  --segment-delay-ms 2000 \
  --jitter-ms 250 \
  --delay-segment segment-012.ts \
  --discontinuity-before 13
```

The ready line reports only the bind address and port. Test resources are:

- `/hls/live.m3u8`: a bounded sliding live window that publishes one additional
  generated segment per playlist GET;
- `/hls/event.m3u8`: the generated event playlist, byte-for-byte;
- `/hls/segment-NNN.ts`: generated media with byte-range support;
- `/healthz` and `/__air/status`: readiness and fixed-shape counters.

Useful independent fault switches are:

```bash
--http-fail-once segment-012.ts --http-fail-status 503
--disconnect-once segment-012.ts
```

One-shot faults are consumed atomically, so a retry receives the exact original
segment bytes. `--delay-segment` may be repeated; when no delay target is given,
the configured delay applies to every generated segment. Jitter is a stable
hash of the seed and segment name, not runtime randomness, so repeated runs are
reproducible.

The server binds to loopback by default. It neither implements authentication
nor TLS, so bind to a LAN address only on a trusted test network. Request paths,
queries, headers, and client addresses are never logged. Diagnostics contain
only fixed event names and validated generated segment names.

Memory and shutdown behavior are deliberately bounded:

- at most eight concurrent handlers by default (`--max-clients`, hard limit 32);
- at most 64 KiB read per media chunk and 256 KiB per input manifest;
- no request-history, URL, or header retention;
- signal-driven shutdown cancels delayed requests before joining handlers.

`--discontinuity-before` inserts a standards-level discontinuity marker and
maintains its discontinuity sequence as the window slides. The encoded samples
on either side remain continuous; this tests manifest/timeline handling, not a
decoder format transition.

## Player consumption

Desktop backends open `http://127.0.0.1:18080/hls/live.m3u8`. For an Android
emulator, keep the server loopback-only and forward the port to the selected
device explicitly:

```bash
adb -s <serial> reverse tcp:18080 tcp:18080
```

The Android player then opens the same loopback URL. Remove the forwarding rule
after the run with `adb -s <serial> reverse --remove tcp:18080`. A physical
Android device can use `adb reverse` over USB or a trusted-LAN bind.

For the resilient gate, establish the backend's native resilient live policy,
record `liveEdgeOffsetMillis`, `bufferedAheadMillis`, and the rebuffer counter,
then allow `segment-012.ts` to be requested. Passing means the segment is
byte-correct after the configured impairment, playback remains in the live
window, and the rebuffer count does not increase. The harness does not create
an application segment queue or claim success from target offset alone.

These self-tests prove server timing, byte/range correctness, one-shot faults,
sliding playlist behavior, handler bounds, redacted logging, and cancellation.
They do not prove decoder recovery, hardware decode, power use, Wi-Fi behavior,
or no-rebuffer playback. Those remain release-build measurements on each real
desktop/TV/device backend, including Apple and Windows hardware.
