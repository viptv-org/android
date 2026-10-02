# 03: Back from an episode source picker opens the show's info page

**What to build:** Continue Watching opens an episode's source picker. Back opens
that show's info page and episode selector, allowing another episode to be
chosen. Back from the info page restores the original queue card.

**Blocked by:** None (design is already committed).
**Status:** in-review
**Triage:** ready-for-agent
**Owner:** Android navigation worker and source_picker_tv (GPT-6 Sol, low).
**Contract:** CW-SOURCE-BACK-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [ ] Episode picker Back opens the parent series with the relevant season/episode.
- [ ] Selecting a different episode discovers that episode's sources.
- [ ] Back from info restores the originating Home/list queue card and focus.
- [ ] Discovery/preparation and late metadata cannot restore a canceled picker.
- [ ] Missing metadata remains safe and recoverable; live and other source return paths remain correct.
- [ ] Controller/browser regression checks and scoped native acceptance pass.

## Commits and evidence

Android implementation: `94ac6a5`; the parent-identity regression passed with
the local unit suite. TV-web implementation: `a906e47`; the responsive browser
flow passed queue episode sources -> parent details -> different episode sources
-> details -> Home. Broader review and native route acceptance remain pending.
