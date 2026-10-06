# Backend v2 cutover handoff — preserved 2026-10-06

This record preserves the undated development handoff previously at the top of
README.md. **2026-10-06 is the archival date, not a verified execution date.**
Branch isolation, integration status and open gates below describe the handoff
at the time it was written; they are not assertions about today's workspaces.
See [README.md](../../README.md) for current cursor compatibility and category
integration guidance.

## Candidate and checkout isolation

`refactor/android-backend-cutover` was isolated from the owner's active UI
checkout. It targeted backend `refactor/backend-v2` at `2c2eca2` or later compatible
revisions, with shared core `8ae9f81`. The handoff warned against installing this
candidate against the older production backend: live browsing requires the v2
next/previous cursor contract. Further backend work was required to preserve
that wire contract or use a new protocol version. No production migration or
deployment was implied.

Networking/controller changes removed legacy live discovery/playback/catalog
calls, retained only three channel pages and fetched bounded viewport schedules.
Layouts were unchanged. The small `GuideScreen.kt` integration diff reported the
viewport, restored its saved position/focus and corrected search copy. The
handoff asked the UI owner to review that diff when merging UI work; the owner's
checkout had not been switched or overwritten.

Category controller logic supported next/previous replacement pages of 200,
with separate cancellation/retry and scope guards. `GuideScreen.kt` wired
observation-only viewport/anchor callbacks and existing TV terminal controls;
phone paging required continued drag beyond the existing row ends. The isolated
Settings screen no longer offered Maximum quality. Original UI-checkout
integration, physical-device qualification and real backend/gateway integration
remained separate gates; see [TESTING.md](../../TESTING.md).

## Integration rationale and observations

The handoff required the existing row, fixed All/My channels/Recent/Search
actions and visuals to remain, without paging buttons or reserved provider IDs.
It distinguished category IDs from fixed tab indices and required callbacks to
capture the revision belonging to the rendered row. A replacement page's first
forward/last backward anchor had to be restored before acknowledging its
viewport; stale/unrestored callbacks were ignored to prevent page oscillation.
Returning without replacement preserved the saved visible index/offset.

Intentional remote category boundaries used `changeGuideCategoryPage`; existing
retry actions used `retryGuideCategories`. Fixed actions, especially Search,
remained independently reachable. Category state changes did not reset channel
filters, programme time, schedules or unrelated playback, and did not invent a
default catalog override from response metadata. The root Android checkout was
not modified by that isolated work.

The isolated screen recorded exact namespaced row keys/index/offset (including
fixed controls) and actual TV focus. Search-Right advanced; All-Left reversed;
ordinary provider-to-Search/Recent focus moves and Search activation remained.
Phone terminal overscroll waited for drag/fling settlement and closed overlays
before restoring the provider anchor. Core/server wire declarations were
unchanged by that integration work.
