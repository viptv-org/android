# 11: Restore the selected episode after source Back

**What to build:** On Android TV, selecting episode 1059 from the info page,
opening Choose a source and pressing Back keeps that episode visible and focused.
The row cursor must remain separate from the shared Play/Resume target.

**Status:** regression covered; reported direct-path reset remains unreproduced
**Owner:** Sol implementation and native regression, Luna research.

- [x] Check a fresh info route displaying episode 1 before selecting episode 1059.
- [x] Retain regression coverage using the actual Details route state holder.
- [x] Verify the reported large-list path; avoid a speculative fix without reproduction.
- [x] Record native evidence and narrowly commit the demonstrated regression coverage.

Initial investigation: the 1,410-episode route-holder test returned focus to
episode 1059. Native personal-account touch and remote paths also retained 1059;
the account's current resume target was 1059, so a different selection remains
necessary to exclude a masked reset. No speculative behavior change is justified.

Committed coverage in `d9b5701`: actual DetailsScreen within the route-keyed
saveable holder renders 1,410 episodes with the initial cursor at episode 1,
jumps to 1059, activates it using remote OK, takes the actual controller through
Sources and Back, then asserts episode 1059 is focused. This passed on isolated
API 36 TV emulator-5576. The test does not replace MainActivity's entire route
host, so the original account-dependent report remains open to reproduction.

Signed-in native check on the owner's emulator-5572: Search -> Anime Kitsu
One Piece (1,410 episodes) opened a fresh info route visibly at episode 1.
Native Episode # -> 1059 focused the selected card; remote OK opened the
19-source picker, and Back restored episode 1059 with its focus ring.
Accessibility capture confirmed the focused card at [196,624][556,972]. No
source was activated and no viewing progress was written. This excludes the
earlier concern that an already visible resume episode could mask a row reset.
Expected behavior is verified in this current build; the reported reset itself
remains unreproduced, so no direct-path speculative fix was committed.
