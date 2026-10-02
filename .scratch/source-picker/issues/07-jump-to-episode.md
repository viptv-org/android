# 07: Jump to an episode number from show info

**What to build:** A visible episode-number jump control beside the season
badges on the TV show info page lets viewers reach a particular episode without
scrolling through a large series such as One Piece.

**Blocked by:** None.
**Status:** done
**Triage:** ready-for-agent
**Owner:** episode_jump_research (GPT-6.1 Sol, high), then implementation.

- [x] Trace actual episode numbering and season/focus behavior, including large anime.
- [x] Commit a canonical design rule and adopt its immutable revision.
- [x] Place a discoverable Jump to episode control to the right of the season badges.
- [x] Enter an episode number through native numeric Android input for the selected season.
- [x] Validate against available metadata, including gaps and unavailable numbers.
- [x] Scroll and focus the matching episode without starting playback or changing history.
- [x] Cancel/Back keeps the original season, position and invoking-control focus.
- [x] Episode activation retains shared source/resume behavior; core owns Play/Resume selection.
- [x] Verify a distant episode in a large series locally, with meaningful regression checks.

## Commits and evidence

Owner requested 2026-10-01 while native keyboard acceptance was in progress.
Research is delegated to subagents so the main chat can receive further tasks. Current
season is the initial interpretation; research must check real numbering before
implementation. Keep this separate from source/provider and keyboard commits.

Luna research completed: match actual current-season numbers (missing season as 1); allow 0 only if present, choose first metadata entry for duplicates. Count is not a maximum. Invalid stays open and cancel preserves position. Separate implementation subagents own the Android TV design rule and UI adaptation.

Implementation `ec61aaa` adopts AND-EPISODE-JUMP-001 at design `6e74a9a`.
Focus/validation refinement `96cdbae` handles touch input mode, parent window
focus, bounded acknowledged restoration, stale-origin guards, repeated invalid
submission focus and accessible danger styling. Initial full Windows gate
passed 222 unit tests, normal/test APK assembly and lint; final follow-up compile
passed. On actual One Piece with 1410 episodes, keyboard Enter focused 1409;
touch Go focused 1400 after the fix; jumping back to 2 focused its card with the
same process alive. Invalid 999999 retained the field and inline error. First
Back hides native numeric Gboard and second dismisses to the Episode # chip
without moving the row. No playback or history writes occurred.

Final qualification, 2026-10-02: native One Piece metadata retained all 1410
episodes. Jumping to 1410 focused the last card, Left focused 1409, and jumping
to 2 focused its card with the same process alive throughout. The row remains a
virtual Compose LazyRow; jumps scroll directly to the matching metadata index.
Invalid input displayed the accessible danger state; IME-first Back and dialog
cancel restored the invoking chip without changing the row. Private inspected
captures are under `qualification/artifacts/native-final-acceptance-20261001`.

The final scoped emulator run passed all five UI tests, including repeated
invalid numeric submission and correction, masked PIN submission, cancel,
source description overflow and progressive source loading. The fixture uses
the application's TV reference density (`a97b225`). Final normal/test APK
assembly passed with design `df1a965`; the pin correction is `ada1605`.
Independent Luna Standards and Spec reviews found no confirmed remaining gaps.
This qualifies local API 36 TV UI, not physical-TV playback capabilities.
