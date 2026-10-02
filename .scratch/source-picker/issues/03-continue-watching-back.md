# 03: Back from an episode source picker opens the show's info page

**What to build:** Continue Watching opens an episode's source picker. Back opens
that show's info page and episode selector, allowing another episode to be
chosen. Back from the info page restores the original queue card.

**Blocked by:** None (design is already committed).
**Status:** done
**Triage:** ready-for-agent
**Owner:** Android navigation worker and source_picker_tv (GPT-6 Sol, low).
**Contract:** CW-SOURCE-BACK-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [x] Episode picker Back opens the parent series with the relevant season/episode.
- [x] Selecting a different episode discovers that episode's sources.
- [x] Back from info restores the originating Home/list queue card and focus.
- [x] Discovery/preparation and late metadata cannot restore a canceled picker.
- [x] Missing metadata remains safe and recoverable; live and other source return paths remain correct.
- [x] Controller/browser regression checks and scoped native acceptance pass.

## Commits and evidence

Android implementation: `94ac6a5`; the parent-identity regression passed with
the local unit suite. TV-web implementation: `a906e47`; the responsive browser
flow passed queue episode sources -> parent details -> different episode sources
-> details -> Home. Broader review and native route acceptance remain pending.

Native acceptance found a remaining failure: a Continue Watching series-shaped
S2E9 item returned to Home instead of the episode selector. The episode-only
entry marker missed series items carrying season/episode coordinates. The Sol
worker is adding that exact regression and fixing the route/parent projection.

Correction and acceptance (2026-10-02): Android 0bfcb52 and TV-web 9f9fa4b recognize series-shaped Continue Watching episode cursors. On signed-in API 36 TV, the affected card's Choose source -> Back opens complete parent info and available episode selector; second Back restores the original Home queue. Its stored S2E9 cursor has only Season 1 available in current upstream metadata; the UI shows actual metadata without inventing missing items. TV-web browser acceptance also selected a different episode and returned through info to Home. Controller regressions cover delayed/cancelled metadata and non-episode return paths. 9fa4319 preserves core hero Play/Resume policy. Final Standards/TV-web Spec reviews found no hard violations/confirmed gaps (retained Sol). No playback or history write.
