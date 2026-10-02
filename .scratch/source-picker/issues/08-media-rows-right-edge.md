# 08: Extend TV media scrollers to the right edge

**What to build:** Horizontal Android TV media rows, including Popular movies
and show-info episodes, extend to the viewport's right edge. The owner prefers
this to a decorative gradient. Keep the established left alignment, card sizes
and remote focus behavior.

**Blocked by:** Row/layout research and canonical Android TV design rule;
coordinate shared files after tickets 06 and 07.
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** media_row_edge_research (GPT-6 Luna, max), then Sol implementation.

- [ ] Identify all horizontal media-row surfaces and the parent right inset.
- [ ] Commit the canonical Android TV rule and adopt its immutable revision.
- [ ] Popular movies and other Home, Search, library and detail episode rows
      use the same viewport-edge treatment where applicable.
- [ ] Preserve header/text bounds, left alignment, card sizes, phone layouts
      and non-media surfaces.
- [ ] Focus rings and final cards remain reachable and unclipped.
- [ ] Inspect representative native TV screenshots and run relevant checks.

## Commits and evidence

Owner requested 2026-10-02 and explicitly named Popular movies. Luna research
runs separately while native keyboard and episode-jump checks continue.
