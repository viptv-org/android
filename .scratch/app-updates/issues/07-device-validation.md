# 07 — Device validation of self-update

Blocked by: 06
Status: blocked
Owner: unassigned
Triage: ready-for-human
Repositories: android
Implementation commits: none

## Acceptance

- serve-avd TV emulator: install a versioned build N, publish or serve build
  N+1, update from Settings and from the launch prompt, sign-in preserved.
- Owner's Fire TV (explicit request and coordination required): record the
  installed SHA and back up the APK first (rollback baseline in TESTING.md);
  verify the install-unknown-apps permission path and the update.
- Failure cases: no network, checksum mismatch, permission denied, user cancels.

## Evidence

Pending.
