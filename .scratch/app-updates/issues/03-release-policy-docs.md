# 03 — Update release policy and versioning docs

Blocked by: 01, 02
Status: done
Owner: Claude
Triage: ready-for-agent
Repositories: android, workspace (viptv-org root notes)
Implementation commits: 8dffc54

## Acceptance

- `AGENTS.md` delivery policy allows tag-triggered GitHub releases of the Android APK.
- `VERSIONING.md` documents tag → versionName/versionCode, `update.json`, and
  how to cut a release (tag on main, push tag, verify the release page and asset hash).
- `DEVELOPMENT.md`/`README.md` mention installing from GitHub Releases and that
  releases tell users to update the backend to latest.
- Workspace root notes mention that Android releases come from tags, if they
  describe delivery.

## Evidence

`AGENTS.md`, `README.md`, `DEVELOPMENT.md` and `VERSIONING.md` updated (content
review only). Workspace root notes do not describe Android delivery, so no change there.
