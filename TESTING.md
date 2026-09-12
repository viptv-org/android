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

## Release acceptance record

Record immutable commit, hosted run, device model/OS, source delivery mode, entry action, result, failure/recovery and return destination for each scenario. Record automated, real-media and hardware evidence separately. Missing tests and observed failures remain explicit; neither a debug APK nor passing fixture tests establishes 100% device or design conformance.
