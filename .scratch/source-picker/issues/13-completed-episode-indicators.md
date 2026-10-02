# 13: Show completed-watching indicators on episode cards

**What to build:** Each episode card on the Android info page displays a clear
watched-completion indicator when the selected profile has completed it.
Use existing shared watched facts; retain partial progress as partial progress.

**Status:** complete; Windows and native qualification passed
**Owner:** data and design research, implementation.
**Contract:** AND-EPISODE-WATCHED-001 at design
`4ac008108374b41f034fa025550f4d2bfbbbca97`, imported by Android `0e305b4`.

- [x] Identify the canonical completed-watching fact and profile-scoped data flow.
- [x] Adopt the design's existing watched badge, or record the shared UX addition.
- [x] Show completion accessibly on TV and phone cards without hiding episode text.
- [x] Keep incomplete and unknown episodes distinct from completed episodes.
- [x] Verify profile isolation, fresh progress and large virtualized lists.
- [x] Run meaningful regression and device checks; narrow commit and push.

Research: profile-scoped `seriesProgress` already supplies explicit `watched`
facts; Core fills missing child series identity from its parent. The UI must
not reuse Core's separate initial-episode 95-percent fallback as a badge rule.
Manual correction returned only a toast and left the open episode stale.
Playback exit likewise restores a saved Details snapshot before final progress
save completes. Qualification must cover authoritative refresh after that save,
profile changes, recycled cards, and source-picker as well as direct-info returns.

The design archive passed Linux validation: 12 required documents, 878 assets,
453 reference files and 516 tokens. Inventory-exact CRLF normalization was
required only in the temporary archive; owner design edits remain preserved.

Runtime red evidence: the actual controller's Player exit against a localhost
profile-progress fixture left the retained parent episode `watched=false`.
`EpisodeWatchedReturnTest` failed its completion assertion after five seconds
on emulator-5576 before the authoritative refresh hook was connected. A missing
helper compilation failure during scaffolding is not counted as bug reproduction.

UI inspection caught a clipped badge in the 320 dp phone fixture. The canonical
follow-up design `18b19af378b27655e3b6401f17b92321739dba83`, imported by Android
`c83a88a`, allows constraint-driven wrapping below the episode number while
retaining artwork and the 44 dp options target. TV retains the adjacent badge.
The updated design archive passed the same full inventory/link validation.

Implementation `f9f3332` renders the per-profile authoritative watched fact
without a UI completion threshold. Incomplete episodes retain partial progress;
completed episodes show an informational checkmark and Watched label. Rendering
checks cover TV, narrow 320 dp phone layout, changing facts and an unwatched
parent with a completed child. Private inspected captures show full episode
numbers, labels and titles; the badge adds no remote focus stop.

Playback return waits for the final save job, then rereads profile series
progress. Route/profile guards discard replaced destinations while admitting
quick Back to the exact retained Details object. Native controller HTTP tests
cover refreshed completion, quick source Back and a profile change. Initialized
decoder playback and a real final-save HTTP transaction were not exercised;
save-before-read ordering also has a focused synthetic unit regression.

Manual-correction follow-up `41cf7b9` rereads authoritative progress
after the successful correction so Mark unwatched clears the old resume position
when the server supplies zero. The actual-controller test first reproduced the
retained 30-second position. Additional cases cover replaced route/profile,
read failure and a successful response missing the corrected row. Accepted
watched state survives missing/failed reads; last known position is retained
until an authoritative row arrives. No client completion/reset policy is added.

Final Windows JDK 17 / SDK 36 flow passed: host Core, 228 unit tests with no
failures/errors/skips, all three Android ABIs, normal and instrumented APK
assembly, lint, and 19 targeted native tests. Luna reviewed final correction
ownership and omission handling independently. Design remains 18b19af; Core
remains 1f8483e. This qualifies emulator UI/controller behavior, not physical
TV capability or actual provider decoding.
