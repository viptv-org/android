# 08 — Cut the first release (v0.3.0)

Blocked by: 01, 02, 03
Status: blocked
Owner: owner
Triage: ready-for-human
Repositories: android
Implementation commits: none

## Acceptance

- Owner approves tagging `v0.3.0` on main and pushing the tag.
- The workflow publishes a regular release with the APK, `SHA256SUMS`,
  `build.json` and `update.json`; the `latest/download/update.json` URL returns it.
- Downloaded APK sha256 matches `update.json`; it installs over the current
  development build with `adb install -r` and keeps sign-in.
- A later release (after 06) proves the in-app update end to end.

## Evidence

Pending.
