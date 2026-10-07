# Native torrent runtime availability and evidence

The owner-approved native contract is pinned by `DESIGN_REF` under
[the native Android behavior specification](../design-contract/specs/behavior/torrent-native-android.md).
Normal debug and release builds enable native capability by default on supported
Android phones and Android TVs. No receipt, account/device allowlist, operator
enablement or user setting is required.

## Runtime prerequisites

`NativeTorrentRuntime.isAvailable()` requires API 24+, a supported bundled
process ABI, successful actual facade initialization, cleartext permission for
literal IPv4 `127.0.0.1` and a coherent suspension-aware elapsed clock. The
controller must also adopt valid server/account/profile/device authorization
facts and initialize an available exclusively owned private cache. Any failed
prerequisite omits native advertisement; independently authorized HTTP/HLS
delivery remains eligible.

Backend admission is enabled by default for supported Android clients and still
requires successful closed-protocol negotiation, current resource authorization,
an enabled account-owned producer, the exact selected VOD/source/hash/file index,
a valid private grant and available capacity. Native-ineligible sources retain
their ordinary authorized delivery. An admitted native failure uses existing
explicit recovery and cannot silently choose another source or gateway.

## Settlement and qualification limits

Reads stop before owned transport is cancelled and joined. All local work shares
the two-second settlement deadline; failed settlement keeps the manager
quarantined, its exclusive ownership and its reservations. Arbitrary stalled
synchronous OS IO has no proven unconditional two-second completion bound. Native
defaults accept this documented limit and retain the fail-closed quarantine
behavior. A later completion or a replacement timer cannot convert failed
settlement into successful retirement.

Implementation, host validation, actual Android ABI loading, owned-network
Media3 playback and physical/public-network observations are separate results.
A host pass does not establish JNI decoding, emulator evidence does not qualify
physical hardware, and owned private seeders with DHT disabled do not establish
public-peer behavior. Record PASS, FAIL or NOT RUN with the exact source pins,
APK/library hashes, device process ABI and tested scope. Outstanding physical,
public-network, sustained-resource or distribution evidence does not add a
global runtime enablement gate.

Source and packaged bytes are identified by `CORE_REF`, `TORRENT_REF` and their
vendor locks. Preserve archive-free dependency/license inventories, source stamps
and 16 KiB ELF/APK alignment. An inventory alone does not settle distribution
obligations.

## Normal and fixture separation

Normal gateway artifacts exclude archive extraction, the control server and
`test-network-policy`. The normal app uses `newNativePublic`, private
`noBackupFilesDir`, system-CA trust and literal-loopback cleartext permission.
Private grants, local capabilities and random cache/scope identities stay outside
launches, saved state, logs, diagnostics and history.

Owned-private constructors, seeded peers, fixture CAs and configuration belong
only to the separate debug fixture application. Rebuild normal APKs without
fixture properties, then inspect their actual libraries, notices, trust and
configuration exclusion. Preserve the shared emulator's account, origin,
profile and data; install only a verified normal APK with the existing signing
identity.

The maintained procedures are
[artifact adoption](NATIVE_TORRENT_ARTIFACTS.md),
[control](NATIVE_TORRENT_CONTROL.md),
[coordinator](NATIVE_TORRENT_COORDINATOR.md),
[player boundary](NATIVE_TORRENT_PLAYBACK.md) and
[owned qualification](NATIVE_TORRENT_OWNED_QUALIFICATION.md).
The [runtime default guide](NATIVE_TORRENT_RUNTIME.md) describes normal device installs.
Qualification captures, credentials and environment/device values stay outside
tracked documentation. Source/build success does not prove a deployed service;
environment rollouts use the owner's authorized private runbook.

## Rollback and retirement

A rollback restores an earlier verified normal artifact with the same signing
identity while preserving app data and the selected server. Retire active and
pending grants through player-stop, control invalidation, joined native work and
backend lease release/request tombstones. Hiding capability alone does not retire
authority. Reject late results and retain quarantine/accounting after any failed
settlement. Ordinary delivery remains subject to its existing authorization;
an already admitted native source receives no silent fallback.

Backend rollback preserves resource revocation/expiry and deployment data.
Disclosed torrent identity cannot be recalled from a modified client. Device
installs and source pushes do not authorize production deployment.
