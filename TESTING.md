# Native rolling piece cache — 2026-10-07

Normal app source `8d9ab835356d4f2e98719d34848c785b0a2e1094` adopts gateway
`1513e462b4c0f54ef5aa1b74b77bef325b9ee70f`, core
`0f500daad567c11db9ff6608f225341a01fe4afa` and design
`2cf33c35e916bcf1ea99d90ce6d2d872697a6c62`. Large native inputs use a
256 MiB per-input rolling piece cache within the 2 GiB aggregate ceiling, rather
than requiring their full logical torrent payload to fit. Exact-file authority,
independent grants, disk admission and joined cleanup remain enforced.

An isolated clean main checkout passes 47 library and 287 app unit tests, all
three core/native ABI builds, 33 snapshot/importer tests, normal APK assembly,
lint and instrumentation APK assembly. The real APK probe verifies minSdk 24,
three-ABI core/native/JNA contents, exact pins/checksums/notices, system-CA trust,
literal-loopback cleartext policy and 16 KiB ZIP alignment. The normal APK SHA256
is `3d4dd8d8f9dbb55274fbe7cb6ca5899bdff5afd3ff2443847119027fbe311f71`.
Unrelated hero UI edits are absent from this package.

Gateway's locked workspace suite with owned-network fixture features passes
213 tests (56 opt-in tests ignored). A valid 100 GiB virtual exact-file input
reads distant and backward ranges through a 4 MiB cache and re-downloads evicted
pieces. A 64 KiB fixture verifies cross-file reads, sequential EOF, concurrent
independent grants and revocation. Unit/facade cases verify bounded sharing,
piece-slot and aggregate refusal while preserving outgoing work.

The actual Android Kotlin adapter, generated UniFFI facade, Rust core and a real
isolated backend/owned TCP peer pass a Linux host pipeline in 4.874 seconds.
It verifies the rolling cache, exact selected bytes, a host-FFmpeg decoded seek
frame against the source, and independent-grant cleanup. Native readiness takes
173 ms; joined local retirement takes 6 ms. The owned cache is reclaimed and
both fixture listeners close. This is host transport/decode evidence, not Android
JNI, Media3, physical sound, public swarm, 4K or production acceptance.

No APK was installed and no shared-emulator state or production service changed.
Android decoded playback remains pending a coordinated device window. The isolated
fixture source includes cache-allocation and candidate-refusal checks for that run.
The isolated x86_64 fixture app and instrumentation APK also compile. A scoped
APK probe passes fixture identity, private constructor/checksum, trust policy,
notices and 16 KiB ZIP alignment. The full three-ABI fixture probe is not claimed:
only x86_64 fixture products were built; all three normal product ABIs passed
their normal APK probe above.

# Native startup diagnosis and failure facts — 2026-10-07

The normal system-trust APK with SHA-256
`e7c6729f6f02e2f45be6d9883b0ff113c1f157ea2900c2cd7ae53d138b9663e4`
was verified and installed with `install -r` on the shared API 36 x86_64 TV
emulator. Existing sign-in, profile, server selection and app data were preserved.
The source pins are core `adb8a7165977cc1c827b5695f1924524928a1d9e`,
design `5b68802c6f5ea91dc248defb047439f3ea96cce5`, and gateway
`d91582a2c4d5aa56b442e497bb1d291eb60fdd8e`.

The original first raw Torrentio source for Limitless, displayed as 38.62 GB,
reached native full-payload admission and reported `native_payload_limit`.
The complete, legible recovery message states that the torrent does not fit the
device's 2 GiB playback cache budget, including its other files. The generic
selected-source startup message did not recur in this replay. Manually selected
raw 1.4 GB and 751 MB YIFY rows each reached the total startup deadline and
reported `native_acquisition_timeout`. No decoded public-source playback or
native loopback-byte success is claimed; the existing owned-fixture decode
qualification below remains separate. Source cycling stopped after these attempts.

The diagnosed Android race allowed expiry authorization to advance the core's
observed clock between startup authorization and private grant getters. A real
UniFFI regression failed before the shared read guard and passed with the guard
covering grant extraction and metadata validation. Closed engine failure facts
survive FFI and Android worker completion into core-owned copy; unknown adapter
diagnostics remain flattened. Host checks passed: 47 video/library tests and
287 app tests, including distinct capacity/selection recovery after retirement.
App assembly, app lint and real APK integrity checks passed. Gateway validation
passed 187 workspace tests and five strict FFI acquisition fixtures in isolated
network namespaces; 55 opt-in workspace fixtures remained ignored. All three
normal native ABIs built and passed alignment checks. Temporary diagnostic
instrumentation was removed. Private captures and raw logs are not tracked.

# Default native availability on shared Android TV — 2026-10-07

The shared interactive emulator received normal system-trust APK `8e1ffec`
with `install -r`, preserving the requested personal account/profile and data.
APK SHA-256:
`936b41c2e35b655721b85707b5b9e57db0b22908f4df8b19e9886c29aa41e484`.
Supported Android runtimes advertise native transport without an enable setting,
operator allowlist or qualification receipt. The development backend was
updated to `d66085be95ad6e7d3401bf8be326172ee7f66d09`; running executable
checksum and HTTPS health were verified, with existing configuration, database,
providers, history, profiles, keyring, frontend assets and tunnel preserved.

The signed-in source picker displayed 179 source choices, including Torrentio,
rather than the reported unsupported-formats message. A manually selected
1080p source rendered actual video after Resume. That normal source observation
does not independently prove its delivery kind or sustained public-peer native
behavior. The separate owned native pipeline evidence below proves actual
engine/Media3 decoding and renewal for synthetic selected-file grants.
No production deployment or physical-device qualification is claimed.

# Shared browser-accessible Android TV emulator — 2026-10-07

Following the owner's explicit account-switch request, the existing emulator
session was signed out through Settings and paired through the normal
authenticated device approval flow to the requested personal account. Its
existing primary profile was selected. An inspected Home capture shows the
personal Continue Watching entries and resume progress. No playback was started,
profile created or history imported. The temporary approval browser session was
logged out; credentials and captures remain outside tracked files.

Normal debug APK from Android `cebd090bc1475c571bf16caf2bf0f94987b49662`,
core `8ef18be39ca08e96c40b20f40666f62a5f2eadfc` and gateway
`ec134b9bf86fcd06efc4db5b4e94891989b86c02` was installed with `install -r`
on the designated shared x86_64 Android TV emulator. All three viewer services
were active and Android boot completion was verified. App data and the existing
backend selection/sign-in were preserved. The existing Development profile
remained selected; this does not verify a personal account association.

Single-worker normal APK assembly and artifact checks passed: three native ABIs,
pinned library checksums/notices, minimum API 24, 16 KiB alignment, system-only
certificate trust and no fixture assets or qualification receipt. APK SHA-256:
`7fca96eec7252199e3b00096f753819b6856eec5ef6feafeb148736ff58295ec`.

Inspected captures show the signed-in profile chooser and loaded Home catalog,
hero artwork and title cards. The app was left running on Home for owner use.
Private captures/build receipts are under ignored `.scratch/native-torrent/`.
No playback, physical hardware or native torrent qualification is claimed.
Native advertisement remains disabled; no backend service change occurred.

# Reconciled source picker verification — 2026-10-06

Explicit provider interaction owns TV focus even when discovery starts empty.
An independent review found that resetting to All providers after late results
could select the reset while moving focus to the first source card. The focused
regression reproduced Selected=true with Focused=false before correction and
passes after provider activation claims the one-time initial-focus handoff.
A re-review found the initial-focus coroutine also needed to recheck ownership
after its frame suspension. The frame-gated unit regression failed with one
unexpected focus request before that recheck and both handoff cases pass afterward.

The final configured API 36 TV VM run passed nine focused provider, list-bottom,
description and discovery tests. Real signed-in DEV navigation showed one
horizontal provider row, no quality controls, provider-specific empty-state copy,
All providers reset with retained focus, and source cards reaching the panel
bottom. Private captures and logs are `.scratch/native-torrent/reconcile-*`.
No source was activated from the picker. Navigation briefly opened the existing
Resume playback while locating the source action and returned immediately; this
is not media, codec or physical-device qualification.

With JDK 17 and SDK Platform 36, host and three-ABI Android core preparation,
47 library tests, 234 app tests, app/test APK assembly and lint passed with zero
test failures/errors/skips and zero lint errors (77 warnings under the existing
baseline). The 32-test native-artifact validator suite, core/design integrity and
Git whitespace checks also passed. Normal APK SHA-256:
`a18f06d353534fd56a4a4a2d7375fb9bd216ab4836cdd8cff0798d30ed95beab`.
The APK was installed with install -r without clearing data or changing origin.
Native torrent capability remains disabled; no backend deployment occurred.

# Source picker bottom inset — 2026-10-06

The source picker omits decorative panel-bottom padding. Top/side padding,
phone safe-area/IME insets and every other overlay's default padding remain.
Provider badges retain one horizontal scrolling row without a quality filter.

A real SourcePicker regression failed at the list-bottom assertion: y=1048
rather than the panel's y=1080 at the fixture's 0.5 density, corresponding to
64dp padding. It passes with the source-only override, including scrolling to
and fully displaying the final card. A stable list tag keeps the test addressable
when lazy scrolling disposes its first row. All eight focused device tests passed
on the configured API 36 TV VM, including provider and discovery regressions;
phone-mode Compose checks do not qualify separate phone hardware.

JDK 17 / SDK Platform 36 unit tests, app/test APK assembly and lint passed:
228 app tests, zero failures/errors, and the cached passing 47-test library
result; lint zero errors, 77 warnings with the existing baseline retained.
Normal APK SHA-256:
`deee12c780ce4f00bac476e38f3216bde5b5e8272c0b0eeb32392962ae99e15f`.
It was installed with install -r, without clearing data or changing the origin.
Logs are `.scratch/native-torrent/source-padding-*`. No native torrent, media
decoding or physical-device qualification, commit, push or backend deployment
was performed for this UI change.

# Visible source provider choices — 2026-10-06

Choose a source exposes core-projected provider choices directly, including empty
producers, with a single-provider selection and All providers reset. Badges stay
on one horizontally scrolling row without wrapping or a dropdown. No quality-filter row
or quality-filter state remains; source-card quality facts are unchanged. Filter
activation retains TV focus on the choice instead of requesting the first result.

The first three provider regressions failed against the dropdown baseline. Final
instrumentation passed all seven tests: phone-mode provider switching/reset and
horizontal touch scrolling, progressive arrivals across qualities and empty producer
outcomes, TV D-pad overflow scrolling with fixed row height and badge Y positions,
retained focus, plus existing description and discovery-status regressions.
These ran on the configured API 36 TV VM;
phone-mode Compose rendering is not separate phone-device qualification.

JDK 17 / SDK Platform 36 host/native preparation, both unit-test tasks, app/test
APK assembly and lint passed: 228 app tests with zero failures/errors/skips;
unchanged video-library task retained its passing 47-test result. Lint reported
zero errors and 77 warnings under the existing baseline. Normal debug APK audit
found all three core ABIs and no fixture CA. SHA-256:
`3900a5469479ff314044e3d1b0836f9b4c13ae5d91e613798c65d656cef3aed1`.

The configured signed-in TV VM received install -r with no uninstall, data clear
or origin change. Real DEV Home/details/source-picker navigation remained signed
in; the final picker captures show one scrolling provider row and no quality
controls. Private captures are in `qualification/artifacts/source-provider-tabs/`;
logs are `.scratch/native-torrent/provider-scroll-*`. No media playback, native
torrent, codec, HDR, PiP or physical-device qualification is claimed. No commit,
push or backend deployment was performed for this change.

# Repository sweep and protocol foundation — 2026-10-06

Android and TV-web adopt core `4ca402587df7dbc842d0db522052b1d397f5a03f`.
This supplies inactive protocol parsing/request types, without enabling native
torrent capability or playback admission. The Android snapshot passes core and
design integrity checks.

With JDK 17 and SDK Platform 36, host preparation and both Gradle unit tasks
passed: 47 library tests and 229 app tests, with no failures, errors or skips.
All three native Android ABIs built; debug APK assembly and lint passed. Lint
retains 77 warnings and the existing three-error baseline.

Host port discovery sent unrelated `HEAD /` probes to temporary HTTP fixtures,
consuming their request budgets and causing API timeouts. The dedicated real
HTTP regression failed before the fixture fix and passes afterward with the
full suite. Fixtures return 404 for those probes while preserving API request
counts and assertions. No emulator, physical device or deployment was exercised.

# Android TV seek-hint layout — 2026-10-06

The TV player omits the focus-dependent "Use left or right to seek" and
"Seeking to…" text row. Left/right seeking, skip controls, preview/commit and
cancellation are unchanged; preview time remains in the existing timeline clock.

Two regressions against the real PlaybackScreen failed before removal on the
isolated API 36 TV AVD `viptv-source-qa`, serial `emulator-5584`. Moving focus
from transport controls to the timeline moved the track upward by 27 pixels
(y=814 to y=787); a preview also rendered the unwanted "Seeking to 0:30" row.
Both tests pass after removal, asserting identical track/title bounds across
focus changes, stable preview/cancel bounds, absence of both text variants and
the preview clock. The private 1920×1080 capture was inspected: the timeline,
endpoint clocks and transport controls remain visible without the extra row.
Logs and capture are under `.scratch/player-seek-hint/`.

With JDK 17 / SDK Platform 36, host-core preparation and both Gradle unit tasks
passed: 228 app tests, zero failures/errors, plus the unchanged cached 47-test
video-library result. All three Android core ABIs built; app/test APK assembly
and lint passed (zero errors, 77 warnings, existing three-error baseline).

This is native Compose geometry/input evidence with a supplied preview state,
not media decoding, physical remote or real-server playback qualification.
The signed-in interactive emulator was untouched. No push or deployment was
performed as part of this qualification.

# Shared Android policy migration — 2026-10-06

Android and TV-web adopt core `246a26f4c3788397314ae3e9ffe4d5ffa5e1e74d` through
the owning sync scripts. Rust owns episode/history and detail enrichment,
hero/queue actions, watching/progress and phone presentation, batch source ranks,
producer outcomes, Discover groups/defaults, authorization comparison, scoped
preview/continuation/countdown decisions, and playback/lease/live-page reducers.
Android retains clocks, configured budgets, Media3, transport, cancellation-safe
cleanup, session fences and route/focus effects; backend authority and manual
source selection remain intact. Root/app AGENTS.md document this boundary.

Episode flag/timestamp and detail-cursor regressions failed before correction.
Review also reproduced stale history identity/timestamps and an empty initial
channel page incorrectly receiving category validation; focused regressions and
the complete app suite pass after correction. Discover projections use bounded
memoization and composition remembers per-type facts rather than repeating JNI
calls while rendering.

