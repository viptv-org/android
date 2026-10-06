# Native torrent artifact validator — 2026-10-06

Scope: Android dependency-import tooling only. No native snapshot, gateway JNI
library, Gradle source set, local byte endpoint or client capability was adopted.
The inspected installed TV APK contained no gateway torrent library. This record
does not establish native playback readiness or distribution/license clearance.

`scripts/native-torrent-sync.mjs` validates a trusted bundle's expected immutable
revision, byte hashes, required three ABIs/binding/notices, ELF target/load bounds
and 16 KiB alignment. Existing owner edits, unpinned files, path/link aliases,
case collisions and predictable destination-type conflicts refuse before writes.
`.gitattributes` preserves snapshot bytes instead of applying Windows text
conversion. Procedures and separate qualification gates are in
[Native torrent dependency artifacts](../native-torrent-artifacts.md).

Verification on the Windows Android development machine:

- The initial 21 importer/parser tests passed.
- Independent static review identified five defects. Seven concrete Windows/ELF
  regressions failed before correction, including hard-linked overwrites,
  root/ancestor/dangling metadata links, case-fold aliases, partial writes on a
  type conflict and a floating-point alignment false positive.
- Corrected suite: 32 tests passed, zero failures/skips. Added device-basename,
  trailing-dot and hidden notice-name rejection. Synthetic ELF headers exercise
  parsing only; no JNI/codec/network behavior is simulated or claimed.
- Independent re-review approved the bounded importer. The reviewer inspected
  all test definitions but did not independently execute Node tests.
- Node syntax and Git whitespace checks passed. `git check-attr` reported text
  unset for generated Kotlin and native-library snapshot paths.
- Host core preparation and both Android unit-test Gradle tasks passed; the unit
  tasks reused their unchanged passing results. No APK rebuild/device run is
  needed to qualify standalone Node tooling, and none is claimed for this slice.

Private command outputs are `.scratch/native-torrent/artifact-validator-*`.
The real gateway/core/backend/native coordinator and device integration are
separate unfinished tickets. No push, service restart or deployment occurred.
