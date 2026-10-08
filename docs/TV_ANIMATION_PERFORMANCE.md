# Android TV animation performance — 2026-10-08

## Neighbour artwork preloading

The animated Home/Details backdrop adopts design
`581289b695f6802e77da4efeedad9bf64bab9b04`. Home passes the two nearest existing
hero-row items; Details passes the two nearest existing selected-season episodes
(the first two before episode focus). Core resolves their artwork roles on a
background dispatcher. There are no speculative metadata requests.

`HeroArtPreloader` retains at most three decoded images and 12 MiB of bitmap
references, keyed within one backdrop size/lifetime. One speculative request runs
at a time. Foreground selection cancels unrelated speculation and shares a warm
bitmap or pending decode for the same URL. Focus-window changes cancel stale
work; backdrop disposal/resize cancels requests and drops retained references.
Coil-owned bitmaps are never recycled by this cache.

Both display and preload use the same software-bitmap request builder, output
dimensions and inexact precision. Inexact precision preserves the existing
no-upscaling/low-resolution rejection behavior; see Coil 2.7's
[decoder implementation](https://github.com/coil-kt/coil/blob/2.7.0/coil-base/src/main/java/coil/decode/BitmapFactoryDecoder.kt).
The 12 MiB limit is this cache's retained references, not total process memory;
Coil's existing shared cache, in-progress decoding and renderer textures are
additional allocations. An oversize decoded image is displayed but not retained
by this preloader. GPU upload, mipmaps and cold shader compilation still occur
when showing a new bitmap.

Twelve coroutine tests cover ready/in-flight reuse, foreground priority, serial
speculation, rapid window changes, duplicate/blank URLs, byte/entry limits,
oversize images, disposal, failure/retry and immediately completed requests.
The controlled 250 ms loader fixture yields zero additional fetch/decode time
when its warmed neighbour is focused, with one loader call. This is deterministic
scheduling evidence, not a measured TV latency improvement. All 48 library and
324 app tests, three core ABIs, debug APK assembly and baseline-aware lint pass.
Shared-emulator and physical TV navigation/frame-time qualification remain pending.

The shader timing measurements below describe the earlier rendering optimization;
they do not measure this artwork-preloading change.

Branch `perf/tv-animation-latency` starts at the fetched shader branch
`463b126`. It adopts design `e860bb2dd60463ca7b930e51f9df8f0f94dcb559`.
Home/Details retain the shader catalog, genre edge pools, full-resolution art,
drift, interruption snapshots and system-animation fallback. Remote actions,
the 700 ms hold and focus restoration are unchanged.

| Rendered timing | Baseline | Candidate |
| --- | ---: | ---: |
| Catalog hero transition, after art decode | 750–900 ms | 280–350 ms |
| Rapid browsing dissolve | 400 ms | 180 ms |
| Episode focus settling, before art decode | 350 ms | 120 ms |
| TV screen entrance / fade | 280 / 220 ms | 180 / 140 ms |
| Resting decoration on a 60 Hz display | 30 fps | 15 fps |

Network/decode and cold shader compilation are additional costs, not included
in the transition durations. Phone entrance timing is unchanged. Frame pacing
uses display timestamps and caps active transitions at 60 fps independently of
display refresh. It does not guarantee 60 fps on weak hardware. Superseded queued
images are discarded before upload; an upload superseded while running releases
its texture. Programs compile only when requested and remain cached until the
surface is destroyed; the full unused catalog no longer compiles during focus
movement. Failed programs also stay cached, so an unsupported effect uses its
fallback without retrying compilation every frame. Teardown removes the
outstanding frame callback and releases held art.

The ambient pass excludes the art rectangle, removing 921,600 of its 1,824,000
fragment positions at full size (50.5%). Two scissor regions preserve its visible
left/bottom areas. Edge shaders avoid ambient work in the opaque interior, and
styles that become unchanged artwork return before noise/extra texture sampling.
Watercolour retains its colour treatment. No artwork downscaling is introduced.

The regression `HeroMotionPolicyTest.everyShippedTransitionCompletesWithinTheTvBrowsingBudget`
failed before the fix (`fade takes 0.4s; TV hero changes must finish within 350ms`)
and passes after immutable design adoption. Frame-pacing tests cover 30/60/120 Hz,
rest-to-transition response and surface recreation. All 48 library and 312 app
unit tests pass. All three core ABIs build; normal debug APK assembly and
baseline-aware lint pass. Existing lint warnings/baseline exclusions remain.
The real APK checker passes native/core/JNA packaging, minSdk, trust policy,
checksums, notices and alignment. All 23 packaged hero files match the design pin.

`scripts/hero-render-benchmark.py` executes the shipped GLSL through desktop
GLES 3 with synthetic, nonuniform art at the scene's actual resolution. It compares
the complete ambient/edge output, including blended edge changes and OLED ground,
at 1920×950 and a scaled 1280×633 backdrop. Empty draws and GLES errors fail the
probe; output differences must stay within one colour level out of 255.
All 11 styles pass the full/scaled comparisons. The incoming wipe is tested over
a different outgoing style, including watercolour in both directions.

On NVIDIA GTX 1080, 24 alternating samples per variant at full size measured
these median ambient/edge costs. The median of the paired percentage reductions
is **18.3%**; all measured full-size styles improved in this run.

| Style | Baseline ms | Candidate ms |
| --- | ---: | ---: |
| Linear | 0.177 | 0.145 |
| Cinematic | 0.229 | 0.185 |
| Smoke | 0.292 | 0.216 |
| Fog | 0.328 | 0.271 |
| Brush | 0.241 | 0.191 |
| Watercolour | 0.284 | 0.247 |
| Streak | 0.212 | 0.159 |
| Stipple | 0.177 | 0.145 |
| Old film | 0.165 | 0.127 |
| Embers | 0.177 | 0.148 |
| Heat | 0.186 | 0.152 |

A six-sample scaled run ranged from a 15% improvement to a 2% slowdown (Embers),
with Brush tied. These tiny desktop GPU timings vary; they do not establish a
universal speedup. Timings use `glFinish`, include Python submission overhead,
and exclude Android composition, upload, scene/transition rendering, mipmap
generation, cold compilation and physical presentation. Timing samples measure
resting passes; edge-wipe cases establish pixel equivalence, not wipe latency.
The source digests emitted by the final full-size probe are:

- Baseline: `3d1b21ece61c657167ab4437f9097359fc93ddfba4236b77d22621cb847158c6`
- Candidate: `f856f47f293b754fdd8fbb61416769bbaebf7e813a5e0353bd1d54bf24c01b6a`

Reproduce on Linux with libEGL/libGLESv2 installed:

```sh
baseline_dir="$(mktemp -d)"
git archive 463b126 app/src/androidMain/assets/hero | tar -x -C "$baseline_dir"
HERO_BENCH_TRIALS=24 python3 scripts/hero-render-benchmark.py \
  "$baseline_dir/app/src/androidMain/assets/hero" app/src/androidMain/assets/hero
HERO_BENCH_SCALE=0.6666666667 python3 scripts/hero-render-benchmark.py \
  "$baseline_dir/app/src/androidMain/assets/hero" app/src/androidMain/assets/hero
```

The shared emulator was not installed, restarted or navigated during this work:
its required coordination answer remained pending. On-device frame pacing,
rapid-input focus, background/return, cold compilation and visual review remain
unverified for this candidate. GLES 2 blur limitations recorded in TV-042 remain.
No physical TV or deployment qualification is claimed.
