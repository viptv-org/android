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
