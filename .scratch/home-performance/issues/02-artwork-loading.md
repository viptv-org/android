# Reduce artwork work during Home browsing

Blocked by: none
Status: implemented; owner device/artwork validation pending
Owner: Codex
Triage: ready-for-agent
Implementation commits: owner-authorized focused checkpoint (see Git log)

## Acceptance

Measure request timing/counts and cache reuse at the real artwork-loading seam.
Keep every feature and the pinned blurred ambient/sharp hero composition.
Keep artwork selection/fallback in core. Bound any look-ahead and memory work;
cancel work that no longer belongs to the visible Home/profile. Preserve saved
sign-in and backend. Validate with text/numeric output only, never image captures.

## Evidence

Before: the real HeroBackdrop made two independent Coil requests for the same
data, at 1920x950 and 1120x720. After: one shared painter/request resolves at the
sharp layer's physical size (1120x720 on the 1080p fixture); both draw layers,
72dp blur, 0.6 alpha and the gradients remain. Decode dimensions follow actual
Compose density. Same-image metadata changes retain the request. Missing art
does not start work, and replacement art starts one replacement request.

Core still resolves the artwork URL. Existing Coil memory/disk cache behavior
and pin 2.7.0 remain. The test uses a fake fetcher that returns no image bytes:
only request counts and resolved sizes are recorded, with no image fixtures,
screenshots or downloads. This proves removed duplicate work and bounded decode
requests; it does not measure network latency or certify visual appearance.

Private evidence: artwork-before.log, after-first.log and numeric-first.log
under qualification/artifacts/home-performance-20261003/. Final checks pending.

Current review: the normal/test APK build included this five-case numeric fixture; current host checks passed 237 tests with zero failures/errors/skips. The five-case artwork device fixture was not executed in this session. The owner now performs interactive checks; no pictures or new device UI checks are taken.
