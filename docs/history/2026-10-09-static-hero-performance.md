# Android TV static hero performance experiment — 2026-10-09

The isolated worktree is `.scratch/android-tv-performance`, branch
`perf/static-tv-hero`, based on the tested hero branch
`perf/tv-animation-latency` at `9bcce382dae45f368630b8444fef604bf0f47942`.
The hero changes were not in `origin/main` (`685f66c`) when this work began.
Core remains `30789432121f54348d705460c0fa29495795295c`; design remains
`581289b695f6802e77da4efeedad9bf64bab9b04`.

## Implementation

- Replace the continuously rendered GLES hero, drift, transition shaders and
  category edges with a static sharp image and a cached small ambient blur.
  Decode once through Coil; compute the ambient blur on a background dispatcher.
  Keep the image fade into the copy and shelves.
- Retain the bounded neighbour preloader, core-owned artwork selection,
  resolution check and 120 ms episode-focus settle. Keep displayed artwork while
  a replacement is pending or fails; cancelled loads cannot publish stale art.
- Present incoming TV routes immediately, omitting their entrance layer. Phone
  entrance motion retains its existing durations.
- Position the Home viewport directly at the first shelf boundary, removing the
  280 ms scroll animation. New directional input supersedes pending restoration.
- Cache filtered shelves and neighbour selection. Pass loading and a stable
  restoration snapshot into shelf rows instead of the whole changing app state.
- Request initial hero focus after action placement and window readiness. Enter
  remote/keyboard input mode before requesting TV focus. Handle each subject
  and restoration request once so a window handoff preserves the chosen control.

These Android presentation choices were explicitly requested for this experiment.
They differ from the pinned TV-042 animated presentation; imported design assets,
generated bindings and design/core pins are unchanged. A shared contract update
belongs with any upstream adoption of this presentation.

## Measurement method

The owner-authorized hero emulator was used, preserving the signed-in account,
server and app storage. The other emulator and physical TVs were not used. This
is an API 36 x86_64 TV emulator, 1920×1080, two virtual CPU cores, 2 GiB RAM and
SwiftShader software graphics. APKs are debug builds. Global animation scale
stayed at 1; animation removal comes from the implementation.

Each artifact gets three process-separated repetitions of five journeys:

1. Home idle: eight seconds after an eight-second setup settle.
2. First shelf: four Right and four Left presses, 450 ms apart, then three seconds.
3. Vertical shelves: two Down and two Up presses, 450 ms apart, then three seconds.
4. Verified Details action: Center then Back, two seconds apart, then three seconds.
5. Process-cold launch: force-stop/start, retaining storage and disk caches, then
   eight seconds. Android `am start -W` reports first display, not loaded Home.

Setup requires a fresh UI dump showing Home and a focused primary control.
Earlier animated-variant setup attempts rendered Home without a focused control.
The harness can normalize that state with setup-only directional keys before
sampling. Failed setup attempts are excluded and retained in private logs. No playback
action was invoked. Instrumentation fixtures use separate preferences and do
not change real sign-in.

`qualification/measure-navigation.py` brackets input with `gfxinfo reset`, stores
frame histograms and process PSS, and verifies the installed APK SHA-256 against
the source checkout's APK. Raw UI, graphics and memory captures are ignored under
`qualification/artifacts/static-hero/`; they contain private device/content facts.
`source-manifest-final.json` records hashes of the uncommitted source patch.
This task's builds, profiling and repository indexing were kept outside the
final sampling windows. Other workloads on the shared host were not controlled.

Frame counts include continuous decoration in the animated variant and mostly
event-driven rendering in the static variants. Percentiles therefore describe
different frame populations; they are not button response times. Zero frames
means no idle rendering, with no percentile or jank-rate sample. Three repeats
on software graphics establish a local diagnostic, not physical-TV performance
or statistical significance. Backend/catalog completion and fully drawn Home
timing were not instrumented by this experiment.

## Artifact provenance

| Variant | APK SHA-256 |
|---|---|
| Animated hero baseline at `9bcce382` | `9157f524eeda3a0294669240894398928e85f8ccf6418dd9544a2b08ce7d44f5` |
| Intermediate static hero with TV entrance motion | `c2965ac215b4a411d1860a66f04138b5406461aacce9c269e3ba8e4ba0a9cbe1` |
| Final static hero, direct TV routes and remote focus | `fe7d074ad2453ef7453bbe2a3c417245327e2f6bfc3db8d186d2ede58bdacb23` |

## Results

The tables show medians of three runs per cell, 45 measurement windows total.
Percentile cells are frame durations from `gfxinfo`, not input latency.

