# Hero emulator performance investigation — 2026-10-09

## Result

The animated Home backdrop creates a substantial continuous graphics workload
on the software-rendered TV emulator. Focus changes also incur expensive Compose
work and drawing when the static fallback is active. These are separate measured
costs. Metadata caching alone cannot address either steady-state rendering cost.

Thirty primary journey windows were collected: five scenarios, three repetitions,
with motion enabled and disabled. Additional lower-shelf, trace, readiness and
restoration checks were collected. Twenty-five hero unit tests pass.
The [earlier gateway investigation](2026-10-09-navigation-performance.md) records
controlled loading measurements from the main checkout; those are a different
revision and measurement boundary.

## Tested build and environment

The owner explicitly authorized the existing hero emulator for these tests.
The shared browser emulator was left untouched during this follow-up.
The hero emulator is Android API 36, x86_64, 1920×1080, configured with two cores,
2 GiB RAM and SwiftShader software graphics. These are debug/emulator diagnostics,
not physical-TV or release-build qualification.

The primary build is the clean `perf/tv-animation-latency` worktree at
`9bcce382dae45f368630b8444fef604bf0f47942`, core
`30789432121f54348d705460c0fa29495795295c`, design
`581289b695f6802e77da4efeedad9bf64bab9b04`. APK SHA-256:
`9157f524eeda3a0294669240894398928e85f8ccf6418dd9544a2b08ce7d44f5`.
The real APK checker passes minSdk, three-ABI native/core/JNA contents, pins,
notices, system trust, loopback policy and ZIP alignment. No fixture CA is packaged.
Each primary sample verifies the installed APK matches this artifact.

The previously installed APK had SHA-256
`70921454a1872440891498265edc7fffb39ce64cde13fc75a68e840964715a0c`.
It was saved privately before the known candidate was installed with `install -r`.
Its source revision is not established here; its initial idle observation is
excluded from the primary comparison.

The static comparison uses the app's existing system-animation fallback via
`animator_duration_scale=0`, followed by process restart. This disables other
screen motion as well as the shader backdrop: it is a diagnostic comparison,
not a recommendation to remove the shipped visual behavior.
The original APK was restored with `install -r` and its hash verified afterward.
Normal motion was restored with the explicit default scale `1`; the initial
settings entry was absent. A settled restoration sample confirms ongoing
animated frames. Sign-in, selected profile, backend and app storage were retained;
the app was left on Home. No playback, history write, provider configuration,
physical-device test or deployment was performed.

Raw APKs, screenshots, UI trees, traces, memory/frame dumps and test logs remain
private in ignored `qualification/artifacts/performance-2026-10-09/hero-device/`.

## Journey measurements

Medians of three windows. Frame times are Android's histogram percentiles in
milliseconds; these are not input-to-visible latency measurements.

| Journey | Motion | Frames per window | Jank % | p50 | p95 | p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Home idle, eight seconds | Animated | 120 | 93.39 | 48 | 65 | 65 |
| Home idle, eight seconds | Static | 0 | Undefined | Undefined | Undefined | Undefined |
| Horizontal focus, four Right/four Left | Animated | 66 | 92.42 | 73 | 150 | 200 |
| Horizontal focus, four Right/four Left | Static | 16 | 68.75 | 81 | 109 | 109 |
| Vertical shelves, two Down/two Up | Animated | 80 | 94.29 | 69 | 150 | 150 |
| Vertical shelves, two Down/two Up | Static | 9 | 100.00 | 65 | 101 | 101 |
| Hero Details, then Android Back | Animated | 68 | 83.82 | 65 | 750 | 1200 |
| Hero Details, then Android Back | Static | 8 | 62.50 | 117 | 200 | 200 |

Navigation windows use 450 ms between directional inputs and three seconds of
settling after the sequence. Details/Back uses two seconds between inputs and
three seconds after. ADB and foreground checks add overhead to these wall-clock
windows. Motion variants produce very different numbers/types of frames, so
neither their percentages nor their medians alone establish an improvement in
remote response. Individual runs vary substantially; preserve the raw ranges.

Private captures verify return to the first saved card after horizontal and
vertical journeys. Details/Back returns to Home; an inspected return focuses
Resume rather than the Details button used to enter. This observation is recorded
without claiming it violates a specific exact-control restoration contract.
No source or Play/Resume action was activated.

Once the hero was scrolled completely out of the viewport, an additional
eight-second lower-shelf idle sample recorded zero new frames. All three static
Home idle samples also recorded zero. Empty frame histograms have no valid
percentiles; the sampler represents them as null rather than treating Android's
empty 4,950 ms bucket as a real frame.

The animated Details/Back cold windows contain long tails: p99 was
1,200 / 1,400 / 150 ms. This identifies expensive route/surface initialization
as a follow-up target; it does not isolate shader compilation, texture upload,
networking or composition as the sole cause of those outliers.

## Causal trace evidence

An Android system trace of the settled animated Home covers 9.162 seconds.
CPU scheduling time, not nested span totals:

| Thread | CPU time |
| --- | ---: |
| Hero GL thread | 6,016.7 ms |
| RenderThread | 4,592.3 ms |
| App main thread | 137.3 ms |

The hero runs on a dedicated GL thread. It is inaccurate to describe its resting
GL work as running on the main thread. The graphics workload nevertheless competes
for resources and reaches the composited frame path. Trace spans show:

- Hero `eglSwapBuffers`: 136 calls, mean 53.30 ms, maximum 64.78 ms.
- RenderThread drawing: 136 calls, mean 39.75 ms, maximum 62.61 ms.
- RenderThread `eglSwapBuffersWithDamageKHR`: mean 36.31 ms.

