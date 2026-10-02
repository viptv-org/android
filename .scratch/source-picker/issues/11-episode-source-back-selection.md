# 11: Restore the selected episode after source Back

**What to build:** On Android TV, selecting episode 1059 from the info page,
opening Choose a source and pressing Back keeps that episode visible and focused.
The row cursor must remain separate from the shared Play/Resume target.

**Status:** investigating; no confirmed reproduction yet
**Owner:** Sol implementation and native regression, Luna research.

- [ ] Reproduce with the saved resume target different from the selected episode.
- [ ] Retain regression coverage using the actual Details route state holder.
- [ ] Correct only a demonstrated failure; verify large and sparse episode lists.
- [ ] Record native evidence and narrowly commit any demonstrated fix.

Initial investigation: the 1,410-episode route-holder test returned focus to
episode 1059. Native personal-account touch and remote paths also retained 1059;
the account's current resume target was 1059, so a different selection remains
necessary to exclude a masked reset. No speculative behavior change is justified.
