# 12: Hero Resume Back opens the episode list

**What to build:** The large Home hero's Continue Watching Resume entry must
return from Choose a source to the parent show's info and resumed episode.
The next Back returns to Home and restores the originating hero focus.

**Status:** complete; loaded episode selection corrected and qualified in ticket 14
**Owner:** Luna research, Sol implementation.
**Related contract:** CW-SOURCE-BACK-001; this entry point was missed by ticket 03.

- [x] Reproduce the hero Resume entry separately from the queue card.
- [x] Cover both episode-shaped and series-shaped saved episode cursors.
- [x] Preserve exact Resume behavior and parent identity.
- [x] Cover the hero's manual source action and non-episode Home returns.
- [x] Cover hero Resume player exit as well as source-picker cancellation.
- [x] Verify the native hero -> source -> info -> Home flow without changing history.
- [x] Run the Windows gate, independent review, narrow commit and push.

Diagnosis: the actual hero Resume regression failed on the isolated API 36 TV
emulator because its Sources route omitted `queueEpisodeReturn`. The queue-card
entry already passes that marker. The corrected source round trip passed; hero
hold/manual-source and player-exit entry points are also being checked.

The separate hero Resume player-exit regression also failed behaviorally:
`exitPlayer` returned child `show:1:1059` instead of parent `show`. A targeted
Home queue-resume branch now opens parent metadata with the saved episode cursor
and Home as its return route. Ordinary source-player returns keep their existing
source-picker flow. Final coupled progress-refresh qualification is pending.

Implementation `d9b5701` includes the actual rendered Home hero Resume click,
controller source cancellation, hold/manual-source episode/series/movie cases,
and Resume player exit. These four hero tests and the separate large-list
Details focus test passed on the isolated TV emulator. Parent route identity,
season/episode and subsequent Home return are asserted; the hero fixture does
not claim real-provider playback or a fully loaded live account episode list.
Host Core, 224 unit tests, all three Android ABIs, normal and instrumented APK
assembly, app lint and design integrity passed before watched-refresh integration.

Follow-up `6d85bda` adds series-shaped Resume/player return and actual remote
hero-focus coverage. The remote Home -> Sources -> parent info -> Home test
failed because Resume was visible but not focused after the final Back.
An explicit Hero `restoreRequest` effect now restores focus; the existing
media-identity guard continues to protect rail focus during background updates.
The same native test passed after this focused fix. Five route/Compose tests
and the post-change unit suite passed; final watched integration gate follows.

Independent review found the new delayed focus restoration also needed to
honor `HomeFocusPolicy.inputEpoch`. A paused-frame native test reproduced
Resume stealing focus after a newer directional-input fact. `7a7f47c` captures
the request, target and input epoch, then checks the latest state before focusing.
Six focused native tests passed after that correction, including the cancellation
case. Final combined badge/navigation gate is pending.

Final combined qualification passed after watched-state integration: 228 unit
tests, host Core, three Android ABIs, normal/test APKs, lint and 19 targeted
native tests on API 36 emulator-5576. The six navigation tests cover the actual
hero source -> parent info -> Home remote flow, restored Resume focus, delayed
restore cancellation, both saved-cursor shapes and the 1,410-episode route.
Luna independently reviewed the final routing and focus guards. Source/player
controller fixtures do not claim real-provider playback or physical TV behavior.

Correction to the earlier acceptance scope: the hero fixture used placeholder
Details text, so it did not prove the saved episode was actually revealed.
Ticket 14 reproduced that gap on the latest APK and fixed common Details
restoration after metadata arrival in `2f0791b`. The updated real-screen
test and owner native hierarchy check now verify episode 1059 is visible/focused.
