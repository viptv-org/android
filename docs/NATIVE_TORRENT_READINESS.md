# Native torrent readiness and activation boundary

Production native playback is disabled. Android's ordinary constructor leaves
`nativePlaybackQualified` false; the backend constructs its registry with
`native_policy_enabled = false`. The backend's authenticated `[1]` protocol
support response describes implemented wire support and does not enable native
admission. No environment variable enables either production gate.

The owner-approved contract is SRC-TORRENT-NATIVE-001 at design
`83d338b6ffc1fc5e7f14ad4059f6159b8ee84509`. Implementation, host validation,
owned-network Android playback and physical/public-network qualification are
independent results. The local readiness record must bind each result to the
actual source revision, APK hash, artifact lock, device process ABI and applicable
platform; a passing test from another artifact or ABI does not transfer that
qualification.

## Current release decision

Keep both production gates disabled. The maintained dependency and selected
acquisition implementations report honest failed settlement for an arbitrary
stalled synchronous OS IO call, retaining work and cache ownership. They do not
establish the contract's unconditional two-second quiescence bound. Passing
cooperative cancellation and owned-seeder media tests cannot close that gap.
Before enabling native, either demonstrate the required bound for the intended
execution/storage environment or obtain and immutably adopt an owner-reviewed
contract revision that addresses the measured failure. Do not silently relax
the timeout, discard reservations, or reinterpret a failed join as completion.

Public DHT/TCP egress and sustained peer/resource qualification require a separate
authorized run. Owned loopback/private seeders with DHT disabled prove only their
isolated policy. Physical audible playback requires direct human confirmation;
an AAC declaration, decoder track or Media3 Ready state does not supply it.
Untested ABI/device behavior remains unqualified. Preserved license inventories
and archive exclusion also leave the recorded distribution review outstanding.

## Immutable implementation cohort

| Component | Reviewed implementation revision | Evidence scope |
| --- | --- | --- |
| Design | `83d338b6ffc1fc5e7f14ad4059f6159b8ee84509` | Approved closed native v1 contract |
| Shared client core | `91c5a4f53845ce0579f47caf1d57a74446710247` | Strict private transport, shared scope/recovery/clock decisions; actual native/WASM parity |
| Generic gateway | `764e518b66e024ac18d6dd7f14e489b696b8301e` | Maintained task ownership, network/metadata policy, selected acquisition, disk/full-payload accounting and normal UniFFI metadata cardinality |
| Backend | `65650c32615b35aa58e3fe424bac9d063add41ec` | Authenticated negotiation/admission/renewal, exact source and request tombstones; production policy disabled |
| Android player integration | `1063a99` | Private native Media3 boundary and explicit recovery; production qualification disabled |

The backend's committed `server/shared/NATIVE_CONTRACTS.json` records its
independent source exports: core `28e1149` and generic policy `f0f8c0d`. Do not
replace those records with the later client-core pin or imply a backend build
used the client cohort. Android's `CORE_REF`, `TORRENT_REF` and corresponding
vendor locks identify the actual imported sources; the normal artifact importer
and APK inventory checks bind packaged bytes to those locks.

The final normal development APK was built from Android source inputs
`e1da3d1ea48c4820f04bb616bc362d3663acd38a`, including controller `1063a99`.
Its SHA-256 is
`6b0e7be3a3d95bc0eed57a38e222da4c32bfe604396286d2c52ec52ee65c15ab`.
Subsequent artifact-verifier commits `0ee5fe1` and `fe9aaa2` validate SDK 36
manifest output and compiled trust resources without changing that sealed APK.
Normal packaged-byte, manifest/trust, alignment, unit, APK, lint and test-APK
checks pass. Actual Android JNI loading and runtime network policy remain
NOT RUN for every ABI in that report; the configured ARMv7 device was offline,
and no ARM64 or x86_64 device was configured.

| Normal gateway library process ABI | Packaged SHA-256 |
| --- | --- |
| armeabi-v7a | `88dc15e1fab490d023996ce6255db34313ef3166aa340a73dc39212427be83a6` |
| arm64-v8a | `3a1ab0ef7737389d0309c585665f3b5b3852aaa2978bb5687a457e85dab2c636` |
| x86_64 | `7b260575187393a6e7f1f9117f80253c81dbda1d7ca762e5601748099bf3eb4e` |

These hashes identify normal build evidence, not a capability-enabled release.
The inventory contains 264 archive-free packages and 16 toolchain notice
entries; its distribution clearance remains false. Separate fixture APKs and
their actual device results must retain their own hashes and qualification scope.

## Evidence required for capability