| Journey | Baseline frames / janky frames | Final frames / janky frames | Baseline p95 / p99 (ms) | Final p95 / p99 (ms) |
|---|---:|---:|---:|---:|
| Home idle, 8 s | 118 / 89 | 0 / 0 | 65 / 113 | No rendering sample |
| Horizontal, eight keys | 71 / 46 | 30 / 19 | 117 / 150 | 97 / 150 |
| Vertical, four keys | 83 / 68 | 25 / 14 | 150 / 250 | 150 / 150 |
| Details and Back | 66 / 49 | 25 / 11 | 450 / 1,300 | 350 / 550 |
| Process-cold launch, 8 s | 73 / 61 | 13 / 11 | 550 / 1,300 | 1,350 / 1,350 |

| Other measurement | Animated baseline | Intermediate static hero | Final implementation |
|---|---:|---:|---:|
| First display, median (ms) | 1,456 | 2,015 | 1,730 |
| First display, individual runs (ms) | 1,456; 1,466; 1,402 | 2,015; 1,711; 3,684 | 1,549; 1,730; 4,188 |
| Idle process PSS (KiB) | 180,494 | 176,047 | 184,434 |
| Horizontal process PSS (KiB) | 196,864 | 186,803 | 197,392 |
| Vertical process PSS (KiB) | 184,020 | 177,400 | 184,869 |
| Details/Back process PSS (KiB) | 188,470 | 189,185 | 185,725 |
| Launch process PSS (KiB) | 180,824 | 175,711 | 180,030 |

The intermediate static hero retains TV entrance motion: median frame/janky
counts are 0/0 idle, 31/20 horizontal, 32/14 vertical, 23/13 Details/Back and
15/13 launch. Its Details p95/p99 are 800/850 ms; its launch p95/p99 are
1,250/1,250 ms. This intermediate artifact is separate from the final APK.

The final implementation eliminates idle rendering, cuts rendered navigation
frames by about 58% horizontally and 70% vertically, and reduces observed janky
frame counts by 59%, 79% and 78% for horizontal, vertical and Details/Back
journeys respectively. Details p99 falls from 1,300 to 550 ms. Horizontal p99
and vertical p95 do not improve. Idle memory is about 2% higher; this experiment
does not establish a memory saving.

Startup is unresolved: median first display is 19% slower than the animated
baseline, and launch p95 is higher. Both static variants have multi-second
startup outliers. The different frame populations and uncontrolled shared-host
workloads prevent attributing that difference to one function. Eliminating
continuous decoration does not establish faster loaded Home or backend loading.
The remaining first-display and route stalls need a separate startup trace and
markers for session restoration, first Home data, artwork availability and fully
drawn content before changing catalog/metadata ordering or core projection work.
Consistency contracts in those paths were not changed here.

## Validation

Host core preparation and the full unit suite pass: 48 library and 314 app tests.
Native core preparation passes for all three ABIs. Final APK assembly, test APK
assembly and app lint pass against the existing lint baseline; existing lint
warnings remain. The APK integrity probe verifies minSdk 24, all three native
ABIs, pins, notices, ordinary trust policy and 16 KiB ZIP alignment.

All 13 selected device tests pass: five hero sizing/request cases; pending,
failed and stale artwork replacements; six Home focus/scroll cases; and immediate
TV entrance progress. The six Home cases also pass in a repeated run. Scroll
tests exercise real Home, DPAD input and numeric viewport positions; reveal/top
restoration completes within three test frames without queued motion. The
entrance test checks the actual Compose entrance helper on the incoming frame;
it does not play media.

The final Home capture shows readable copy, the focused Resume control, sharp
art fading into the ambient background and a clean join to the shelves. All
45 journey end captures show Home with one focused control. After qualification,
the original installed APK (`70921454…`) was restored with `install -r` and its
full checksum verified. Signed-in Home is visible, animation scale remains 1,
and only the test package installed for this task was uninstalled.

Earlier fixture failures exposed three separate setup issues: asynchronous core
session restoration overwrote seeded state, Coil maps String requests to Uri
before custom fetchers, and the Home fixture could start in touch mode. The
first two were corrected in isolated fixtures. All six Home tests failed in
touch mode and passed with a fixture-only keyboard-mode request; production
Home now performs that request itself, and the original fixture passes without
the workaround. Layout/window readiness alone did not fix touch-mode rejection.

For a manual follow-up, verify the intended screen/control first and use the
owner's private device environment:

```sh
python3 qualification/measure-navigation.py --serial "$ANDROID_ADB_SERIAL" \
  --source-checkout "$PWD" --directory qualification/artifacts/manual \
  --label home-horizontal --keys RIGHT RIGHT LEFT LEFT --seconds 3
```
