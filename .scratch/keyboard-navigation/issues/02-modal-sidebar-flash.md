# 02 — Prevent sidebar expansion during watched-menu focus handoff

- Blocked by: none (builds on implemented ticket 01)
- Status: implemented; owner intermittent-symptom validation pending
- Owner: Mimir

## Report and scope

Owner saw a brief sidebar opening after a long hold while going to Mark watched; not reliably reproducible. Both Continue Watching and episode menus open an Android dialog. Rail focus callbacks currently request expansion without checking modal ownership or main-window focus. Address this concrete gap without asserting the exact intermittent event has been reproduced. Preserve watched actions and existing per-page focus restoration.

## Acceptance criteria

- Dialog/PIN ownership prevents background navigation expansion.
- An unfocused main window cannot expand navigation.
- Clearing these conditions does not resurrect previous expansion.
- Keyboard guard and ordinary deliberate rail navigation remain intact.
- No pictures or automated device UI checks; owner verifies the intermittent symptom.

## Evidence

- Before guard: all four new modal/window host regressions failed; existing keyboard/ordinary-navigation tests passed (8 tests, 4 failures; exit 1). This reproduces the decision-level gap, not the exact device timing.
- Added modal visibility and main-window ownership to the existing shared render/request guard; clear remembered expansion across these handoffs. All 8 focused tests passed (exit 0).
- Local JDK 17 / SDK 36 host-core and both unit tasks: 237 tests, zero failures/errors/skips (exit 0).
- All three Android native ABIs and normal debug assembly passed (exit 0).
- Data-preserving `adb install -r` returned `Success`; app launch returned `Status: ok`, with PID present. No backend/sign-in changes.
- Scoped `git diff --check` passed (exit 0). No pictures or automated emulator UI checks.
- Private text logs: `qualification/artifacts/dev-tv-session/modal-navigation-*.txt`.
- Exact intermittent long-hold/sidebar flash remains for owner verification; watched actions and existing page focus restoration were not changed.
Owner subsequently authorized incremental commits; this modal guard and its regressions are committed separately from ticket 01 and preserved earlier UI work. No push requested.
