# Shared torrent runtime v2

Android and Android TV use the immutable Go runtime named by
`TORRENT_RUNTIME_REF`, behind JNI in the non-exported `:torrent_runtime` service.
The UI process retains Rust core, Media3, accounts, history and subtitles.
`CORE_REF` matches the viewing client's core revision. Backend negotiation uses
`/api/v2/torrent-runtime-protocol`; unavailable native torrent support produces
an explicit refusal. Native torrents never retry through the gateway.

The private worker protocol travels through bounded, length-prefixed pipes;
Binder carries file descriptors and the owned child PID. Metainfo-sized requests
never use Binder's transaction buffer. Closing retires the handle, and the host
terminates its child when cleanup cannot settle within two seconds. Stale handle
cleanup cannot terminate a replacement worker.

The cache lives in app-private, excluded-from-backup storage. Verified pieces
survive playback stop, process restart and profile replacement. The shared
runtime owns the aggregate 2 GiB default, free-space reserve, hashing and rolling
eviction. Sign-out and principal replacement clear the content. Cached content
never replaces fresh backend authorization.

The core's private holder checks original response bytes, scope/generation,
renewal, clocks, optional file selection and fixed resolved file/member identity.
The Android clock includes suspension. One 120-second elapsed startup budget
includes worker/cache preparation, control and player opening; the runtime also
refuses 60 seconds without meaningful progress. Renewal continues during
preparation. Media3's rendered-frame event acknowledges the first decoded frame;
endpoint readiness and player Ready do not acknowledge it. Factual stage labels
and specific archive/storage/authority failures come from core.

`TORRENT_REF` and `vendor/playback-gateway` retain v1 artifacts for development
comparison during qualification. The comparison library is packaged in debug
builds, and its generated facade is available to instrumentation. Production
source does not call that facade; performance builds package only the shared Go
transport. Gateway benchmarks retain the existing engine comparison separately.

## Artifact adoption

Build the owning gateway at a clean committed revision with
`torrent-runtime/scripts/build-native.py android`. Then import the checked
bundle with:

```sh
GO=/path/to/go python3 scripts/torrent-runtime-sync.py sync ../playback-gateway /path/to/android-artifacts
python3 scripts/torrent-runtime-sync.py check
```

The importer pins hashes, all three ABIs, API 24-compatible artifacts, transitive
dependency notices and 16 KiB ELF alignment. The actual APK checker verifies
packaged hashes and ZIP alignment. Building or importing artifacts does not
establish physical-device or production qualification.

## Local qualification, 2026-10-09

Core: `d1787f3910306822d68192a5d3c45f980a665234`.
Runtime: `11872567b94e5c3df763fef0742a91a3d030e629`.
The core passes native/actual-WASM parity and the runtime passes its complete
Go race suite and gateway Rust suite. Android's full library/application unit
suite passes before the final packaging cleanup; final checks are recorded below.
All three native core and Go/JNI ABIs build. The normal debug APK passes API 24,
three-ABI, checksum, notice and 16 KiB ZIP alignment checks.

An owned API 36 x86_64 Android TV emulator passed worker loading, process
isolation, a 2 MiB control request, invalid preparation and joined shutdown.
A separately identified, debug-only QA application decoded a privately recorded
failing H.264 source through the real Go worker and Media3 texture surface.
The 2.05 GB selected file was streamed with a 128 MiB cache. One cold run reached
its endpoint in 11.0 seconds and its first decoded frame in 14.0 seconds.
A subsequent process restart with retained verified content reached a frame in
1.74 seconds. Its 120-second forward seek completed in 15.3 seconds; backward
seek and worker retirement also passed. The earlier seek timer observed position
alone and is excluded from seek evidence.

These are single-source emulator observations. They do not establish general
public-swarm reliability, physical ARM/device loading, reference-client latency
parity or production rollout. Startup and seek performance remain follow-up
work in the gateway's `docs/TORRENT_PERFORMANCE_FOLLOWUP.md`.

Final normal-build checks pass: 53 library tests and 306 application tests,
all three immutable native ABI products, debug and optimized performance APKs,
performance lint under the existing baseline, APK byte hashes/notices/alignment
and FFmpeg audio artifact checks. Lint reports existing warnings and three
baseline-filtered errors; no baseline was expanded. The performance APK excludes
the v1 comparison transport and every QA Activity.

The latest artifact update preserves repeated tracker hints from magnets/metainfo
and add-ons by validating and deduplicating destinations before admission. All
three API-24/16-KiB libraries and the normal debug APK were rebuilt and checked
from the same immutable revision used by desktop and gateway.

## Phone and full-authority qualification, 2026-10-09

An owned API 36 x86_64 phone emulator decoded the original privately recorded
failing source through the shared worker and Media3. One cold observation reached
the endpoint in 65.5 s and its first rendered frame in 68.2 s. It is retained as
a slow-start observation. Restarting with verified cache gave a frame in 1.61 s;
a forward seek required 9.83 s and fresh TextureView updates after landing.
Backward seeking and worker retirement passed. A position-only 1 ms seek result
is excluded. Physical ARM/API24/16-KiB-page device playback remains separate.

The real backend/core/coordinator path exposed an app cache ownership defect:
its marker was being passed to Go's strict record directory. The manager keeps
ownership markers in `content-v2` and Go records in `content-v2/pieces`.
Actual-worker instrumentation covers this boundary. The corrected full-authority
run negotiated v2, received a native grant, acquired locally and rendered a
frame in 25.9 s, then joined native/cache retirement. No gateway fallback occurred.
Its backend account/profile/vault and source fixtures were ephemeral; ordinary
login/catalog screens were not driven. Source IDs, bearer and captures remain
private. One interrupted instrumentation run is excluded from evidence.

The shared runtime also retains resolved torrent/archive selection in budgeted,
checksummed cache records. Restart revalidates it against content metadata;
retained records never grant playback authority.
