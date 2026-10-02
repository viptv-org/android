# 05: Open a large anime without closing the TV app

**What to build:** Opening a large series such as One Piece on Android TV keeps
the app running and displays usable title information and episode selection.

**Blocked by:** Native reproduction and a concrete crash cause.
**Status:** in-progress
**Triage:** needs-triage
**Owner:** provider_anime_research (GPT-6 Luna, max); implementation assigned after findings.

- [ ] Reproduce opening the title and identify a safe, redacted crash signature.
- [ ] Fix the demonstrated cause without dropping episodes or inventing source policy.
- [ ] A large-series regression exercises the failing boundary and normal navigation.
- [ ] Native TV acceptance confirms details remain usable and Back returns correctly.
- [ ] Preserve personal sign-in and viewing data; do not start playback for reproduction.

## Commits and evidence

Research in progress. Android TV opening the title was reported to close the app.
