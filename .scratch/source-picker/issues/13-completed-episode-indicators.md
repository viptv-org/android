# 13: Show completed-watching indicators on episode cards

**What to build:** Each episode card on the Android info page displays a clear
watched-completion indicator when the selected profile has completed it.
Use existing shared watched facts; retain partial progress as partial progress.

**Status:** research queued
**Owner:** Luna data and design research, Sol implementation.

- [ ] Identify the canonical completed-watching fact and profile-scoped data flow.
- [ ] Adopt the design's existing watched badge, or record the shared UX addition.
- [ ] Show completion accessibly on TV and phone cards without hiding episode text.
- [ ] Keep incomplete and unknown episodes distinct from completed episodes.
- [ ] Verify profile isolation, fresh progress and large virtualized lists.
- [ ] Run meaningful regression and device checks; narrow commit and push.
