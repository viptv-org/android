# viptv design specification

This repository is the source of truth for viptv product UI, UX and app assets. The normative baseline is the actual Roku implementation at `vynxc/viptv@7d6b413`; historical notes are evidence, not overrides. This migration makes no Roku runtime changes.

## Reading order
1. CONTEXT.md defines shared product terms.
2. [viptv-design-system/](viptv-design-system/README.md) is the visual design system for every platform: tokens (`tokens/tokens.json` is the single source of truth), component rules, exact copy, settled decisions and the reference screens. It replaces the former `specs/visual/`, `tokens/` and responsive/TV visual documents, which remain in git history.
3. specs/behavior/ defines navigation, button actions, playback, continuation and account behavior.
4. PLATFORM_PLAN.md and PLAYBACK_CAPABILITIES.md define proposed platform adapters and the direct-first fallback contract.
5. assets/ contains app artwork, fonts/icons where available, generators and provenance. It contains no screenshots.
6. REPOSITORIES.md and specs/repos/ define ownership and delivery contracts.

For every platform UI change, pin update, asset import or parity claim, follow [DESIGN_SYNC.md](DESIGN_SYNC.md). It defines immutable adoption, integrity versus freshness, per-state evidence and the proposed future Figma authoring workflow.

## Required format for any new or revised feature
State its stable identifier, status (baseline or proposed), source revision, user intent, entry/exit points, complete visible copy and data, layout in reference coordinates, focus order/restoration, every input's press/release/repeat/hold behavior and threshold, disabled/loading/empty/error states, cancellation and recovery, timing, accessibility and platform equivalents. Supply concrete acceptance scenarios including failure and return navigation. Explicitly mark unknown measurements; never invent parity evidence.

## Change process
A design issue describes the before/after user experience, affected platforms and assets. Update the normative spec, acceptance scenarios and change log before implementation. Implementation issues reference an immutable design commit. A platform exception needs its reason, equivalent discoverable action and parity test; it must not silently remove a familiar feature. The Roku baseline is frozen during extraction. Proposed behavior does not become baseline until implementation and validation evidence are recorded.

## Reconstruction standard
An implementing agent should be able to build screens and interaction flows from these specifications and packaged assets alone. Source citations support auditing, not an instruction to reverse-engineer omitted behavior. Screenshots are not checked in or embedded. Inspect them privately only to resolve visual facts; UI source supplies geometry and state logic.

## Platform consistency
TV layouts use the Roku reference canvas and focus model. Phone and desktop layouts may adapt density and pointer/touch navigation while preserving action meaning, queue/source intent, resume position, Back/cancel and next-episode semantics. Hold-only actions need an accessible visible menu/keyboard equivalent. Platform decoder limitations affect the playback adapter, not unrelated product behavior.

Current platform implementation scope and test interfaces: [TV_IMPLEMENTATION.md](TV_IMPLEMENTATION.md).

Shared data/state architecture and startup adoption: [SHARED_CORE.md](SHARED_CORE.md).

## Visual design system

Phone, desktop (Tauri), web and TV share one design system: [viptv-design-system/](viptv-design-system/README.md). Build every screen from its reference image and HTML (`reference/screens/`), the rules in `components.md`, the strings in `copy.md` and the decisions in `decisions.md`. Platform theme files are generated from `tokens/tokens.json` by `tools/gen-themes.mjs`; never hand-edit a generated theme or hard-code a value.

## Local addon mode

[LOCAL_MODE.md](LOCAL_MODE.md) proposes account-free operation of the shared viewing client from an on-device addon registry: availability gating, addon management, discover reuse of the RUI-030 hierarchy, direct-first playback without server preparation, and the persistence/privacy contract. Status is proposed; no platform claims adoption until its implementation records evidence.