Verification on this Windows PC with JDK 17 and SDK Platform 36:
- Rust workspace all-feature tests: 107 passed; strict all-feature/all-target
  Clippy and workspace formatting passed. Kotlin/TypeScript, UniFFI and real WASM
  artifacts regenerated at the canonical source.
- Actual WASM suite passed, including 37 public-normalizer vectors identical to
  native Rust. TV-web's imported binary has the same SHA-256 as that artifact.
- `scripts/prepare-core.sh host` and `:testDebugUnitTest :app:testDebugUnitTest`
  passed: 228 app tests, zero failures/errors/skips; the unchanged 47-test video
  library task retained its passing result.
- `scripts/prepare-core.sh android` built arm64-v8a, armeabi-v7a and x86_64;
  `:app:assembleDebug :app:lintDebug` passed. Lint reports 77 warnings with the
  existing three-error baseline unchanged.
- Consumer core integrity checks passed; TV-web passed 285 tests, a final 20-test
  API rerun, all seven typecheck groups and production build.

Logs are under `.scratch/core-policy-migration/`; the debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. No emulator, surface/remote, codec,
HDR, PiP, physical-device, real-server playback or deployment qualification was
performed. Scoped commits are local; nothing was pushed or deployed.

# Shared playback error normalization — 2026-10-06

Android and TV-web imported core `d104fd91e96ce2bdc468c7aa702222b96f90fe7b` through
their owning sync scripts. Shared Rust supplies canonical safe messages for known
backend source/gateway failures through HTTP errors and terminal playback leases.
Wire types, native decoder mapping, source selection and retry policy are unchanged.

Two native regressions failed before the mapping fix; all three focused tests,
73 Rust workspace tests under default/all features, strict Clippy and formatting
passed. Actual WASM passed the same 26 canonical error vectors and its existing
suite. Kotlin/TypeScript and native UniFFI declarations regenerate unchanged.
Both consumer integrity checks passed. TV-web passed 43 targeted tests, all
build/type checks and 26 imported-WASM error vectors.

On this Windows PC, JDK 17 / SDK Platform 36 host-core preparation and the two
Gradle unit tasks passed. The app executed 200 tests with zero failures/errors/
skips; the unchanged 47-test video-library task retained its cached passing result.
The three Android native ABIs built successfully. APK/lint verification in the
active checkout failed during Kotlin compilation after concurrent app changes
introduced lifecycle/playback DTO references absent from the pinned core, including
`CountdownAction` and `PlaybackFailureDecision`. Those changes were left intact;
the failure log is `.scratch/playback-errors/android-assemble-lint.log`.

An isolated committed Android `05080c4` snapshot with core `d104fd9` passed
`:app:assembleDebug :app:lintDebug`. It used the three native libraries built
above; APK inspection confirmed arm64-v8a, armeabi-v7a and x86_64 core slices.
Lint reported zero errors and 77 warnings under the existing three-error baseline;
no baseline or suppression changes were made. The debug APK is retained at
`.scratch/playback-errors/app-debug-d104fd9.apk` (SHA-256
`62c57a7763700ed266762a2ad7f1db654974e5d26fc695a662cddc23dd61d1e8`).
The build log is `.scratch/playback-errors/android-isolated-assemble-lint.log`.
This snapshot excludes the concurrent migration and was not installed on a device.

No emulator, media, surface, remote, codec, HDR or PiP checks were performed.
No push or deployment was performed as part of this qualification.

# Android TV Home rail focus restoration — 2026-10-03

Reproduced the owner's Right-from-navigation failure with the real Home and
rail composables: refresh disposed the remembered control, so Right collapsed
the rail while Play remained unfocused. The original and minimized text-only
regressions both failed before correction. FocusMemory now tries the saved
control, clears an invalid requester, and falls back to the current page entry
control. Profile Right, navigation Right and shell Back share this handoff;
the rail stays available if loading content offers no valid focus target.
Route-keyed focus lifetime is preserved. No delay or layout workaround added.

Five dedicated TV emulator checks passed (final rebuilt harness: 44.36s):
hero return, refreshed hero return,
shelf return, Profile Right after refresh, and loading Home then retry.
The season/watched and Home hero return regressions also passed
**OK (11 tests)** in 54.979s. All checks use text/focus assertions only; no
screenshots, images, recordings or pixel assertions were captured or used.
Private evidence is under `qualification/artifacts/rail-focus-20261003/`.

JDK 17 / SDK 36 host-core preparation, **228 unit tests** with zero
failures/errors/skips, all three Android ABIs, normal/test APK assembly and
app lint passed. Lint reports 77 warnings under the existing three-error
baseline; no baseline changes or suppressions were added. The same production
fix on isolated latest main `44196c4` passed **237 unit tests**, also clean.
The test harness owns one FocusMemory per fixture, avoiding lint's incorrect
Unit inference for the cross-source-set constructor inside remember.

The normal debug APK was installed with `adb install -r` and relaunched on the
dedicated local TV emulator, preserving app data/sign-in and backend settings.
SHA256: `31EDA02C2E5023008061F4AC40BAF94D8C708D9F4707AF39BEDC6276DDC43D57`.
Other destination pages and physical remote behavior are not device-qualified
by this Home-focused run. No commit or push was requested.

# Android TV episode season cards and watched hold — 2026-10-02

Owner-requested working change on Android main `1ea4b0e`, with the same
change unit-checked on isolated latest main `44196c4`. Previous season and
Next season cards bookend the episode row for available adjacent seasons.
OK opens the previous season at its last episode or the next at its first;
focus alone does not change seasons. The episode lazy-list offset is accounted
for in saved-cursor restoration and Episode #. Episode hold opens the existing
700ms menu, offering Mark watched or Mark unwatched according to its saved fact.
Accepted correction updates Watched and retains the selected season/episode.

JDK 17 / SDK 36 local flow passed host-core preparation, both unit tasks
(47 library + 181 app = **228 tests**, zero failures/errors/skips), all three
Android native ABIs, normal APK + Android-test APK assembly, and app lint.
Latest-main isolated unit checks also passed: **237 tests**, zero failures,
errors or skips.

On the dedicated Windows API 36 Android TV emulator, the text-only
`EpisodeSeasonNavigationTest`, `DetailsEpisodeReturnTest`, and
`EpisodeCorrectionProgressTest` returned **OK (14 tests)** in 48.197s.
Scenarios cover sparse seasons including 0, unavailable end directions, exact
destination focus, Episode # with a preceding card, delayed metadata, Sources
Back, actual Android hold/release delivery, watched/unwatched badge updates,
Cancel, rejected saves, and stale profile/route progress responses. The initial
test APK had a cross-window key-injection bookkeeping failure; the final rebuilt
harness uses native Android hold/release events.

The normal debug APK was installed with `adb install -r` and relaunched,
without clearing app data or changing its backend. SHA256:
`B1E485A516F1A03B28F02A83C28FBB9833FEE779CCC22F01648D0DDB38A176FC`.
Private text evidence is under
`qualification/artifacts/episode-season-20261002/`; no screenshots, images,
recordings or pixel assertions were captured/used in this run.

The canonical owning design working draft is
`../design/specs/behavior/android-tv-episode-season-cards.md`. Pins remain
unchanged pending an authorized commit/adoption; no publication or other-client
parity is claimed. Windows-normalized design validation passed docs, links,
tokens and the recorded asset inventories; the original validator retains its
Windows path/CRLF limitation. Physical remote and visual appearance remain
unverified.

# Windows-local v2 upgrade and playback UI fixes — 2026-10-01

Android main `c8f8ffa`, design `6da30a5`, core `1f8483e`: existing Windows
work was preserved during upstream integration. The TV track label now yields
space to Current/unavailable markers. Positioned subtitle cues use the measured
text bounds and retain their horizontal viewport anchor even with automatic
vertical placement; safe edges and visible controls constrain their placement.

The position-only cue regression failed before the fix in
`qualification/artifacts/v2-subtitle-before.log`. The final JDK 17 / SDK 36
local flow built host core, ran both Android unit-test tasks, built all three
Android core ABIs and assembled the normal APK. Final XML reports contain
**206 tests, zero failures/errors/skips**; the final combined Gradle run passed.
The normal APK was installed with `adb install -r` on this PC's dedicated TV
emulator and launched successfully, without clearing app data or changing its
backend. Evidence is private under `qualification/artifacts/v2-windows-*`.

The simultaneous automatic-cue regression also failed before correction in
`qualification/artifacts/v2-subtitle-stack-before.log`. Automatic cues now share
one vertical stack while retaining each horizontal anchor. The stack is measured
and clipped above visible controls, including oversized text.

Two real Compose pixel tests passed on the Windows TV emulator: simultaneous
left/right/default cues occupy distinct rows at their horizontal anchors, and a
tall cue cannot paint underneath the reserved controls area. Invocation used the
explicit TV serial and `org.viptv.app.SubtitleOverlayPlacementTest`; result:
`OK (2 tests)` in `qualification/artifacts/v2-subtitle-render-emulator.log`.
This render-only test uses no account, network, source or media fixture.

Not claimed: real provider playback, Media3-to-overlay end-to-end subtitles,
physical TV remote input, codec/HDR or PiP. Record post-cutover signed-in
discovery and playback independently below.

Post-cutover dev checks: backend `1b218e84` plus retained numeric-pairing work
is running on loopback3000 through the unchanged tunnel. Public dashboard,
activation, TV-web, health/auth-status routes returned200; four frontend asset
hashes match the reviewed candidate. Numeric pairing and PNG QR passed.
Backup-first addon migration encrypted both entries, assigned the unowned entry
to the personal account and retained the other owner. Integrity and preservation
checks passed for15 protected tables (two accounts, two profiles, four sessions).

On the Windows TV, the existing personal profile remains selected; populated
Cinemeta Home, title details and account-scoped Addons render. Choose source
renders the expected zero-source state with a catalog-only addon, not a playback
success. No personal stream provider or Stremio import/apply was configured.
The separate gateway candidate is qualified but public conversion is deferred.
Private captures are `qualification/artifacts/v2-tv-*.png`.

# Actual shell/native TV keyboard and phone text insets — 2026-10-02

Application baseline Android main `4d88d1b328c118ffc7d7a7f69ecce6b8d3db3750`,
Core `f66c87e13a2c93b6dad3234da9694c57f8530b0a`, design
`1742afa2b50d30638fa46f3abc8c1a76638a51e1`. No application/layout code changed.
The isolated synthetic HTTPS fixture uses the same installed app and actual
controller/HTTP boundary, not an actual backend account/source in this slice.

`check-guide-shell-ime.py` passed on owned API36 Android TV x86_64 emulator5576,
1920×1080. Starting at actual ApplicationShell Home, D-pad entered the rail,
selected Live TV, applied News, and reached Search channels. The real OS
LatinIME was visible; D-pad navigation selected each letter of cnbc and its
Check/Done action. Captures privately inspected the empty keyboard, complete
query with Check focused, and returned future programme. No Compose text/IME
semantics, text injection, touch, Tab or test-only focus request entered this
successful flow. After the CNBC-only result, ordinary D-pad reached future
Halftime Report. OK opened Watch live details; native Android Back returned
to the same focused programme bounds `[1535,503][1818,597]` with CNBC retained.
The fixture journal showed zero playback-admission delta. This adds actual
shell/rail/OS-keyboard evidence to PR19's distinct public-composition proof;
it does not expose the private Compose node ID or qualify physical input.

Phone: owned API36 Google APIs x86_64 emulator5574,390×844dp,780×1688 pixels
at320dpi. Four final route captures passed `capture-phone-font-insets.py` and
were inspected: matching Home at1.0 and1.3 font scale, Boruto Title at1.3, and
Search with query naruto and real native IME shown at1.3. Home's enlarged text
and action remain usable; the two-line Title, source/provider/count and Play
fit. The focused Search field bounds `[32,228][748,364]`, filters and first
result row remain above the keyboard. The helper verifies the private fixture
origin, HTTPS health/trust, viewport, font setting, route and actual IME before
capture. These are selected-route observations, not a pixel score, all route
families, every device/font scale or continuous-typing acceptance.

The ordinary three-ABI system-trust APK was assembled again without fixture CA;
normal assembly/lint passed in4 seconds. JDK17 explicit-CA QA assembly passed
in8 seconds; only the owned installed QA APK trusts that fixture. Helper syntax
and Core/design integrity pass. The shared preview fixture's committed module
blob was `694478c2eb0cdfde7d2d3bbb8bd012b81588a0f1`; its working bytes matched.
The
previous237 host tests/build/lint and managed-audio case were not repeated.
The TV helper initially collided with an operator UI-tree dump; its bounded
snapshot retry now follows the existing driver. Mixed-touch/incorrect-route
calibration captures and a synthetic Resume-envelope failure are excluded.
No native playback was qualified by these phone captures.

Only the owned phone's font setting was restored to1.0 and viewport overrides
reset; its synthetic app state was cleared. Both owned emulators and fixture
listeners stopped. Private images/XML/credentials remain ignored. Guide
presentation gaps still remain under the standing layout constraint. Physical
remotes/decoders, audible fidelity, HDR/DRM, signing and production acceptance
remain separate; broad design#6/android#3 stay open.

# Actual backend/gateway Android TV managed audio — 2026-10-02

Android application baseline `e61f6ffc0775b8fb45544b2d852bdbd6bc5f3ca4`,
Core `f66c87e13a2c93b6dad3234da9694c57f8530b0a`, unchanged design
`1742afa2b50d30638fa46f3abc8c1a76638a51e1`. The actual app ran on the owned
API36 Android TV x86_64 emulator5576, 1920×1080, with emulator audio disabled
and a generated 120-second H.264/two-silent-AAC torrent. No application or
layout code changed.

`check-native-managed-audio.py` drove the actual ApplicationShell Home →
Details → Choose source → Generated torrent addon through native hierarchy
inspection/taps and ADB-injected remote keys. Account/catalog/discovery,
encrypted addon/vault, approved source admission, playback leases and gateway
media were actual backend APIs; responses were not mocked. The synthetic
account session was provisioned privately in the owned app, so this does not
qualify login/pairing UI. A fixture-only HTTPS ingress forwards the real APIs
and media and rewrites only delivery URL origin for emulator routing; the
production endpoint validator is unchanged. Request/response body lengths
are recomputed after forwarding/rewriting.

The final uninterrupted automated case passed. Initial delivered HLS was
AAC/eng. Selecting Spanish created a new lease at 5.772 seconds and the real
ready descriptor selected input2/spa. A bounded read-only observer captured
the actual FFmpeg `-map 0:2` and correlated its output segment bytes with the
served segment, matching exactly one observed output. The runtime deliberately
tags this transcoded output AAC/und. Both input tracks are silent: this proves
the selected input and output path, not audible Spanish or an output language
tag of spa.

