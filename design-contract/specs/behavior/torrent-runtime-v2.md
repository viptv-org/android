# SRC-TORRENT-RUNTIME-002 — Shared streaming transport

Status: owner-approved implementation plan, 2026-10-09. Client integration is
authorized with current measured performance accepted for this phase; performance
improvements remain follow-up work. Existing native v1 and gateway behavior
remain the deployed baseline until delivery is separately coordinated.

Owner acceptance, 2026-10-09: the current emulated playback and controlled
runtime checks are sufficient to merge this unreleased implementation into
`main`. Physical-device testing is not a gate for this integration phase.
Known untested hardware remains unqualified; production rollout and further
performance work are outside this main-branch promotion.

## Delivery and ownership

Android/API24+ (phone and TV), Linux and Windows acquire torrents locally. They
never substitute gateway delivery for a failed or unsupported native torrent.
Web clients and Roku use the gateway. Ordinary HTTP playback is unchanged.
The generic runtime owns transport, verified storage, archive byte mapping and
local Range serving; it receives no accounts, profiles or catalog policy.
Backend authorization and core/client lifecycle remain authoritative.

Native transport v2 has separate versioned negotiation; the strict native v1
response is unchanged. Private descriptors preserve public tracker hints,
magnet/metainfo identity, optional explicit file index and an independent,
renewable authority deadline. Unavailable v2 is an explicit native unsupported
result, not permission to send the torrent to a gateway. Cached bytes confer
no authority. Selection uses explicit fileIdx, otherwise the largest torrent
file (lowest index breaks equal sizes), and remains fixed for resume.

## Storage, networking and archives

Use a bounded piece-addressed cache, default aggregate native capacity 2 GiB,
reduced to preserve free space. Count incomplete writes and persistent metadata;
pin data through reads, writes and verification. Never publish unverified
pieces. Completed cache data survives playback stop and process restart;
uncertain records require verification. Sign-out clears account content;
profile replacement revokes active access without authorizing cached content.

Public trackers (UDP/HTTP/HTTPS), DHT, PEX and TCP/uTP may operate concurrently.
Private trackers, automatic port forwarding and background completion/seeding
are excluded. Remove torrent demand and peer activity at the final handle's
close. DHT routing state may remain warm independently of torrent authority.

Support unencrypted stored RAR/RAR5, including contiguous multipart volumes,
by mapping inner-media offsets to verified torrent bytes. Explicit archive
member index wins; otherwise select the largest member, with stable index ties.
Missing volumes, invalid metadata, compressed entries and encrypted entries
produce specific errors; no full archive download/extraction requirement.
ZIP, 7z, Usenet, FTP and compressed/password-protected RAR are not this version.

## Existing player surface

Entry is the existing explicit source action. Use the existing starting state,
layout, type scale, focus and Cancel/Back actions; add no picker or percentages.
The status line uses exactly `Finding peers…`, `Fetching metadata…`,
`Opening archive…` or `Buffering…` from measured runtime stages. Announce a
stage transition accessibly once; do not repeatedly announce byte updates.
Press/release, 700 ms hold suppression, keyboard/touch equivalents, navigation,
playback controls and return-focus behavior retain the pinned player contract.

One elapsed startup budget lasts 120 seconds through first decoded frame.
Fail after 60 seconds without meaningful progress. Unique metadata blocks,
verified demanded bytes or a first successful peer connection count; polling,
failed connections and repeated counters do not. Renewal continues while
preparing. Expiry, revocation, invalid input and cancellation act immediately.
No retry or stage transition resets the original startup deadline.

Use the existing failure surface with the observed phase and these distinctions:
no reachable peers, metadata unavailable, missing archive volume, compressed
archive unsupported, encrypted archive unsupported, cache capacity unavailable,
unsupported decoder, and revoked/expired authority. Preserve canonical copy
ownership in core before consumer activation; raw errors/source data stay private.
Back returns to the originating source selection and restores its focus/filters.
Closing revokes the local endpoint immediately. The host terminates its owned
worker if joined settlement exceeds two seconds; recovery distrusts partial data.

## Qualification gate

Comparative measurements cover current rqbit, upstream rqbit
with broader discovery, the anacrolix candidate, and libtorrent/Nuvio/Stremio
references with matched sources, demand, bandwidth, network and cold/warm state.
Measure acquisition separately from decoding; record actual missing comparisons.
No rollout proceeds on a library feature claim or synthetic-only speed result.

Required scenarios include tracker/DHT/uTP-only discovery; unreachable, slow,
corrupt and handshake-dropping peers; files larger than cache; cross-file pieces;
eviction and crash recovery; beginning/resume/head/tail/random seeks; stored RAR4
and RAR5/multipart boundaries; malformed/missing/compressed/encrypted archives;
cancel at every stage; renewal/expiry/profile replacement; independent readers;
all Android ABIs; Linux/Windows native decoders; gateway web/Roku output.
Controlled correctness and hard storage bounds remain required. The owner has
authorized client integration before latency parity with the reference clients.
Record the measured startup gap and remaining comparisons for follow-up; this
acceptance does not imply device or production qualification. Physical devices
and deployment require separate evidence.

The v2 negotiation route is `/api/v2/torrent-runtime-protocol`, returning the
closed `{"version":2,"native_torrent_versions":[2]}` support object. The existing
`/api/v2/playback-protocol` response remains unchanged for native v1 clients.

References: [native v1](torrent-native-android.md),
[gateway source baseline](torrent-gateway-sources.md),
[Stremio source selection and limits](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md).
