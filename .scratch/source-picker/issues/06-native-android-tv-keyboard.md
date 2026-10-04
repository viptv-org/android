# 06: Use the native Android keyboard on TV

**What to build:** Replace Android TV's app-rendered keyboard and PIN keypad
with the device's native Android input method in Search and text-entry dialogs.
The owner's 2026-10-01 request supersedes the older custom TV keyboard design.

**Blocked by:** None; research and canonical Android UX are complete.
**Status:** done
**Triage:** ready-for-agent
**Owner:** native_keyboard_android (GPT-6.1 Sol, high), after GPT-6.1 Sol high research.
**Contract:** AND-KEYBOARD-001 at design 8ce8971ebcece32af59c39ecd56ea9fc8c880936.

- [x] Identify every custom Android TV keyboard surface and current native IME.
- [x] Update the owning design contract and adopt the exact revision.
- [x] Search uses an editable field and the native IME; query and catalog shelves
      retain their identity, progressive results and physical text input.
- [x] Native Search/Done dismisses the keyboard and enters available results;
      no-results and returning from results retain a usable field and focus.
- [x] Text-entry dialogs use the native IME with existing limits, masked numeric
      parent PIN input, Done/Cancel behavior and focus restoration preserved.
- [x] Back dismisses the keyboard before dismissing the dialog/leaving Search.
- [x] Remove the unused custom keyboard implementation; leave TV-web untouched.
- [x] Verify real native TV input, remote selection, dismissal/reopening and
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

Final acceptance (2026-10-02): b2b2fec removes the custom keyboard. Search
focus/entry fixes are 363d2d3, 7e4a97b, 688cd42, 0efc536 and 5842e13.
Compose's legacy text overload ignores showKeyboardOnFocus; the TV field now
uses its supported state-based API. Actual Gboard Search focuses the first
result; Left returns to the field with IME hidden, and Select reopens it.
Back hides IME before leaving Search. Connected keyboard text entry also works.
Isolated API 36 NativeTextEntryTest (0a6c216) passed two checks for masked
8-digit numeric PIN submission and Cancel without submission. The later full
Windows gate passed 222 unit tests, normal/test APK assembly and lint (77
warnings, unchanged three-error baseline). No owner credentials, PIN, backend
configuration or history were changed. Fresh Luna Spec review found no remaining
Search contract gap. Native emulator evidence does not qualify physical TVs.
