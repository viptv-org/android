# 07: Jump to an episode number from show info

**What to build:** A visible episode-number jump control beside the season
badges on the TV show info page lets viewers reach a particular episode without
scrolling through a large series such as One Piece.

**Blocked by:** Ticket 06 native input completion and canonical UX adoption.
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** episode_jump_research (GPT-6 Luna, max), then Sol implementation.

- [x] Trace actual episode numbering and season/focus behavior, including large anime.
- [ ] Commit a canonical design rule and adopt its immutable revision.
- [ ] Place a discoverable Jump to episode control to the right of the season badges.
- [ ] Enter an episode number through native numeric Android input for the selected season.
- [ ] Validate against available metadata, including gaps and unavailable numbers.
- [ ] Scroll and focus the matching episode without starting playback or changing history.
- [ ] Cancel/Back keeps the original season, position and invoking-control focus.
- [ ] Episode activation retains shared source/resume behavior; core owns Play/Resume selection.
- [ ] Verify a distant episode in a large series locally, with meaningful regression checks.

## Commits and evidence

Owner requested 2026-10-01 while native keyboard acceptance was in progress.
Luna research is delegated so the main chat can receive further tasks. Current
season is the initial interpretation; research must check real numbering before
implementation. Keep this separate from source/provider and keyboard commits.

Luna research completed: match actual current-season numbers (missing season as 1); allow 0 only if present, choose first metadata entry for duplicates. Count is not a maximum. Invalid stays open and cancel preserves position. Separate Sol workers own the Android TV design rule and UI adaptation.
