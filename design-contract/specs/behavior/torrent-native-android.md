# SRC-TORRENT-NATIVE-001 — Authorized Android native torrent VOD

Status: owner-approved for implementation on 2026-10-06, with default availability
approved on 2026-10-07. The owner approved rolling native piece caching on
2026-10-07 for video files larger than device storage. Supported Android clients enable native torrent transport
by default for every authorized account. No user setting, operator enable flag,
account/device allowlist or qualification receipt controls availability. Runtime
compatibility and resource authorization still apply. Implementation, deployment
and qualification evidence must be recorded separately. This is the normative,
closed v1 contract, not a
list of proposed API names. Implementation requires review/commit of this
amendment and immutable adoption under [DESIGN_SYNC](../../docs/process/DESIGN_SYNC.md).

Approval provenance: local Markdown ticket 01, `01-native-delivery-contract.md`,
and the owner-approved `.scratch/native-torrent/contract-proposal.md` in Android;
VPS handoff `.scratch/android-native-torrent-handoff/contract-proposal.md` in
playback-gateway. These ignored working records are not alternative specs.
Source evidence: design `b64c98e3a97660fbee17aebb7c548f87a6494790`; backend
tracked `origin/main` `3b44caf8071a358bd2e446f7d48fcdffd352bd56`,
`server/src/gateway/playback.rs::{Facts,Start,response}` and
`server/src/routes.rs`; core `851a4bd59a7afc4dd2f420f962adfdf3c3cba403`,
`crates/viptv-core/src/{dto.rs,domain/playback.rs}`; gateway
`d04a558e2b4feb046603b150d751d5bf7fadf62a`,
`ffi/src/lib.rs`, `engine/src/{torrent.rs,torrent_stream.rs}` and `PROVENANCE.md`.
Those revisions do not implement or qualify this contract. Closed backend
Facts/Start and core Direct/Gateway parsing require coordinated changes;
existing engine waits, range APIs and clone-stop semantics do not demonstrate
cancellation or independent grants.

## Intent, ownership and unchanged UX

An authorized Android/Android TV viewer plays the exact file explicitly selected
by an enabled account-owned add-on for the selected VOD title/exact episode.
Media3 remains the decoder. No magnet entry, file chooser, source-entry form,
new setting, assets or layout are introduced. The observed native failure copy
below uses the existing error surface. Other clients retain
[SRC-TORRENT-GATEWAY-001](torrent-gateway-sources.md); ordinary HTTP delivery and
[BE-002](../../plans/backend-v2/BACKEND_V2.md) resource policy are unchanged.

The [Roku behavior contract](roku-ux-contract.md), SRC-PROVIDERS-001 and player
controls in [components](../../viptv-design-system/components.md), and existing
[copy](../../viptv-design-system/copy.md) own entry/exit and visible states.
Preserve source-card geometry, focus order/restoration, click/tap/Enter and OK
release activation, 700 ms hold with suppressed release, no hold repeats,
overflow/keyboard equivalents, accessibility, Back and title/queue return
identity. Discovery and starting use existing partial rows, skeleton/busy and
safe failure states; no torrent/download percentages or new detail text.
Resume, seeking, watched history and continuation use media time, never bytes
or download completion. Audio/subtitles, pause intent and position retain the
existing player contract. Non-seekable/seekable live distinctions are unchanged:
native torrent v1 is VOD only.

Backend owns admission, opaque sources, exact-title/episode proof and grants.
Rust core owns negotiation, closed wire validation, request construction and
generated Kotlin/WASM types; it must not project private grants into ordinary
serializable launch/session/card/history models. Android owns native transport/
cache/lifecycle and Media3 effect adaptation. Public player-library types remain
free of Media3/UniFFI implementation types. The generic gateway engine gains no
account/profile/device/title identifiers or product policy.

## Admission and privacy decision

Native requires all of: successful protocol negotiation; runtime-supported
`android` or `android_tv` capability; explicitly selected
opaque source; exact authorized VOD identity; canonical BitTorrent v1 hash; and
an explicit valid add-on `fileIdx`. Source wire index is integer 0..65535; the
resolved metadata must have at most 4096 files and contain that exact index.
Identity is hash plus metadata file index, not filename or bytes. Never choose
the largest file or infer an episode. Missing index is native-ineligible, not
permission to invent a choice; gateway selection retains its own contract.

