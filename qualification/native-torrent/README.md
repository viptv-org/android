# Owned Android native qualification

This harness connects the real test-only backend router and authorization middleware,
canonical Rust core, strict generated UniFFI engine and Android Media3. All media and
credentials are synthetic. It is a qualification tool, and does not enable native
production advertisement or admission.

The normal gateway source pin is also the fixture source pin. The fixture builder
adds `torrent,test-network-policy` in a separate private output directory; its
`newNativeOwned` constructor supplies only explicit literal-loopback TCP peers and
disables DHT. Normal generated bindings, native snapshots and APKs must exclude this
constructor and the fixture Activity. The production control request retains
`public_dht_tcp_v1`; private policy is an engine injection, never a wire capability.

## Windows and private inputs

Coordinate the single Cargo/Gradle/device worker before building or connecting.
Use the configured device from the ignored workspace device notes and environment.
Never create an emulator on the VPS, modify wireless debugging, replace an interactive
server origin, clear a normal app, or read another package's preferences. The fixture
APK has the `.nativefixture` application ID suffix and its own session, data and
no-backup directory. It may be installed only after the normal APK proof is recorded.

Generated media, manifests, TLS keys, grant configurations, APKs, runtime logs and
screenshots stay under an ignored private directory, with directory mode 0700.
`serve_pipeline.py` sets umask 077. Synthetic bearer tokens occur only in that private
configuration and isolated fixture APK assets. They must not enter tracked documents,
issues, normal artifacts, or command output. The ingress suppresses access logging.
Do not print the configuration or backend readiness JSON.

## Prepare

Run the inexpensive deterministic harness checks first:

```sh
python3 -m unittest discover -s qualification/native-torrent -p 'test_*.py' -v
python3 qualification/native-torrent/owned_fixture.py generate PRIVATE_MEDIA_DIRECTORY --seconds 40
```

The torrent contains a readme at index 0 and H.264/AAC episodes at indices 1 and 2.
Both episodes have real English/Spanish AAC and mov_text tracks, with timed owned
subtitle cues. `manifest.json` records canonical info hash, metainfo and file SHA256,
full payload length, explicit indices and prefix hashes. No tracker, webseed, private
flag or peer hint is present in metainfo. The TCP peer serves BEP3 pieces and BEP9
metadata only for that exact hash, verifies payload hashes before listening, and binds
literal `127.0.0.1`. Atomic private hold files allow actual metadata/piece waits.

Within the assigned Cargo window, build separate fixture artifacts:

```sh
python3 qualification/native-torrent/build_fixture_artifacts.py ../playback-gateway PRIVATE_ARTIFACT_DIRECTORY --abi armeabi-v7a --abi arm64-v8a --abi x86_64
```

The source must be committed and unchanged throughout the build. Products have a
separate source/features/Cargo.lock/compiler/RUSTFLAGS/hash stamp. They never overwrite
normal vendor or generated directories. The explicit fixture Gradle property verifies
every stamped product and the normal source pin before compilation. Normal artifact
verification and notices remain required.

Compile the ignored backend fixture without starting production services:

```sh
CARGO_BUILD_JOBS=1 cargo test --manifest-path ../backend/server/Cargo.toml --lib --no-run --message-format=json
```

Use the `viptv_server` test executable from compiler-artifact JSON as the next command's
private executable input. The ignored test
`gateway::torrent_native_acceptance::actual_backend_android_native_server` creates
an in-memory account/profile/vault/addon and exact source proofs, enables admission
only in that test process, and binds literal loopback. It expires after 900 seconds.
Index 99 is an intentionally invalid exact-file fixture. This proves real middleware
and lease authorization with a synthetic pre-issued session; it does not prove the
human sign-in UI. A second owned profile permits actual profile scope invalidation.

```sh
python3 qualification/native-torrent/serve_pipeline.py PRIVATE_MEDIA_DIRECTORY --backend-executable PRIVATE_BACKEND_TEST_EXECUTABLE
```

The finite server environment writes `pipeline-config.json` and a one-day loopback
TLS certificate. The ingress forwards only `/api/` to the real backend and has a
fixture-only authenticated control endpoint for held pieces/metadata and revocation.
No public listener or ordinary production API is added. The fixture process owns only
its synthetic database. Stop the environment after the device run.

## Isolated Android build and device proof

After the normal APK build and security probe, build the separate fixture APK using
the explicit private artifact/configuration properties and generated fixture CA:

```sh
./gradlew --no-daemon --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest -PnativeTorrentFixtureArtifacts=PRIVATE_ARTIFACT_DIRECTORY -PnativeTorrentFixtureConfig=PRIVATE_MEDIA_DIRECTORY/pipeline-config.json -PfixtureCa=PRIVATE_MEDIA_DIRECTORY/tls/cert.pem
```

The fixture property must reject release tasks and preserve the normal prebuild
integrity check. Its generated Kotlin/JNI inputs replace the normal gateway inputs,
and its Activity/test roots are compiled only for the isolated APK. Fixture trust
is available only with this explicit property. Do not change `TORRENT_REF` to admit
private policy into normal artifacts.

Use the configured ADB executable and selected serial from private notes to reverse
the chosen literal-loopback control and peer ports (defaults 19445/19446). Install
only the `.nativefixture` APK and its matching instrumentation APK. Run
`org.viptv.app.OwnedNativePipelineTest` with AndroidJUnitRunner. Do not uninstall,
force-stop, clear, reinstall or change preferences in `org.viptv.app`.

The tests cover actual decoded surface pixels and cue delivery, real alternate track
confirmation, exact selected bytes, token/index refusal, pause renewal and foreground
revalidation, independent overlapping grants, metadata Back cancellation, wrong-index
and aggregate capacity refusal, a held real HTTP piece body with concurrent joins,
actual scheduled heartbeat and elapsed expiry after a test-only heartbeat worker
fault, ordinary HTTP/HLS and a 404 negotiation endpoint, and actual AppController
identity restoration/Media3/return-position/paused producer refusal/profile/sign-out
cleanup. The expiry test cancels only the heartbeat job through test reflection;
the real clock, actual grant deadline, expiry job and native owner remain intact.
Each cancellation measures local native/HTTP joins under one two-second interval;
remote release follows separately. These owned cooperative waits cannot qualify an
arbitrary OS filesystem call that is blocked beyond the join deadline.

Safe result JSON is written inside the fixture's no-backup directory. Collect it
with `run-as` for the fixture package, alongside APK/ABI checksums, exact owning
repository revisions and the private instrumentation log. Human physical sound,
remote/focus observation, ordinary HLS/older-server device compatibility, sustained
public-peer/resource tests and blocked OS IO remain separate observations. A
clock/position/selected-audio event does not prove audible sound.

## Result accounting

Record PASS, FAIL or NOT RUN separately for NT-01 through NT-08, and distinguish
host, emulator and physical evidence. A partial fixture run never makes the entire
acceptance ID pass. Missing-piece/body/metadata tests must have been demonstrably
pending before cancellation, and failed settlement must retain its owned manager,
payload and charges. Preserve failed cache trees for diagnosis, without copying
native secrets into diagnostics. No successful owned fixture authorizes production
activation. See the local ticket 12 evidence and the rollout matrix for current runs.
