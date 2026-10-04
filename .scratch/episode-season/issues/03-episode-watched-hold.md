# Episode hold and watched indication

Blocked by: 02-season-edge-cards
Status: done
Owner: Codex
Triage: ready-for-agent
Repositories: android
Implementation commits: owner-authorized focused episode-navigation checkpoint (see Git log)

## Contract

Owner explicitly requested a watched indication and episode long-press action.
Existing EpisodeManage and profile-scoped correctProgress are reused. The pinned
Watched badge remains authoritative; no percentage-based completion is invented.

## Acceptance

- Holding OK for 700 ms opens the episode menu; releasing it cannot also open Sources.
- Unwatched episodes offer Mark watched; watched episodes offer Mark unwatched.
- Accepted correction updates the Watched badge, preserves season/episode focus and does not start playback.
- Cancel and failed saves retain episode selection; another profile/route cannot receive stale correction results.
- Text assertions only; do not run the existing screenshot-producing EpisodeWatchedBadgeTest.

## Evidence

- Reused existing profile-scoped correction and authoritative Watched badge; EpisodeManage now offers only the appropriate watched/unwatched toggle.
- Episode hold records its logical selected row and restores focus after menu dismissal and progress refresh. A held OK release does not activate Sources.
- New text-only native-key/semantics fixture proves watched -> badge -> unwatched, cancellation, rejected writes, retained season/episode focus and no Sources transition. Existing correction tests prove stale route/profile guards, failed/missing progress and resume clearing.
- Final targeted emulator run: OK (14 tests); private log and build evidence are recorded in ticket 02. Normal app and test APKs are installed on the dedicated local TV emulator. Fixture corrections use loopback and do not modify the owner's backend history.
- No screenshot-producing EpisodeWatchedBadgeTest, image/pixel assertion, screenshot, recording or image tool was used.
