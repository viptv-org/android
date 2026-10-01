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
