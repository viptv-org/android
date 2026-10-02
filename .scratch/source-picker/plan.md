# Source picker and Continue Watching plan

Owner instructions, 2026-10-01: keep tickets in local Markdown, use Mimir model
routing, make narrow commits, verify locally on Windows and push the changes.

Canonical design: `ce7084ff80d0541257509748ea772ef0f95d8058`, including
`SRC-OVERFLOW-001` and `CW-SOURCE-BACK-001`. Android and TV-web adopt this exact
revision before implementation. Shared Rust policy remains authoritative.

| Order | Ticket | Dependencies | Execution |
| --- | --- | --- | --- |
| 01 | [Readable source descriptions](issues/01-source-descriptions.md) | None | Android and TV-web workers in parallel |
| 02 | [Visible source loading](issues/02-source-loading.md) | None | Same workers, separate narrow commits |
| 03 | [Continue Watching Back opens show info](issues/03-continue-watching-back.md) | None | Separate Android worker; TV-web worker after its source UI edits |

Workers use GPT-6 Sol with low reasoning for implementation. Independent
validation and review use GPT-6 Luna with max reasoning, per Mimir's AGENTS.md.
Shared files in one checkout have a single writer. Gradle runs sequentially;
TV-web tests use one worker and do not overlap its production build.

Verify descriptions with long multiline text and long unbroken torrent names,
initial and partial loading, focus/blur, reduced motion and route cancellation.
Verify episode-source Back reaches the parent show's season/episode selector,
then returns to the original queue card. Cover delayed/failed metadata and live
sources so the change cannot create stale navigation or affect channel Back.

Run Android host-core preparation, unit tests, Android-core preparation and APK
assembly on this Windows PC. Use an isolated TV emulator for Compose/input
acceptance. Run TV-web unit tests, production build and targeted browser checks.
Record scoped results in tickets; do not infer physical TV playback capability.

Commit each behavior and its regression checks narrowly, then record evidence.
Push previously qualified account/import/Home work along with these completed
changes in dependency order: design, dashboard and clients, then backend pins.
Preserve owner edits and personal emulator sign-in. Deployment remains separate
from build automation; no automatic release or deployment workflow is added.