Revalidate account/profile/device resource grants, parental restrictions, source
producer ownership/enabled state/configuration revision and exact source/file
at admission, poll and renewal. Native is independent of `can_play_direct`.
`force_gateway`, non-`auto` conversion and server-owned track extraction or
conversion bypass native. Existing server-owned controls `audio_track`,
`subtitle_track`, `audio_language` or `subtitles_off=true` also bypass native;
preferred language hints alone do not. Archive/RAR, private trackers, webseeds,
v2/hybrid torrents, live, unsupported media and unsupported clients retain the
authorized gateway path or existing safe unsupported state. Native eligibility
is not a promise that Media3 can decode arbitrary media.

The owner authorizes public DHT discovery and TCP peer traffic during a valid
grant, including possible uploads. Peers/DHT can observe the device network
address and torrent identity. Backend authorization controls the conforming
app; it cannot revoke a disclosed hash/metainfo from a modified client, which
can continue independent peer access. Gateway authorization can refuse later
media requests, but neither delivery can erase copied media or peer observations.
This amendment adds no UI disclosure string; it records the approved privacy
trade-off rather than claiming gateway-equivalent peer revocation.

## Closed HTTP/JSON protocol

All names below are normative snake_case backend wire names; generated binding
names are Rust-owned, not client-specific aliases. JSON objects are closed:
reject duplicate/unknown fields, wrong types, mixed variants, non-finite numbers,
invalid UTF-8 and out-of-bound values before transport activity. Integers are
JSON integers (no fractional/exponent coercion). Strings are byte-bounded UTF-8;
identifier means 1..128 ASCII letters/digits/`-`/`_`. Authentication and profile/
device authorization use existing v2 headers, not grant-body identity. HTTPS is
required for control traffic; never use a delivery URL for control.

### Support negotiation and old servers

Add authenticated `GET /api/v2/playback-protocol` (no request body), with the
same account/profile/device resource authorization as playback. Complete 200
body: `{"version":1,"native_torrent_versions":[1]}` when the backend implements
the entire v1 extension, or `{"version":1,"native_torrent_versions":[]}` without
it. Both fields are required; only integer `1` is allowed, at most once. This
advertises protocol support, not account admission, capacity or qualification.
Authorization, source eligibility and bounded capacity can refuse a request;
there is no configurable torrent-enable policy. Use `Cache-Control: no-store`,
zero redirects, a 4096-byte body limit and a 5-second total request deadline.

Negotiate before each new native-capable start, scoped to the configured backend
origin and authenticated generation. Only a valid 200 with `[1]` allows
`client.native_torrent`. 404/405, `[]`, unknown version, malformed/oversized
response, timeout/network failure or other non-authentication error means omit
that field and use the unchanged legacy v2 shape/path; do not infer support from
`can_play_direct`, gateway capabilities or a failed optimistic POST. 401/403 or
known resource-authorization refusal follows existing auth recovery, not legacy
admission. Origin/session/profile change invalidates the result; late results
cannot authorize another generation. A client without native runtime support
omits the field even after negotiation. Web/TV-web/desktop/Roku never advertise
it. Older clients receive only `direct`/`gateway`, with no native fields.

The cited backend has no negotiation route and uses `deny_unknown_fields` on
Facts/Start. This route/extension is required work, not existing support.

### Start request

`POST /api/v2/playback` retains its route, authentication, idempotency, 202
starting / 200 identical-retry behavior and following complete request shape.
Only `client.native_torrent` is added. Body limit is 16384 bytes for this
extension; total control request deadline 10 seconds, zero redirects. Optional
means omit or use the listed default; do not send null except where allowed.

| Field | Type and bounds | Presence/default |
| --- | --- | --- |
| `request_id` | identifier | required |
| `stream_id` | nonempty opaque source string, at most 128 bytes | required |
| `client` | closed object below | required |
| `position` | finite seconds, 0..604800 | optional, 0 |
| `conversion` | `auto`, `audio`, `video`, `audio_video` | optional, `auto` |
| `force_gateway` | boolean | optional, false |
| `audio_track`, `subtitle_track` | integer 0..65535 or null | optional, null |
| `audio_language`, `preferred_audio_language`, `preferred_subtitle_language` | 1..35 ASCII letters/digits/`-`, or null | optional, null |
| `subtitles_off` | boolean | optional, false |

`subtitles_off=true` conflicts with non-null `subtitle_track` or
`preferred_subtitle_language`. `client` fields:

