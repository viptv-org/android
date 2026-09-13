# VIPTV Android video agent guide

Read `DESIGN_REF` and `SPEC.md` before changing playback behavior. The pinned design commit defines shared UX; this library owns Media3 adaptation and reports facts rather than deciding product policy.

Keep every public type free of Media3 implementation types. State is level-triggered and session-scoped; one-off completions, errors, and track events are event flows. Reject callbacks from replaced sessions. Preserve the distinction between on-demand, seekable live, and non-seekable live. Never serialize source URLs, headers, cookies, licenses, or local paths into logs, exceptions, analytics, or `toString()`.

Validate with JDK 17 and Android SDK Platform 36. Run Android unit tests for implementation changes; require an emulator or physical Android TV for surface, remote, codec, HDR, PiP, and device-capability claims. Keep release automation out of this imported repository until the organization delivery policy is approved.

Shared behavior is owned by ../core (viptv-org/core), pinned in CORE_REF. Edit Rust there, regenerate Kotlin/WASM, commit, then run scripts/core-sync.mjs sync ../core. Never edit vendor/core. Keep the Android and TV-web pins together for a shared rule change. Hosted app-review builds the pinned native library before Kotlin tests/APK; do not run local Gradle or emulators on this memory-constrained server. Generated Kotlin fields come from Rust; Compose view adapters may translate units/layout types but must not invent provider aliases or repeat source/artwork/resume policy.
