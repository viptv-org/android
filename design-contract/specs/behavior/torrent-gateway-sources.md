# SRC-TORRENT-GATEWAY-001 — Add-on torrent sources through playback leases

Status: proposed implementation contract, 2026-10-02, for
[playback-gateway issue 15](https://github.com/viptv-org/playback-gateway/issues/15).
Review and implementation/device qualification remain pending.

Source revisions: design `18b19af378b27655e3b6401f17b92321739dba83`; gateway
`76d100b8fce0922aefca203e791c10e41f83019c` (reviewed transport source
`889dbe6359668aedf0190f731898fda4c3a034d7`). Existing account ownership and
playback policy remain [BE-002](../../BACKEND_V2.md); source interactions remain
[the Roku behavior contract](roku-ux-contract.md) and SRC-PROVIDERS-001 in
[components](../../viptv-design-system/components.md). Each implementation
must reference a reviewed immutable design commit before adopting this draft.

## Intent and entry

A viewer chooses a source returned by an enabled, account-owned add-on for the
already selected title or exact episode. The backend can additionally retain
the supported torrent/archive input privately, issue the existing opaque source
handle, and prepare authenticated gateway media for that exact selection.

This adds no source-entry form, magnet paste action, file browser, catalog,
automatic replacement source or new settings controls. Xtream live discovery
retains its existing HTTP(S) contract. Source selection, explicit Resume,
continuation matching, profile restrictions and parental authorization retain
their established meaning. The independent gateway receives no account/profile
or title/episode identifiers.

## Input seam proposed for review

Only the existing account-owned add-on stream response is an acquisition seam.
Before publishing an opaque source handle, normalize and structurally validate
one supported transport variant:

- An ordinary HTTP(S) `url`, with the existing bounded allowlist of required
  request headers.
- A BitTorrent v1 add-on `infoHash`, normalized from exactly 40 hexadecimal
  characters to a hash-only magnet. Optional `fileIdx` is an integer from 0 to
  65535 and becomes private gateway `input.file_index`.
- A structurally valid v1 `magnet:` in `url`, using the gateway's bounded
  single-hash validation. Optional `fileIdx` has the same mapping.
- An HTTP(S) `url` whose URL path ends in `.torrent` (case insensitive,
  independent of query), with optional `fileIdx` and the existing required
  request headers.
- An HTTP(S) `url` whose URL path ends in `.rar`, with existing required headers;
  `fileIdx` is invalid because direct archive selection is gateway owned.

Reject ambiguous transport discriminants, malformed/out-of-range `fileIdx`,
`fileIdx` on ordinary HTTP media, torrent/archive live input, and magnet
required headers. `externalUrl`, `ytId`, local files and unsupported schemes
remain unsupported. Filename or other presentation hints never decide input
transport or cause source I/O during discovery.

Stremio `sources` may contain tracker/DHT hints. Bound the array to 32 entries
and each string to 2048 bytes without control characters. Recognized hints are
`tracker:` followed by an HTTP(S) or UDP URL with host and no userinfo or fragment,
and `dht:` followed by the same 40-character hexadecimal info hash. Omit these
hints from the gateway request: this subset does not contact trackers and the
gateway owns DHT discovery. Reject malformed hints,
source-supplied peer addresses, alternate-source/webseed hints and unknown hint
types rather than silently authorizing a broader transport. A DHT node ID that
does not equal this stream's hash is outside this subset.

Without a file index the gateway chooses its largest recognized media/archive
payload, which is narrower than Stremio's generic largest-file fallback. The
add-on's `rarUrls`, `zipUrls`, `7zipUrls`, `nzbUrl`, `servers`, `fileMustInclude`
and other multi-input archive/Usenet contracts remain unsupported; they cannot
implicitly become HTTP media by discarding required transport context.

Private-tracker torrents, webseed metainfo, BitTorrent v2, encrypted archives,
direct compressed HTTP RAR, and partial-progress archive seeking remain outside
the supported subset. A source cannot promise playback before the gateway has
inspected it. No tracker credentials, peers or other new input controls are
exposed to clients.

## Delivery and privacy

Gateway delivery remains the path for web/TV-web, desktop, Roku and unqualified
or non-negotiated Android clients, and for archives or sources outside the native
subset. They use the existing backend v2 request with opaque `stream_id` and
consume gateway HLS through their existing decoder. The owner-approved
[SRC-TORRENT-NATIVE-001](torrent-native-android.md) defines the sole Android/
Android TV exception: separately negotiated, qualified exact-index v1 VOD with
private grants, public peer/DHT policy and a narrow literal-127.0.0.1 byte
capability. Supported Android clients enable it by default for all authorized
accounts without a torrent-enable setting or operator allowlist. Qualification
and deployment evidence remain separately recorded. Ordinary HTTP direct
remains unchanged. No path sends a magnet, metainfo or archive URL directly to
a decoder, and archives never enter native torrent delivery.

Gateway output remains copy/remux first, with conversion only under existing
capability/explicit conversion policy. The initial integration uses HLS because
BE-002's backend projector already validates HLS; progressive support requires
its own closed output contract and playback qualification before adoption.

The backend revalidates account/profile grants, source producer ownership,
enabled state and configuration revision at admission and throughout the lease.
Before source activity, gateway selection checks `torrent: true`, HLS, readiness,
the exact namespace and all five operation scopes: capabilities/create/read/
renew/release. Affinity may prefer an existing compatible output; it cannot
bypass the required torrent capability or authorization checks. Advisory
capacity snapshots do not reserve jobs and do not prohibit joining an existing
output merely because fresh-input capacity is zero.

Private source identity includes account scope, input transport, canonical input,
required headers, live/VOD semantics and selected file index. Distinct file
selections cannot share an affinity key or accidental playback identity.
Source handles remain transient and scoped; source URLs, hashes, source hints,
headers, peer lists and gateway integration keys never enter public source
cards, persisted history, logging, exception messages or analytics. Redaction
also covers those values reflected in title/description/filename/binge-group
metadata. Gateway clients receive only the existing short-lived HTTPS media
capability and bounded safe media/track metadata. The native exception's private
transport grant is not a public source/card/history model; its closed disclosure
and redaction rules are owned by SRC-TORRENT-NATIVE-001.

The gateway may report selected `input_file` basename/index/size on its own API.
The backend does not forward arbitrary upstream JSON. No client file chooser
is added, and this initial integration does not need to expose selected-file
metadata in the v2 public response. If subsequently exposed, validate a closed
projection and redact echoed transport identifiers before returning it.

## States, copy and interaction

Source card geometry, focus order, tap/click/Enter/release activation, 700 ms
hold semantics, repeat behavior, accessibility and platform equivalents remain
the pinned source-picker contract. No TV layout or asset changes occur.

Discovery retains partial usable rows and truthful configured producer names.
Unsupported rows produce the existing safe `source_format_unsupported` producer
outcome. Canonical proposed strings are in [copy](../../viptv-design-system/copy.md)
and the producer rule in [components](../../viptv-design-system/components.md).
Its revised complete copy is:

> [provider] returned formats this app cannot play. Choose another source.

The API-level message for that code is:

> This source format is not supported. Choose another source.

This removes the obsolete universal HTTP-only promise without advertising
uninspected torrent support. A configured add-on with zero rows retains the
existing `No playable sources from [provider]` state; pending producers retain
`Still checking [provider]`. Copy changes apply to all source-picker equivalents
through the normal immutable design/Core adoption path.

Selection prepares the existing `starting` lease and its existing busy/skeleton
surface; no fabricated percentages or torrent progress counters appear.
Capacity, unavailable gateway, timeout and unsupported-selection failures use
existing safe retry/source-choice surfaces. `torrent_cache_capacity` maps to
the existing `gateway_capacity` classification and copy. Unsupported output
maps to `delivery_unsupported`; startup timeout maps to `gateway_startup_timeout`;
unknown gateway diagnostics remain a safe generic failure.

Back cancels preparation, releases an admitted viewer even after an ambiguous
start response, and restores the selected source focus and originating title/
queue return target. Late preparation cannot reopen playback after Back,
profile/source change or sign-out. Playback keeps the backend ID for progress,
seeking/replacement, 20-second renewal and release. Failure or expiration stops
the player and retires the capability. Leaving playback or switching source
releases only that viewer; another authorized viewer continues normally.

## Acceptance

1. Mixed add-on HTTP, supported torrent and malformed/unsupported rows retain
   usable candidates and the configured producer identity. Raw private input
   values echoed across every display field are absent from the public event.
2. Missing/malformed hash; conflicting input variants; live torrent/archive;
   bad file index; headers on magnet; peer/webseed hints and external-player
   input fail without new source activity or public private-value disclosure.
3. No authorized gateway, runtime torrent disabled, missing operation scope or
   unsupported capability produces the existing safe failure for gateway
   admission. Clients without negotiated/qualified SRC-TORRENT-NATIVE-001 support
   never receive native torrent delivery; archives always remain gateway-owned.
   Ordinary HTTP direct remains available under BE-002, including HTTP-only
   providers where permitted. Native acceptance is recorded independently.
4. Two authorized selections with the same payload but different file indices
   remain distinct; identical compatible selections retain existing sharing.
   Cross-account/profile, credential revision and namespace boundaries remain
   enforced during discovery, admission, readiness and renewal.
5. Trusted local HTTPS backend/gateway fixtures prove gateway media playback,
   decoded VOD seek to the selected logical position, late join, track defaults,
   renewal/release and final cleanup. Peer fixtures use a local TCP seeder and
   generated media, never public torrents.
6. Web and installed desktop evidence are recorded separately. Android gateway
   HLS is consumed by actual Media3 in an ordinary phone/TV emulator; native
   facade host/ELF tests do not substitute for that scenario.
7. Back during pending source preparation; timeout/ambiguous start; rapid source
   or profile change; sign-out; foreground renewal; expiration and ready-media
   failure cannot revive obsolete media or leave an owned active lease.
8. Source error copy matches all equivalents, with source/title/queue focus
   restoration tested independently. No layout parity follows from pin checks.

Record source/image/design/Core/app pins and functional/browser/installed/emulator
results independently. Ready-viewer cache exhaustion, sustained peer workloads
and unexpected gateway process death remain the gateway issue's independent
qualification requirements. Physical devices, signing, design review, rollback
and production cutover remain open gates; this draft authorizes none of them.
