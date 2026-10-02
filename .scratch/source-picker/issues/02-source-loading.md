# 02: Show discovery progress while sources are arriving

**What to build:** Choose a source displays an unmistakable busy indicator while
discovery is pending, both before the first source and while more providers are
still returning. Arrived sources remain selectable.

**Blocked by:** None (design is already committed).
**Status:** done
**Triage:** ready-for-agent
**Owner:** source_picker_android and source_picker_tv (GPT-6.1 Sol, high).
**Contract:** SRC-OVERFLOW-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [x] Initial pending discovery shows a spinner and Finding sources status.
- [x] Partial discovery retains selectable rows and visible Still checking sources status.
- [x] Completion, failure and cancellation stop the discovery indicator.
- [x] Loading does not steal focus or trigger source selection.
- [x] Rendering/browser checks cover initial, partial and finished states.

## Commits and evidence

Android implementation: `5be0b0e`. The isolated API 36 TV emulator passed the
Finding sources -> Still checking sources -> completed status/spinner test,
including stable allocated height. TV-web implementation: `faf203c`, qualified
through `0545d52` with the production build, 251 unit tests, React partial-status
browser checks and SolidTV source previews. Real-account sources settled before
the native screenshot, so that run does not prove its initial loading frame;
the isolated Compose test provides that scoped evidence. Final review pending.

Final qualification (2026-10-02): isolated Android rendering verifies initial, partial and complete discovery. TV-web final 256-test suite, production build and progressive React/SolidTV browser checks passed. Final Standards review found no documented-rule violations and TV-web Spec review found no confirmed gaps (retained Sol workers). Qualified TV-web 04239f8 assets are pushed and active on DEV.
