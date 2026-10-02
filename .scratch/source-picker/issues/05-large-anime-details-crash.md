# 05: Open a large anime without closing the TV app

**What to build:** Opening a large series such as One Piece on Android TV keeps
the app running and displays usable title information and episode selection.

**Blocked by:** None; the original installed APK crash has been reproduced.
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** large_anime_fix (GPT-6 Sol, low), with provider_anime_research (GPT-6 Luna, max) for reproduction.

- [x] Reproduce opening the title and identify a safe, redacted crash signature.
- [ ] Fix the demonstrated cause without dropping episodes or inventing source policy.
- [ ] A large-series regression exercises the failing boundary and normal navigation.
- [ ] Native TV acceptance confirms details remain usable and Back returns correctly.
- [ ] Preserve personal sign-in and viewing data; do not start playback for reproduction.

## Commits and evidence

The old installed APK closed when Search opened Anime Kitsu's 1999 One Piece
details on the signed-in Windows TV emulator. Android reported an app exception;
the safe stack enters `CoreModels.initialEpisode` through native `normalize`,
with `CoreException.InvalidInput`. The adapter sends the episode list twice and
exceeds the core's 2 MiB input limit. The Rust selection policy needs only the
original season/episode alongside one full episode list. A Sol worker is fixing
that request shape and adding a large-series regression. Private logs remain
ignored; no playback, sign-out or data clearing occurred.
