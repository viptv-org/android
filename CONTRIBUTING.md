# Contributing to VIPTV Android

This repository owns the native Android/Android TV app in `app/` and the
Android-only playback library in `src/`. Read [AGENTS.md](AGENTS.md),
[SPEC.md](SPEC.md) and the contract pinned by `DESIGN_REF` before implementation.
Shared behavior belongs in `viptv-org/core`; product UX belongs in
`viptv-org/design`. Import their committed snapshots with the sync scripts;
never edit generated or vendored files directly.

## Local checks

[DEVELOPMENT.md](DEVELOPMENT.md) describes the Windows tool setup and owned TV
emulator. Use JDK 17, SDK Platform 36 and the checked-in Gradle wrapper. For
implementation changes, start with focused tests, then run the local flow:

```sh
node scripts/core-sync.mjs check
node scripts/design-sync.mjs check
scripts/prepare-core.sh host
./gradlew --no-daemon :testDebugUnitTest :app:testDebugUnitTest
scripts/prepare-core.sh android
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

Run Gradle tasks sequentially in this checkout. Host tests do not qualify
native input, rendering, codecs or device capabilities; follow the evidence
requirements in `SPEC.md` and record observed results in [TESTING.md](TESTING.md).
Documentation-only and comments-only changes need content, link and diff checks,
not a build or device run.

## Comments and documentation

Describe current behavior and non-obvious rationale, not the sequence of edits.
Follow the writing rule in `AGENTS.md`: retain history only for active
compatibility requirements, migration/rollback hazards, provenance,
qualification limits or design constraints. Put dated implementation and
validation reports in tickets or explicitly historical records. Replace obsolete
instructions rather than appending overrides.

Preserve unrelated owner edits. Use local tickets as described in
[docs/agents/issue-tracker.md](docs/agents/issue-tracker.md). Commit or push only
when requested. Delivery workflows produce Android build artifacts on main
pushes and manual dispatch; they do not create releases, publish packages or
deploy. Do not introduce other platform implementations or npm package/release
tooling into this repository.
