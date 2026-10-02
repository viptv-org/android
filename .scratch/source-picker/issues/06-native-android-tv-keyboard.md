# 06: Use the native Android keyboard on TV

**What to build:** Replace Android TV's app-rendered keyboard and PIN keypad
with the device's native Android input method in Search and text-entry dialogs.
The owner's 2026-10-01 request supersedes the older custom TV keyboard design.

**Blocked by:** None; research and canonical Android UX are complete.
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** native_keyboard_android (GPT-6 Sol, low), after Luna research.
**Contract:** AND-KEYBOARD-001 at design 8ce8971ebcece32af59c39ecd56ea9fc8c880936.

- [x] Identify every custom Android TV keyboard surface and current native IME.
- [ ] Update the owning design contract and adopt the exact revision.
- [ ] Search uses an editable field and the native IME; query and catalog shelves
      retain their identity, progressive results and physical text input.
- [ ] Native Search/Done dismisses the keyboard and enters available results;
      no-results and returning from results retain a usable field and focus.
- [ ] Text-entry dialogs use the native IME with existing limits, masked numeric
      parent PIN input, Done/Cancel behavior and focus restoration preserved.
- [ ] Back dismisses the keyboard before dismissing the dialog/leaving Search.
- [ ] Remove the unused custom keyboard implementation; leave TV-web untouched.
- [ ] Verify real native TV input, remote selection, dismissal/reopening and
      result navigation locally without entering account credentials.

## Commits and evidence

Luna research found custom grids only in TV Search and common TextEntry;
TextEntry covers profile names, server URLs, parent PIN, filters, guide search
and add-on entry. Existing Compose AppField already supports native input;
the API 36 owner TV emulator has Gboard enabled and permits its display with
a physical keyboard. No device-setting change is needed. The canonical design
was validated from Git LF blobs (12 documents, 878 assets, 453 reference files,
516 tokens) and pushed. Implementation/native acceptance remain in progress.
Preserve the owner's selected profile, sign-in, IME configuration and history.
