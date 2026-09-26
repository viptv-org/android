# VIPTV native Android app

Read `DESIGN_REF`, `SPEC.md` and the pinned `design-contract/ANDROID_DESIGN.md` before changing `:app`. AND-035 replaces the historical Roku reconstruction with the current shared phone and TV design. Compose stays native; Media3 owns the video surface.

Use the native television UI mode to select the uniformly scaled 1920×1080 TV layout. Phones use native density, font scaling, touch, rotation and system text entry. Use the shared controls, cards, sheets and fonts in `DesignUi.kt`. Import design assets/tokens with `scripts/design-sync.mjs`; never edit generated tokens or vendor/core. The remaining `assets/roku` files are shared avatar assets and their provenance, not a separate UI implementation.

Keep shared source/artwork/progress/continuation policy in Rust. `AppController` coordinates effects and platform route/focus lifetime; `VipTvHttpGateway` is the backend adapter. Do not automatically choose a substitute source. Exact-source Resume, 700 ms hold with suppressed release, scoped cancellation and originating-route restoration remain required. Live channels use the direct channel path and do not expose pause, seek or Next controls.

Tokens, stream URLs, headers and parent PINs are secrets. Persist credentials only in private app storage, keep PINs transient, and never log them. The debug fixture origin and opt-in CA are for isolated emulators only; release routing and ordinary APK trust remain unchanged.

Run the documented host/native tests, app assembly and lint on a suitably provisioned machine. Use isolated phone/TV emulators for native surface/input claims; do not commandeer an existing emulator or configure wireless debugging. Inspect private matching-content captures and record observed results in `TESTING.md`. Physical decoder, HDR/DRM, real-server managed delivery and store release require separate evidence.
