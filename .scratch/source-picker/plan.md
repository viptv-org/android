# Source picker and Continue Watching plan

Owner instructions, 2026-10-01: keep tickets in local Markdown, use Mimir model
routing, make narrow commits, verify locally on Windows and push the changes.

Canonical source design: `d088071106e4d5479aea761469152e037f011a3c`, including
`SRC-OVERFLOW-001`, `CW-SOURCE-BACK-001` and `SRC-PROVIDERS-001`. Android's
native keyboard uses the later `AND-KEYBOARD-001` platform exception. Each
client records its exact adopted revision; shared Rust policy stays authoritative.

| Order | Ticket | Dependencies | Execution |
| --- | --- | --- | --- |
| 01 | [Readable source descriptions](issues/01-source-descriptions.md) | None | Android and TV-web workers in parallel |
| 02 | [Visible source loading](issues/02-source-loading.md) | None | Same workers, separate narrow commits |
| 03 | [Continue Watching Back opens show info](issues/03-continue-watching-back.md) | None | Separate Android worker; TV-web worker after its source UI edits |
| 04 | [Distinct installed add-on providers](issues/04-distinct-addon-providers.md) | Concrete research findings | Luna research, then Sol implementation |
| 05 | [Large anime details crash](issues/05-large-anime-details-crash.md) | Native reproduction and cause | Luna research, then Sol implementation |
| 06 | [Native Android TV keyboard](issues/06-native-android-tv-keyboard.md) | Native IME and focus research; canonical Android UX | Luna research, then Sol implementation |
| 07 | [Jump to an episode number](issues/07-jump-to-episode.md) | Episode-number/focus research; canonical UX | Luna research, then Sol implementation |
| 08 | [Media rows reach the right edge](issues/08-media-rows-right-edge.md) | Layout research; canonical UX; coordinate 06/07 files | Luna research, then Sol implementation |

Workers use GPT-6 Sol with low reasoning for implementation. Independent
validation and review use GPT-6 Luna with max reasoning, per Mimir's AGENTS.md.
Shared files in one checkout have a single writer. Gradle runs sequentially;
TV-web tests use one worker and do not overlap its production build.

Incoming tasks join this queue without replacing work already authorized.
Research stays with Luna workers so the main chat can receive new tasks.
Tickets 01-08 are complete, with implementation and qualification recorded in
their individual files. Independent final Android reviews used Luna workers.

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
