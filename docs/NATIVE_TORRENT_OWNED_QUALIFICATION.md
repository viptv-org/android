# Owned native qualification evidence

Ticket 12 implements the isolated owned-media harness. Android TV qualification
remains **NOT RUN**: the configured physical TV was offline in ticket 07's trusted
device attempt. This run did not contact another device or start an emulator.
Production native advertisement and admission remain off.

## Actual results

The Linux run used the real isolated backend authorization middleware, canonical
Rust bridge, generated UniFFI binding and release torrent engine. Its literal
loopback TCP peer supplied real BEP9 metadata and owned pieces with DHT disabled.
Exact selected episode prefix/middle bytes matched SHA256; a separately authorized
same-file grant remained readable after the first grant retired. Grant acceptance
through native readiness took **119 ms**, and the common local native/control join
took **4 ms**. The complete JUnit case passed in 4.449 seconds. These observations
do not prove Android JNI loading, Media3 decode or arbitrary blocked OS IO settlement.

- Owned Python TCP/metadata/piece-wait/TLS/range/provenance checks: 13 PASS.
- Backend default checks: 294 PASS, 4 ignored; final fixture source compiled and
  exercised through the actual Linux authorization/native pipeline.
- Isolated Android app and instrumentation APK assembly: PASS, JDK 17 / SDK 36.
- Normal Android root/app unit suites: 47 / 283 PASS; APK assembly and lint PASS.
- Actual Gradle fixture release and CA-without-isolation requests: refused as required.
- Three fixture ELF ABIs: LOAD alignment at least 16 KiB; only libdl/libm/libc needed.
- Actual fixture APK: private package, non-exported fixture Activity, separate inputs,
  fixture CA, all three stamped ABIs/core/JNA, notices and 16 KiB ZIP alignment PASS.
- Normal APK rebuilt afterward: system CA/literal-loopback policy, pinned normal
  ABI bytes and notices PASS; fixture constructor, Activity, configuration and CA absent.
- Final host/backend/peer/TLS logs and safe host result contain no synthetic bearer,
  info hash, magnet input or byte token URL.

Prior canonical core/gateway checks and native/WASM parity are recorded by their
own tickets; their immutable implementations were consumed without rechecking them.
Device state/preferences, human sign-in, physical sound, remote/focus, Media3 cues,
pending-piece seek and device settlement remain unobserved.

## Provenance

| Input | Revision |
| --- | --- |
| Android fixture/runtime source | `9f07a3b` |
| Normal isolation checker | `7394c14` |
| Backend final fixture | `ef7f2a7` |
| Backend complete default check | `9a78499` |
| Core | `91c5a4f53845ce0579f47caf1d57a74446710247` |
| Gateway normal and fixture source | `764e518b66e024ac18d6dd7f14e489b696b8301e` |
| Design | `83d338b6ffc1fc5e7f14ad4059f6159b8ee84509` |

The final backend amendment changes only the ignored fixture's device principal and
allowed profiles. Normal gateway products exclude `test-network-policy`; private
products include it and use `newNativeOwned` with explicit owned TCP peers. Source,
compiler, Cargo.lock, feature, metadata-library and product hashes are stamped in
the ignored private artifact manifest. No vendor snapshot was changed.

| Sealed APK | SHA256 |
| --- | --- |
| Fixture | `9db7109eb1e1dff47fd10e55706cdb9087323b9eafcd19b7a8e4d8af710abec0` |
| Fixture instrumentation | `b02e7f0cc8a452e65668a4a968d408b68110dfce7d2d57b465e4393235fa3dfb` |
| Normal rebuilt after fixture | `7085c886ce559086ad2899f935b4aa19a43ce93ce1a703fc8585bc738a465039` |

| Fixture gateway product | SHA256 |
| --- | --- |
| Kotlin | `8a61dd1fb8800c3b0291f817038bef1b212555b305c0149e4a42399933cf1175` |
| Linux host library | `acc6967b30dd4a1cd900ac8bef5bc3b571291b4350e84b47823d3725c982c8df` |
| ARMv7 | `4881f6a9001c61b1f348ae2dc811836ee21e5ac8d085f25236f103bc23ba2c73` |
| ARM64 | `ec819507926eff725b327708b7fdd7b7d62c8f5576555d1074dfe188105114e0` |
| x86_64 | `ff2694e774ca50fdbd90bcb6728d16f3992e9c3c286577206f0ebc6786b8e6c1` |

## Acceptance accounting

| ID | Complete Android qualification | Supporting ticket-12 observation |
| --- | --- | --- |
| NT-01 | NOT RUN | Actual Linux authenticated `[1]` negotiation and canonical advertise decision PASS |
| NT-02 | NOT RUN | Actual authorized canonical hash/index and optional-size selected bytes PASS |
| NT-03 | NOT RUN | Real grant receipt/read guard/control disposal; device heartbeat/expiry/revocation tests compiled |
| NT-04 | NOT RUN | Real Linux startup, joined retirement, retired URL refusal and independent grant PASS; device pending-body/metadata tests compiled |
| NT-05 | NOT RUN | Owned literal TCP only and DHT=false; public-peer qualification not attempted |
| NT-06 | NOT RUN | Actual Linux owned cache closed with zero held capacity; device scope/refusal/privacy tests compiled |
| NT-07 | NOT RUN | Host/three-ABI/APK checks PASS; all Android ABI loads and distribution review remain open |
| NT-08 | NOT RUN | Full actual-controller/Media3/track/cue/ordinary compatibility instrumentation compiled; no device execution |

## Handoff

Use [the harness runbook](../qualification/native-torrent/README.md) for fresh
private configuration, backend/TLS/peer preparation, explicit fixture build flags,
instrumentation selectors and result collection. Sealed APKs and all private logs,
hash manifests and detailed evidence are under `.scratch/native-torrent/`; no
credentials or configured device addresses belong in tracked documents.

The full selector is `org.viptv.app.OwnedNativePipelineTest`. Its dedicated
`#manualOwnedNativeObservation` selector requires `ownedNativeManualSeconds=180..300`
and renders the real MainActivity/player/Sources/remote handlers. Run it separately
against a fresh backend/configuration and matching APK assets; the full class's
final case intentionally mutates its synthetic producer/session. Its physical sound
and remote/focus fields require separate human verdicts. See the runbook for safe
`no_backup/owned-*-evidence.json` collection from the fixture package.

All three owned listeners were stopped and verified closed after this run. No APK
was installed, no device app/preferences/server origin changed, and no deployment,
push or production activation occurred. Cargo, Gradle and device windows are free.
Arbitrary OS IO two-second settlement remains an open gate even if a future owned
device run passes.