| Field | Type and bounds | Presence/default |
| --- | --- | --- |
| `platform` | `android`, `android_tv`, `desktop`, `web`, `tizen`, `webos`, `roku`, `vizio` | required |
| `can_play_direct` | boolean, ordinary HTTP fact only | optional, false |
| `max_width`, `max_height` | integer 2..16384 | required |
| `video_codecs`, `audio_codecs` | arrays, 1..16 entries, each 1..32 ASCII letters/digits/`_` | required |
| `native_torrent` | closed object `{"version":1,"network_policy":"public_dht_tcp_v1"}` | optional; omit when unsupported, never null |

`native_torrent.version` is integer 1 and `network_policy` is exactly
`public_dht_tcp_v1`; both required, no other fields. Only runtime-supported Android
platforms may send it. A malformed or unsupported extension is
`invalid_playback_request`, never silently ignored. Idempotency binds the entire
normalized request, authenticated principal/device/session and exact opaque
source/file. Conflicting retries are `playback_conflict`; an idempotent retry
cannot acquire a different source, grant or transport. An explicit gateway retry
uses a new request ID after retiring failed authority.

### Lease and native delivery

Keep `GET /api/v2/playback/:id`,
`POST /api/v2/playback/:id/heartbeat` and `DELETE /api/v2/playback/:id` (bodyless).
The lease envelope has exactly these required fields:

| Field | Type and bounds |
| --- | --- |
| `id` | backend playback identifier |
| `status` | `starting`, `ready`, `failed`, `expired`, `released` |
| `delivery` | null outside ready; existing direct/gateway object or native object below when ready |
| `error_code` | existing safe code, at most 128 ASCII letters/digits/`_`, or null |
| `error` | existing safe server-projected message, at most 1024 bytes, or null |
| `expires_at` | integer Unix seconds, 0..9007199254740 |
| `renew_after_seconds` | integer 20 for native leases |

Existing direct/gateway field contracts remain unchanged. For native ready,
`delivery` has exactly these required fields:

| Field | Type and bounds |
| --- | --- |
| `kind` | exactly `native_torrent` |
| `position` | requested media seconds, finite 0..604800 |
| `live` | exactly false |
| `format` | exactly `original` |
| `preferences` | closed object: `audio_language`, `subtitle_language` each null or language string as above; `subtitles_enabled` boolean; all required |
| `grant` | closed private object below |

No `url`, headers, cookies, authorization key, gateway object, track arrays,
filename or arbitrary upstream JSON is allowed in native delivery. Native
preferences are local selection hints, never server extraction commands.
A native result must match the requesting Android capability/version/policy;
otherwise reject without engine activity. Ready error fields are null.

| Grant field | Type and bounds | Presence |
| --- | --- | --- |
| `version` | integer 1 | required |
| `id` | opaque identifier, independently revocable per playback | required |
| `server_time` | integer Unix seconds sampled when emitting this response | required |
| `expires_at` | integer Unix seconds, equal to envelope expiry; greater than server_time and at most server_time + 60 | required |
| `info_hash` | exactly 40 lowercase hexadecimal characters | required |
| `file_index` | integer 0..65535; must exist in metadata | required |
| `network_policy` | exactly `public_dht_tcp_v1` | required |
| `input` | exactly one closed variant below | required |
| `expected_file_size` | backend-verified selected length, integer 1..2147483648 | optional; omit if not verified, never inferred from presentation |

`server_time` and `expires_at` use the envelope integer bounds. Validate
lifetime arithmetic with checked operations. Backend binds
grant ID privately to playback ID, principal/device/session, authorization proof,
producer revision, source, v1 hash and exact index. Grant identity/input/index/
policy/expected size stay immutable across polls/renewals; only server_time and
expiry change. No client-provided identity fields replace that proof.

Input variants (all fields required, no extras):

- `{"kind":"magnet","uri":"magnet:?xt=urn:btih:<40-lowercase-hex>"}`:
  URI is exactly 60 ASCII bytes and hash matches `info_hash`. No tracker, display
  name, peer, alternate-source, webseed, extra parameter or encoded alias.
- `{"kind":"metainfo","metainfo_base64":"<RFC4648-base64>"}`:
  canonical standard padded base64, no whitespace, URL-safe alphabet or data-URI
  prefix. Decode before engine admission; raw size 1..4194304 bytes, encoded
  length at most 5592408 bytes, with checked allocation and canonical re-encoding
  equality. Authorized SHA-1 of the exact bencoded `info` bytes must match
  `info_hash`. These angle-bracket values are synthetic schema placeholders,
  not real source input and not literal valid payloads.