The last-frame ring captures from the three idle samples separately report
median UI work before sync of 0.33–0.34 ms, sync wait of 0.44–0.52 ms, and
render-sync-to-frame-completion of 50.17–50.99 ms. These are different samples
from Android's histogram, so their exact totals need not equal the histogram p50.

This trace, the same-screen static control, and the offscreen-hero observation
support a continuous hero/graphics bottleneck on this emulator. They do not
establish which shader pass dominates or predict hardware-accelerated TV cost.
The scene pass, mipmap generation, ambient/edge effects, graphics driver and
TextureView composition still need individual timing before selecting a fix.

A second trace covers 6.210 seconds of static horizontal focus changes:

- Main thread CPU: 664.0 ms; RenderThread CPU: 465.7 ms.
- `Recomposer:recompose`: 16 spans, mean 21.51 ms, maximum 50.41 ms.
- Measure/layout: 14 spans, mean 7.84 ms, maximum 13.35 ms.
- Main traversal: 25 spans, mean 15.86 ms, maximum 25.70 ms.
- RenderThread drawing: 25 spans, mean 22.21 ms, maximum 33.44 ms.
- Background bitmap decoding: ten spans, mean 23.33 ms, maximum 69.67 ms.

The static trace establishes focus-driven composition/layout/drawing as another
cost beyond the animated renderer. The recorded spans overlap and must not be
added together as exclusive work. No trace here proves a particular native
projection or Kotlin function caused the recomposition time.

## Landing and cold-process launch

All six primary launches report `LaunchState: COLD`; app storage and image disk
cache remain intact, so these are process-cold launches, not clean installs.

| Motion | Android first-display samples | Median |
| --- | --- | ---: |
| Animated | 2,198 / 1,505 / 1,499 ms | 1,505 ms |
| Static | 2,621 / 1,505 / 1,418 ms | 1,505 ms |

Turning off animation does not remove the measured first-display cost.
The `--wait-home` UI probe confirms a saved-row heading plus Play/Resume controls;
it is a coarse upper bound that includes UIAutomation startup/idle-wait overhead.
It does not prove artwork or all catalogs are ready. An initial static probe
returned a null UI root and could read a stale dump; its 5,160 ms value is excluded.
The sampler now deletes the previous dump and requires a successful fresh capture
before reading it. Corrected confirmation runs verified Home by 5,967 ms static
and 7,700 ms after restoring the setting, with tracing overhead in the latter.
These must not be reported as exact Home-load durations or a causal speed ratio.

The additional launch trace was captured after removing the static phase's
settings entry. Its effective motion mode was not independently confirmed, so
it is excluded from motion comparisons. It records `bindApplication` at 597.88 ms,
`activityStart` at 228.94 ms, a peak recomposition span of 560.56 ms,
and main frame spans up to 734.01 ms. It establishes substantial cold UI/init
work; it does not partition backend request latency or loaded-art presentation.
Exact first usable content and first displayed hero require app-level draw and
request/decode markers; `am start -W` and UIAutomation alone cannot supply them.

## Optimization priorities

1. Reduce the resting hero's graphics cost. Time scene rendering, mip generation,
   ambient/edge passes and buffer presentation independently, then compare the
   same artwork/edge with one change at a time. Preserve the pinned visual
   contract and measure actual frames rather than desktop shader timings alone.
2. Reduce focus-driven UI work. Add named timings around native projections and
   heavyweight composables; isolate changing focus state from reusable shelf
   content and avoid equivalent state publications when evidence shows their
   cost. Keep row reuse, exact intent, cancellation and restoration semantics.
3. Investigate cold route/surface setup and landing composition. The Details
   outliers and startup recomposition spans warrant targeted traces. Record
   first shell, first usable saved card, artwork decode and actual displayed art
   separately before changing loading policy or network concurrency.
4. Revalidate successful optimizations on a release-like artifact and explicitly
   requested physical TV. SwiftShader results establish this emulator's costs,
   not production TV frame rates. Android's
   [measurement guidance](https://developer.android.com/topic/performance/measuring-performance)
   and [Perfetto trace analysis](https://perfetto.dev/docs/getting-started/command-line-analysis)
   describe the appropriate measurement boundaries.

No production renderer, shared-core, navigation or loading behavior was changed
by this investigation.

## Validation and repetition

The known candidate's clean worktree passes host core preparation and all 25
tests in `HeroArtPreloaderTest`, `HeroMotionPolicyTest`, and `HeroFramePacerTest`:

```bash
bash scripts/prepare-core.sh host
./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest --tests 'org.viptv.app.hero.*'
```

`qualification/measure-navigation.py` supports `--source-checkout` to associate
each sample with the correct candidate artifact, `CENTER` for a verified Details
control, and `--restart --wait-home` for fresh Home-shell verification. The APK
hash remains authoritative; checkout SHA alone does not prove installed provenance.
Keep captures in ignored artifacts and inspect/verify the starting route and
focus before replaying a sequence. Do not send CENTER to an unverified control.

```bash
python3 android/qualification/measure-navigation.py --serial emulator-5590 --source-checkout .scratch/hero-shader-android --label candidate-home-horizontal-1 --directory android/qualification/artifacts/performance-2026-10-09/hero-device --seconds 3 --interval .45 --keys RIGHT RIGHT RIGHT RIGHT LEFT LEFT LEFT LEFT
```

The command runs from the organization workspace root, with ADB on PATH or
`ANDROID_ADB_BIN` configured. It exercises the installed build; changing the
source-checkout argument does not install that checkout's APK.
