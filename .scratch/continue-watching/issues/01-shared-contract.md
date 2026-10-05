# 01 — Continue Watching shared facts and presentation contract

Blocked by: completion of the active Stremio task (owner explicitly requested wait)
Status: blocked
Owner: Mimir
Triage: ready-for-agent
Repositories: design, core
Implementation commits: none; planning only

## Acceptance

- Record the approved flow and compact status in canonical design, not vendored files.
- Define authoritative ongoing/ended/unknown facts and a nullable released-unwatched behind count; count partial episodes and exclude future/manual-watched episodes.
- Define next released unwatched selection and retained last-episode watched/progress semantics without inventing playback progress.
- Confirm whether explicitly followed means existing Favorites/My List or a distinct signal; preserve queue removal/suppression.
- Normalize metadata in core, regenerate Kotlin/TypeScript wire outputs and WASM, and cover release-boundary/unknown-metadata/progress regressions.

## Evidence and resume

Approved examples, backend/core ownership map, known gaps, and regression priorities are in ../plan.md. Read-only investigation is provisional because concurrent import/progress changes touched the inspected files. No feature code changed. Re-read completed Stremio changes and fetch main before claiming this ticket.