Every native playback response has a 6291456-byte (6 MiB) HTTP body limit,
enforced while reading before JSON allocation. Transfer uses identity content
encoding; reject compression and redirects. Control requests, including poll/
heartbeat/release, have a 10-second total deadline; deadlines never extend the
lease. Bodyless release returns exactly `{"ok":true}` and is idempotent.
Pending/terminal/error responses contain no usable grant or private input.
Repeated ready input is transient transport data, not a persisted snapshot.

Add bodyless `DELETE /api/v2/playback-requests/:request_id` under the same
authorization; limit/deadline/redirect policy matches release. It returns exactly
`{"ok":true}` whether that scoped request is pending, admitted, cancelled or
absent, without cross-scope existence disclosure. Cancellation atomically records
a tombstone and retires any associated grant, including a racing/late admission.
Retain tombstones for the authenticated session lifetime; quota exhaustion must
refuse admission, never evict a live tombstone. Request IDs cannot be reused in
that session. This negotiated extension is required so Back/close can release an
ambiguous admission even without a returned playback ID. Old-server flows retain
their existing cancellation contract, never optimistically call the extension.

### Synthetic ready example

This illustrates the complete native shape, not usable authorization or a real
source. The all-zero hash, IDs and historical times are synthetic placeholders;
no torrent is identified or acquired by this example.

```json
{
  "id": "playback_example",
  "status": "ready",
  "delivery": {
    "kind": "native_torrent",
    "position": 120,
    "live": false,
    "format": "original",
    "preferences": {
      "audio_language": null,
      "subtitle_language": null,
      "subtitles_enabled": false
    },
    "grant": {
      "version": 1,
      "id": "grant_example",
      "server_time": 1700000000,
      "expires_at": 1700000060,
      "info_hash": "0000000000000000000000000000000000000000",
      "file_index": 0,
      "network_policy": "public_dht_tcp_v1",
      "input": {
        "kind": "magnet",
        "uri": "magnet:?xt=urn:btih:0000000000000000000000000000000000000000"
      }
    }
  },
  "error_code": null,
  "error": null,
  "expires_at": 1700000060,
  "renew_after_seconds": 20
}
```

### Metainfo acquisition and validation

Only backend-normalized add-on hash/magnet or backend-vetted `.torrent` bytes
enter a grant. Android never receives an upstream metainfo URL or fetch headers.
For `.torrent`, backend fetch deadline is 10 seconds total across DNS/connect/
redirect/body; at most three redirects and 4194304 raw bytes. Apply BE-002's vetted
scheme/DNS/address/credential/header policy to the initial URL and every redirect;
revalidate resolutions at connection, reject forbidden destinations and downgrade
redirects, and never forward credentials cross-origin. Fetch only HTTP(S) with
identity content encoding and a bounded HTTP header budget of 32768 bytes.
No backend video relay and no source I/O during discovery. For a `.torrent`
source without an add-on hash, derive the canonical grant hash from the vetted
exact `info` bytes; if an add-on hash is also present, require equality. Validate
metainfo and the explicit index before emitting its ready grant. HTTP(S) provider
support and operator-managed source-network policy do not authorize private
native peer destinations.

Validate backend-fetched and peer-discovered metadata before file access:
bounded bencode (depth at most 32, at most 65536 values, individual strings at
most the raw 4 MiB limit), no duplicate/unsorted dictionary keys, malformed
integers, trailing data or overlapping/unsafe paths. Only v1: `info` contains
`name`, `piece length`, `pieces`, and exactly one of `length` or `files`;
optional `name.utf-8`/file `path.utf-8` are accepted only as safe equivalent path
representations. File entries contain `length`, `path` and optional `path.utf-8`.
Reject `private`, v2/meta-version/file-tree, symlink/attribute or other unsupported
info keys. Require positive power-of-two piece length 16384..16777216, positive
payload lengths, correct 20-byte hash count for the total payload, checked sums,
at most 4096 files and a positive selected media length. Relative paths cannot
contain empty/`.`/`..` components, separators within components, absolute/drive
prefixes, NUL/control characters or colliding normalized names. Paths never
become display data or diagnostics. The ordered v1 file list defines index.

