# VIPTV Android video agent guide

Delivery policy: only Android, desktop, Roku and TV-web build workflows run,
triggered by main pushes and manual dispatch. No PR gates, automatic releases,
image publishing or deployment. Retain local checks.

Choose the development host from the current checkout, not a remembered host.
Read DEVELOPMENT.md. In the Linux organization workspace, read the workspace
root's private `.local-device-testing.md` and use its existing serve-avd emulator
for routine Android TV UI/input checks. Coordinate disruptive runs with its
browser user and preserve sign-in. On Windows, load scripts/windows-dev.ps1 and
use that checkout's private .env and dedicated local emulator.

Read `DESIGN_REF` and `SPEC.md` before changing playback behavior. The pinned design commit defines shared UX; this library owns Media3 adaptation and reports facts rather than deciding product policy.

Keep every public type free of Media3 implementation types. State is level-triggered and session-scoped; one-off completions, errors, and track events are event flows. Reject callbacks from replaced sessions. Preserve the distinction between on-demand, seekable live, and non-seekable live. Never serialize source URLs, headers, cookies, licenses, or local paths into logs, exceptions, analytics, or `toString()`.

Validate with JDK 17 and Android SDK Platform 36. Run Android unit tests for implementation changes; require an emulator or physical Android TV for surface, remote, codec, HDR, PiP, and device-capability claims. Documentation-only and comments-only changes need content/diff checks, not a build or device run.

Shared behavior is owned by ../core (viptv-org/core), pinned in CORE_REF. Edit Rust there, regenerate Kotlin/WASM, commit, then run scripts/core-sync.mjs sync ../core. Never edit vendor/core. Keep the Android and TV-web pins together for a shared rule change. Run the local Gradle flow before pushing implementation changes: `scripts/prepare-core.sh host`, `./gradlew --no-daemon :testDebugUnitTest :app:testDebugUnitTest`, then `scripts/prepare-core.sh android && ./gradlew --no-daemon :app:assembleDebug`. Use the host's documented resource limits and run Gradle checks sequentially. Reuse the designated TV emulator for device-level proof; use separate owned QA emulators for destructive fixtures. Generated Kotlin fields come from Rust; Compose view adapters may translate units/layout types but must not invent provider aliases or repeat source/artwork/resume policy.

## Shared decision logic

Use the boundary **platform facts → Rust decision/projection → platform effects**.
Before adding Kotlin policy, inspect the canonical core API and extend it when
needed. A pure rule is not Android-specific merely because its first caller is
an Android screen.

- Rust owns episode/history joins, metadata fallback precedence, watched versus
  active-rewatch facts, card/hero/queue intents, safe source ranking and producer
  outcomes, presentation text, and scoped continuation/preview decisions.
- Rust owns authorization comparison and request/response transition invariants.
  Inject measured capabilities, elapsed time, scope/generation and configured
  budgets; do not turn an Android timeout or cache size into a universal rule.
- Backend facts remain authoritative for release/completion, actual successor,
  delivery admission and lease expiry. Never infer missing facts or select an
  automatic substitute source to repair a failed action.
- Kotlin owns Compose geometry, focus/IME/remote gestures, image loading, Android
  lifecycle and clocks, HTTP/storage execution, cancellation-safe cleanup and
  Media3 adaptation. Keep native session fencing even when core also rejects
  stale application results.
- Adapt generated DTOs without dropping optional flags, identity or timestamps.
  Use batch projections for collections; do not call the JSON/FFI bridge once
  per candidate with the same whole collection or on animation hot paths.
- Move policy regression vectors to canonical Rust and check actual native/WASM
  parity. Keep Android integration tests for effect order, cancellation, route
  return and focus. Regenerate at the source and adopt one immutable core pin
  in Android and TV-web; never patch generated or vendored code by hand.

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
