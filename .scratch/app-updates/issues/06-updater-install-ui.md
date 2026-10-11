# 06 — Install flow and update UI

Blocked by: 04, 05
Status: blocked
Owner: unassigned
Triage: ready-for-agent
Repositories: android
Implementation commits: pending

## Acceptance

- Manifest adds `REQUEST_INSTALL_PACKAGES`.
- On API 26+, route to `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package when
  `canRequestPackageInstalls()` is false, and resume the flow on return.
- Install through a `PackageInstaller` session; handle `STATUS_PENDING_USER_ACTION`,
  success and each failure status with the design's copy.
- Settings row and launch prompt follow the design spec; nothing appears or
  installs while the player is active.
- Remote-only navigation works on TV.

## Evidence

Pending.
