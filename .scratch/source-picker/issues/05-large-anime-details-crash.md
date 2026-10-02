# 05: Open a large anime without closing the TV app

**What to build:** Opening a large series such as One Piece on Android TV keeps
the app running and displays usable title information and episode selection.

**Blocked by:** None; the original installed APK crash has been reproduced.
**Status:** in-review
**Triage:** ready-for-agent
**Owner:** large_anime_fix (GPT-6 Sol, low), with provider_anime_research (GPT-6 Luna, max) for reproduction.

- [x] Reproduce opening the title and identify a safe, redacted crash signature.
- [x] Fix the demonstrated cause without dropping episodes or inventing source policy.
- [x] A large-series regression exercises the failing boundary and normal navigation.
- [x] Native TV acceptance confirms details remain usable and Back returns correctly.
- [x] Preserve personal sign-in and viewing data; do not start playback for reproduction.

## Commits and evidence

The old installed APK closed when Search opened Anime Kitsu's 1999 One Piece
details on the signed-in Windows TV emulator. Android reported an app exception;
the safe stack enters `CoreModels.initialEpisode` through native `normalize`,
with `CoreException.InvalidInput`. The adapter sends the episode list twice and
exceeds the core's 2 MiB input limit. The Rust selection policy needs only the
original season/episode alongside one full episode list. Commit `5842c28` fixes
that request shape. A 1,050-episode native-core regression failed before the fix
and passed after it; the complete Windows unit suite passed 216 tests, followed
by the three-ABI Android build. The normal APK was installed preserving sign-in.
Native API 36 TV acceptance opened the actual 1,410-episode One Piece details,
chose episode 2's sources without playback, and returned to details with the
same app process running. Private captures/logs stay ignored; no playback,
sign-out, data clearing or viewing-history change occurred.
