# Native playback control boundary

`VipTvHttpGateway.nativePlaybackControl` constructs a private control owner for
one opaque authorization epoch and playback generation. The coordinator supplies
runtime availability, its private cache, lifecycle callbacks and a suspend-aware
clock. Normal builds advertise native capability when those prerequisites are
available, without a receipt or operator enablement. Ordinary `BackendGateway.playback` keeps
its existing HTTP/gateway contract.

The canonical `nativeTorrent` / `authorizationScope` Rust operation compares
transient configured HTTPS origin, account, profile and stable device authorization
epoch facts. It normalizes origins, validates opaque identifiers, and returns
`create`, `keep`, `retire` or `reject`. Token rotation and playback generation are
excluded from those facts. A changed or revoked scope must settle and delete its
old owner before creating another; malformed/unavailable facts reject admission
and require retiring any existing owner. Scope facts have no serializable public
DTO, and Rust debug representations redact them.

Every native-capable start negotiates through the generated Rust request builder.
Negotiation has a five-second total deadline and 4096-byte body bound. Native
control has a ten-second deadline, 16 KiB start-body bound and 6 MiB response
bound. The dedicated OkHttp client disables redirects and implicit connection
retries, requests identity encoding, rejects compressed responses, and reads
bounded raw bytes before the generated Rust private parser sees them. Session
refresh can replay one rejected bearer within the same total deadline. Invalid
or unsupported negotiation retains the legacy request shape; authorization
refusal and replaced scopes never trigger admission fallback.
Native heartbeats send a zero-byte POST body. Lease release and ambiguous
request cancellation use bodyless DELETE requests.

Rust derives a trusted receipt-time upper bound from the authenticated integer
server time, full measured request round trip, supplied bounded uncertainty and
one second of precision. Android supplies zero additional uncertainty because
the receipt anchor comes from the authenticated backend clock. Rust computes the
grant remainder and validates identity, timestamp ordering and response sequence.
Android advances that anchor using `SystemClock.elapsedRealtime`, including
suspension; device wall time supplies no native authorization. Missing/regressing
elapsed facts reject authority. Polls cannot renew the deadline. A heartbeat job
runs every twenty seconds during preparation and while the player is idle or
paused, independently of progress reporting. Failed network renewal preserves
only the last accepted deadline.

The coordinator calls `authorize()` before native reads and resume. Backgrounding
prevents reads; foregrounding requires successful current-grant backend renewal
before authorization resumes. `firstGrantAcceptedAtMillis()` supplies the
unchanging first ready-receipt fact for the coordinator's total acquisition
budget. Private input stays inside `NativeTorrentBridge`; its private scalar
getters are engine-adapter operations, never launch/history/UI projections.

Teardown calls `retireLocal()` first to prevent reads, invalidate Rust authority
and cancel HTTP/timer work. An independent IO worker calls
`joinLocal(deadlineNanos)` under the cache owner's common settlement deadline.
HTTP reservations remain charged until both the body callback and Rust consumer
settle. `releaseRemote()` is a separately bounded best-effort operation while the
cache still accepts control work: known playback IDs release their lease;
ambiguous admissions cancel their request ID. `closeAfterSettlement()` disposes
the invalidated Rust holder. Scope deletion follows joined local work and any
attempted remote cleanup. Failure preserves the cache owner's quarantine and
accounting. The convenience `stop()` combines local and remote cleanup; its
ten-second remote wait is not proof of native two-second quiescence.

`NativePlaybackControlTest` uses real owned loopback HTTP fixtures, real storage
and generated UniFFI calls. These host fixtures prove transport limits, clocks,
response ordering, cancellation and lease effects; device TLS, JNI loading,
Media3 playback and public-peer behavior have separate qualification evidence.
