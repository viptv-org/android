# 04 — Design spec for in-app updates

Blocked by: none (Fire TV permission check in 07 may adjust copy)
Status: ready
Owner: unassigned
Triage: ready-for-agent
Repositories: design, then android design-contract sync
Implementation commits: pending

## Acceptance

- New `design/specs/behavior/app-updates.md` covers: launch check cadence,
  Settings "Check for updates" row, states (up to date, checking, available with
  version, downloading with progress, needs install permission, ready to install,
  failed with retry), TV focus/back behavior, and "never during playback".
- Copy for the permission step on Android TV / Fire TV and phones.
- `scripts/design-sync.mjs` includes the spec when present; `DESIGN_REF` advanced.

## Evidence

Pending.
