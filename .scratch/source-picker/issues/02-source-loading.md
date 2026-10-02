# 02: Show discovery progress while sources are arriving

**What to build:** Choose a source displays an unmistakable busy indicator while
discovery is pending, both before the first source and while more providers are
still returning. Arrived sources remain selectable.

**Blocked by:** None (design is already committed).
**Status:** in-review
**Triage:** ready-for-agent
**Owner:** source_picker_android and source_picker_tv (GPT-6 Sol, low).
**Contract:** SRC-OVERFLOW-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [ ] Initial pending discovery shows a spinner and Finding sources status.
- [ ] Partial discovery retains selectable rows and visible Still checking sources status.
- [ ] Completion, failure and cancellation stop the discovery indicator.
- [ ] Loading does not steal focus or trigger source selection.
- [ ] Rendering/browser checks cover initial, partial and finished states.

## Commits and evidence

Android implementation: `5be0b0e`. The isolated API 36 TV emulator passed the
Finding sources -> Still checking sources -> completed status/spinner test,
including stable allocated height. TV-web implementation: `faf203c`; final
browser/build evidence is being recorded.
