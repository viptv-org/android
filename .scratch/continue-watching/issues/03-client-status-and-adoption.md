# 03 — Compact Continue Watching indicators and aligned client adoption

Blocked by: 01-shared-contract, 02-backend-release-queue
Status: pending
Owner: Mimir
Triage: ready-for-agent
Repositories: android, tv-web
Implementation commits: none

## Acceptance

- Adopt the same committed canonical core pin in Android and TV-web; never hand-edit generated/vendored contracts.
- Render a top-right release/ongoing indicator and compact `N behind` from authoritative shared facts; partial current episode counts as behind, future releases do not.
- Distinguish new available episode, backlog, awaited release, ended/watched and unknown metadata. Confirmed ongoing is compact; unknown ongoing/count is not guessed.
- Preserve actual playback bar, retained completed cards, title identity, focus/scroll stability, keyboard/modal navigation guards, and normal manual source selection.
- Add host/shared projection tests and compile both clients. Run the local Android host/native/unit/build flow, then data-preserving install.
- Owner performs interactive checks. No pictures, screenshots, recordings or screenshot-producing tests.
- Commit each coherent validated change, inspect/fetch/pull main safely at task boundaries, preserve unrelated owner edits, and do not push unless authorized.
