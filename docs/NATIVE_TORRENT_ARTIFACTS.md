# Native torrent artifact adoption

`TORRENT_REF` and `vendor/playback-gateway/lock.json` pin the generated Kotlin,
three Android libraries and dependency notices from one immutable gateway
revision. Treat this snapshot like `vendor/core`: regenerate at its source and
use the importer; do not edit vendored bindings, notices or checksums.

For observed startup stages, visible error codes and safe device log collection,
see [native source diagnostics](NATIVE_TORRENT_DIAGNOSTICS.md).

The gateway's native feature graph excludes archive extraction, `unrar-rs`,
the control server and fixture network policy. The dependency inventory includes
normal and build dependencies for armeabi-v7a, arm64-v8a and x86_64, their package
licenses and preserved notice texts. The snapshot also preserves the exact
source Cargo lock and installed NDK/Rust supplier notices, with checksums; supplier
notices may include build tools that are not linked into the application.
GPL-2.0-only gateway sources and Apache-2.0
dependencies retain separate distribution obligations. An inventory and archive
exclusion do not establish compatibility or permission to distribute.

From a clean, committed gateway checkout, regenerate Kotlin at the source and
commit any generated change before building the source-stamped native libraries:

```sh
./ffi/scripts/build-bindings.sh
# Commit the reviewed source and generated binding before the native build.
CARGO_BUILD_JOBS=1 ./ffi/scripts/build-android.sh
python3 ffi/scripts/export-android.py /private/artifact-directory FULL_GATEWAY_SHA
```

The exporter rejects dirty or mismatched source, missing license texts,
unverified non-system dynamic dependencies and native products whose source
stamp/checksums do not match. Build with API 24 and the configured NDK, using
16 KiB page alignment. The artifact directory is a local build output rather
than a source repository.

In Android, import the checked bundle and run the normal JDK 17 / SDK 36 checks:

```sh
node scripts/native-torrent-sync.mjs sync /private/artifact-directory FULL_GATEWAY_SHA
node scripts/native-torrent-sync.mjs check
node --test scripts/native-torrent-sync.test.mjs
bash scripts/prepare-core.sh host
./gradlew --no-daemon --max-workers=1 :testDebugUnitTest :app:testDebugUnitTest
bash scripts/prepare-core.sh android
./gradlew --no-daemon --max-workers=1 :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
python3 scripts/check-native-torrent-apk.py app/build/outputs/apk/debug/app-debug.apk
```

Gradle adds the gateway Kotlin/JNI directories independently of core and JNA.
Its prebuild integrity check prevents unnoticed snapshot changes. The APK
contains the gateway license, provenance and full dependency notices under
`assets/playback-gateway`; the APK check verifies their exact pinned bytes,
all three gateway/core/JNA libraries and 16 KiB ZIP alignment. It inspects the
compiled manifest and XML policy to verify system-CA trust and a cleartext
exception only for literal `127.0.0.1`. Runtime policy and ABI loading remain
separate instrumented checks.

`NativeTorrentArtifacts.isLoaded()` initializes the actual generated facade
without starting transport. Missing ABI, linkage failure or failed facade
initialization produces an unavailable platform fact. Normal debug and release
builds use this fact with the supported process ABI, suspend-aware clock,
literal-loopback policy and available private cache owner before advertising
native capability. No enablement receipt or target-device allowlist is required.
Loading remains separate from decoded playback and device qualification.

For each actual supported process ABI, coordinate the configured device window
before installing either APK. Preserve the owner's application data, sign-in,
backend origin and history. Run only `NativeTorrentArtifactLoadTest`, supplying
the explicit `nativeTorrentExpectedAbi` instrumentation argument. The test checks
the process architecture, calls the actual generated gateway facade, opens the
real core facade and verifies Android cleartext policy. Without that argument
the ABI loading test skips; a skipped or synthetic ELF test supplies no loading
evidence. Record each ABI separately, including untested devices, before a
qualification claim. Native loading does not prove decoded or audible playback.
