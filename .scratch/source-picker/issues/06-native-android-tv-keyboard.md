# 06: Use the native Android keyboard on TV

**What to build:** Replace Android TV's app-rendered keyboard and PIN keypad
with the device's native Android input method in Search and text-entry dialogs.
The owner's 2026-10-01 request supersedes the older custom TV keyboard design.

**Blocked by:** Keyboard and remote-focus research; canonical Android UX update.
**Status:** ready
**Triage:** ready-for-agent
**Owner:** Luna research, then Sol implementation; root coordinates builds.

- [ ] Identify every custom Android TV keyboard surface and current native IME.
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

Queued while provider outcomes and the large-anime native acceptance finish.
Research is delegated so the main chat can keep receiving new tasks. Preserve
the owner's selected profile, sign-in, IME configuration and viewing history.
