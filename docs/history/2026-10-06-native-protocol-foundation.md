# 2026-10-06 — inactive native-torrent protocol foundation

This records local implementation and verification, not deployment or native
playback qualification. The owner authorized narrow local commits only; no push,
service restart, native activation or TV VM update occurred.

## Immutable sources

- Approved design: `83d338b6ffc1fc5e7f14ad4059f6159b8ee84509`.
- Canonical core: `4ca402587df7dbc842d0db522052b1d397f5a03f`.
- Gateway selected-file foundation: `f9bf8d7002422e7d65c879dc22f9e78f072c94d6`.
- Android artifact validator: `81f9cf1`; no gateway ABI snapshot was imported.

Core adds strict original-text `playbackProtocolV2` validation and bodyless
GET `/api/v2/playback-protocol` / DELETE `/api/v2/playback-requests/:request_id`
request construction. Existing Android intents do not advertise native support.
Private grants, backend authorization/tombstones and native session adaptation
are not part of this slice. Android Kotlin and TV-web WASM adopt the same core
revision through their source-owned synchronization scripts.

Gateway's opt-in selected starts restrict file metadata, URLs and reads to the
explicit index and issue independent capabilities for overlapping grants.
Legacy starts and explicit clone aliases retain their shared-stop semantics.
Shared payload reservation does not make grants aliases.

## Observed verification

- Canonical core: 110 tests each for default/all-feature workspace runs;
  formatting and strict Clippy pass. Actual native Rust/WASM parity passes
  90 raw-text vectors and nine baseline legacy vectors. Repeated generation
  produces identical artifacts.
- Gateway: three owned-seeder regressions fail before the selected-lease fix;
  14 engine / two FFI focused fixtures pass afterward. Final workspace passes
  169 tests with 55 existing ignored, inside a loopback-only network namespace.
  Matching Kotlin binding generation is reproducible.
- Independent read-only review finds no blockers for inactive adoption of the
  two foundation commits. It does not approve capability activation.
- Android host native consumer regression: two of three tests fail against the
  preceding pin, then all three pass against the adopted pin. Tests exercise
  original JSON text through actual UniFFI, generated DTO decoding, bodyless
  request construction and preservation of the native-absent client shape.
- Windows JDK 17 / SDK 36 flow: host-core preparation, library/app unit tests
  (47 + 231, zero failures/errors/skips), Android core preparation for the three
  supported ABIs, APK assembly and lint all pass. No fixture CA build property
  is used. This is not JNI torrent or Media3 device evidence.
- TV-web: lockfile dependency installation, 285 tests across 55 files and
  production build/typecheck/integrity checks pass. No browser/physical-TV
  qualification is claimed for this inactive contract import.
- Android core/design integrity and whitespace checks pass. Owner source-picker,
  padding and documentation edits are excluded from the adoption commit.

## Reproduced cancellation gap and qualification limits

Two isolated probes against real librqbit initialization/storage show that
`Session::stop()` returns after approximately one second while a controlled
initialization read remains active beyond two seconds. Releasing the owned read
allows cleanup. Probe exit zero establishes reproduction, not successful
bounded cancellation. No timeout-only facade was added.

Required work includes a maintained, provenance-pinned dependency completion
layer; cancellable pre-handle acquisition with one total deadline; joined reader,
listener and dependency settlement; public-only TCP/DHT/bootstrap policy and
pre-allocation metadata bounds. Failed settlement must retain accounting and
fail native admission rather than free active resources.

Backend/core private grants, admission and renewal/revocation, Android cache and
session ownership, matching native ABI artifacts and the real authorization ->
JNI -> token-protected selected localhost bytes -> Media3 device pipeline remain
unimplemented/unqualified. Native capability stays disabled. Existing sign-in,
server selection, providers and data are untouched.
