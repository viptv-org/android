# 01: Read source descriptions without losing the source row

**What to build:** Every provider's source description wraps inside a fixed
two-line window. Focused overflowing text scrolls vertically from its beginning
to its end, allowing the torrent details to be read without widening the panel.

**Blocked by:** None (design is already committed).
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** source_picker_android and source_picker_tv (GPT-6 Sol, low).
**Contract:** SRC-OVERFLOW-001 at design ce7084ff80d0541257509748ea772ef0f95d8058.

- [ ] Android phone/TV and React/SolidTV rows wrap all descriptions, including long tokens.
- [ ] At most two lines are visible; overflow traverses vertically with readable pauses.
- [ ] Focus/hover loss resets the text; reduced motion keeps it still and full text accessible.
- [ ] Row geometry, focus, source activation, hold and Back remain usable.
- [ ] Meaningful rendering/browser checks pass and private captures are inspected.

## Commits and evidence

Pending implementation and qualification.
