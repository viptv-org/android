# viptv design specification

Native Roku adopts the current TV design through [ROK-042](docs/platforms/ROKU_DESIGN.md).

This repository is the source of truth for viptv product UI, UX and app assets. The behavioral baseline is the actual Roku implementation at `vynxc/viptv@7d6b413`; historical notes are evidence, not overrides. The visual source for the redesign is the [VIPTV Redesign canvas](https://claude.ai/artifact/UX1E5AtPUoSKnaSLPou3Pp) together with its committed export in [viptv-design-system/](viptv-design-system/README.md); resolve visual questions from the canvas and import changes here before implementation.

## Reading order
1. CONTEXT.md defines shared product terms.
2. [viptv-design-system/](viptv-design-system/README.md) is the visual design system for every platform: tokens (`tokens/tokens.json` is the single source of truth), component rules, exact copy, settled decisions and the reference screens. It replaces the former `specs/visual/`, `tokens/` and responsive/TV visual documents, which remain in git history.
3. specs/behavior/ defines navigation, button actions, playback, continuation and account behavior.
4. plans/PLATFORM_PLAN.md and docs/playback/PLAYBACK_CAPABILITIES.md define proposed platform adapters and the direct-first fallback contract.
5. assets/ contains app artwork, fonts/icons where available, generators and provenance. It contains no screenshots.
6. docs/architecture/REPOSITORIES.md and specs/repos/ define ownership and delivery contracts.

For every platform UI change, pin update, asset import or parity claim, follow [docs/process/DESIGN_SYNC.md](docs/process/DESIGN_SYNC.md). It defines immutable adoption, integrity versus freshness, per-state evidence and the proposed future Figma authoring workflow.

## Required format for any new or revised feature
State its stable identifier, status (baseline or proposed), source revision, user intent, entry/exit points, complete visible copy and data, layout in reference coordinates, focus order/restoration, every input's press/release/repeat/hold behavior and threshold, disabled/loading/empty/error states, cancellation and recovery, timing, accessibility and platform equivalents. Supply concrete acceptance scenarios including failure and return navigation. Explicitly mark unknown measurements; never invent parity evidence.

## Change process
A design issue describes the before/after user experience, affected platforms and assets. Update the normative spec, acceptance scenarios and change log before implementation. Implementation issues reference an immutable design commit. A platform exception needs its reason, equivalent discoverable action and parity test; it must not silently remove a familiar feature. The Roku baseline is frozen during extraction. Proposed behavior does not become baseline until implementation and validation evidence are recorded.

## Reconstruction standard
An implementing agent should be able to build screens and interaction flows from these specifications and packaged assets alone. Source citations support auditing, not an instruction to reverse-engineer omitted behavior.

Two kinds of image are distinguished:

- **Canvas renders are allowed.** The reference screens and component sheets in `viptv-design-system/reference/` are renders exported from the design canvas. They are normative design artifacts, inventoried with SHA-256 hashes and their source in `reference/FILES.json`, and checked by `scripts/validate.py`.
- **App and device captures are forbidden.** Screenshots or recordings of a running app, emulator, browser build or physical device are never checked in or embedded. Inspect them privately only to resolve visual facts and record the result as text measurements; UI source supplies geometry and state logic.

## Platform consistency
TV layouts use the Roku reference canvas and focus model. Phone and desktop layouts may adapt density and pointer/touch navigation while preserving action meaning, queue/source intent, resume position, Back/cancel and next-episode semantics. Hold-only actions need an accessible visible menu/keyboard equivalent. Platform decoder limitations affect the playback adapter, not unrelated product behavior.

Current platform implementation scope and test interfaces: [docs/platforms/TV_IMPLEMENTATION.md](docs/platforms/TV_IMPLEMENTATION.md).

Shared data/state architecture and startup adoption: [docs/architecture/SHARED_CORE.md](docs/architecture/SHARED_CORE.md).

Browser HTTPS playback and conversion: [docs/playback/BROWSER_MEDIA_PIPELINE.md](docs/playback/BROWSER_MEDIA_PIPELINE.md).

## Visual design system

Phone, desktop (Tauri), web and TV share one design system: [viptv-design-system/](viptv-design-system/README.md), exported from the [VIPTV Redesign canvas](https://claude.ai/artifact/UX1E5AtPUoSKnaSLPou3Pp). The canvas is the visual source for the redesign; a missing screen is designed there first and then exported here with an updated `reference/FILES.json`. Build every screen from its reference image and HTML (`reference/screens/`), the rules in `components.md`, the strings in `copy.md` and the decisions in `decisions.md`. Platform theme files are generated from `tokens/tokens.json` by `tools/gen-themes.mjs`; never hand-edit a generated theme or hard-code a value.

## Local addon mode

[docs/archive/LOCAL_MODE.md](docs/archive/LOCAL_MODE.md) is superseded for the upcoming v2 cutover by
[BE-002](plans/backend-v2/BACKEND_V2.md): accounts and providers require the backend. Existing
runtime removal is tracked in the [implementation ledger](plans/backend-v2/IMPLEMENTATION_V2.md).
The [ADM-002 admin rebuild](plans/backend-v2/ADMIN_V2.md) does not redesign viewing clients.
