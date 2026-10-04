# Home navigation rail Right handoff

Blocked by: none
Status: done
Owner: Codex
Triage: ready-for-agent
Repositories: android
Implementation commits: owner-authorized focused checkpoint (see Git log)

## Contract and acceptance

Pinned TV-034 / TV-038 require Right/Back to close the rail and restore content
focus. Owner reported Right minimizes the rail without selecting Home content.
Test the real Home/rail interaction before fixing it. Cover retained and
recreated Home controls, empty/loading content, Profile and navigation rows,
route replacement and stale remembered requesters. Preserve prior season-card
and watched-hold changes. Use text/focus checks only, with no screenshots.

## Evidence

The real Home/rail refresh regression failed twice before the fix: the full
two-test run and the minimized single-test run both left Play unfocused after
Right collapsed the rail. Private text logs are in
`qualification/artifacts/rail-focus-20261003/before.log` and
`before-minimal.log`.

Cause: the rail retained a non-null FocusRequester for a Home control disposed
during refresh. Its request failed, was swallowed, and never tried the current
page entry control. FocusMemory.restore now tries the remembered control, then
clears the stale reference and tries the current entry control. Right on both
Profile and navigation items, and shell Back, collapse only after a successful
handoff. A loading Home without a focus target keeps the rail available.

Five text-only emulator tests passed after the fix (`after.log`): originating
hero control, recreated hero control, originating shelf card, Profile Right
after refresh, and loading Home with a later retry. Existing shell requesters
and memory remain scoped by route.screenKey(); route replacement creates fresh
entry/memory instances. This lifetime was checked in source; no device-level
claim for other destination pages is made.

The season navigation/watched-hold and Home hero return suites also passed:
`OK (11 tests)` in 54.979s (`episode-regression.log`). Root unit XML reports
228 tests and isolated latest-main reports 237, both zero failures/errors/skips.
Host core and all three Android ABIs passed. Normal and test APK assembly and
app lint passed (77 warnings, existing three-error baseline unchanged). The
test fixture owns one FocusMemory outside composition; this avoids lint's
incorrect Unit inference of the internal cross-source-set constructor inside
remember without changing the fixture's focus lifetime or suppressing lint.
The final rebuilt harness returned `OK (5 tests)` in 44.36s
(`after-final.log`). The normal debug app was relaunched on the dedicated local
TV emulator. Normal APK SHA256:
`31EDA02C2E5023008061F4AC40BAF94D8C708D9F4707AF39BEDC6276DDC43D57`.
No screenshots, image fixtures, pixel assertions or debug instrumentation used.

Current session: the same five rail-focus cases passed in the 21-test text-only TV run before the owner took over interactive checks; current host unit run passed 237 tests, zero failures/errors/skips. Earlier implementation is reviewed and committed separately from the keyboard/modal guards.
