# Smooth Home transitions to and from Continue Watching

Blocked by: none
Status: implemented; owner broader performance validation pending
Owner: Codex; reviewed/checkpointed by Mimir
Triage: ready-for-agent
Implementation commits: owner-authorized focused checkpoint (see Git log)

## Acceptance

Measure the real Home composable's scroll position with DPAD events. First
Continue Watching and hero focus retain the full top viewport; lower shelves
reveal the entire selected card/caption. Returning upward must animate rather
than teleport. Rapid input must not allow an older restore to fight new focus.
Hero changes only for the top Continue Watching row; lower shelf focus retains
the last top-row hero selection, including after a Home route restore.
Preserve rail, source return, episode navigation and watched hold. Text only:
no screenshots, pixel assertions or image fixtures. Record before/after evidence.

## Evidence

The real Home/DPAD regression failed before the fix: Up from the next shelf
restored all 277 pixels in one frame. Home focus had an instant scrollToItem(0)
effect keyed by every media focus change. It now animates only on entry into
the top region, and leaving the region cancels that animation. First green run:
277 total pixels, maximum sampled frame step 98 pixels, 12 moving frames.
The same top/CW and sharp/ambient request harness returned OK (3 tests).

Owner's additional hero constraint has its own red/green UI regression. Before
the fix, Down from the second CW card changed the hero to the lower shelf; after
the fix, that card's hero and Resume remain. The focus snapshot carries the last
top shelf media key independently of the focused lower card. Route restoration
and directional cancellation preserve this key. The unit test covers the same
lifetime without changing core's source/artwork/continuation rules.

Private text/numeric evidence: qualification/artifacts/home-performance-20261003/.
Final five-scroll/five-artwork plus prior UI regression run and unit/lint checks
pending. Whole-app native frame statistics are noisy and do not establish a
general FPS improvement; the first post-change p90 was worse than the baseline.

Current-session evidence: all five HomeScrollMotionTest cases passed in the text-only 21-test TV regression run before the owner took over interactive checks. Current full host unit checks passed 237 tests with no failures/errors/skips. No further emulator UI checks or pictures are being run; broad performance and physical-device behavior remain unqualified.
