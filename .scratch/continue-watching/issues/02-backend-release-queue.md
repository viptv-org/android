# 02 — Profile-scoped continuation and one-shot release promotion

Blocked by: 01-shared-contract; completion of the active Stremio task
Status: pending
Owner: Mimir
Triage: ready-for-agent
Repositories: backend, core
Implementation commits: none

## Acceptance

- One card per series; watched/manual correction advances to the next released unwatched episode with its own actual progress.
- Retain ended/completed and ongoing/caught-up cards distinctly, including actual saved final-episode progress.
- Supply exact released-unwatched behind counts and confirmed ongoing facts; unknown metadata stays unknown.
- Only watched/explicitly followed titles qualify. Persist promotion eligibility/watermarks per profile/show.
- First eligible release promotes once; later releases while behind do not re-bump; watched/manual-watched predecessor re-arms. Repeated refresh and restart must be idempotent.
- Define bounded metadata refresh and preserve suppression, profile isolation, existing imports and progress migrations.
- Red/green tests cover eligibility, episode identity, count boundaries, watched-successor skipping, multiple releases, restart and stale/profile responses.

## Coordination

Do not edit the actively changing continuation.rs/library.rs/import/progress files before the owner-authorized wait ends. Revalidate those drafts as committed inputs; avoid broad database changes or unsolicited deployment.
