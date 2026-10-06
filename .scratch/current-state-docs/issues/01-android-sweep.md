# 01: Current-state comments and documentation

**Blocked by:** none
**Status:** done
**Owner:** Mimir
**Triage:** ready-for-agent

## Acceptance criteria

- Add a current-state writing rule to root `AGENTS.md`, with explicit exceptions
  for useful rationale, compatibility, migration/rollback risk, provenance and
  qualification limits.
- Clean up first-party evergreen guides and incidental comment narration without
  changing runtime behavior or imported/generated snapshots.
- Keep valuable dated validation/handoff evidence in explicitly historical records.
- Reconcile superseded instructions with the approved delivery policy.
- Verify Markdown links, diff whitespace and comment-only source changes.
- Commit only this sweep when verified; no push or deployment.

## Evidence

- Local `main`: `git pull --ff-only` reported already up to date; no tracked owner
  edits were present. Existing untracked files are preserved.
- Server repository pulls and read-only audit are a separate workstream; findings
  do not authorize changing remote source, pushing or deploying.
- Build/device checks are not applicable to documentation/comment-only edits.
- `git diff --check` passed. The local verifier checked 14 Markdown files and
  24 local links/anchors, and confirmed the three source edits are comments-only.
- `node scripts/core-sync.mjs check` passed at core pin
  `851a4bd59a7afc4dd2f420f962adfdf3c3cba403`; design integrity check passed.
- Independent review found no actionable or blocking concerns; it confirmed
  preservation of safety/compatibility facts and qualification evidence.
- This ticket and the three linked history records accompany the narrow local
  documentation commit. No push or deployment is part of this ticket.
