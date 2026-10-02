# 08: Extend TV media scrollers to the right edge

**What to build:** Horizontal Android TV media rows, including Popular movies
and show-info episodes, extend to the viewport's right edge. The owner prefers
this to a decorative gradient. Keep the established left alignment, card sizes
and remote focus behavior.

**Blocked by:** None.
**Status:** done
**Triage:** ready-for-agent
**Owner:** media_row_edge_research (GPT-6 Luna, max), then Sol implementation.

- [x] Identify all horizontal media-row surfaces and the parent right inset.
- [x] Commit the canonical Android TV rule and adopt its immutable revision.
- [x] Popular movies and other Home, Search, library and detail episode rows
      use the same viewport-edge treatment where applicable.
- [x] Preserve header/text bounds, left alignment, card sizes, phone layouts
      and non-media surfaces.
- [x] Focus rings and final cards remain reachable and unclipped.
- [x] Inspect representative native TV screenshots and run relevant checks.

## Commits and evidence

Owner requested 2026-10-02 and explicitly named Popular movies. Luna research
runs separately while native keyboard and episode-jump checks continue.

Luna research located the 96-unit parent end inset on Home, Search and show
details. Implementation `a809e88` removes that inset from horizontal media rows
while retaining it for headers, descriptions, controls and other non-media
content. Library and Discover full pages use grids, which retain their layout.
Existing card dimensions, left alignment and phone layout are preserved.

Canonical AND-TV-ROW-EDGE-001 is committed and pushed at design `df1a965`;
Android mirrors match that revision, with final pin/lock commit `ada1605`.
Existing Compose LazyRows retain virtual composition; episode jumps scroll
directly to an index without rendering preceding cards.

Native API 36 acceptance on Windows: Home Continue Watching and Popular movies,
Search catalog rows and the 1410-episode One Piece row extend beyond the former
right boundary to the viewport edge. D-pad navigation reached the last Popular
movie and last episode with visible focus rings. Details Back restored the
selected Home card. Geometry was inspected at 1920x1080 and 1280x720, then the
owner emulator was restored to its physical 1920x1080 resolution. Private
captures remain under `qualification/artifacts/native-final-acceptance-20261001`.

JDK 17 / SDK 36 full host/native flow passed: 222 unit tests with zero
failures/errors/skips, three core ABIs, normal/test APK assembly and lint (77
warnings, existing error baseline unchanged). The final combined scoped native
run passed five UI tests in `qualification/artifacts/final-scoped-tv-ui-tests.log`.
Independent Luna Standards and Spec reviews found no confirmed remaining gaps.
Physical-TV playback, codecs and HDR are outside this UI qualification.
