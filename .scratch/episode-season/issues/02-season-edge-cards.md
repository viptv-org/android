# Episode-row season navigation cards

Blocked by: 01-check-season-boundary-navigation (done)
Status: done
Owner: Codex
Triage: ready-for-agent
Repositories: android, design
Implementation commits: owner-authorized focused episode-navigation checkpoint (see Git log)

## Contract

Owner approved this change in chat on 2026-10-02. Base design pins: Android
working checkout 18b19af; latest isolated Android 1742afa. Extend the Details TV
episode row with Previous season and Next season cards. Shared Core episode
selection, progress and playback policy are unaffected.

## Acceptance

- Previous season precedes the first episode; Next season follows the last.
- Show only available adjacent seasons, including Specials (0) and sparse numbers; never wrap.
- Left/Right reach the cards; OK release activates once. Focus alone does not change season.
- Previous opens the preceding available season at its last episode; Next opens the following season at its first.
- Preserve manual Season selection, episode-number jump, delayed details, Sources Back and saved episode focus with the extra lazy-row item.
- Text-only tests and local JDK 17 / SDK 36 unit/build flow; preserve existing TV sign-in.

## Evidence

- Implemented in the original working checkout and the isolated latest-main checkout, preserving owner edits and leaving both design/core pins unchanged. The owning design working draft is specs/behavior/android-tv-episode-season-cards.md, with copy/components/decisions/changelog updates. No commits or publication were requested.
- Original checkout: JDK 17 / SDK 36; host core preparation passed; 47 library + 181 app unit tests passed with zero failures/errors/skips. All three Android native ABIs built, and normal debug plus Android-test APK assembly passed. app:lintDebug passed.
- Isolated Android 44196c4 plus this feature: both unit tasks passed, totaling 237 tests with zero failures/errors/skips.
- Local API 36 TV emulator: EpisodeSeasonNavigationTest (5), DetailsEpisodeReturnTest (4), EpisodeCorrectionProgressTest (5): OK (14 tests), 48.197 seconds. Text-only semantics/key/controller assertions cover sparse seasons 0/5/9, first/final direction omission, focus-only behavior, first/last destination episode, episode jump with the prepended item, delayed details and Sources Back.
- Private output: qualification/artifacts/episode-season-20261002/season-regression-instrumentation.log. The first run used an outdated test APK and failed key-up bookkeeping across dialog windows; the rebuilt test uses native Android down/up events for holds. Product navigation passed that first run and all final assertions passed.
- Normal APK SHA256: B1E485A516F1A03B28F02A83C28FBB9833FEE779CCC22F01648D0DDB38A176FC. Installed with adb install -r and relaunched successfully. No app data clear, server switch or screenshot operation occurred.
- Canonical design validator's docs/links/tokens/inventories passed through the private Windows normalization wrapper: 12 required docs, 878 asset files, 453 reference files, 516 tokens. Direct validate.py inherits Windows separator/CRLF incompatibility; wrapper accepts only the recorded raw or LF digest, with no missing/extra assets or repository validator edits.
- Physical remote input, visual appearance and other-platform parity remain unverified. The owner explicitly required text-only validation.
- Current session rechecked the season/watched suite within a 21-test text-only emulator run before the owner took over interactive checks. Current host checks passed 237 tests. Commit authorization is now explicit; earlier work is preserved for upstream integration.