Accept bounded outer `info`, `announce`, `announce-list`, `creation date`,
`comment`, `created by`, `encoding` keys only. Tracker fields are syntactically
vetted without contact and removed; harmless outer annotations are removed.
Reject webseed/peer/node/alternate-source fields and unknown outer keys.
The backend emits a canonical metainfo wrapper containing only the unchanged
validated `info` bytes, so tracker stripping cannot change the hash. The native
engine applies the same outer-field policy to peer metadata and disables tracker
contact. Require selected index and verified size to match; wrong hash/index/
size is an authorization/selection failure, never fallback to another file.
Existing bounded Stremio `sources` ingestion remains gateway-spec-owned; omit
recognized tracker/DHT hints and reject peer/webseed/unknown hints as specified
there. No source-supplied address reaches native network configuration.

## Lease, clocks and revocation

Native leases are 60 seconds with heartbeat every 20 seconds, including backend
preparation, native preparation, playback and pause. Native completion never
ends or renews authorization. Poll does not renew; heartbeat extends only the
same grant. No operator override may lengthen these native v1 bounds.

On receiving a grant, let rtt be the full measured monotonic control request
round trip, and u the non-negative bounded clock uncertainty in seconds.
Accept only when `expires_at > server_time`, server lifetime is at most 60 seconds,
and the trusted wall-clock upper bound (including u) is before expiry. Set a
suspend-aware monotonic deadline at receipt plus
`expires_at - server_time - rtt - u - 1 second`; the one second accounts for integer
timestamp precision. Non-positive remainder, unbounded/unknown uncertainty,
unavailable trusted server-clock anchor or suspension-aware timer means reject
native. The trusted wall-clock upper bound may be derived from authenticated
backend `server_time`, measured RTT and integer timestamp precision, advanced
using suspend-aware elapsed time; a separate platform time oracle is unnecessary.
Device wall time alone does not establish bounded uncertainty. Compare wall and
monotonic deadlines before transport/read/resume; either expiry invalidates authority. Clock changes
or process suspension cannot extend it. Renewal can establish a later deadline
only from a successful current-grant response, never local download status or a
stale/older response. Reject regressing server_time/expiry; serialize or sequence
control responses by generation so late polls cannot regress renewal.

Any rejected renewal, authorization/source/producer revocation, expiry, sign-out,
profile/server change or controller close stops that grant's reads and native
work. Network failure can retry only within the last valid deadline and does not
extend it. Foreground resume must revalidate authorization with the backend
before reopening/continuing transport. Local invalidation has the two-second
quiescence bound below. Without push, remote revocation is observed on the next
successful poll/20-second heartbeat or no later than the last accepted 60-second
expiry; this is not instantaneous server-enforced revocation of peer access.

## Native network and selected-file byte capability

Production `public_dht_tcp_v1` permits public DHT and public TCP peers only.
Disable trackers, webseeds, source discovery/peer hints, operator-supplied direct
TCP bootstrap peers, UPnP/NAT-PMP, persisted DHT state, local peer discovery and
non-TCP peer transports. DHT bootstrap nodes come only from a pinned app/operator
allowlist shipped/versioned with the qualified native artifact; source/grant
fields cannot add nodes. Record concrete inventory/version in qualification
before enabling capability. Validate every bootstrap DNS answer/connection,
every DHT routing-node contact and every TCP destination, including returned
peers: globally routable unicast only. Deny loopback, private, link-local,
multicast, unspecified, documentation/benchmark/reserved and other special-use
ranges, including IPv4-mapped IPv6 and transition forms embedding denied IPv4.
An invalid mixed DNS answer set is rejected, not narrowed by a source. Filtering
must be generic engine policy, not only a Kotlin preflight. No listener or mapping
may expose the byte service on a peer-facing interface.

Synthetic qualification uses a separate non-production test policy with DHT
disabled and explicitly owned private/loopback TCP seeders. The production wire
cannot request that policy. Owned synthetic fixtures use a separate QA emulator
or explicitly requested physical device, preserving the shared interactive
emulator's sign-in and trust configuration. Normal authorized viewing may use
the account's configured providers and public peers. Fixture listeners remain
private; credentials never enter tracked artifacts.

