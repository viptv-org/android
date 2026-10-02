# 10: Fit/Fill toggle in the site video player

**What to build:** An accessible Fit/Fill toggle in the responsive website
video-player controls. Fit shows the complete picture; Fill fills the viewport
with cropping while preserving the picture's aspect ratio.

**Status:** done
**Blocked by:** None.
**Owner:** web_player_fit_fill (GPT-6 Sol, low), independent Luna review.

- [x] Commit canonical visual/input behavior and adopt its immutable design pin.
- [x] Add a visible, accessible control beside fullscreen on phone and desktop.
- [x] Apply Fit/Fill to both native HTML video and MediaBunny canvas surfaces.
- [x] Preserve playback/session/position while toggling and resizing/fullscreen.
- [x] Keep controls usable on small screens and provide keyboard focus.
- [x] Run meaningful browser acceptance, unit checks and production build.
- [x] Commit/push narrowly and update the authorized development preview.

## Scope

Owner requested 2026-10-02 while episode 1059 artwork diagnosis was ongoing and
clarified the control belongs on the video player. Existing account data,
source/resume behavior and playback delivery remain unchanged. The setting is
presentation state in the responsive website player; it does not prepare or
replace a playback session. Fit is the default for a new media item.

## Commits and qualification

Canonical WEB-PLAYER-FIT-001 is committed and pushed at design `02fbcdd`;
its LF archive passed the Linux Python validator. TV-web pin `dfbf1e2` and
implementation `aed6ed5` are pushed, with backend TV pin `a85df00` pushed too.
The production build and all 256 web unit tests passed. Two single-worker
Chromium checks exercised decoded HLS at desktop and 390x844 phone widths:
Fit letterboxing, centered Fill crop, five reachable phone tools, keyboard
activation/focus, unchanged paused source/time, fullscreen retention, same-title
source return and leaving/re-entering reset. Both surfaces share CSS projection;
the hidden canvas mode was asserted, but real MediaBunny decoding is unverified.

The manually qualified bundle is active at `https://dev.embedez.com/tv/`.
Public React/Solid HTML and five entry asset hashes match the candidate. DEV
activation preserved the running backend binary and its other settings;
SQLite backup/quick-check and protected account/profile/session/add-on/import/
favorites/progress records passed before/after checks. No production change or
automatic deployment workflow was added. Private screenshots/logs stay ignored.
