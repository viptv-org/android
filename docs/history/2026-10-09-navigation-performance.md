# Android TV loading and navigation investigation — 2026-10-09

## Scope and evidence

Checkout: Android `685f66c181af4c5287e6729b95367409b1b391d1`, core
`df62d8a893bbfbd8c2cd471b07c40efa6a78a4aa`, design
`d9562790ea69d5e29cb4ec2630b814a7f30f9761`.
Measurements exercise the real Kotlin HTTP adapter, response normalization and
native core. They do not measure Compose rendering, remote response, network
performance of configured providers, or physical television hardware.

Other source/test edits appeared in the shared checkout while this investigation
was running. They were left untouched. The unit-test totals below describe the
completed runs, not blanket validation of subsequent concurrent edits.

Raw samples, build/test logs, screenshots, memory and frame dumps are private
under ignored `qualification/artifacts/performance-2026-10-09/`. No credentials,
provider addresses or account data are included in this record. Existing owner
edits in `TESTING.md` were preserved.

## Controlled loading measurements

`HomeLoadingMeasurementTest` makes 24 Home loads: four scenarios, three independent
fixtures per scenario, cold and warm metadata caches on each fixture. Each fixture
has twelve saved queue items, six catalog rows, empty favorites and valid empty
v2 Live pages. Slower dependencies each receive 250 ms of artificial latency.
Cold means a fresh gateway/metadata cache, not a cold Android process or JVM.
Warm means reusing that gateway; catalogs and providers still make HTTP requests.

Medians of three samples, milliseconds:

| Scenario | Cache | First saved queue | First catalog row | All Home work complete | Metadata HTTP requests |
| --- | --- | ---: | ---: | ---: | ---: |
| No injected latency | Cold | 14 | 72 | 158 | 6 |
| No injected latency | Warm | 47 | 98 | 146 | 0 |
| Slow catalog list | Cold | 5 | 259 | 348 | 6 |
| Slow catalog list | Warm | 4 | 299 | 385 | 0 |
| Slow metadata | Cold | 8 | 52 | 639 | 6 |
| Slow metadata | Warm | 4 | 48 | 133 | 0 |
| Slow catalog providers | Cold | 6 | 302 | 597 | 6 |
| Slow catalog providers | Warm | 45 | 297 | 593 | 0 |

The blocked-dependency test additionally holds catalogs and metadata behind a
latch, requires the actual saved queue callback before releasing either, and
asserts Home work has not completed. It passes. Timing values are diagnostics;
the tests assert ordering, row contents and cache request counts rather than
flaky wall-clock limits. Fixture startup/JIT, HTTP keepalive and host scheduling
affect the absolute values, especially the first cold sample (443 ms complete).

Measured conclusions:

- At the gateway boundary, slow catalogs/metadata/providers do not prevent the
  saved queue from appearing. First usable content and completion of all loading
  are distinct metrics.
- Six cold metadata requests, bounded to three at a time, extend completion to
  639 ms in the injected-latency case. Warm metadata removes all six requests
  and reduces the same scenario to 133 ms. This does not include artwork fetch,
  image decoding or presentation of the hero.
- Six provider rows, also bounded to three at a time, complete in about 597 ms
  with 250 ms injected latency per request. Metadata caching alone does not
  remove this provider tail.
- All these loads publish sixteen updates, including warm loads with zero
  metadata HTTP requests. These are gateway callbacks, not sixteen measured
  Compose recompositions; a trace is needed to assess their rendering cost.

## Passive emulator observations

The designated shared API 36 x86_64 TV emulator was inspected without installing,
restarting, sending input, switching server/profile or clearing app data.
The visible screen was title details, not Home. The installed APK hash was
`81a9a7315e7db2df60e9a0f6e0834bfd716b1bfc3b18a76c4c544ed378166fe7`, matching
the local debug artifact before this investigation's build. The hash identifies
the tested binary; the checkout SHA alone is not installed-binary provenance.

Accumulated, unbracketed counters reported 8,300 rendered frames, 7,567 janky
frames (91.17%), p50 81 ms, p95 129 ms and p99 200 ms. They cover earlier unknown
journeys and cannot attribute the jank to the hero, Home, networking or a specific
transition. They are a signal to investigate, not a baseline for comparison.

Three freshly reset eight-second idle title-detail windows each recorded zero
new frames. Frame percentiles are therefore undefined; Android's empty histogram
reported 4,950 ms, which must not be interpreted as a frame duration.
PSS was 176,630 / 175,502 / 175,490 KiB. This short settled-screen observation
does not establish navigation performance or rule out leaks over longer use.
It provides no evidence of continuous redraw on this particular settled screen.

