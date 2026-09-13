# Native shared-core adoption — 2026-09-13

CORE_REF pins 888ff5a7041582b099644fdbdcc3a748a277b2d7; DESIGN_REF pins e821c297de2e81b47a6a1b22ed8aa0522cdd04e5. The app now calls native Rust for session restoration/profile selection/sign-out, normalized responses and artwork/source/continuation/progress rules. Generated Kotlin codecs decode the shared DTOs. Hosted CI builds the exact pinned native source for JVM unit tests and three Android ABIs before the app APK.

The implementation is ready for hosted validation; results will be recorded below. No local Gradle, emulator or new physical device acceptance is claimed. Earlier device measurements apply to their named historical revisions, not automatically to this migration.

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
