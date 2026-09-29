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
