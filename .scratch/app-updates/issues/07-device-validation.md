# 07 — Device validation of self-update

Blocked by: 06 (emulator part done)
Status: in-progress
Owner: Claude (emulator); owner (Fire TV)
Triage: ready-for-human
Repositories: android
Implementation commits: none; fix e0ccf12 came from this run

## Acceptance

- serve-avd TV emulator: install a versioned build N, publish or serve build
  N+1, update from Settings and from the launch prompt, sign-in preserved.
- Owner's Fire TV (explicit request and coordination required): record the
  installed SHA and back up the APK first (rollback baseline in TESTING.md);
  verify the install-unknown-apps permission path and the update.
- Failure cases: no network, checksum mismatch, permission denied, user cancels.

## Evidence

Emulator (API 36 Google TV) done 2026-10-11 with an isolated QA package: prompt,
permission screen and return, download progress, checksum failure and retry,
system confirmation cancel and accept (0.3.0 → 0.3.1), Later suppression,
Cancel during download (after fix). Details in TESTING.md.

Remaining: signed-in Settings › About VIPTV on a device, the real GitHub
release redirect (after ticket 08), and the owner's Fire TV permission path
(explicit request; back up the installed APK first).
