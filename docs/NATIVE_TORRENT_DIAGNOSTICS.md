# Native source startup diagnostics

The recovery dialog contains the canonical explanation and a `Diagnostic:` code.
Record that code, the approximate attempt time and the app/core/torrent revisions
when reporting a failure. A startup timeout alone does not prove absent seeders.
The first observed engine failure survives deadline races and joined cleanup.

| Code | Observed fact and next check |
|---|---|
| `native_metadata_timeout` | The startup deadline expired while obtaining torrent metadata. Check DHT/network reachability and try an independently available source. This is distinct from media decoding and cache admission. |
| `native_cache_preparation_timeout` | Local cache preparation or admission queueing did not finish before the deadline. Check storage and retiring work. |
| `native_session_timeout`, `native_session_unavailable` | Creation of the torrent network session timed out or failed. Check device network access. |
| `native_initialization_timeout`, `native_initialization_failed` | Metadata arrived; local torrent/storage initialization did not complete. Check storage and the native engine. |
| `native_loopback_timeout`, `native_loopback_unavailable` | Initialization completed; publication of the local player endpoint did not complete. Check local endpoint creation. |
| `native_retirement_pending` | Previous owned work is still retiring. Wait for cleanup before retrying. |
| `native_payload_limit` | A measured rolling-cache reservation or piece-slot requirement was refused. It is not inferred from displayed torrent size. |
| `native_storage_unavailable`, `native_cache_unavailable` | Measured disk admission or cache ownership/cleanup failed. Check free space and exclusive cache ownership. |
| `native_metadata_invalid`, `native_file_unavailable` | Metadata is invalid/unsupported or the authorized exact-file selection does not match. Refresh or choose another source. |
| `native_dns_unavailable`, `native_tls_failed`, `native_connection_failed` | A typed control-network exception identified DNS, TLS or connection establishment failure to the playback server. These facts do not describe DHT peers. |
| `native_control_timeout` | A bounded control request did not complete. Check server health and connectivity. |
| `native_acquisition_timeout` | The overall startup budget expired without a more specific engine-stage fact. |
| `media3_<numeric code>` | Media3 reported this measured player code. An observed HTTP status remains in its explanation. Check the numeric Media3 error and the selected media's supported format. |
| `media3_open_timeout` | Media3 did not report READY before its configured opening deadline. The dialog/log reports that budget in milliseconds. Native metadata and local endpoint creation have already completed for a native source; this fact alone does not identify absent peers or a decoder failure. |

Native control failures preserve the backend HTTP status and canonical error code.
`404 source_not_found` means the selected source handle is unavailable; refresh the
sources and select again. Backend handles last 30 minutes. A stale handle is not
a Cloudflare 502 or a torrent cache/decoder failure. Recovery discards retained
title discovery before fetching new source handles. Malformed successful lease
responses remain a separate protocol-validation failure. Error response parsing
is bounded to 4 KiB and discards provider error text.

The adapter writes only validated native codes to `NativePlaybackDiagnostic` and
numeric player/HTTP facts to `PlaybackDiagnostic`. Use the host's private device
configuration and explicit serial to collect these tags:

```sh
"$ANDROID_ADB_BIN" -s "$ANDROID_TV_SERIAL" logcat -v brief -s NativePlaybackDiagnostic:W PlaybackDiagnostic:W
```

No source URL, torrent hash, peer address, capability, header, credential, local
path or raw exception text is attached to these diagnostic lines. Keep any wider
device capture private. Host fixtures qualify code preservation, not the cause of
an earlier uncaptured user attempt or public-swarm availability.

Native byte delivery uses a 35-second HTTP read budget and a 60-second player
opening budget. The engine can wait up to 30 seconds for a requested piece;
shorter HTTP defaults can repeatedly abandon otherwise valid downloads. These
player budgets do not extend the native acquisition deadline or grant authority.
