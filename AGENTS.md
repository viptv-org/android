# VIPTV Android video agent guide

Delivery policy: only Android, desktop, Roku and TV-web build workflows run,
triggered by main pushes and manual dispatch. No PR gates, automatic releases,
image publishing or deployment. Retain local checks.

This Windows checkout is Windows-first: build/test and run Android TV on this PC.
Read DEVELOPMENT.md and load scripts/windows-dev.ps1; the git-ignored local .env
records this PC's tools and the owner's exact VPS SSH target. The VPS hosts the
dev backend; do not start a remote TV emulator unless explicitly requested.

Read `DESIGN_REF` and `SPEC.md` before changing playback behavior. The pinned design commit defines shared UX; this library owns Media3 adaptation and reports facts rather than deciding product policy.

Keep every public type free of Media3 implementation types. State is level-triggered and session-scoped; one-off completions, errors, and track events are event flows. Reject callbacks from replaced sessions. Preserve the distinction between on-demand, seekable live, and non-seekable live. Never serialize source URLs, headers, cookies, licenses, or local paths into logs, exceptions, analytics, or `toString()`.

Validate with JDK 17 and Android SDK Platform 36. Run Android unit tests for implementation changes; require an emulator or physical Android TV for surface, remote, codec, HDR, PiP, and device-capability claims. Documentation-only and comments-only changes need content/diff checks, not a build or device run.

Shared behavior is owned by ../core (viptv-org/core), pinned in CORE_REF. Edit Rust there, regenerate Kotlin/WASM, commit, then run scripts/core-sync.mjs sync ../core. Never edit vendor/core. Keep the Android and TV-web pins together for a shared rule change. Run the local Gradle flow before pushing implementation changes: `scripts/prepare-core.sh host`, `./gradlew --no-daemon :testDebugUnitTest :app:testDebugUnitTest`, then `scripts/prepare-core.sh android && ./gradlew --no-daemon :app:assembleDebug`. The machine has ample memory; emulators are allowed but start them only when a device-level claim needs proof. Generated Kotlin fields come from Rust; Compose view adapters may translate units/layout types but must not invent provider aliases or repeat source/artwork/resume policy.

## Comments and documentation

Describe current behavior, contracts and procedures, and explain non-obvious
rationale. Do not narrate edits ("we changed X to Y", "previously", "now uses")
in code comments or evergreen guides. Keep history only when it explains an
active compatibility requirement, migration/rollback hazard, provenance,
qualification limit or design constraint. Put implementation chronology and
dated results in tickets, commits, ADRs or explicitly historical records; link
there when useful. Replace obsolete instructions rather than appending overrides.
Do not mechanically ban words such as "old" or "new": runtime state changes and
useful historical evidence are valid. Fix imported/generated prose at its source,
not in vendor snapshots.

## Agent skills

### Issue tracker

Use local Markdown tickets in .scratch/<feature>/issues/, sorted by dependencies
and ticket number. See docs/agents/issue-tracker.md. Do not create external
issues for these tickets.

### Triage labels

Keep the existing five triage labels. See docs/agents/triage-labels.md.

### Domain docs and workers

Use the single-context layout and pinned contracts. See docs/agents/domain.md.
Use the workspace subagent policy: GPT-6.1 Sol (`gpt-6.1-sol`) with high
reasoning (`high`) for implementation, testing, investigation and review. Keep each
ticket's implementation and checks in narrow commits, preserving owner edits.
