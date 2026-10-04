# 01 — Close Android TV navigation while native keyboard is open

- Blocked by: none
- Status: implemented; owner interactive validation pending
- Owner: Mimir
- Scope: Android TV application shell; preserve existing Search and focus behavior.

## Acceptance criteria

- Opening the native keyboard collapses expanded navigation.
- Navigation focus cannot expand the rail while the keyboard is visible.
- Dismissing the keyboard does not automatically reopen navigation.
- Normal navigation remains available when the keyboard is hidden.
- No pictures; owner performs interactive emulator checks.

## Evidence

- Extracted the shell's existing expansion decision without changing behavior: 4 host regressions ran, 3 failed as expected (Gradle exit 1).
- Added native IME visibility guarding for rendering, focus expansion requests, and rail Back handling; cleared remembered expansion on keyboard open. All 4 focused regressions then passed (Gradle exit 0).
- JDK 17 / SDK 36 local host-core and both unit tasks passed: 233 tests, zero failures/errors/skips. Three Android ABIs and normal debug APK build passed (exit 0).
- `adb install -r` returned `Success`; launch returned `Status: ok`, app PID present. Existing app data and backend settings were not cleared or changed.
- `git diff --check` passed (exit 0). Existing owner edits preserved.
- No screenshots/pictures or post-request automated UI interaction. Actual Search/keyboard behavior remains for the owner's interactive check.
- Private text build/test evidence: `qualification/artifacts/dev-tv-session/keyboard-navigation-*.txt`.
Owner subsequently authorized incremental commits; this ticket and its keyboard guard are committed separately from preserved earlier UI work. No push requested.