| Gate | Host evidence | Android/device evidence still required for enablement |
| --- | --- | --- |
| NT-01 negotiation | Core/backend closed schemas, fallback/auth refusal, Android bounded control and request shaping | Actual qualified start and old-server/disabled-policy behavior on the target artifact |
| NT-02 admission/input | Backend exact authorized VOD/source/index proof, vetted metadata and fetch; Rust private bridge | Authorized exact-index JNI/Media3 path, wrong-index/hash/size refusal without another file or transport |
| NT-03 grants | Actual Rust/Android control clocks, request sequencing, heartbeat and retirement effects | Preparation/pause renewal, suspension/foreground, expiry/revocation and scoped cleanup with real transport |
| NT-04 isolation | Real owned engine/FFI token/range/cancellation fixtures plus sticky failed settlement | Total startup and joined reader/listener/peer quiescence measurements on supported devices; unresolved blocked-OS-IO bound |
| NT-05 network | Generic IPv4/IPv6/DNS/DHT/TCP policy and metadata/allocation fixtures | Separately authorized public-peer egress/resource qualification and pinned bootstrap inventory |
| NT-06 cache/lifecycle | Real filesystem lock/restart and full-payload/disk/accounting tests; controller effects | JNI/Media3 candidates, overlapping grants, Back/scope/process lifecycle and physical retained/deleted ownership |
| NT-07 artifacts | Archive-free normal dependency graph, notices/checksums/source stamps, ABI/ELF/APK alignment and normal build | Actual supported process-ABI loading, compatible minSdk and completed distribution review |
| NT-08 Android TV | Canonical Rust decisions and actual controller effect regressions | Owned exact-episode decoded playback, missing-piece seek, tracks/cues, pause, history/focus/remote/lifecycle and physical audible confirmation |

Record PASS, FAIL or NOT RUN for each scenario and evidence class. A host pass
cannot stand in for an Android pass, an emulator cannot stand in for physical
hardware, and a fixture cannot stand in for production public networking. A
skipped ABI load test is NOT RUN. Remote/audio/UI observations must identify what
was actually observed, preserving the owner's account, server and history.

## Production and fixture separation

- Normal gateway artifacts select the `torrent` facade graph, with engine
  defaults disabled. Normal graphs exclude `archive`, `unrar-rs`, the control
  server and `test-network-policy`; the owned-private constructor is gated by
  that last feature. Normal generated bindings and libraries must come from the
  same committed source and retain the original source/dependency notices.
- Android normal source selects `newNativePublic` and app-private
  `noBackupFilesDir`; its runtime load fact never raises qualification. The
  private grant bridge, transient capability and random cache/scope receipts do
  not become generic launches, saved state, backup, logs or history inputs.
- Normal application trust is system CA only. Its cleartext exception is only
  literal IPv4 `127.0.0.1`, without subdomains; neither localhost/LAN aliases nor
  a global cleartext exception is approved. Application backup is disabled.
- Isolated owned-policy libraries, backend fixtures, fixture CAs and qualified
  controller overrides belong in explicit debug/instrumented fixture builds.
  Their APKs, libraries, hashes and results are recorded separately and must not
  replace the normal `TORRENT_REF` artifact cohort or count as a distribution
  candidate. Build normal APKs without fixture properties and check their real
  packaged bytes/trust configuration, rather than relying only on source XML.

The implementation and guard details live in
[artifact adoption](NATIVE_TORRENT_ARTIFACTS.md),
[control](NATIVE_TORRENT_CONTROL.md),
[coordinator](NATIVE_TORRENT_COORDINATOR.md) and
[player boundary](NATIVE_TORRENT_PLAYBACK.md). Qualification evidence and private
environment/device values stay outside tracked documentation.

## Concrete scoped enablement proposal

The current proposal distributes no capability-enabled APK and changes no
backend policy. Keep the reviewed source pins and build the normal development
APK through the documented importer/check flow. Record its final Android commit,
APK SHA-256, three gateway ABI hashes, three core ABI hashes, artifact-lock hash,
normal trust evidence and signing identity in the ignored qualification record.
Record any fixture APK as a separate artifact with no production qualification.

When every applicable gate passes, prepare a separate reviewed change that
replaces Android's disabled qualification fact with a predicate bound to the
qualified platform/process ABI and exact artifact cohort. It must require
successful native facade initialization, cache ownership and a suspend-aware
clock, and must not introduce a fixture owner, private peer selector or test CA.
Preserve native-field omission on other clients and on any unavailable platform.

Prepare backend policy enablement as a separate explicit, scoped change in the
native admission registry. There is no supported environment flag to flip in
the current implementation. Specify the authorized account/device/platform
scope, admission limit and reviewable rollback before adding an operator switch;
do not replace the global false default with unconditional admission. Keep
protocol support reporting separate from policy and preserve unsupported-client,
HTTP/direct/gateway/live and explicit-recovery behavior. An approved source patch
is not evidence that a deployed environment runs it.

Only after those concrete patches/artifact hashes and the complete evidence are
reviewed can the owner authorize distribution or environment activation through
the private deployment runbook. No build/CI success, local install or served
protocol response supplies deployment proof. Verify any separately authorized
deployment with the private runbook's actual served asset/artifact revision.

## Rollback

Disable new native admission and restore Android qualification to false for the
affected cohort. Retire active/pending native authority through the existing
player-stop, control invalidation, joined acquisition/cache and backend
release/request-tombstone path; hiding capability alone does not retire grants.
Reject late results and retain quarantine/accounting when settlement fails.
Restore the previously approved disabled normal artifact with its original
signing identity while preserving application data and the selected server.
Ordinary authorized delivery remains available. Do not silently replace an
already-admitted native source with gateway/transcoding; existing explicit Retry
still needs retired authority, exact-source identity and a fresh request ID.

Backend rollback restores the false admission policy and existing resource
revocation/expiry semantics. Do not claim immediate remote peer revocation from
removing protocol support; disclosed torrent identity cannot be recalled from a
modified client. Preserve deployment data/volumes and use only the owner's
separately authorized runbook for an environment rollback.
