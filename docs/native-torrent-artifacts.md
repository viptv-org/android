# Native torrent dependency artifacts

Authority: Android's pinned
[authorized native torrent contract](../design-contract/specs/behavior/torrent-native-android.md).
Artifact integrity is not playback qualification. Native capability stays disabled
until the contract's transport, authorization and device gates pass.

## Producer and trust boundary

Build on the VPS gateway checkout at one committed revision. Run the gateway's
native dependency guard, required Rust tests and binding generator, then its
Android ABI build with a provisioned Linux NDK. Kotlin and native libraries must
come from that same revision. Transfer only generated artifacts and their notices
into this Android checkout; do not create an independent Windows gateway clone.

Provision a Linux NDK before building ABI libraries. Native lifecycle/network
qualification is separate from toolchain provisioning. Do not import fabricated
or stale libraries merely to make a pin check pass.

A trusted build bundle contains `manifest.json` with exactly these fields:

- `version`: `1`.
- `repository`: `viptv-org/playback-gateway`.
- `revision`: the full lowercase 40-hex committed gateway revision.
- `files`: an object mapping each relative file path to its byte-exact lowercase
  SHA-256 digest. No line-ending normalization is applied.

Required paths:

- `LICENSE`, `PROVENANCE.md`.
- `ffi/generated/kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt`.
- `ffi/generated/android/jniLibs/armeabi-v7a/libplayback_gateway_ffi.so`.
- `ffi/generated/android/jniLibs/arm64-v8a/libplayback_gateway_ffi.so`.
- `ffi/generated/android/jniLibs/x86_64/libplayback_gateway_ffi.so`.

Flat `THIRD_PARTY/*` notice files may also be pinned. Source/binding compatibility
and the manifest's relationship to the committed build must be established by the
trusted producer and build record. A revision label and hashes alone are not a
signed build attestation. Dependency/license inventory and dynamic dependencies
need independent checks; archive exclusion alone is not distribution clearance.

## Import and check

With the local Windows tools loaded:

```powershell
node scripts/native-torrent-sync.mjs sync .scratch/native-torrent/artifacts/bundle <full-gateway-revision>
node scripts/native-torrent-sync.mjs check
node --test scripts/native-torrent-sync.test.mjs
```

The importer validates required files, hashes, ABI ELF machine/class, shared-object
headers and every load segment's bounds/congruence and at least 16 KiB alignment
before writing. It imports into `vendor/playback-gateway/` and writes `TORRENT_REF`
and the snapshot's `lock.json`. Existing snapshot edits, unpinned files, link paths
and incomplete inputs cause refusal rather than overwriting owner work. Removing
an imported file requires an explicit migration; the importer never deletes files.
Use an exclusively owned bundle/destination during import; concurrent mutation is
not a supported transport or transaction protocol.

No artifact snapshot or TORRENT_REF is created by adding this tooling. A missing
snapshot fails `check`. The script does not add Gradle source sets, package JNI
libraries, permit localhost networking or advertise a client capability. Connect
those platform effects only through the approved integration tickets.

## Qualification limits

Parser tests use synthetic ELF headers to test validation and file operations;
they do not establish JNI loadability, torrent networking or decoding. Actual
NDK-built libraries still require dynamic-dependency, ELF and APK ZIP-alignment
checks, matching UniFFI loading on supported ABIs, and owned-media authorization
through the real native engine and Media3. Record cancellation/settlement,
exact-file denial, independent grants, lease rejection and scope-change cleanup
before enabling native playback on the owner's TV. Preserve sign-in/data when
installing the normal debug APK. Do not use fixture trust as production policy.
