# Check episode-row season advancement

Blocked by: none
Status: done
Owner: Codex
Triage: needs-triage
Repositories: android, design, tv-web; workspace pulls also include backend

## Acceptance criteria

- Fetch/pull available Windows and VPS organization checkouts without losing owner edits.
- Check latest Android main for automatic season advancement at the end of the Details episode row.
- Use text-only validation; no screenshots, recordings, image generation or pixel assertions.
- Distinguish source/unit evidence from physical TV remote qualification.

## Evidence

- Android origin/main: `44196c4`. Original working checkout remains at `1ea4b0e` because local AGENTS.md and TESTING.md edits block fast-forward. Latest code is isolated at `.scratch/episode-season/latest-android`.
- Latest DetailsScreen.kt filters episodes by the selected season (line 57); the TV LazyRow only renders that list (lines 206-212). Episode focus updates its row index, without changing seasons. The only subsequent season assignment is the manual Season dialog selection (line 236).
- Existing Episode # jump explicitly matches within the current season. EpisodeJumpTest covers exact episode metadata lookup; it does not exercise season-boundary remote navigation.
- No season-boundary advancement handler was found in latest DetailsScreen.kt. This is source inspection, not a passing device navigation test.
- TV-web DetailScreen.tsx likewise changes seasons through its season controls; no equivalent boundary handler was found in this inspection.
- Windows backend pulled to `f14fb51`; TV-web pulled to `74242fc`; dashboard is already current with local owner edits preserved. Design pull is blocked by local CHANGELOG.md edits.
- VPS workspace updater ran. All cloned repositories except backend are current; backend is behind 17 commits and blocked by existing untracked Stremio preview files. Existing development service was not rebuilt/restarted.
- ADB text package metadata reports the installed TV app was updated on 2026-10-02. Version 0.1.0/code 1 contains no build SHA, so installed source revision is not established by that metadata.
- JDK 17 confirmed; Android compileSdk is 36. `bash scripts/prepare-core.sh host` passed against pin `f66c87e`, followed by `./gradlew.bat --no-daemon :testDebugUnitTest :app:testDebugUnitTest`: BUILD SUCCESSFUL (3m 13s). The library test task reused Gradle cache; app tests executed. No images were captured or used. Existing unit tests do not qualify season-boundary remote navigation.
- Backend's clean TV submodule was updated to its committed pin `e7683b0`; dashboard owner edits were retained. No app APK was installed or account data cleared.
- Final XML results: 47 library tests (cached) and 190 app tests, totaling 237; zero failures, errors or skips. EpisodeJumpTest passed its current-season metadata lookup assertion.

## Implementation commits

None. This ticket records the requested check; no product behavior was changed.

## Follow-up

Implement and qualify Android TV season-boundary navigation against the shared design contract. Cover the final episode, final season, sparse season numbers, focus restoration and key-repeat behavior with text assertions. Physical TV remote qualification remains separate.