Selection returned to Audio; immediate OK reopened Spanish Current without
directional repair. Native Back and immediate OK retained that control and
selection. Managed forward seek admitted position65.772 with input2 retained.
Actual Media3 position advanced72 →81 seconds; the inspected private capture
showed decoded video with burned-in82.920 seconds and a1:22 player timeline.
Four lease DELETEs returned200, including explicit Exit. The gateway reclaimed
all2 input/2 output/4 viewer slots; its deterministic peer delivered4,014,682
bytes over19,924ms. The fixture test passed and checked cache/peer teardown;
the runner exited0 and all owned backend/gateway ingress listeners stopped.

Execution provenance: backend fixture source `d52a9c1422c94ea4e2d866cef3e135484473595e`,
observer source `31b2dc8551bc682cb9b60a0984b9b979308c31e4`, immutable copied
gateway driver `20e515faa03a254963db03c540a4596484ffab2f`, service image
`sha256:3ed14fbbf179d9d4a326b6e798118ad62c209f01a7fdf3d0524233a52e367825`.
The private launcher used an immutable driver path and a local sudo Docker
wrapper; its Git blob was `8211fccc7efa273b9065b44c3a5281b15cacbf72`, not a
claim of byte identity with a published backend runner. Earlier expired,
fixture-handshake, operator-framing and helper-calibration attempts are excluded.

Core/design integrity and helper syntax passed. The unchanged ordinary
system-trust debug APK from the Core f66 build still contains all three Core
ABIs and excludes fixture CA material. Only the isolated installed QA APK uses
the explicit fixture certificate. The previously recorded fresh237 host tests,
three-ABI build, normal/instrumentation assembly and lint remain that build's
checks; no fresh broad suite is claimed for these operator helpers/docs.
Physical hardware, audible fidelity, HDR/DRM, Guide pixel parity/rail/OS IME,
store signing and production delivery remain unqualified. Private source URLs,
credentials, proc arguments, segment bytes, logs and captures remain ignored.

# Current Core Android TV Guide remote chain — 2026-10-02

Baseline: Android main `7b0d00c`, Core
`f66c87e13a2c93b6dad3234da9694c57f8530b0a`, unchanged design
`1742afa2b50d30638fa46f3abc8c1a76638a51e1`. The opt-in
`GuideRemoteFixtureTest` passed on the owned API36 Android TV x86_64 emulator
in 5.144 seconds (one test). The test asserts native television mode and uses
the paired synthetic session, actual AppController/GuideScreen, local HTTPS
fixture responses and the application's 1920-coordinate density.

One initial public focus setup selects All. Compose key input moves to News
and applies that filter; Compose remote OK opens Search. The editable field
and IME action are driven through Compose semantics to submit CNBC, and the
real controller replaces the channel page and loads its schedule. Compose
remote key navigation then reaches future Halftime Report. OK opens its
Watch live details; native Android Back restores the same programme semantics
ID, bounds and focus on CNBC. Back uses an actual instrumentation Android key
event. No focus request repairs Search or Back. The
HTTPS fixture journal proves no playback admission before or after the flow.

This completes the formerly unqualified filter/Search/result/future-details/
Back functional chain through the pre-agreed public composition/remote seam.
It uses the actual controller and HTTP boundary with synthetic backend data;
it is not actual backend/gateway delivery, full ApplicationShell/rail navigation,
pixel parity, OS keyboard UI interaction or physical remote/decoder acceptance.
Current Guide presentation
gaps remain unchanged. The earlier partial-chain caveats below describe the
older checkpoint and are superseded only for this scoped functional chain.

Core/design integrity, targeted native test, instrumentation/normal APK assembly
and lint pass. The ordinary debug APK excludes fixture CA material; only the
isolated installed QA APK trusted that fixture certificate. The unchanged
Core f66 host/three-ABI rebuild and fresh 237-test host gate are recorded below.
No application/layout code, production data, deployment or physical device was
changed. Private credentials, media, logs and captures remain ignored.

# Core malformed-response HTTP status adoption — 2026-10-02

Adopted immutable Core `f66c87e13a2c93b6dad3234da9694c57f8530b0a`
through the normal core-sync importer after Core PR6. Malformed successful
identity/token responses retain their actual HTTP status instead of looking
like a transport failure. The Kotlin interface is unchanged; Android-specific
policy and the existing design `1742afa` remain unchanged.

JDK17/SDK36 with Cargo jobs2 and Gradle workers2: rebuilt the host library,
freshly executed all 237 host tests (zero failures/errors/skips), rebuilt
arm64-v8a/armeabi-v7a/x86_64 native Core libraries, and assembled the normal
debug and instrumentation APKs. Core/design integrity and lint passed.
The normal APK has all three expected ELF architectures and no fixture CA.
The initial cached Gradle test result was followed by an explicit fresh run
against the rebuilt library; it was not treated as new runtime evidence.

This is pin/build/host-runtime evidence. The earlier Android TV Guide, source
picker and dual-AAC checks below were performed at Core `c9e7bea`; they are
not represented as fresh managed gateway or physical-device acceptance at
this pin. The original checkout and TV layout remain unchanged.

# Android TV Guide, source failures and alternate native audio — 2026-10-02

Application baseline: Android main `297d469`, Core
`c9e7bea8c0df4258e363bcd9141a38716904a6aa`, design
`1742afa2b50d30638fa46f3abc8c1a76638a51e1`. API36 Android TV x86_64,
owned emulator5576 at 1920×1080, JDK17 and two Gradle workers. No application
or TV layout code changed in this qualification slice.

`GuideProgrammeFocusReturnTest` passed on that Android TV emulator: one test
in 1.9 seconds. It composes the actual Guide and controller with a synthetic
schedule based on the current clock, at the application's 1920-coordinate
density. One initial public semantics focus request establishes the future
programme. Remote OK opens its Watch live details; native Android Back closes
the dialog and returns focus to that exact programme. Public route,
preparation and error state remain idle for playback. No focus request repairs
the return. The isolated test preference namespace preserves the app's session.
The initial test fixture allowed startup errors to race its seeded state;
waiting for startup before initial setup removed that fixture race.

The actual authenticated synthetic app also showed News filtering, native
channel Search and a CNBC result. Those observations are separate from the
repeatable future-programme regression; a complete automated filter/Search/
future-details remote chain has not been qualified. The current collapsed
Guide capture and source still show the previously recorded preview, progress,
programme-time and now-marker gaps. Native keyboard substitution remains the
Android exception. No new full-screen similarity or physical-TV claim is made.

The local HTTPS source fixture now supports a bounded `sourceDelay` and a
named `sourceError` producer through its private control endpoint. Native
Choose source showed 12 healthy rows with Still checking sources. Selecting
Failed provider showed the safe unsupported-format explanation while the
total remained 12; returning to All providers retained playable rows. An
empty-result Retry entered that partial discovery successfully. The final
remote sequence established Choose source focus before activation, opened its
partial picker, then used Android Back to restore that same invoking control
without another playback admission. The fixture request journal recorded no
playback admission during discovery and provider filtering. A stopped fixture
caused intervening empty retry results; those were not accepted as application
failures or successful qualification.

`check-player-audio.py` passed against the actual four-minute H.264/dual-AAC
MP4 from `make-track-media.sh`. The direct player uses Media3's real native
en/es inventory. Back from Audio restored the invoking control; immediate OK
reopened Audio without navigation. Selecting Spanish also returned to Audio,
and immediate OK showed es · Current. Explicit remote Exit released the sole
fixture playback lease. This proves native direct selection and return on this
emulator, not audible hardware fidelity or server-managed output replacement.

Core/design integrity, fixture syntax, the existing category fixture check,
237 host tests (zero failures/errors/skips), normal debug APK assembly,
instrumentation APK assembly and lint passed. The normal debug APK contains no
fixture CA. The same pinned native Core binaries for three ABIs were reused;
no new ABI build is claimed. Media, certificates, APKs, logs and captures stay
ignored. Managed alternate-audio delivery, physical input/hardware and full
Guide presentation remain separate acceptance work.

# AND-042 real multi-track TV return and Guide audit — 2026-10-02

App source: `3f21e4126c708bae87720391994217960249a2f0`; Core
`1f8483e365867f99eb39928cd7f3a23920003515`; design
`85a20e918d44af28d20caa52997b1d307922cf29`. API36 Android TV x86_64,
Android16, dedicated emulator5576, 1920×1080, local HTTPS fixture. JDK17,
Gradle max-workers2, normal app code and Media3; no test player/controller.
The three native Core ABI binaries were reused from the prior qualified build
at the identical Core pin. The fixture-trusting debug APK assembled successfully.

The old adapter rewrote only `value.delivery`, while the current shared TV-web
preview returns a legacy flat playback response. Thus ANDROID_FIXTURE_MEDIA
had no effect and the app could still receive the short HLS clip. The adapter
now wraps flat/nested playback into the current ready v2 lease, sets the actual
native duration/media and renews the same delivery envelope at heartbeat. A
flat fixture POST and its heartbeat returned a 240-second direct original
delivery; the native player independently displayed `4:00` and decoded video
past three minutes. This corrects qualification tooling, not application UX.

`make-track-media.sh` supplies an ignored 240-second H.264 test pattern, two AAC
audio tracks and two actual mov_text subtitle tracks (English/Spanish). The
remote acceptance uses actual native track inventory, not server-synthetic rows:

- INFO → Right → OK opened Subtitles with Off/en/es and Off · Current.
- Back returned to the highlighted Subtitles control. Immediate OK, without
  re-navigation, reopened Subtitles rather than toggling playback.
- Selecting English closed the panel and kept Subtitles highlighted. Immediate
  OK reopened it with `en · Current`. A real English subtitle cue rendered over
  continuing decoded video. The native sequence also paused/resumed media.
- `python3 qualification/check-player-tracks.py` passed the repeatable Back,
  selection and Current checks. Its saved native cue screenshot was inspected;
  subtitle cues are hidden from accessibility, so XML is not a cue-render claim.

Source/app UI is unchanged. This qualifies direct native subtitle-track return
on this emulator/fixture only. Managed output replacement, selecting alternate
audio, long track-list scrolling, physical TV input, sideloaded external
subtitles and image subtitle formats remain unqualified by this pass.

The populated native Guide was captured and inspected. `DESIGN_AUDIT.md`
records the fresh TvLive/TvLiveDetails/TvLiveSearch source comparison at the
exact pin: missing preview/progress/next, channel/logo/row geometry, cell times/
progress, now marker, and details action/hint gaps. The native keyboard is an
explicit Android exception. Details and Search gaps are source comparisons,
not a new complete remote-flow acceptance. TV layout remains unchanged and
design#6 remains open; no new pixel-similarity or physical-device claim is made.

Validation: debug APK assembly, Core/design integrity, Node fixture syntax,
shell generator syntax, Python checker compilation, existing bounded category
fixture check and the actual native remote check passed. No application code or
wire contract changed; no new Rust/Android unit-test count is claimed. Private
media, certificate/key, logs, APK and captures stay ignored. No production data,
deployment, migration or physical device was used.

# AND-042 phone presentation and track menus (design#6) — 2026-09-30

Adopts design `6da30a58c839e4e66465a76c8f80471139147f27` (AND-042). Phone Home
has no header bar and opens on the rounded hero. Bottom navigation is
icon-only and keeps accessible names. Phone cards show only S1 E1 or the year,
with a 6dp lifted progress bar. Home catalog shelves are headed by content
type ("Movies · Popular"), and phone Home live shelves use 104×72dp logo tiles.
Home, Discover/catalog grids, appended pages, Search and the phone Live list
use skeletons instead of loading copy. Audio/Subtitles open the anchored
PhPlayerSubs panel on phones and the TvPlayerSubs row panel on TV, with
current/unavailable markers and key hints.

JDK 17 host tasks passed 202 tests (library 47, app 155), including the new
`PhonePresentationPolicyTest`, with zero failures/errors/skips. Library lint is
clean. App lint reports 74 warnings plus three baseline-filtered UniFFI
errors. The normal debug APK assembled without `viptv_fixture_ca`.

Emulator checks (fixture-trusting debug APK against the HTTPS fixture; private
captures, not committed):
- `air-phone-api36` at 780×1688/320dpi: the Home hero sits directly under the
  status bar with no header; icon-only nav; "Movies · Popular"/"Movies ·
  Seasonal" headings; monogram live tiles; year captions. Fixture media played.
  The Subtitles panel was anchored above the timeline with Off · Current.
  Choosing Subtitles 1 closed it, reopening marked it Current, and Back closed
  the panel while the player stayed open. The phone Live list showed
  now/next/progress rows.
- `air-tv-api36` at 1920×1080: the Home hero and Continue watching were
  unchanged. Player Info → Right → OK opened the right Subtitles panel: the
  focused off-white row was "Off · Current", followed by Subtitles 1 and the
  ▲▼/OK/BACK hints. Selecting a track restarted the fixture output and reset
  control focus to Play/Pause, which predates this change. Back focus return
  to the Subtitles control was not observed, because the 5-second fixture media
  kept ending and restarting.