The shared emulator's software GPU, debug APK and browser streaming environment
limit inference about TVs. Android's [performance measurement guidance](https://developer.android.com/topic/performance/measuring-performance)
requires release-like builds for representative performance assessment.
Use [system traces](https://developer.android.com/topic/performance/vitals/render)
to distinguish main-thread, rendering and scheduling work. `gfxinfo` alone is
insufficient to identify a specific CPU/GPU bottleneck.

## Remaining causal tests

The initial phase did not have a confirmed device window. The owner subsequently
authorized the separate hero emulator; Home idle, D-pad movement, Details return
and cold-process launch were measured there. See the
[hero emulator follow-up](2026-10-09-hero-emulator-performance.md) for the tested
candidate, thirty device windows, traces and remaining measurement limits.
Connected Compose tests were not run. The shared browser emulator remains
untouched by that follow-up.

Ranked hypotheses and falsifiable next measurements:

1. Rendering or focus/scroll work causes movement jank. Bracket Home horizontal
   focus changes, vertical shelf changes and title-details return separately;
   capture frame timeline and main/render threads. Compare identical warm-content
   journeys to exclude network waiting. Repeat each journey at least three times.
2. Cold hero metadata/artwork accounts for visible hero settling. Record profile
   selection → saved row, selected card → metadata ready, artwork request →
   decode ready, and decode ready → displayed image. Compare identical cold/warm
   selections. Existing `HeroArtworkRequestTest` checks bounded/shared Coil
   requests but its fetcher intentionally returns no image bytes; it cannot
   establish decode/GPU cost.
3. Initial controller sequencing delays the first saved rows. `AppController.loadHome`
   awaits `catalogRevision()` before calling `gateway.home()`, unlike the measured
   gateway-only scenarios. Inject revision latency at the real controller seam;
   if it gates landing, first saved content should shift by the injected delay.
   Preserve the requirement that revision precedes catalog-list fetching when
   evaluating overlap with independent saved-row requests.
4. Progressive publication adds avoidable UI work. Trace native projections and
   Compose work during the sixteen callbacks; if duplicate publications dominate,
   suppressing equivalent snapshots in an isolated comparison should lower
   main-thread time while preserving early-row ordering and cancellation.

These are hypotheses, not proven causes of the owner's reported slowdown.
No production loading, focus, visual or shared-core policy was changed.

## Repeatable commands

Use JDK 17, Node 22+, the configured SDK and one Gradle worker. Prepare the host
core before tests:

```bash
bash scripts/prepare-core.sh host
./gradlew --no-daemon --max-workers=1 :testDebugUnitTest :app:testDebugUnitTest
./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest --tests org.viptv.app.HomeLoadingMeasurementTest
```

For a coordinated device window, `qualification/measure-navigation.py` saves
installed APK hash, frame summary, raw frame stats and PSS inside ignored
artifacts. It requires an explicit serial and foreground VIPTV. Name the actual
starting screen and input sequence; inspect the screen before and after each
journey. It does not select an account/profile, verify route/focus success, or
measure input-to-visible latency. Start with an already loaded Home and repeat
each sequence from the same focus position:

```bash
python3 qualification/measure-navigation.py --serial emulator-5580 --label home-idle-1 --directory qualification/artifacts/performance-2026-10-09
python3 qualification/measure-navigation.py --serial emulator-5580 --label home-horizontal-1 --directory qualification/artifacts/performance-2026-10-09 --keys RIGHT RIGHT RIGHT LEFT LEFT LEFT
python3 qualification/measure-navigation.py --serial emulator-5580 --label home-vertical-1 --directory qualification/artifacts/performance-2026-10-09 --keys DOWN DOWN UP UP
python3 qualification/measure-navigation.py --serial emulator-5580 --label cold-launch-1 --directory qualification/artifacts/performance-2026-10-09 --restart
```

`--restart` force-stops and relaunches the app while preserving storage. Its
`am start -W` timing is initial display, not fully loaded Home. Navigation may
cross surfaces depending on the initial focus; labels alone do not prove a route.
Zero rendered frames are explicitly represented as undefined frame percentiles.
Physical-TV measurements require the owner's separate explicit device request.

## Validation

The full uncached first run passed all 48 library and 294 existing app tests.
The two added tests initially rejected the measurement fixture's legacy Live
route; after correcting the fixture to current v2 pages, both pass. The targeted
run records all 24 loading samples and the blocked-dependency ordering proof.
Python CLI/content checks and `git diff --check` pass.

`scripts/prepare-core.sh android` built all three native ABIs successfully.
The subsequent sequential `:app:assembleDebug :app:lintDebug` run failed at
`:app:compileDebugKotlinAndroid`: concurrent edits in `CoreLifecycle.kt:40–41`
introduced `PreviewAction`/`Int` and following constructor-argument type
mismatches. Assembly did not finish and lint was not reached. This investigation
did not edit that file or attempt to repair another active change. No APK was
installed. The earlier unit runs passed before these later source edits.