The byte endpoint binds literal IPv4 `127.0.0.1` on an ephemeral port and requires
an unpredictable in-memory capability (at least 128 random bits) per grant.
Android production cleartext permission is restricted to literal `127.0.0.1`,
not `localhost`, LAN, global cleartext or a broad debug fixture exception. Only
an accepted internal adapter constructs the local Media3 URL; backend/UI cannot
supply one. Never forward source headers/cookies to loopback or remote control
requests. Token-protected GET/HEAD/single-range reads and native `readRange`
accept only the exact authorized file; another existing index, wrong token,
traversal, method or invalid/multiple range returns no media bytes. No directory
listing or unselected-file length exposure. Invalidating a grant invalidates its
listener/capability and active bodies, not just future URL creation. All byte
reads, including cache hits, require a current grant; no authority by hash alone.

## Cache authority and resource limits

Use app-private non-backed-up storage, excluded from diagnostics. Random local
directory identities are unrelated to account/profile IDs and generic native
engine scope. At most one exclusively locked authorization-scope cache owner is
active: configured server + account + profile + stable local device/account
authorization epoch. That epoch changes on sign-out/re-pairing, principal/profile/
server change or device/account authorization revocation, not ordinary playback
replacement or renewal. Grants/generations are separate authorities within it.

Enforce 2147483648 bytes (2 GiB) aggregate input-cache reservations, including
retired-but-unsettled work. Ordinary selected-file torrents larger than the
268435456-byte (256 MiB) per-input piece budget reserve that bounded cache,
not their full logical payload or unselected-file lengths. Smaller inputs reserve
their full payload. Same-owner sharing reserves once only when proven; each
independently stoppable grant retains separate authority. Enforce measured
available disk and a separate 67108864-byte (64 MiB) aggregate metainfo/control
ceiling; limit each metainfo to 4 MiB. Accounting is not an exact filesystem
ceiling. A rolling cache must hold at least two torrent pieces; refuse admission
when its required piece slots or aggregate reservation cannot fit.

Download only active readers' bounded windows. Reuse unprotected piece slots
and invalidate have/chunk accounting atomically before re-download. Verify pieces
before exposing bytes, including cross-file boundary pieces. Pin current reads,
in-flight writes and checksum work; never evict a live grant or whole active
input. Backward seeks outside retained data fetch pieces again and may wait for
peers within existing read deadlines. Do not advertise evictable pieces to peers
or serve uploads from this cache. No full-sized sparse payload files or persisted
availability may bypass the bound. Compressed archives remain native-ineligible.

Candidate/outgoing work shares the budget; acquisition/capacity failure preserves
outgoing playback. Reservations remain charged until readers/tasks settle and
shared cache file descriptors close; reap only idle entries, never a live/settling
manager's directory. No silent budget enlargement.

Changing scope/epoch stops all old-scope transport, waits for settlement, closes
the manager and deletes its owned cache before native admission in another scope.
Deletion/settlement failure leaves native unavailable; do not reuse that scope or
pretend reservations disappeared. Independently authorized HTTP/HLS remains
eligible. Revoking one grant does not stop another valid grant in the same epoch,
even for the same hash/index. Sharing a payload cannot share a revocation token,
stopped flag or grant-owned listener. Process restart resumes no old grant,
source input or DHT state; clean only identified app-owned inactive native cache
directories before new admission, never unrelated app/user data.

## Acquisition, replacement, cancellation and recovery

One total 30-second acquisition deadline starts at first native grant acceptance
and includes lock waits, metadata discovery, manager startup, file validation and
endpoint preparation. Renewal does not reset it. Cancellation must be addressable
before a native handle exists and through every await/FFI initialization; per-step
30-second engine waits are not a total bound. Late/rejected handles are disposed
idempotently and cannot publish into retired generations.

Within two seconds of local cancellation/revocation/expiry detection, settle all
acquisitions, grant-owned listeners/body readers and peer work solely authorized
by that grant. Prevent further Media3 reads first, cancel/stop, join native work,
then close/reap/delete. Piece waits must observe cancellation. Aborting a listener
or cancelling a coroutine without joining is insufficient. Work shared with
another valid grant in the same epoch may continue only through its authority,
never the retired byte capability. If the bound cannot be demonstrated, native
capability stays off pending a reviewed contract revision.

Prepare/validate a candidate while outgoing playback remains usable. Retire the
outgoing player/lease only at the accepted player-open boundary; pre-boundary
failure leaves it intact. Generation plus grant gates every callback on the main
thread. Accepted generations own both native resources and backend leases.
Torrent-byte completion is never playback Ended/watched. Same-file overlapping
outgoing/candidate grants must be independently stoppable; cloned engine stop
semantics alone do not satisfy this requirement.

