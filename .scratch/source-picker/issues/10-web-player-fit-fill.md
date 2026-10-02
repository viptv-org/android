# 10: Fit/Fill toggle in the site video player

**What to build:** An accessible Fit/Fill toggle in the responsive website
video-player controls. Fit shows the complete picture; Fill fills the viewport
with cropping while preserving the picture's aspect ratio.

**Status:** in-progress
**Blocked by:** Canonical behavior contract and implementation.
**Owner:** web_player_fit_fill (GPT-6 Sol, low), independent Luna review.

- [ ] Commit canonical visual/input behavior and adopt its immutable design pin.
- [ ] Add a visible, accessible control beside fullscreen on phone and desktop.
- [ ] Apply Fit/Fill to both native HTML video and MediaBunny canvas surfaces.
- [ ] Preserve playback/session/position while toggling and resizing/fullscreen.
- [ ] Keep controls usable on small screens and provide keyboard focus.
- [ ] Run meaningful browser acceptance, unit checks and production build.
- [ ] Commit/push narrowly and update the authorized development preview.

## Scope

Owner requested 2026-10-02 while episode 1059 artwork diagnosis was ongoing and
clarified the control belongs on the video player. Existing account data,
source/resume behavior and playback delivery remain unchanged. The setting is
presentation state in the responsive website player; it does not prepare or
replace a playback session. Fit is the default for a new media item.
