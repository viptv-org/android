# VIPTV native Android app

Read `DESIGN_REF`, `SPEC.md` and the pinned `design-contract/ANDROID_DESIGN.md` before changing `:app`. The pinned design governs the shared phone and TV UI. Compose stays native; Media3 owns the video surface.

Use the native television UI mode to select the uniformly scaled 1920×1080 TV layout. Phones use native density, font scaling, touch, rotation and system text entry. Use the shared controls, cards, sheets and fonts in `DesignUi.kt`. Import design assets/tokens with `scripts/design-sync.mjs`; never edit generated tokens or vendor/core. The remaining `assets/roku` files are shared avatar assets and their provenance, not a separate UI implementation.

Keep shared source/artwork/progress/continuation policy in Rust. `AppController` coordinates effects and platform route/focus lifetime; `VipTvHttpGateway` is the backend adapter. Do not automatically choose a substitute source. Exact-source Resume, 700 ms hold with suppressed release, scoped cancellation and originating-route restoration remain required. Live channels use the direct channel path and do not expose pause, seek or Next controls.

Apply the root Shared decision logic boundary to every controller/helper. Do not
reimplement episode merges, metadata fallback lists, hero/queue action selection,
watching/progress eligibility, source ranking/producer labels, preview reuse,
countdown decisions or authority comparison in Kotlin. Feed generated core DTOs
and observed facts into the shared projection, then execute its intent natively.
Keep `watched`, `resumeActive`, `completionOnly`, `watchDateKnown`, exact source
identity and updated/released timestamps intact through view-model copies. A
watched episode can have an active rewatch; completion-only imports are not resume
activity. Delivery kind, not a gateway's processing mode, distinguishes native
direct playback from managed title-clock playback. Route/focus restoration and
cancellation ownership remain in the native shell.

Tokens, stream URLs, headers and parent PINs are secrets. Persist credentials only in private app storage, keep PINs transient, and never log them. The debug fixture origin and opt-in CA are for isolated emulators only; release routing and ordinary APK trust remain unchanged.

For implementation changes, run the documented host/native tests, app assembly and lint on a suitably provisioned machine. Documentation-only and comments-only changes need content/diff checks. For routine TV UI/input checks on the Linux workspace, use the designated serve-avd emulator following the workspace root's private `.local-device-testing.md`. Coordinate with its browser user and preserve app data/sign-in. Use separate owned phone/TV QA emulators for fixtures that reset state, change servers or install fixture trust. Physical-device testing requires an explicit request. Inspect private matching-content captures and record observed results in `TESTING.md`. Physical decoder, HDR/DRM, real-server managed delivery and store release require separate evidence.