Not claimed: physical phone/TV evidence (android#3), long-list TV panel
scrolling with real multi-track media, and a pixel metric against the canvas
boards.

# Subtitle cue rendering (android#8) — 2026-09-30

The library now publishes `VideoPlayer.subtitleCues` (backend-neutral, session
scoped) from Media3 `onCues`, and the app draws them over the video viewport with
the profile's Subtitle size/appearance preferences. Host tests cover cue mapping
(positions, numbered lines, blank/bitmap drop), session scoping (cleared on open,
stop, track switch, Off and native deselection; stale-session cues ignored),
sideload ID matching and the appearance/chrome-lift mapping.

Emulator `air-phone-api36` (API 36, x86_64), corpus from `corpus/generate.sh`
served over local HTTP through `adb reverse`:
`./gradlew :connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.viptv.video.AndroidMedia3SubtitleCueTest -Pandroid.testInstrumentationRunnerArguments.airCorpusUrl=http://127.0.0.1:18090`
passed 3/3: in-stream default SubRip, default+forced SubRip chosen without an
explicit selection, sideloaded WebVTT and SRT, and Off clearing cues. The run
found and fixed sideloaded tracks reporting `external = false` (Media3 prefixes
merged format IDs with the source index). Not yet claimed: in-app visual display
on a physical phone/TV (needs an authenticated playback session).

# Playback contract, capability claims and open cancellation — 2026-09-30

SPEC.md is now the Android playback library contract (android#1). Capability
probing is a pure, unit-tested mapping over runtime facts: PiP needs API 26 and
FEATURE_PICTURE_IN_PICTURE; hardware decode comes only from API 29+
`isHardwareAccelerated`/`isSoftwareOnly` and is reported as Decode; subtitle
formats are text-only; seekable live, surface reattachment and playback rate are
`false` until the SPEC.md validation matrix is satisfied. Pending opens are now
cancelled by caller cancellation, `stop()` and `close()`, and a stopped session's
late failure is never published. Dead controller paths, the unused TrackType enum
and the retired quality preference field were removed.

JDK17 host tasks passed 183 tests (library 38, app 145) with zero
failures/errors/skips. Library lint found no issues; app lint keeps 73 warnings
and the three baseline-filtered UniFFI errors (baseline unchanged). No APK,
emulator or device run: no new device capability is claimed.

# AND-041 foreground implementation checkpoint — 2026-09-30

The isolated `fix/android-foreground-lifecycle` candidate adopts design
`9bb130ae80af18d41c411c137c6a515b175a5991`. Host tests exercise bounded/coalesced
foreground identity, explicit denial, late cancellation, profile/account authority
changes and profile-scoped metadata. Decoder/network failures now retire the exact
lease and expose explicit recovery rather than silently requesting gateway or
transcode delivery. Same-delivery managed network recovery remains unchanged.

JDK17 library/app host tasks passed with 174 tests and zero failures/errors.
Core8 remains unchanged. This is an implementation/review checkpoint, not final
native-media qualification. Earlier phone/TV synthetic-account delay/timeout/Retry
observations do not qualify later authority/readiness changes. Accepted token
rotation cancellation, actual required-header/nonzero Resume media, native/backend
cleanup and the final normal system-trust APK remain pending. Fixture-trusting
QA APKs must not be distributed. No production operation or ARM/device claim.

# BE-002 Android live cutover handoff — 2026-09-29

## Actual Android x86_64 JNA/JNI instrumentation — 2026-09-30

The same isolated native-stress branch now adds only `:app` instrumentation test
dependencies (existing runner/catalog versions), a three-case test class and this
evidence. Production UI/policy, Core8 source/bindings/wire/pins and the owner's
original Android/handoff checkouts are unchanged. `AndroidJUnitRunner` was already
the configured runner; no Compose test Activity or MainActivity rule was added.

Normal debug and test APK assembly passed. All three packaged `libviptv_core.so`
files were copied from the reviewed handoff's existing Core8 builds and independently
hash-verified: arm64-v8a `7bda7ca7…`, armeabi-v7a `ff9c1687…`, x86_64 `66bda53c…`.
Only x86_64 was executed here; packaging unchanged ARM files is not ARM runtime
qualification. Normal APK SHA256:
`ea1bdedc1082898b6333f9c53a487aee4b718792a87b28b5981052cd7607ffaa`;
test APK:
`ea14f9420f57fa8ee6911f81adee88b8b019b09c2eb341332f0445af2fa4fa9b`.

One new API36 Google APIs x86_64 phone AVD used a private `ANDROID_AVD_HOME`,
owned serial `emulator-5584`, and only class-filtered instrumentation:
`org.viptv.app.SharedCoreAndroidNativeStressTest`. AndroidJUnitRunner reported
**OK (3 tests)** in 0.887s, zero failures. Each case has a 60s deadline:

- 200 actual Android native bridge start/resolve/drop and UTF-8/error-buffer cycles;
  repeated close is safe and post-close access is rejected.
- Real Android main-looper/CoreSession work: 50 queued begin/close cases never
  reach storage/render; 20 native storage-error renders actually suspend and
  cancel, with no callbacks after close. Actual Android main-looper identity is
  asserted; no `setMain`, test-owned dispatcher, virtual clock or replacement
  core is used.
  Only a test storage effect fails intentionally, before any HTTP is produced.
- 20 actual JNI concurrent-close races, with 3,813 real native views and 4,267
  exact closed-handle refusals; old storage effects cannot publish into a newer
  native epoch. Owned workers terminate; unexpected errors are not swallowed.

MainActivity/default-origin code was never launched. Each activity monitor reported
zero app Activities, and before/after task dumps contained no MainActivity. Effects
were resolved in test scope or refused by the failing storage fixture; no real
backend/provider/account/network or media source was used. Private evidence is
retained at `/tmp/android-native-jni.T3QJqL/`. Both installed packages were removed,
the exact owned emulator stopped, its private AVD deleted and ports5584/5585 verified
free. No existing AVD/device, shared HTTPS service or production data was changed.
The test-only AVD can be recreated from the installed system image; no user data
was discarded. Host regression tasks and Core/design/diff checks passed; the 169
qualified host results remain unchanged.

This closes the bounded Android x86_64 JNI/buffer/MainLooper cancellation cases,
not foreground/background Activity, active Android HTTP cancellation, PiP/media,
ARM execution, physical hardware or instrumented native leak/allocation proof.
Those remain separate gates. Runner configuration was checked against current
Context7 Kotlin documentation and the official AndroidJUnitRunner/SDK documentation;
the existing `androidTarget` configuration was retained without a plugin migration.

## Actual host UniFFI lifecycle stress — 2026-09-30

Isolated `test/android-native-core-stress` starts at handoff
`14bc969ff8049e47bb0c91658eeb4db33f5077c4`, with unchanged Core
`8ae9f81bb753aaf2de53af5b594ead29845e1a8a`. Only a new host test class and
this evidence note change; original Android UI, handoff checkout, main source,
generated bindings, frozen wire, design and native pins are untouched.

`SharedCoreNativeStressTest` adds three actual generated UniFFI/JNA + host Rust
tests, each with a 60-second deadline:

- 300 create/start/resolve/drop cycles, idempotent close/post-close refusal, UTF-8
  response buffers, malformed native bridge/normalization errors and ten oversized
  (>2 MiB) normalization refusals. Subsequent calls must still work after errors.
- 40 shared-object close races, four readers and 16,000 read attempts; real native
  views and only the exact generated closed-handle refusal are accepted. Native,
  JSON, worker, timeout or unexpected exceptions fail the test rather than being
  swallowed. Both native successes and concurrent closed refusals must occur.
- Ten waves of three actual loopback HTTP reads generated by native Crux. A real
  server holds the replies; two reads are cancelled and one returns a real late
  200 response in each wave. Resolving either stale outcome after a new native
  epoch cannot alter the fresh Pairing view.
  Completion count, cleared native-call references and owned executor/server/socket
  termination are asserted. The only executed network origin is owned loopback;
  no default backend/provider origin or real account is contacted.

These exercise 370 native bridge lifetimes and generated String/RustBuffer success
and error paths. They do not instrument allocation/free counts, prove sanitizer/
Valgrind leak freedom or manipulate raw native pointers. Own-process RSS/heap
observations are diagnostic only: JVM allocation, JIT and collection change them.
The final full-suite observation is recorded with the test XML; it is not a memory
leak acceptance threshold.
That run observed 1,004 real native race views and 15,156 expected closed-handle
refusals. RSS was 262,492/411,480/242,676 KiB at warm/end/post-collection points;
used JVM heap was 121,993/283,785/30,123 KiB. These whole-process readings show
collection effects, not native allocator ownership or leak freedom. The three
stress cases completed in 0.494 seconds on this host; the deadlines remain 60s.

JDK17 host preparation, focused tests and the complete 169 library/app host tests
(29 + 140), with zero failures/errors/skips, passed. Core/design integrity and
diff checks passed. No emulator, ABI rebuild, APK build/install, hosted workflow
or production operation was required or performed for these test-only changes.

Remaining gates: actual Android JNA/JNI/device runtime, `CoreSession` owner-thread
and Activity foreground/background/cancel/close behavior, PiP/native media stress,
physical decoder/remote coverage and instrumented native allocation ownership.
Existing Kotlin-injected retry/controller tests are not relabeled native proof;
this host stress checkpoint does not certify every BE-002 native lifecycle gate.

## Isolated category screen wiring and quality-control retirement — 2026-09-30

GuideScreen preserves its existing row, fixed All/My channels/Recent/Search
actions, geometry, channel/time state and Back meaning. Existing Search-Right /
All-Left TV terminal edges request category pages; normal provider-to-fixed-control
focus movements remain available. Phone uses explicit continued user drag beyond
the true row terminal, never provider visibility or inertial arrival alone.
Observation-only pager callbacks retain captured revision/scope guards. Exact
namespaced row key/index/offset and TV focus survive full Guide leave/reentry;
new pages/scopes reset that state. Actual provider focus confirms TV anchors;
three bounded frame attempts plus manual-focus confirmation recover failed requests.
Touch anchors wait for drag/fling settlement and closed overlays before restoration.
Settings removes only Maximum quality and its obsolete description; real decoder
and source-quality facts remain unchanged. Generated Core/server wire is untouched.

All 166 library/app host tests passed (eight added guard/row/focus cases), normal
debug APK assembly and lint passed. Lint retains 74 warnings/three baseline-filtered
errors. Core/design checks and the bounded/default/400-category opaque-cursor fixture
checks passed. All three previously built Core8 ABIs remain packaged; normal APK
has no fixture CA and its packaged network XML trusts system certificates only.
Normal APK SHA256:
`b4bfc60c1aaca0905d3e1165986b12a4c864d2e7248bc70f0b42aa9f3d7be8fb`.

Dedicated API36 QA phone5574/TV5576 used fresh owned loopback HTTPS ports9447/9448
and synthetic400 categories/400 channels. TV verified first-visible-channel Up to
a composed category control, ordinary Search access, forward actual Category201
focus, reverse actual Category200 focus, Search dialog Back, and noninitial All /
Search full Guide-to-Home/reentry focus/row restoration. Phone verified Search
activation/Back at the terminal, forward/reverse provider anchors, and a continued
1.5-second terminal drag with exactly one category request and real201 at the left
anchor after settlement. The earlier fast-response drag race was reproduced and
fixed; ordinary short drag also restored201. Private screenshots were inspected
under ignored qualification/artifacts; no captures or fixture-trusting APK ship.

These are native emulator UI/Core checks with mocked API/art boundaries, not real
provider/gateway media, physical remote/codec/HDR/DRM or production qualification.
The owner's original Android UI checkout is untouched; this remains a separately
reviewable/cherry-pickable handoff, not a merged or deployed UI update.

## Engine-free shared Core adoption — 2026-09-30

The isolated handoff adopts published Core
`8ae9f81bb753aaf2de53af5b594ead29845e1a8a` using `scripts/core-sync.mjs`.
Host/Android preparation selects only `native`, removing the retired provider
feature. Vendored changes are script-generated; the frozen Kotlin wire/model
declarations are byte-identical. AppController, GuideModels, GuideCategoryPager,
all application/library source and UI files are untouched in this adoption.

JDK17 host preparation, fresh execution of all 158 library/app unit tests,
three release native ABI builds (arm64-v8a, armeabi-v7a, x86_64), normal debug APK
assembly and lint passed. Core/design integrity checks and diff checks passed.
Lint retains 74 warnings and three baseline-filtered errors. The packaged APK
contains all three native libraries, no fixture CA resource, and its packaged
network security XML trusts system certificates only. Normal APK SHA256:
`f52367af8207e5616cfe8e3e25b40d23370bec22788b08f3b6ad5c3727cca935`.

No APK installation, emulator/physical playback, hosted build or production
operation was performed. Existing category UI integration and hardware gates
remain open. The owner's original Android checkout remains untouched.

## Category controller preparation — 2026-09-30

The authenticated category coordinator retains one replacement page of at most
200 records and preserves the original implicit/explicit catalog query across
opaque forward/reverse cursors. Profile/catalog/snapshot/revision guards cancel
or reject late work; failures retain current categories and explicit retries use
the same cursor. A 15-second page deadline clears busy state safely. Channel/EPG/
time/playback state is independent, and new-page viewport acknowledgement avoids
automatic forward/backward oscillation. Guide channel publication additionally
rechecks the selected profile; controller disposal cancels guide work.

JDK17 host preparation and all 158 library/app unit tests passed, including the
final unchanged-viewport guard. New tests exercise five forward/back category pages,
200-record retention, implicit and explicit queries, stale scope/revision,
restoration acknowledgement, failed retry, metadata/duplicate/empty rejection,
safe diagnostics and deadlines. A real HTTP/native-core wire fixture traverses
400 synthetic categories forward and back without inserting a catalog override.

This is controller/transport preparation, not visible UI or emulator acceptance.
`GuideScreen.kt` has not been edited in this pass and still needs the owner's
boundary/viewport integration described in README. The frozen v2 wire and core
pin are unchanged; the root Android UI checkout remains untouched.

All three native ABIs, normal debug APK and lint also passed at `76338e8`.
Lint retains 74 warnings/three baseline-filtered errors; no fixture CA is in the
normal APK. Artifact SHA256:
`3e50663994d416d05f17f1b11bde0da814a8b80ef3eacdc12c4a6cb2413eb6d2`.
The controller-only category addition was not re-emulated or installed.

## Preference transport follow-up — 2026-09-30

Preference PUTs now omit the retired `quality` field while sending the six active
audio/subtitle/autoplay fields. The compatibility model remains until the UI
owner removes its Maximum quality row; v2 playback already ignores that field
and retains measured decoder dimensions. No settings layout, generated core
binding or frozen v2 playback/catalog contract changed.

JDK17 host preparation and all 148 library/app unit tests passed. The new actual
HTTP adapter fixture verifies the outgoing body and decoding a backend response
without quality; its synthetic partial-merge store retains a historical value.
This is transport evidence, not a real backend migration or a device test.
All three native ABIs, normal debug APK assembly and lint passed at source
`c3ebe7b`; lint retains 74 warnings and its existing baseline constraints.
The normal APK contains no `res/raw/viptv_fixture_ca.pem` (ZIP entries checked).
Artifact SHA256:
`8332e35f654a067f8e36393d04d62d73003d9b81516ed33ccc2a3825c45ec1eb`.
No APK installation or repeat emulator qualification was performed for this
transport-only change. The owner's root Android checkout remains unchanged.

Isolated branch `refactor/android-backend-cutover`; core fba95c8, matching backend
2c2eca2. Normal Home/recent/search/guide and exact live playback use v2. No native
original URL is obtained from a source card: its opaque handle goes through the
ordinary lease path. Gateway recovery retains the exact selected live channel;
renewal/stop never fall back to legacy endpoints. Native HTTP stays supported.

Guide channels retain at most three forty-row server pages, with reverse cursors
for evicted pages and no local playlist index or fabricated totals. Schedule
fetching covers the actual viewport plus bounded lookahead, with a 200-entry
cache, debounced scrolling, generation guards and cancellation. Suspended guide
work clears busy flags without losing its viewport; return resumes missing EPG.
Paging errors retry on a later scroll action, not unchanged effect callbacks.
Native responses reject missing reverse contracts, duplicate IDs and substituted
explicit playlists. The existing viewing geometry is unchanged.

Phone API36 emulator-5574 exercised synthetic 400-channel HTTPS input: lazy
entry, forward cursor pages beyond channel300, backward retrieval to channel1,
real H.264/AAC HLS frame decoding through Media3, exact source admission/release,
and return to channel157 at the same scroll coordinates. Captures were inspected
privately; no real provider or production account was used. These observations
precede final defensive guide lifecycle guards, which additionally have host
policy coverage; Android TV/physical-device qualification remains unperformed.
The first playback fixture omitted mandatory direct headers and was corrected;
the client validator was not relaxed. Native input used ADB, not host-window
keyboard forwarding or a physical remote.

JDK17: host preparation, all three Android ABIs, 147 library/app unit tests, debug
APK and lint passed. Lint retains 74 warnings and three baseline-filtered errors.
Earlier native RustBuffer crash stress qualification is still open. Categories
currently expose one bounded 200-entry page; extended category traversal and real
backend/gateway end-to-end tests remain gates. No deploy or production migration.

# BE-002 raw live API preparation — 2026-09-29

Core pin b75393e matches TV-web. New explicit transport methods use generated
raw live/category DTOs, original default/cursor filters, exact live source cards
and v2 guide routes. JNI/HTTP fixtures retain HTTP logos/provider order and safe
catalog-change/parent errors without indexing a playlist or falling back to
legacy responses. Source cards cannot supply playable URL/header authority.

JDK17 host preparation, all 137 library/app tests, three native ABIs, debug APK
and lint passed. A fresh rerun of all 108 app tests also passed. One actual
adapter bug was fixed: bodyless canonical POST/PUT/PATCH requests now supply an
empty native HTTP body instead of failing before network execution. Five new
wire tests exercise these APIs; ordinary Guide/Home/live playback callers remain
legacy pending the coordinated cursor/lease cutover. No viewing UI was added.

The first full JVM run aborted with native RustBuffer assertions/SIGSEGV. C ABI
checks against the same built library and later focused/full/fresh JVM reruns did
not reproduce it. Its cause remains unproven; the crash record is kept privately,
not committed. Passing reruns do not close native stress/device qualification.
No APK installation, provider use or production deployment occurred.

# BE-002 same-source gateway recovery — 2026-09-29

Initial native open and active VOD failures use the same bounded delivery ladder:
original delivery, authorized gateway auto output, then explicit conversion only
after another native decoder refusal. The source, title position and manual tracks
are unchanged; successful gateway/conversion intent survives seek/pause/track
replacement. A new source starts direct-first again. Control errors never take
the conversion path. Failed admissions release before retry under an independent
five-second bound; cancelled/obsolete openings cannot advance to a new delivery.

Media3 HTTP401/403/404/410/429 failures remain source/access causes rather than
decoder/connection refusals. Error copy preserves the status without raw engine
details. The existing recovery dialog remains the error/Retry/source-choice UI;
no viewing layout or additional control was introduced.

JDK17 host/JNI preparation, all 132 library/app tests (no failure/skips), three
Android ABIs, debug APK assembly and lint passed. Lint reports 74 warnings and
three pre-existing baseline-filtered errors; this is not a warning-free claim.
Nine new pure/coroutine/HTTP fixtures cover bounded escalation, no-gateway refusal,
release/cancellation/obsolescence and exact canonical request/position/track facts.
The first wire fixture omitted required gateway fields and failed; the corrected
complete envelope passed the full rerun. The APK is
app/build/outputs/apk/debug/app-debug.apk; no install/deployment occurred.
Live, emulator/device decoding, PiP, real gateway native integration and the
remaining BE-002 checklist remain open. These are host tests, not codec acceptance.

# BE-002 active Android VOD playback — 2026-09-29

Movie/exact-episode playback now uses shared-core v2 intent/lease contracts with
correct phone/TV platform facts. Startup is bounded to 45 seconds; cancellation
and ambiguous admission use the identical request body for bounded five-second
cleanup. Active leases renew, stop on refusal/expiry and validate on foreground
return. Late renewal cannot restore a released cache entry. Recovery preserves
the last title position and stops progress writes after retirement. A newer
Pause action prevents foreground validation from resuming playback unexpectedly.

Gateway processing `mode: direct` remains managed delivery: native start is zero,
title offset is server-owned, and seek/pause policies use delivery kind. Valid
native HTTP source headers remain intact. Server language/selected-track metadata
feeds Media3 options. Copy URL rejects gateway capabilities and releases its own
lease; canceled copy cannot deliver a clipboard value. Safe HTTP error codes are
retained rather than discarded by the native transport.

With JDK 17: host core preparation, all 123 library/app tests (zero failures or
skips), three Android ABIs and debug APK assembly passed. Eleven new coroutine /
JNI tests cover polling, ambiguous/canceled admission, startup/cleanup deadlines,
late renewal, terminal provider errors, managed timeline and renewal lifetimes.
HTTP fixtures verify platform facts, original headers, gateway metadata, progress
and Copy URL cleanup. The APK is app/build/outputs/apk/debug/app-debug.apk and
requires the matching v2 backend; it was not installed or deployed in this pass.

This is host/wire validation, not emulator, codec, physical-TV or PiP acceptance.
Live remains legacy. Automatic same-source gateway fallback after native decoder
refusal, native/device integration and the rest of the original checklist remain
open. No viewing layouts were added or reshaped.

# BE-002 shared conversion/track mapping — 2026-09-29

Core pin 4418f1ddb3c1640276f31b130deeb2d4ffa6873d matches TV-web. A real JNI /
generated-Kotlin fixture verifies scoped audio conversion, Android TV identity,
2160p decoder facts and preferred language from playbackV2Intent. Host preparation,
both unit suites, all three Android native ABIs and debug APK assembly passed.
Ordinary playback transport is not yet switched to v2; background lease recovery,
profile-preference integration and device qualification remain open.

# BE-002 shared playback lease types — 2026-09-29

Core pin f48f983454b21ba637b4580b426ea8e1647ffbb8 matches TV-web and supplies
generated v2 playback lease/request types and native normalization. Host native
preparation, both unit suites, three Android ABIs and debug APK assembly passed.
A JNI-to-generated-Kotlin fixture verifies native HTTP direct delivery, enum
decoding, expiry units, source authorization and expired-session URL removal.
The application has not yet switched its playback lifecycle to v2; polling,
renewal/cancellation and conversion/track preference parity remain open. No
emulator/device or deployment evidence is claimed for this checkpoint.

# BE-002 VOD discovery adoption — 2026-09-29 (in progress)

Adopted core 4817b07f985d23687ca54df888222f5af96c0cb2, matching TV-web. Movies
and exact episodes use v2 discovery; terminal empty results retain safe provider
failure codes/messages while partial success keeps healthy sources. Native
fixtures check connection-limit text without exposing upstream URL credentials.
Live discovery/playback and the rest of client cutover remain pending.

With JDK 17, prepare-core host, both Gradle unit suites, prepare-core android
(arm64-v8a, armeabi-v7a, x86_64) and app:assembleDebug passed. The debug APK is
app/build/outputs/apk/debug/app-debug.apk. No emulator/device playback or
production deployment is claimed for this checkpoint.

# TV-038 Resume accent — 2026-09-26

Home and detail Resume now retain the user's accent on Android TV. A white
focus ring marks the focused action without changing its size; other TV actions
retain their existing focus style. Adopted design `0322985`.

Library/app unit suites passed **99 tests**; APK assembly and lint passed with
61 advisory warnings and the existing three generated UniFFI baseline entries.
The production-connected API 36 TV emulator received the normal APK as an update
and retained its sign-in. Its Home Resume color, dark icon/text, white ring and
unchanged 228×72 bounds were visually checked. The deliverable contains no test
CA. No new physical-device or playback qualification is claimed for this style
change.

# Search, player layout and Up Next — 2026-09-26

AND-037 corrects TV keyboard navigation, preserves catalog-labelled search
results and resets their offsets per query. A shared native timeline includes
buffered media, aligned time labels and a circular seek handle. Phone portrait
and landscape use distinct control arrangements with transparent tools. Both
platforms now display a cancellable ten-second Up Next card using the resolved
episode still and existing continuation policy.

Library/app tests passed **99 tests** (28 + 71), including independent catalog
arrival/identity and paused countdowns. Debug assembly, lint and core/design
integrity passed. Final lint retains 61 advisory warnings and the existing three-entry
generated UniFFI baseline. The shared
Rust/native ABI pin is unchanged; the APK uses its previously verified ABI set.

[DESIGN_AUDIT.md](DESIGN_AUDIT.md) records inspected surfaces, measurements,
corrections and the limits of the emulator evidence. Native QA used separate
headless emulators and local HTTPS fixtures; normal phone/TV delivery retains
the production origin and sign-in. Test CA/media are not in the delivered APK.

# Native direct playback and phone sign-in — 2026-09-26

AND-036 adds phone username/password sign-in with optional pairing, original-URL Media3 playback, source progress/errors/cancellation, provider identity filters, stateful My List actions and corrected native insets/TV hero behavior. Core is `2cfd963e5cf9178d93e8bbb7462bcccb92b152f3`; design is `fb7e662165da44a917504da23a73bf826f9e3a9c`.

- Host and all three native ABI builds passed. Library/app suites passed **96 tests** (28 + 68). Final APK assembly and lint passed; 61 advisory warnings remain, with the existing three generated UniFFI baseline entries. Core/design integrity checks passed.
- An isolated API 36 phone signed into the real Rust server using a native password form, selected its profile, displayed both configured addon provider groups, and changed the hero's library state immediately.
- A 90-second H264/AAC original MP4 decoded through Media3 with required Cookie, User-Agent and Referer headers. The native player showed direct delivery and the full duration. Scrubbing reached the corresponding decoded frame while paused. Excluding the slider from Android's edge Back gesture fixed scrubbing from the start of the track.
- Explicit exit released native audio and the backend lease. Backgrounding active playback and allowing the activity to stop released audio and returned to title details on foreground. Slow source selection displayed Opening source immediately; Back prevented late playback and left zero active sessions. An actual source HTTP 403 appeared in recovery instead of a generic failure.
- Real-server phone/TV emulator updates retained their sessions. Their DNS resolver had become unreachable; restarting with explicit host-network DNS fixed connectivity without clearing data. The normal APK contains no fixture CA.
- TV remote Down from the hero focused Continue Watching with its heading unchanged at y=700; the hero label remained at y=150. Down to the following shelf scrolled. Up to Continue Watching and then hero controls restored the full hero at those original bounds. Artwork scrolls with the hero. The TV library icon changed to checked immediately, and the temporary favorite was removed after verification.

Private screenshots remain in ignored qualification/artifacts. The isolated MP4 establishes native direct decoding, seek and lifecycle behavior, not every provider/codec/HDR/DRM combination. Physical TV/phone, PiP and alternate subtitle rendering are not newly qualified by this pass. Backend production and server GPU evidence are recorded in the owning deployment notes.

# TV emulator desktop input correction — 2026-09-26

The interactive TV AVD inherited `hw.keyboard=no` and `hw.screen=no-touch`
from its device preset. ADB-injected D-pad tests bypassed those restrictions,
so the earlier app-focus evidence did not qualify computer-keyboard or mouse
input. The dedicated AVD now enables its keyboard, disables keyboard-lid
emulation and exposes a multi-touch screen, retaining its TV image and D-pad.
The cold restart preserved the real-server sign-in; the phone AVD was unchanged.

Checks through the actual emulator windows, against the real VIPTV server:
computer Right moved focus from Resume to Details; an actual mouse click opened
title details; Extended Controls Back returned to Home and its Right button
moved focus. This correction changes local emulator configuration, not the APK
or shared product policy. The required configuration and input-path acceptance
are now documented in `qualification/README.md`.

# Native phone and TV design conversion — 2026-09-26

AND-035 replaces the old Roku UI for Android. `DESIGN_REF` is
`5b1ca1ba1bbccb98c6228362e767e580f94b0865`; `CORE_REF` remains
`be018809e7e8893352c7c57948e9537d6be9457d`. This entry describes the
native conversion on `refactor/open-source`, not the historical candidates below.

## Automated checks

- Host core and arm64-v8a, armeabi-v7a, x86_64 native builds passed.
- Library/app unit suites: **93 tests, zero failures/errors/skips** (28 + 65).
- New regressions cover progressive independent search results, queue publication
  before optional metadata, per-title metadata coalescing, parent series/source
  return routes, minimal focus scrolling, safe identity-read connection recovery,
  and avoiding repeated source display projection.
- Debug APK assembly and Android lint passed. Lint retains 60 advisory warnings
  (dependency/version, KTX/style and existing library warnings). Its three-entry
  baseline is limited to generated UniFFI Cleaner calls: that generated code probes
  the API by reflection and falls back to JNA below API 33. No application errors
  are suppressed. Native runtime behavior on API 24–32 is not newly certified.
- Pinned core/design integrity, all 624 referenced avatar files, design validation,
  fixture syntax and patch whitespace checks passed.

## Native emulator observations

Dedicated API 36 x86_64 emulators: phone `viptv-design-phone` at 390×844dp
(780×1688px, density 320), TV `viptv-design-tv` at 1920×1080px. Input and
screenshots used ADB/UiAutomator against loopback HTTPS fixtures; no production
account/history or physical device was modified. Private captures live only in
ignored `qualification/artifacts`.

| Surface | Observed result |
| --- | --- |
| Pairing and profiles | Phone pairing approval enters chooser; parent PIN is masked and unlock resumes the selected profile. Caption taps and TV OK enter Home without restarting; the twelve-profile TV fixture pages with D-pad controls. Avatar picker displays the correct packaged characters. Create/select/edit/delete and an idle identity refresh were exercised. |
| Home | TV hero and equal-size actions remain in the initial viewport. Queue publishes before optional enrichment. Compact shelves scroll horizontally; long catalogues reveal the selected image and caption. |
| Navigation | D-pad Home → Settings → sidebar → Home restores focus. Collapsed/expanded sidebar icon bounds match. Phone tabs, header profile and Back remain operable. |
| Discover and Search | Phone portrait grid and type filters, system keyboard and dismissal passed. TV physical-key input retains the full fast query; asynchronous groups stay at the top while typing. D-pad can reach the final movie result; empty Discover focuses its retry action and can return to the rail. |
| Series | Phone series hero and vertical episode list render. TV season 2 and episode 10 are reachable; source dismissal restores that season and exact episode. |
| Sources and menus | Source list/quality controls, explicit selection, details and dismissal render at native phone or scaled TV density. A six-second TV hold opened its action menu before release (observed at 3.17s); release did not activate the menu action. Unit tests cover the exact 700ms threshold. |
| Guide/live | Current guide entries render. Watch live uses direct channel playback and returns to Guide. Phone/TV live chrome has audio, subtitles and exit; no pause, seek or next controls. TV media Pause does not expose VOD controls. |
| VOD playback | Local H.264/AAC HLS decoded through Media3 on phone and TV. Pause, track-menu open/close and explicit exit were exercised. Phone fullscreen retains the paused frame and fits its aspect ratio; returning exits fullscreen. |
| Settings/forms | Grouped settings, playback choices, OLED control, profile name input, avatar grid and confirmation sheets were inspected. Phone keyboard insets and 1.3× system font scale remain usable; scale was restored to 1.0. |

The 12-second local HLS test pattern proves native decoding/surface integration
for that fixture only. Its live designation and alternate server track inventory
are synthetic. There is no new certification of physical remotes, real live
networks, alternate track/subtitle rendering, HDR/DRM, UHD hardware capability,
managed remux/transcode, PiP/casting or store signing/publication. No production
deployment is claimed. Historical physical measurements below apply only to
their named revisions. See `qualification/README.md` for reproducible local steps.

# Failed card artwork recovery — 2026-09-14

Card image fetch/decode failure, after the existing resized/original transport retry, is reported to shared Rust using `failedImages`. The renderer requests a new shared projection; episode cards can use the series landscape and never a portrait fallback. Failure observations reset when card identity or artwork changes and do not affect saved progress or playback identity. A native-backed Kotlin regression covers failure input serialization and the shared landscape/empty result. Hosted app review must compile and run it; local Gradle/emulators remain disabled. No new physical-device acceptance is claimed.

# Native shared-core adoption — 2026-09-13

CORE_REF pins dd192cd09e3aac6810ec3cdd862119fb0646c82d; DESIGN_REF pins e821c297de2e81b47a6a1b22ed8aa0522cdd04e5. The app now calls native Rust for session restoration/profile selection/sign-out, normalized responses and artwork/source/continuation/progress rules. Generated Kotlin codecs decode the shared DTOs. Hosted CI builds the exact pinned native source for JVM unit tests and three Android ABIs before the app APK.

Hosted run 34734502071 built the host and three Android native libraries and compiled Kotlin, then exposed a missing Linux JNA dispatch resource in JVM tests. The unit-test runtime now includes the host JAR alongside the app’s Android AAR; the successful final hosted results are recorded below. Final hosted review [34735214063](https://github.com/viptv-org/android/actions/runs/34735214063) passed for implementation `275d9e4c66d10ff027a0c887d2273c5052b3ec84`: 85 JVM tests, zero failures/errors/skips, all three Android native ABI builds, and debug APK assembly. The 38,149,256-byte APK contains both libviptv_core.so and libjnidispatch.so for arm64-v8a, armeabi-v7a and x86_64; no machine-local credential file was packaged. Native-backed tests exercise the generated Kotlin codecs and library, including source display metadata, artwork roles and startup effects.

No local Gradle, emulator or new physical device acceptance is claimed. Earlier device measurements apply to their named historical revisions, not automatically to this migration.

# Clean Roku presentation replacement — 2026-09-12

Every Android screen implementation was replaced with the Roku reconstruction, retaining authenticated account state and the direct-first Media3 adapter. The design pin is `e3c53771a9be75b0ba8815c5b15e8dfeb1e93cde`. No Roku runtime code, production deployment or original profile history was changed. No local Gradle or emulator was used.

The integrated implementation `7ed787f0a2ae3544abaae8490bf9bfecb2dae086` passed [hosted review 34725084592](https://github.com/viptv-org/android/actions/runs/34725084592): 84 tests, zero failures/errors/skips, and APK assembly. It was installed as an update on the authorized Android 14/API 34 onn. Streaming Device 4K pro. Pairing was retained. The final profile-only refinement is `0818892b235e7c1698e6ba591c7b7527ca619405`: original V mark on the chooser and transparent loaded-avatar backgrounds; [hosted review 34725540311](https://github.com/viptv-org/android/actions/runs/34725540311) passed the same 84 tests and APK assembly, and that APK was installed as an update.

## Private visual comparison

Both captures are normalized to 1280×720 using bilinear sampling; whole-frame SSIM uses no masks or alignment adjustment. Images stay private and are not checked in. These measurements apply to the named states, not every page/state in the application.

| Matched state | Whole-frame SSIM |
| --- | --- |
| Home hero, Bleach, owner profile, Resume focused | 0.9301 |
| Home Trending Movies / Popular Series shelves | 0.9471 |
| Settings, Switch profile focused | 0.9485 |
| Empty Search, keyboard focused | 0.9100 |
| The Gentlemen episodes, first episode focused | 0.8723 |

The main Home, Settings and Search comparisons exceed the owner's 90% target. Episode/detail typography and artwork still differ; uniform 90% parity across every screen is not certified. Mayday's portrait differs even after a fresh Roku launch, so this is not attributed to a simple in-memory cache. Dynamic content and native font rendering remain visible differences, not silently excluded pixels.

## Physical end-of-app pass

Remote navigation opened populated movie/series details, season selection, More info, Sources and source Info, and returned through Back. Actual profile episode progress now drives the progress marker, watched state, season and initial episode target. Sources retain title/description/filename metadata; the prior blank-body failure is corrected. Settings/preferences and choice dismissal, Search, populated Guide with server-labelled times and programme Info, Discover and profile selection were inspected on the actual device.

On the dedicated QA profile, exact-source Resume opened real video around 9:13 from the persisted 9:11 position. Hardware Play/Pause paused playback; directional input opened Audio, Back closed only that menu, and subsequent Back hid controls then stopped to Details with `Resume at 9:46`. Backend progress was 586.618 seconds with the exact-source fingerprint retained. Playback was stopped, the original owner profile was restored, and only the recorded QA profile was deleted; original profiles remained.

This pass does not certify every codec/HDR/DRM/device combination or every remote hold/seek/Next edge case. The unchanged policy tests and older behavior records below remain useful history, but are not blanket visual acceptance. Track remaining parity in Android #3/design #6.

---

> Superseded presentation: the owner rejected this UI on 2026-09-12. The clean Roku reconstruction replaces all screen implementations. Historical behavior/build evidence below is not visual acceptance of the replacement.

# Android TV acceptance

Read `DESIGN_REF` and the pinned visual/behavior specifications before evaluating a candidate. A hosted build is a compile/unit result; physical remote, surface, playback and timing results are separate evidence.

## Resource and access constraints

Use hosted `app-review.yml` builds for root player-library tests, app tests and APK assembly. The owner deferred emulators after server OOM; keep local Gradle and emulators stopped. Install debug APK updates with the stable development signature to retain pairing. Keep real-device access in the workspace's ignored, mode-0600 `DEV.local.md`; never publish its contents or temporary pairing codes.

Use a dedicated temporary QA profile for playback/history mutations. Record its exact owned ID privately, preserve all original profiles, and delete only that QA profile after acceptance. Stop playback before finishing. Screenshots are private inspection evidence only and do not belong in this repository, the design repository or release assets.

## Evidence on 2026-09-12

Hosted [34715425796](https://github.com/viptv-org/android/actions/runs/34715425796) passed root player-library tests, app tests and APK assembly for `d9b78fe`.

On the authorized Android 14/API 34 onn. Streaming Device 4K pro, installed as an update:

| Scenario | Observed result |
| --- | --- |
| Update, pairing retention, profile selection | Passed; existing pairing retained and QA profile selected |
| Home, detail and source data | Real data/artwork loaded; initial detail/source focus worked; source human names displayed |
| Explicit source playback | Real video decoded on the TV |
| Hidden-overlay hardware Play/Pause | Overlay returned and pause control changed to Play |
| Periodic and exit progress | A QA history row saved with a nonempty source fingerprint |
| Back return destination | Back hid controls, then stopped playback and returned to Sources |
| Managed full-title timing | Failed: window-relative HLS position regressed as the playlist slid; adapter/title mapping fix requires retest |
| Overlay geometry | Timeline thickness/time labels and gradient bounds differed; corrections require new APK inspection |

Later working-tree changes are not certified by that evidence. Re-run Resume, direct/managed seek with pause preservation, track replacement, Next and progress against the corrected full-title clock. Also inspect the expanded Guide, Search, source paging/filtering, profile/avatar editor and Settings on the actual device. Discover parity and physical focus-return cases remain under implementation/acceptance.

The hosted `1a67fe9` artifact passed compilation and unit checks, but its initial physical
check found that remote DPAD Center did not activate focused Home/hero actions. Touch
activation reached the correct Source picker, isolating the defect to the shared
`Holdable` remote modifier order. The next candidate moves preview key handling ahead
of the sole clickable focus target; this correction still needs hosted and physical
validation.

## Release acceptance record

Record immutable commit, hosted run, device model/OS, source delivery mode, entry action, result, failure/recovery and return destination for each scenario. Record automated, real-media and hardware evidence separately. Missing tests and observed failures remain explicit; neither a debug APK nor passing fixture tests establishes 100% device or design conformance.

## Follow-up physical pass: 1a67fe9

Hosted [34716881231](https://github.com/viptv-org/android/actions/runs/34716881231) passed library/app tests and APK assembly. The APK updated the real TV successfully. The isolated QA history was seeded at 120 seconds with its existing source fingerprint; a direct tap on Resume selected that source, decoded video and showed full title duration (6624.896 seconds), rather than the short HLS window.

Two physical failures remain material. Remote OK was suppressed by the hold handler's modifier order; the correction is included in candidate `1ee08fd` awaiting device acceptance. More seriously, an explicit hardware Pause initially showed/saved 177.168 seconds, but with no further input the PAUSED overlay reached 4:17 and exit saved 259.013 seconds as the HLS window advanced. This is a failed pause/progress test. A captured managed pause position and restart-from-that-position path are required and must be physically retested. Timeline/time labels also need visual correction: the left time was centred and the right time clipped despite both appearing in the accessibility tree. Playback was stopped after the test; the temporary QA profile is retained for the next candidate.

## Physical follow-up: cd3e9fc

Hosted [34717993661](https://github.com/viptv-org/android/actions/runs/34717993661) passed; uploaded XML records 70 tests, zero failures/errors/skips. Update installation retained pairing. On the same physical Android TV, remote OK activated the focused Resume action and the persisted exact source decoded video. The corrected overlay displayed left/right title time and a complete timeline thumb; duration was `1:50:24`.

Managed pause remained at `2:48` across 61.5 seconds between UI samples; backend history stayed at 168.101 seconds with its fingerprint. Hardware Play resumed from that anchor. A subsequent pause held at `3:06`, and a forward 60-second managed seek settled at `4:06` while remaining PAUSED; backend progress then saved 246.319 seconds. Playback was stopped after this pass. Only the owned temporary QA profile was used.

This candidate failed Back navigation: one hardware Back from Sources left the activity instead of returning to Details. The callback enablement read raw controller state outside the Compose observation scope; the follow-on binds it to collected UI state. Sources count text was also clipped, and rows had excessive blank height. Candidate `707001d` addresses these plus explicit failed-source recovery, but requires hosted/device acceptance. Track-dialog candidate `0d3ee5f` passed hosted [34718358422](https://github.com/viptv-org/android/actions/runs/34718358422); its pagination/modal focus has not yet been physically checked. Explicit Exit is being checked separately from Back's hide-chrome behavior.

## Browsing and exit follow-up: 707001d / ce6ed91

`707001d` passed 72 uploaded unit tests with zero failures/errors/skips in [34718579073](https://github.com/viptv-org/android/actions/runs/34718579073). Its physical Sources Back returned to Details, then Home with the original card focused. Source count and compact metadata rows were readable. Discover Catalog opened with remote OK, Back restored Catalog focus, and TMDB By Year applied its declared filter. Search returned 44 source-labelled results for the test query; remote Play/Pause moved into results and Left returned to the keyboard. Query letters were entered by tapping the on-screen keyboard, so this is not a full remote-typing claim.

The real five-channel, two-hour Guide showed current and future programmes. Future programme OK opened details without playback. Back incorrectly reset focus to All; `81ee330` corrects that and awaits physical retest. Live entry also used an extra channel-list screen and lacked canonical search/collection filters; the follow-on Guide work addresses those gaps. The old series Season button had no action; functional season/More info work is included in `0713d20`, also awaiting acceptance.

`ce6ed91` passed hosted [34719011462](https://github.com/viptv-org/android/actions/runs/34719011462). Physical Settings Audio modal Back returned to its originating row, but the viewport shifted; exact scroll restoration remains under refinement. Exact-source playback worked again, and explicit Exit via its observed on-screen hit target stopped immediately to Details and saved 286.238 seconds with fingerprint. Playback is stopped. Audio menu Back dismissed only the menu. Directional access to player controls failed because the persistent full-screen focus owner trapped navigation; `1dbe843` corrects focus ownership and requires hosted/remote retest. Tap-driven dialog checks do not certify remote focus behavior.

An ADB 900 ms key-combination experiment did not establish the event timestamps needed to certify a 700 ms remote hold. Hold/release behavior remains a physical acceptance item; do not count that experiment as a pass or a confirmed app failure.

## Physical player follow-up: 3c78b85

Hosted [34720327168](https://github.com/viptv-org/android/actions/runs/34720327168) passed before this physical pass. On the same authorized Android TV and dedicated QA profile, hidden player chrome accepted Down to Timeline, then Down to the 60-second forward control. Right moved to Audio and remote OK opened the track menu. The menu remained open for more than seven seconds; one physical Back closed only that menu and restored focus to Audio. Right then moved to Exit, and remote OK stopped playback to Details with Resume focused. The reviewed accessibility captures are private inspection evidence and are not committed.

## Physical Home, Guide and Settings follow-up: 02d62ed

Hosted [34720961270](https://github.com/viptv-org/android/actions/runs/34720961270) passed with 82 XML tests and a debug APK. On the authorized Android TV and dedicated QA profile, a six-second held DOWN on the first Continue Watching card opened Queue Manage at 4.14 seconds, before release; release stayed in that menu. Back restored the source card and Menu opened the same Queue Manage action. Removing then Undoing the QA row restored the card and its 551.545-second progress with a source fingerprint.

The same candidate exposed two Home failures. Queue Removed rendered Undo as a near-full-screen focused white control, because the focus modifier replaced the button's size modifier. Also, D-pad Down remained on the first Home card and could not reach lower shelves. Candidate `e7a32af` explicitly sizes dialog actions and adds controller-directed, scrollable shelf focus; `ef571b0` additionally corrects playable hero primary selection. Neither correction has physical acceptance yet.

Guide future-programme interaction passed: a focused future programme opened its Watch channel detail without playback, and one Back restored the exact programme cell. Settings Preferred audio also passed exact focus and scroll restoration: Back from the System default modal returned to the same Audio row bounds. These results do not certify the remaining Guide filters, Home hero selection, or all Settings paths.

## Shared card presentation adoption — 2026-09-14

The app pins core `b6d3a0e30622b9adc6ba4f5c62bb9b507f13ad9b`. Home, Search and collection cards render generated CardPresentation fields and dispatch its primary intent; image fallback, context, progress and live/queue action selection come from Rust. Continue Watching retrieves full metadata with at most three concurrent requests before shared enrichment, including its standalone gateway path. Metadata caching uses the same Rust merge and retains episode/source/progress identity.

Added native-boundary tests for exact episode artwork after enrichment, original resume/source preservation, title-logo propagation, catalog versus queue action, and direct live card intent without progress. These Kotlin tests have **not** been run locally: local Gradle and emulators remain prohibited by the server memory constraint. Core import/hash validation passed; source review is not Kotlin compilation, hosted test success, APK acceptance or physical Android validation. A hosted app-review build and device pass remain required.

### Follow-up build repairs and bounded metadata

Core pin advanced to `1388b17db29a6b0279af5f125cc1fafc76776459`, which avoids duplicating complete series data in shelf entries. Hosted run 34792828290 compiled Kotlin and exercised the native bridge; its new live HTTP fixture failed because it used an external media URL, correctly rejected by the shared same-origin media contract. That fixture now uses the real `/media/` shape. Direct live requests use `channel_id` instead of a fabricated stream identifier, and player exit/recovery retains the actual preceding route. Added coverage for direct live return to Home, My List and Search. The combined follow-up still requires a successful hosted run; no APK/device success is claimed here.

### Hosted validation completed — b5e0b7a

[Android app review 34793195338](https://github.com/viptv-org/android/actions/runs/34793195338) passed on `b5e0b7a2534b3eb1e9b91a5d437bf4f9277cce2b` with core `1388b17db29a6b0279af5f125cc1fafc76776459`. Downloaded unit XML evidence contains 89 tests across 22 files, with zero failures, errors or skips. The run compiled Kotlin, built the host Rust bridge and Android native libraries, assembled the debug APK and uploaded both test evidence and APK. This supersedes the pending hosted-build status for that exact revision above. No physical Android TV or emulator validation was performed for these changes.

## Addon catalog namespaces — 2026-09-14

Adopts core `9317bfeb8bcd172789535cc812d751dcfed81d39`, preserving all addon-defined catalog namespaces and their optional addon names. The Android catalog key adapter now uses the exact generated string type rather than converting a media enum. Core Rust unit/integration tests pass (42); import hash validation passes. Android Kotlin tests/APK compilation require the hosted app-review workflow; no local Gradle, emulator or device run was performed.

Catalog response follow-up adopts core `a8ece4f10576b7ea72b6f2cf6c52ae7dd13dffe7`. Bounded real addon metadata checks confirm anime/anime.series return series, anime.movie/collection return movies, and exact metadata routes return child videos. The generated DiscoverPage adds optional unsupportedCount; this pin is compatible with existing native decoding. Core tests pass (43); Android host compilation for this exact revision remains pending CI.
# AND-038 — Vizio phone remote, 2026-09-27

Design: f9c3b5e (full revision in DESIGN_REF). Native phone-only Compose setup,
pairing, remote sheet and preferences reuse the pinned SmartCast JNI bridge.
Host native-core build, library/app JVM tests (including local-address/gesture
checks), three Android ABI builds, APK assembly and lint passed. Lint retains
the existing baseline; warnings are not a hardware qualification.

On the dedicated API 36 phone emulator (5574, 390×844 logical viewport), used
local HTTPS fixtures and qualification/smartcast-fixture.mjs: device sign-in,
profile/home/settings, remote opt-in, manual address, wrong PIN, automatic
fourth-digit pairing, Buttons/Swipe, Up, Volume up and swipe Right all exercised.
The synthetic server recorded the expected SmartCast key codes. HTTP 503 showed
offline recovery and disabled controls. Reference/implementation images were
inspected privately; no captures are distributed. The normal artifact omits
fixture trust. Physical Vizio discovery/launch, TV playback, and broad device
qualification remain unverified; synthetic acknowledgments are not TV playback.

The final four-box native PIN entry was rechecked after layout changes. Forget
returned Settings to Off and removed the saved selection. On the separate API
36 TV emulator (5576), Home and detail navigation retained the TV frame and
D-pad controls; the phone remote entry was absent. This does not qualify media
decoding. Actions 36359986368 produced the universal APK; its downloaded SHA256
and all three core ABIs were verified, with no fixture CA resource present.

## AND-039 — remote reliability follow-up, 2026-09-27

Discovery decoded the JNI Result envelope as a bare array and failed before
probing. A real native-bridge regression now checks all 508 candidates. The
bounded scan tolerates slower Wi-Fi connection establishment and publishes
results progressively. Client/Keystore/TLS initialization runs on IO; remote
keys use a bounded, ordered queue without changing the setup loading state.
Backgrounding retains the in-memory PIN challenge and screen, discards queued
keys, and releases keep-awake. Cancel/New PIN releases the device-scoped pairing
request; a pending-origin marker supports recovery after process death without
persisting a PIN or challenge. The header shortcut uses the same 44dp circular
avatar surface, no outline, and a 20dp glyph.

On the dedicated API 36 phone emulator 5574, `qualification/check-remote.py`
passed discovery over virtual Wi-Fi, background/return while pairing without a
second request, Cancel, New PIN, successful PIN entry, and four ordered rapid
keys with delayed responses. The launch button stayed enabled and its
acknowledgement stayed visible during key traffic. Separately, force-stop during
pairing followed by retry cancelled the persisted pending request. These are
synthetic SmartCast HTTPS checks, not a physical Vizio or Wi-Fi multicast
qualification. General frame-time performance and physical TV latency remain
unmeasured.

Final JDK 17 library/app JVM tests, normal APK assembly and lint passed. Lint
reports 73 warnings with the existing three-error baseline unchanged. All three
Android core ABIs are packaged, and fixture trust is absent from the normal
APK. A private Home screenshot confirmed equal circular header surfaces and
the reduced remote glyph; no captures are committed.

## AND-040 — Copy stream URL, 2026-09-28

Android Source details includes Copy stream URL with resolving, success and safe
retry states. The native direct-URL request resolves only the selected stream;
its temporary lease is retired without opening the player. URLs remain outside
UI state/logs and are marked sensitive in the system clipboard. Closing or
backgrounding suppresses late clipboard delivery while lease cleanup finishes.

Four JVM regressions cover two exact source URLs (including encoded query data),
headers excluded from copied text, cancellation with late lease cleanup,
rejection of session-bound media URLs, and resolution failure. Library/app unit
checks, all three native ABI builds, normal APK assembly and lint passed; the
existing lint error baseline remains unchanged. The normal APK excludes fixture
trust.

API 36 phone emulator 5574, isolated HTTPS fixture: opened source overflow,
copied and pasted the exact synthetic URL, observed masked system clipboard
preview and URL copied feedback, exercised 503 failure, then closed a second
source's delayed request. The original clipboard content remained unchanged and
the late lease was deleted. Source list/filter state survived Close, no playback
or media fetch started, and the private screenshot showed readable stacked
actions. TV remote focus, physical-device clipboard behavior, and external
players requiring provider headers remain unverified.
# REL-001 — errors, remote resume, lazy metadata and startup (2026-09-28)

Design 80e1331; shared core 64f986e (full immutable revisions in the pin files).
The native HTTP boundary now uses Rust's safe/actionable error projection,
including explicit provider connection capacity versus rate limiting. Initial
history/saved metadata enrichment is capped at six items per row; composed
visible items enrich on demand through a three-request gate. The 30-item wire
fixture returns all items while making only six initial metadata requests.

Remote resume verifies retained pairing silently, retries transport once, and
only then shows offline recovery. Five-second request limits apply. A lifecycle
pending flag (not the key-invalidation epoch) determines whether another check
is required, preventing failed verification from looping. Pause/resume, including
quick app switches, retains the PIN page. Power/Mute and reported-name refresh
use shared SmartCast operations. Startup uses bundled branding and dark system
theme resources, with no artwork gate or artificial launch delay.

JDK 17 library/app tests, all three native ABI builds, APK assembly and lint pass
(74 warnings, unchanged three-error baseline). Dedicated API 36 phone emulator
5574 with isolated HTTPS fixtures: Power/Mute produced codes 11/2 and 5/4;
check-reconnect.py verified silent resume, no duplicate pairing, bounded offline
failure and automatic recovery. The in-app restoration screen was inspected
privately under delayed identity response. The immediate system screenshot was
too early to qualify the OS splash itself. Real Vizio/TV firmware, frame-time
performance and physical-device OS splash behavior remain unverified.

## Development backend connection, 2026-09-29

The owner-requested running API 36 Android TV emulator was connected through
the app's Server setting to the existing public HTTPS development tunnel.
The Rust backend and account dashboard built successfully. Public health and
the device pairing page returned HTTP 200; unauthenticated profiles returned
HTTP 401. A separate development database, owner account and profile were
created. TV pairing and profile selection succeeded through the normal API.
Adding the default Cinemeta catalog to that account populated Discover with
titles and artwork, confirmed in the native UI and a private screenshot.

The backend runs as an enabled user service with restart on failure. Private
credentials and captures remain under ignored qualification/artifacts. No
Android implementation or APK changed; Gradle checks were not rerun. Stream
providers, playback and physical-device behavior were not qualified here.


## HOME-ADDON-001 — Automatic account add-on refresh, 2026-10-01

Android implementation `fddc610` checks the private account catalog revision on
authenticated Home entry and foreground return, then every 15 seconds while
Home remains visible. A changed revision refreshes eligible shelves while
retaining usable rows and focus; failed catalog rows retry on later checks.
Controller, HTTP and focus regressions cover unchanged revisions, changes,
load races, background/route/account replacement, and failure recovery.

The Windows JDK 17 / Android SDK Platform 36 host and native flow passed:
213 unit tests (166 app and 47 library), three Android core ABIs, and the
normal debug APK assembly. `adb install -r` on the dedicated Windows Android TV
emulator (port 5572) retained the personal sign-in. After the development
backend revision became live on dev.embedez.com, the existing app was
foregrounded. Private inspected captures show imported Continue Watching and
Anime Kitsu shelves alongside Cinemeta on Home. Captures remain private.

This qualifies emulator Home rendering and the local build/test flow. Physical
TV behavior, codecs, HDR and playback were not established by this check.

## Android TV sources, navigation and media rows - 2026-10-02

Source descriptions wrap inside a two-line viewport with focused vertical
traversal. Initial and partial discovery retain a visible loading indicator.
Installed add-on identities remain separate, including zero/error outcomes.
Continue Watching episode-source Back opens the parent show's episode selector;
a second Back returns to the original queue. Native Android IME replaces the
custom TV keyboard. Episode # beside Season jumps to an actual current-season
number and focuses its card without starting playback or changing history.

Home (including Popular movies), Search and show episode rows extend to the
right viewport edge while headers retain their safe inset. Compose LazyRows
retain virtual composition. Design is pinned to df1a965; shared core remains
1f8483e. Behavior changes and local evidence are tracked in tickets 01-08 under
.scratch/source-picker/issues, with narrow commits and independent reviews.

Windows JDK 17 / SDK 36 full flow passed: host core, 222 unit tests with zero
failures/errors/skips, all three Android core ABIs, normal/test APK assembly,
and lint (77 warnings, existing error baseline unchanged). Final scoped native
acceptance passed all five source/input tests together: description overflow,
progressive loading, masked PIN, cancel and repeated numeric errors/correction.
The test fixture uses the application's TV reference density.

On the signed-in API 36 Windows TV emulator, One Piece retained 1410 episodes.
Jumping to 1410 focused the final card; Left focused 1409; jumping to 2 focused
its card with the same app process alive. Invalid entry retained the editable
field and accessible error; first Back hid IME and second restored the chip.
Home/Search/episode geometry and final-card focus were inspected at 1920x1080;
1280x720 geometry was also checked, then the owner resolution was restored.
Personal sign-in was preserved and no playback/history write was performed.
Private captures and logs remain under qualification/artifacts. This evidence
qualifies emulator UI, not physical-TV codecs, HDR, PiP or playback.

## Episode artwork failure and web player presentation - 2026-10-02

Android 9d99d15 repairs the actual EpisodeCard fallback path. Authenticated
One Piece metadata retained 1410 videos; episode 1059's supplied thumbnail
returned HTTP 404, while episode 2's thumbnail returned HTTP 200 JPEG. Image
failure observations now reach shared Core policy, with a compact parent-art
context for composed cards. Failed thumbnails use the known landscape; if all
art fails, a readable title placeholder appears. Exact episode/source identity,
progress and virtual composition remain intact. Two device rendering tests
passed after the original fallback-request regression failed before the fix.
The final Windows host/native flow passed 223 unit tests with no failures,
errors or skips, three core ABIs, APK assembly and lint. The normal APK was
installed preserving sign-in. Private inspected actual-account captures show
1059's fallback and episode 2's original still with the same process alive.
Provider playback success is outside this artwork qualification.

Web Fit/Fill is recorded in local ticket 10 and TV-web TESTING.md at aed6ed5.
The qualified website bundle is active on dev.embedez.com/tv/; public HTML and
entry asset hashes match the candidate. Its 256 units, production build and
two decoded-HLS Chromium desktop/phone scenarios passed. The toggle beside
fullscreen applies contain/cover to video and canvas without a playback
session replacement. Real MediaBunny decode and physical devices were not
qualified. The DEV asset update preserved the backend binary/settings and
protected account/profile/session/add-on/import/favorite/progress records.

## Hero episode return and watched indicators - 2026-10-02

Android d9b5701 restores the parent episode list after Home hero Resume source
cancellation or player exit. 6d85bda restores the originating hero focus;
7a7f47c rejects a delayed restore after newer directional input. f9f3332 displays
profile-scoped checkmark/Watched badges on episode cards, retains incomplete
progress and refreshes authoritative series progress after playback return.
41cf7b9 refreshes manual correction progress and preserves accepted
watched facts when a follow-up read fails or omits the corrected episode.
Design is pinned to 18b19af; shared Core remains 1f8483e.

The final Windows JDK 17 / SDK 36 gate passed host Core, 228 unit tests with
zero failures/errors/skips, three Android ABIs, normal/test APK assembly and
lint. All 19 targeted API 36 TV emulator tests passed together: six hero/large
list navigation cases, five badge/layout cases, three playback-return controller
cases and five manual-correction controller HTTP cases. Independent Luna review
covered routing, delayed focus and progress ownership. Private inspected TV
and 320 dp phone captures retain complete episode numbers and watched labels.

The signed-in personal emulator opened Anime Kitsu One Piece freshly at episode
1, jumped to 1059, opened the 19-source picker and returned with 1059 focused.
No source was activated and no viewing progress was written. The reported
direct-path reset remains unreproduced. Local tickets 11-13 record the evidence.
The qualified normal APK was installed preserving the personal sign-in.

These checks qualify emulator UI and controller/HTTP fixtures. Actual initialized
decoder playback, a real final-save HTTP transaction and physical-TV capability
were not exercised. Missing/failed manual refresh retains the last known position
and accepted watched fact until an authoritative row arrives.
## Phone Title dimensions — 2026-10-02

The Title board at pinned design `18b19af378b27655e3b6401f17b92321739dba83`
defines a 300dp hero and 58dp action buttons. Details now uses that hero height
and the generated `sizeButtonPhoneDetail` token for Play/Resume, retry, My List
and More info. Shared control defaults and TV geometry are unchanged.

On the isolated candidate based on Android `a9e4167`, Core and design integrity
passed. JDK17 host preparation, 228 library/app unit tests (zero failures/errors),
and debug APK assembly passed with two workers. Lint retained 78 warnings and
three errors filtered by the existing baseline. All three native Core ABIs were
built from pinned Core `1f8483e365867f99eb39928cd7f3a23920003515`.
No emulator visual, native input or physical-device qualification is claimed.
## AND-043 Title discovery and watching marker — 2026-10-02

Adopts proposed design `85a20e918d44af28d20caa52997b1d307922cf29`, grounded
in the committed Title/TvTitle boards. Title now discovers its Core-selected
Play/Resume target once after a 400ms settle, with a three-minute limit. Its
manual source picker adopts pending/completed rows and producer outcomes.
Profile/target/route replacement invalidates callbacks; empty/failed previews
can retry through the picker. Source summary order uses Core sourceMatch with
measured limits and audio preference. Discovery does not select or play a source.
TV partial-progress episodes show WATCHING; completed episodes retain Watched.

JDK17 checks at Core `1f8483e365867f99eb39928cd7f3a23920003515`: Core/design
integrity, 237 library/app unit tests, debug APK assembly and lint passed with two
workers. Lint retains the existing 78 warnings and three baseline-filtered errors.
The nine added host tests cover settle/recomposition, cancellation, partial and
completed adoption, producer failures, late callbacks, bounded timeout, retry,
profile/route eligibility and shared-Core ranking.

Nine class-filtered Compose instrumentation tests passed on the fresh owned API36
Google APIs x86_64 phone AVD `viptv-and043-qa-20261002` (`emulator-5586`). They
verify displayed quality/provider/count, explicit manual-picker actions,
empty/failure copy, and watched/WATCHING/progress state. TV component variants
use LocalTv in this phone emulator; this is not Android TV remote acceptance or
matched full-screen visual parity. The normal debug APK has no fixture CA;
the tests used synthetic component data, with no account/backend connection.
The exact owned emulator was stopped and its AVD deleted after the run.

Authenticated Title-to-picker emulator flows, TV Guide re-audit, TV track-panel focus
return with longer media, and physical Android acceptance remain unverified.

## AND-043 manual-picker focus return — 2026-10-02

Review exposed a Title-family focus gap: the TV source summary opened its manual
picker, but Back recreated Details and focused Play/Resume. Details now saves a
one-shot logical source-control return target and attaches a fresh focus
requester on re-entry. Consuming that return leaves subsequent primary/episode
restoration paths intact. TV geometry, design `85a20e9`, discovery and exact-source
Resume policy are unchanged.

The new `TitleSourceFocusReturnTest` uses actual DetailsScreen, SourcePicker and
controller navigation in a saveable route composition. It requests keyboard
focus, activates the source control with DPAD_CENTER, injects Android Back, and
asserts source focus. The assertion failed before the fix and passed after it.
The same flow then enters through Play and verifies Back restores Play, rather
than retaining the earlier source-return target. No account/backend/media is
required by this synthetic movie fixture.

All ten class-filtered Compose tests (the new return test plus source-summary and
episode-badge suites) passed on fresh owned API36 Google APIs x86_64 phone AVD
`viptv-focus-qa-20261002`, serial `emulator-5588`, at an owned 1600×900/160dpi test
viewport with LocalTv. This proves component route/focus behavior, not Android TV
remote hardware or full-screen parity. JDK17 host preparation, all 237 unit tests,
design integrity, debug assembly and the three pinned Core Android ABIs pass with
two workers. Lint retains 78 existing warnings and three baseline-filtered errors,
with no new errors. The owned emulator and its AVD were removed after checking.
Broad authenticated Title/picker flows, TV Guide, real long-media track-panel
return and physical acceptance remain open.


## Loaded hero episode restoration - 2026-10-02

Implementation a321cda corrects the common Details screen's restoration after
parent metadata loads. Hero/queue source Back and hero player exit already
carry a saved season/episode through shared parent-series routing; their sparse
loading Details page initialized selection against an empty list and did not
retry when episodes arrived. This left episode 1 visible despite cursor 1059.
Episode-card returns retain their existing populated Details state.
Fresh Details entry identity prevents a later Resume from reusing the preceding
visit's manual selection, even when fast metadata skips a rendered loading page.
Follow-up 2f0791b requests keyboard input mode before episode focus. The owner
emulator first showed the correct row without focus after a mouse/touch launch;
the final native hierarchy check qualifies the correction on both entry points.

The actual rendered delayed-metadata regression failed before the fix. Previous
hero route/Home-focus tests used placeholder Details text and missed the rendered
episode list. Updated coverage exercises actual Details and its restored focus,
including later manual selection. The final host/native gate passed 228 unit
tests with no failures/errors/skips, three Android ABIs, APK assembly, lint and
18 targeted native tests, with independent Luna review. Pins unchanged.
The final batch excludes existing badge capture tests. A preliminary broader
batch produced two private QA fixture captures; neither was inspected/displayed.

After installation preserving sign-in, a genuine signed-in One Piece hero
Resume -> source Back on emulator-5572 revealed/focused episode 1059 in the
loaded 1,410-episode list; the next Back returned Home. Acceptance used native
hierarchy assertions without additional screenshots or source activation.
The Continue Watching card also restored episode 1059 visibly and with focus.
No personal playback/history write occurred. Physical TV is unqualified.

## AND-043 reconciliation with loaded episode return — 2026-10-02

Merged Android main `1ea4b0e` into the Title candidate, preserving the explicit
Details entry identity, delayed saved-episode restoration and keyboard-mode
request for pointer-triggered TV episode returns. The source-control return uses
that same entry identity and clears pending episode restoration before opening
its picker, so an episode restoration cannot compete for focus on Back. Its
route regression now uses the application's actual saveable screen key.

JDK17 host preparation, all 237 library/app unit tests (zero failures/errors/skips),
Core/design integrity, all three pinned Core Android ABIs, debug and instrumentation
APK assembly, and lint passed with two workers. Lint reports zero errors and
78 existing warnings, with three errors filtered by the existing baseline.

All 20 class-filtered Compose tests passed together in 21.794 seconds on the fresh
owned API36 Google APIs x86_64 AVD `viptv-merge-qa-20261002`, serial `emulator-5590`,
at 1600×900/160dpi with LocalTv. Classes: `TitleSourceFocusReturnTest`,
`TitleSourceControlTest`, `EpisodeWatchedBadgeTest`, `DetailsEpisodeReturnTest`
and `HomeHeroEpisodeReturnTest`. The combined suite covers actual Details/source
navigation and source focus, delayed 1,410-episode metadata, saved season/episode,
retained manual jumps and fresh visits, Continue Watching/hero Back and pointer
activation. Fixtures use synthetic data and no authenticated account or media.
The owned emulator was stopped and its AVD removed after qualification.

This proves the combined component/controller behavior on the named emulator;
authenticated Title flows, TV Guide re-audit, long-media track-panel return and
physical Android TV acceptance remain open.

# Private native core bridge adoption — 2026-10-07

Android and TV-web adopt core `28e114949a5bef205ca0aa125d7f6fd2851b6c25` through their owning sync
scripts. Canonical Rust owns closed native grant/input parsing, original UTF-8
bytes, metainfo identity/path bounds, scoped clocks/renewal and explicit
negotiation/recovery decisions. Private holder values stay outside ordinary
launch/session/source models. Generated Kotlin interfaces are consumed without
Kotlin policy copies. Native capability remains disabled.

With existing JDK 17 / SDK Platform 36 and one worker, core/design integrity,
host core preparation, 47 library and 236 app unit tests, three-ABI Android core
preparation, debug APK assembly and lint pass. The two new app tests call the
actual generated UniFFI byte/scalar interfaces against the canonical corpus,
prove native/Kotlin string redaction and permanent retirement after invalid
UTF-8 or an exact-file mismatch. Test failures/errors/skips are zero. Lint has
zero unfiltered errors and 77 warnings under the existing baseline. The APK
contains arm64-v8a, armeabi-v7a and x86_64 core libraries. APK SHA-256:
`0d668b84f419c731fd788d8d4bae79a932966c236922edd22d8d3e0ab6128b3b`.

The source cohort separately passes 116 Rust tests, strict Clippy and 414
native/actual-WASM grant, request, clock, capability, recovery and privacy
vectors plus existing protocol/presentation suites. Local command logs are
`.scratch/native-torrent/evidence/05/` and core `target/native-torrent-05/`.
These are core interface/artifact checks. Android native engine, HTTP control
adapter and player integration belong to subsequent tickets; no device install,
emulator, media/network qualification, capability activation, push or deployment
was performed.


## Owned default-native Android TV pipeline — 2026-10-07

The isolated API 36 x86_64 Android TV AVD `viptv-native-owned-qa` passed all
six automated `OwnedNativePipelineTest` cases in 138.6 seconds. The bounded
human observation selector was skipped. Android runtime `8e1ffec`, backend
`d66085be95ad6e7d3401bf8be326172ee7f66d09`, gateway
`ec134b9bf86fcd06efc4db5b4e94891989b86c02`, core
`8ef18be39ca08e96c40b20f40666f62a5f2eadfc`, and design
`ae1f09d` supplied the run. Controller cases assert the real default runtime
availability; they do not force capability availability. Only the engine cache
factory injects explicit owned loopback TCP peers with DHT disabled. The normal
APK retains its public network policy and system trust.

The real authorization/Core/JNI/loopback/Media3 pipeline decoded the owned
H.264/AAC surface, confirmed English cues and alternate audio, and matched
exact selected bytes for episode indices 1 and 2. Same-file independent grants
survived another grant's retirement. Token and unselected-index requests were
refused. Actual metadata Back cancellation, invalid-index/full-payload capacity
refusal, missing-piece Media3 seek and pending HTTP-body cancellation passed.
The common local retirement joined in 8 ms. Scheduled heartbeat extended the
grant while backgrounded; after deliberately cancelling only its heartbeat
worker, actual elapsed expiry retired authority and joined locally in 12 ms.

HTTP/HLS decode, seek and older-server 404 negotiation kept ordinary leases.
The actual controller returned title position 8,022 ms, honored paused producer
revocation, closed the profile scope, revoked sign-out authority and kept native
input out of app state and authentication preferences. The first device run
exposed a nonempty native heartbeat body; `8e1ffec` sends the endpoint's required
zero-byte body, with an outgoing HTTP regression and successful renewed deadline
check. The corrected complete device run passed without that failure.

| Sealed artifact | SHA256 |
| --- | --- |
| Isolated fixture APK | `65a1ba490c92a467905a57850f16b6f01f5717c7183a14fcec4908bec1f7a818` |
| Fixture instrumentation APK | `c70750b9cc8864381f41ac5736c9f28b247667e0ec355cc7d8aa4cd6b6b729b9` |
| Backend test executable | `9c1ebf52805f33ce9d80e486bcd15fb8be7bc29d15113149cf18d2a4800c6839` |

Matching configuration, explicit fixture trust, isolated application identity,
x86_64 JNI hashes and 16 KiB APK library alignment passed before installation.
The owned torrent charges its complete 268,435,457-byte payload. Runtime logs
passed the synthetic native-value audit. Detailed results and the retained first
run are ignored under `.scratch/native-torrent/emulator-20261007-heartbeat-fix/`
and `.scratch/native-torrent/emulator-20261007-default/`. Owned backend/TLS/peer
listeners and reverse mappings were closed. This QA work did not install or
change the normal application on the shared emulator.

These observations qualify the named owned emulator effects. Physical sound,
codec/HDR/PiP and remote behavior, sustained public-peer/resource behavior, and
arbitrary blocked OS I/O remain separate observations. The fixture does not
claim every host, emulator and physical condition in NT-01 through NT-08.
# Actionable native source failure diagnostics — 2026-10-07

The normal app adopts gateway `698372df3734f6ec8019f70591b549280f47c28f`,
core `ed83a0b9f24c6431f6ccca7c9426cb1da7f53ba5` and design
`d9562790ea69d5e29cb4ec2630b814a7f30f9761`. A connected peer that supplies no
metadata reproduced the generic `StartupTimeout` before the fix; the regression
passes with `MetadataTimeout`. The first measured cache/session/metadata/
initialization/endpoint failure survives deadline races and joined retirement.
Typed DNS/TLS/connection/control deadlines survive the actual HTTP callback,
remain IO failures for heartbeat retries, and enter shared canonical copy.
The recovery explanation includes its validated diagnostic code. Media3 retains
its measured numeric code and observed HTTP status, without raw exception text.
Overall startup expiry reports a failure; actual owner cancellation still cancels.

48 library and 290 app unit tests pass, including the real refused-connection
callback, request timeout/cleanup, startup expiry versus owner cancellation,
private exception redaction and every native FFI reason's shared projection.
33 importer tests and core/design/torrent integrity pass. All three core/native
ABIs build; normal app assembly, lint and instrumentation APK assembly pass.
The real APK probe passes minSdk 24, three-ABI core/native/JNA contents, exact
pins/checksums/notices, system-CA/literal-loopback trust and 16 KiB ZIP alignment.
A DEX probe confirms the diagnostic codes and log tags are packaged.
Normal APK SHA256:
`8c42a63849bceb7a3f562dabb2d164f2af93cfccc4adc1888cbfc9fa666df4f5`.

Gateway's locked owned-network workspace passes 215 tests (56 opt-in tests
ignored); blocked cache and actual storage initialization remain distinct from
metadata timeout. Core passes 120 tests, strict all-target Clippy and actual-WASM
parity including 49 canonical errors and 457 native vectors. TV-web adopts the
same core and passes typechecking, 285 single-fork tests and production build.

The three reported user attempts have no retained diagnostic log sufficient to
assign their individual root causes. No absent-seeder/public-swarm claim is made.
This diagnostic APK has not been installed during this sweep; decoded Android
source opening and visual dialog inspection await a shared-emulator window.
The existing shared emulator, account and app data remain untouched by the sweep.
See [native source diagnostics](docs/NATIVE_TORRENT_DIAGNOSTICS.md) for safe codes
and device log collection. Private captures and APKs are excluded from git.
