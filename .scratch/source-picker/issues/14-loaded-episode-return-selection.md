# 14: Restore the selected episode after parent metadata loads

**What to build:** Hero and Continue Watching source/player returns reveal and
focus the saved episode once the parent show's episode list arrives. Use the
shared Details restoration seam, preserving later manual selection and jumps.

**Blocked by:** none; follows ticket 12's incomplete rendered-list coverage.
**Status:** done; Windows gate and native loaded-return acceptance passed
**Owner:** implementation and device regression; Luna independent research/review.
**Contract:** CW-SOURCE-BACK-001 at the current DESIGN_REF.

- [x] Verify the running owner emulator has the latest qualified APK.
- [x] Reproduce hero Resume -> Back opening episode 1 instead of episode 1059.
- [x] Run a failing regression with actual Details UI and delayed episode metadata.
- [x] Restore one explicit episode target through the common Details screen.
- [x] Preserve retained card selection, manual jumps and season changes.
- [x] Check real loaded hero return and profile/route cancellation without screenshots.
- [x] Run the Windows host/native gate; narrow commit and push.

Installed and qualified APK SHA256 matched exactly on emulator-5572. Owner
One Piece hero Resume -> Back produced the full 1,410-episode info page at
episode 1. The return route already retains the parent and saved episode cursor;
the screen seeds selectedEpisode against an empty loading list and defaults to
index zero. Its restoration effect does not observe later episode-list arrival.
The direct episode-card path retains an already populated Details page, which
masked this separate loading-state failure.

Previous hero tests rendered placeholder parent-detail text and asserted route
identity/cursor plus Home focus. They did not assert the real loaded episode
row. New coverage must exercise that rendering and readiness handoff. User
requested avoiding screenshots; use Compose semantics/focus and native UI
hierarchy checks for the remaining acceptance work.

Read-only review also identified a repeat-entry lifecycle gap: after jumping
away from 1059, saved Details state keyed only by parent could carry that old
selection into a new Home Resume of 1059. The normalized return must distinguish
a new Details visit from metadata/progress updates and an ordinary Sources Back
within the retained visit. It must work even if fast metadata prevents the
sparse loading page from being composed.

Implementation `a321cda` fixed list restoration and explicit visit identity.
Owner native pointer acceptance then revealed a second fact: both hero and
Continue Watching returned with episode 1059 visible but no content focused;
the next directional key started in the rail. The final follow-up must request
keyboard input mode before episode focus in both common TV restoration paths,
as the existing numeric-jump restoration already does. Compose pointer injection
on QA did not reproduce this focus loss; the actual owner hierarchy assertion
is the red/green feedback loop for it. No screenshots were used for this check.

The rendered delayed-metadata test failed before the fix because episode 1059
was not composed; the row remained at episode 1. A separate owner-emulator
hierarchy assertion also returned visible=false/focused=false before the fix.
Implementation `a321cda` applies restoration inside the common Details
screen when the episode rows become available and preserves later selection.
The real hero test now renders actual Details rather than placeholder text.
Fresh Details visits carry a distinct entry identity; loaded metadata and
ordinary source Back retain that identity. Re-entry therefore restores the
saved episode without depending on a loading frame being rendered, while
updates within the current visit preserve manual selection.
Follow-up `2f0791b` requests keyboard input mode before TV focus restoration
for pointer-triggered returns. Owner hierarchy checks reproduced no focused
content after the initial list fix; the final APK is verified against that
native red/green loop on both hero and Continue Watching.

Final Windows JDK 17 / SDK 36 flow passed host Core, 228 unit tests with zero
failures/errors/skips, all three Android ABIs, normal/test APK assembly, lint
and 18 targeted non-screenshot native tests. Luna independently reviewed restoration
ownership and user-selection preservation. Core/design pins are unchanged.

The qualified normal APK was installed on the owner emulator without clearing
sign-in. Genuine backend Home hero Resume -> source Back revealed and focused
One Piece episode 1059 after the 1,410-episode list loaded; the native hierarchy
assertion returned visible=true/focused=true. The next Back returned Home.
The Continue Watching card return passed the same visible/focused assertion.
No screenshots were taken for the final acceptance, no source was activated
and no playback/history write was performed. Physical TV behavior is unqualified.