Native-ineligible input uses ordinary backend-authorized gateway admission before
native work. Once native is admitted, failure cannot silently start gateway,
transcode, choose another source or retry indefinitely, including any existing
automatic direct-to-gateway recovery hook. Preserve position/pause/tracks and use
existing Retry / Choose another source / Back recovery. An explicit retry may
request `force_gateway=true` for the same opaque source/exact file after releasing
failed native authority; refusal of authorization/selection cannot be downgraded
into a bypass. Back cancels by playback ID or request ID, invalidates local work
and restores source/title/queue focus. Release is idempotent even after timeout;
late response/callback cannot reopen playback.

Private hashes/magnets/metainfo/paths/tokens remain transient transport objects
and unavoidable sensitive engine-cache internals only. No public Source/cards,
saved-state bundles, generic JSON/UI events, serializable launch/history,
logs/exceptions/analytics/toString/crash attachments or diagnostics may contain
them, including reflected presentation fields. Rust/generated transport types
need redacted representations. No JVM secret-zeroization promise is made.

### Observed failure explanation

The existing startup/player failure surface displays shared Rust's canonical
message for the adapter's closed failure fact, using the exact strings in
[native playback failure copy](../../viptv-design-system/copy.md#native-playback-failure-reasons-src-torrent-native-001).
This amendment changes only the explanation and retains the existing dialog
geometry, focus order, actions, timings, accessibility and source/title/queue
return behavior. A blank line followed by `Diagnostic: <closed reason code>`
appears beneath the explanation in the existing dialog; the text wraps and
remains readable without moving focus or adding actions. No raw diagnostic
string enters this projection.
Media3 failures retain their measured numeric code as `Diagnostic: media3_<code>`
alongside the existing typed explanation and an observed HTTP status, if present.
Player exception messages and cause text never become presentation inputs.

`native_payload_limit` requires an observed input-cache reservation refusal
against the 2 GiB aggregate budget or a piece-slot requirement exceeding the
per-input budget, including unsettled work. A displayed source size alone
is insufficient. `native_metadata_timeout`
requires the startup deadline to expire while the engine is obtaining metadata.
The engine preserves the measured preparation stage at the first deadline:
local cache preparation, metadata acquisition, torrent initialization or local
loopback endpoint publication. These produce `native_cache_preparation_timeout`,
`native_metadata_timeout`, `native_initialization_timeout` and
`native_loopback_timeout`; `native_acquisition_timeout` remains for an overall
deadline whose stage was not established. Neither asserts absent peers or seeders.
Session creation, initialization, loopback publication and still-retiring work
have distinct closed failure facts. DNS resolution, TLS, connection refusal and
control-request timeout require an observed typed transport exception; a generic
IO exception cannot invent one of these explanations.
Storage and cache failures remain distinct where the adapter can establish
them. Invalid/unsupported metadata, exact-file mismatch, authorization expiry,
transport connectivity and codec support require their respective observed
facts; unknown failures use `native_playback_failed`.

Platforms report bounded closed reason codes, shared Rust owns code-to-copy
projection, and recovery continues through the existing retirement/refusal
decision. Timeout, capacity and storage failure are not selection refusal.
Authorization/selection refusals cannot become a gateway bypass. Never
automatically retry, choose a source or change delivery after native admission.

**NT-09 Failure explanation:** show the canonical explanation for each observed
reason through startup and player failure, preserving its stable reason code
through asynchronous acquisition. Test a payload reservation refusal separately
from a metadata timeout, blocked cache/initialization and a total acquisition
timeout. The visible diagnostic code and safe local diagnostic log must retain
the same closed reason; unknown or malformed
reasons and secret-bearing diagnostic fields cannot become visible copy.
Native and actual WASM produce the same code/message. Existing Retry waits for
authority retirement and follows authorization/selection refusal; Choose another
source and Back preserve source/title/queue focus and position/pause intent.
Record engine/adapter fact qualification separately from copy-vector coverage.

## Distribution, immutable adoption and acceptance gates

Native torrent APK libraries must not link/package archive extraction or
`unrar-rs`. Separate generic torrent features from archive features and inspect
the resolved dependency graph, notices and packaged artifact/license inventory.
The cited gateway archive dependency has an unresolved GPL compatibility finding;
archive-free does not itself clear every remaining license obligation. Preserve
existing Android ABIs/minSdk/JNA/core libraries. Build generic cancellation,
selected-only bytes and independent grants before packaging. ABI loading and
host tests alone do not establish decoder, physical-device or public-peer
qualification; preserve the actual acceptance results with enabled artifacts.

Commit/review design first; backend and shared Rust core adopt its immutable
revision, regenerate Kotlin/WASM and update Android/TV-web core pins coherently.
TV-web does not advertise native. Import Android design through design-sync,
never edit vendored mirrors. Native advertisement requires binding/ABI loading,
archive-free inventory and measured private cache readiness. Acceptance results
below document quality and qualification; they are not a configurable enable
gate or per-account restriction. Supported debug and release builds use the same
default availability rule.
No commit/push/deployment or production activation follows from design approval.

Acceptance IDs (record actual source/design/core/app/artifact revisions and
pass/fail/not-run separately; no qualification is claimed here):

1. **NT-01 Negotiation:** old server 404/405 and closed DTO; old client;
   runtime unavailable; `[1]`/`[]`; wrong/unknown/duplicate schema; auth refusal; redirected,
   timed-out/oversized negotiation; profile/origin switch and late responses.
   Unsupported clients get only unchanged HTTP/gateway leases.
2. **NT-02 Admission/input:** force-gateway/conversion/track gates; wrong account,
   profile/device/producer revision; exact episode/source; missing/wrong index;
   conflicting idempotency; malformed/mixed/oversized base64, hash mismatch,
   metainfo bounds/paths/private/webseed/v2; fetch deadline/redirect/DNS/header
   limits. No new I/O or private-value disclosure on refusal.
3. **NT-03 Grants:** preparation/pause heartbeat, poll without renewal, expiry,
   clock jumps/suspension, maximum/over-bound timestamps, failed renewal,
   source/device revocation and foreground
   revalidation; terminal responses contain no input. Ambiguous start plus
   request-ID cancellation/racing admission and repeated release cannot revive it.
4. **NT-04 Native isolation:** real transport acquisition cancellation before
   handle/through lock and metadata waits; total 30 seconds and measured two-second
   settlement; piece-wait readers, late handles, wrong-token/unselected-index
   GET/HEAD/range/readRange; overlapping same-file grants retire independently.
5. **NT-05 Network:** public-address filter fixtures across IPv4/IPv6/mapped forms,
   mixed DNS/bootstrap/DHT routing nodes/peer results; no tracker/webseed/UPnP,
   persisted DHT or source-added peer contact. Record pinned bootstrap inventory.
6. **NT-06 Cache/lifecycle:** duplicate owner lock, full-torrent aggregate/disk/
   overhead refusal, unselected reservations, candidate failure preserves outgoing,
   same-epoch replacement, per-grant revocation versus epoch change, settled-only
   reaping, cleanup failure and process restart. No cross-scope reuse or secret
   serialization/backup/diagnostics.
7. **NT-07 Artifact:** archive-free dependency and license inventory, reproducible
   source/checksum manifest, ELF/APK alignment, ABI load and minSdk; JDK 17 / SDK 36
   real unit tests and debug APK assembly. Generic engine checks precede packaging.
8. **NT-08 Local Android TV:** authorized backend -> JNI -> narrow loopback ->
   real Media3 using owned synthetic multi-file H.264/AAC VOD and DHT=false;
   exact episode/index, time seek to missing pieces, pause/expiry, Back during
   startup, rapid replacement/sign-out/profile change, source/focus return and
   resume/history/track intent. Use configured local TV emulator/device,
   preserve sign-in, never a remote TV emulator. No UI geometry/copy changes.

Host, emulator and physical evidence are independent. Physical codec/HDR/PiP,
sustained public-peer/resource qualification and production activation remain
separate gates; emulator evidence cannot certify them. Approved-for-implementation
is not implemented, qualified, adopted by consumers or baseline.

## Rolling cache acceptance

**NT-10 Bounded large native input:** admit an exact authorized video larger than
2 GiB through the native acquisition/facade, retain only its configured piece
cache, and read ranges beyond 32-bit offsets. Verify byte identity, forward/backward
seek and re-download after eviction, unselected-file refusal, independent grant
revocation, outgoing survival on candidate capacity refusal, partial-piece retry,
cancellation and joined cleanup with reservations held through file closure.
Record host TCP/FFI and Android decoded playback separately; a virtual large
input does not qualify a full movie, public swarm or physical decoder.
