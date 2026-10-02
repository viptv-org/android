# 01: Read source descriptions without losing the source row

**What to build:** Every provider's source description wraps inside a fixed
two-line window. Focused overflowing text scrolls vertically from its beginning
to its end, allowing the torrent details to be read without widening the panel.

**Blocked by:** None (design is already committed).
**Status:** done
**Triage:** ready-for-agent
**Owner:** source_picker_android and source_picker_tv (GPT-6 Sol, low).
**Contract:** SRC-OVERFLOW-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [x] Android phone/TV and React/SolidTV rows wrap all descriptions, including long tokens.
- [x] At most two lines are visible; overflow traverses vertically with readable pauses.
- [x] Focus/hover loss resets the text; reduced motion keeps it still and full text accessible.
- [x] Row geometry, focus, source activation, hold and Back remain usable.
- [x] Meaningful rendering/browser checks pass and private captures are inspected.

## Commits and evidence

Android implementation: `0fd492c`, with viewport anchoring/compile correction
`3a1e7f7` and controlled native rendering check `5351223`. The isolated API 36
TV emulator passed the measured-overflow, pixel-movement and blur-reset test.
TV-web implementation: `61dc20c`, with source-details audio parity and regression
updates through `0545d52`. Its production build, 251 unit tests, targeted React
long-token/reduced-motion cases and SolidTV Sources/Details/Library previews
passed. Private browser captures were inspected. Native personal-account
acceptance displayed two-line source rows and full Source Details safely.
Physical TV remains unverified; final two-axis review is pending.

Final qualification (2026-10-02): TV-web fa6b903 includes each distinct source description in the shared body projection. All 256 TV-web tests, production build and React/SolidTV browser previews passed. Android measured-overflow, movement and blur-reset render checks passed again on isolated API 36. Final Standards review found no documented-rule violations; final TV-web Spec review found no confirmed gaps. Reviews used retained Sol workers because fresh review launches were unavailable. TV-web 04239f8 is pushed and its qualified assets are active on DEV.
