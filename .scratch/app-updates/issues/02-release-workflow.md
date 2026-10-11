# 02 — Publish GitHub releases from version tags

Blocked by: 01
Status: in-review
Owner: Claude
Triage: ready-for-agent
Repositories: android
Implementation commits: 8dffc54

## Acceptance

- `build.yml` runs for `v*` tag pushes in addition to main pushes and manual dispatch.
- Tag builds reject tags that are not `vMAJOR.MINOR.PATCH`, pass `-PviptvVersion`,
  and verify the packaged APK's versionCode/versionName with `aapt2`.
- Tag builds name the APK `viptv-android-<version>.apk` and add `update.json`
  (schema in ../plan.md) next to `SHA256SUMS` and `build.json`.
- A `release` job with `contents: write`, only on tags, creates a regular GitHub
  release with all artifacts, generated notes, and a beta / update-your-backend note.
- Main-branch builds keep the existing development artifact behavior.

## Evidence

Local: `scripts/package-apk.py` produced the development artifact set for an
unversioned APK, rejected an unversioned APK for `VIPTV_VERSION=0.2.0`
(versionCode mismatch) and a malformed `0.2`, and for the versioned build wrote
`viptv-android-0.2.0.apk`, `SHA256SUMS` (verifies), `build.json` and `update.json`
matching the plan's schema. Workflow YAML parses. Tag/release jobs are unproven
until ticket 08 pushes a tag.
