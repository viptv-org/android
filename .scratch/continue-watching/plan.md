# Continue Watching: continuation, release activity, and compact status

## Owner-approved flow

One card per series. Marking an episode watched advances to the next released, unwatched episode, with that episode's own playback progress. If no released successor exists, retain the last watched episode. Distinguish an ended/completed series from an ongoing/caught-up series. Watched status must not falsify playback progress: preserve actual saved position/duration; a fully played final episode has a full bar, while a manually marked episode retains its actual partial bar.

Only watched or explicitly followed titles participate, not all newly released shows. A first eligible release moves a title to the front once. Subsequent releases must not repeatedly promote a title while the profile is behind. It becomes eligible again after the immediately preceding episode is watched or marked watched. A followed upcoming title can enter when its first episode releases. Refresh/restart/polling must not replay an already-consumed promotion.

## Compact card status

- Top-right release/ongoing indicator, with accessible meaning and no pictures required for validation.
- `N behind` counts distinct released, unwatched episodes, including a partly watched episode; excludes future releases and watched/manual-watched episodes.
- Ongoing is shown compactly only when authoritative metadata confirms it. Unknown metadata must not be presented as ended or a definite zero behind.
- Keep awaited-release, available-new-episode, backlog, and ended/watched states distinct.
- No automatic playback, duplicate series cards, source-policy changes, or forced focus/scroll movement when a queue refresh changes ordering.

## Narrow scope: `N behind` counting rules

This is a planning clarification only, not an implementation or adoption of new runtime policy. The existing wait for the Stremio task remains in force.

`N` is the number of distinct, confirmed-released episodes not watched by the selected profile. Count actual episode identities across seasons, not a subtraction of episode numbers or a count of queue cards. Earlier unwatched gaps still count.

- A partly watched episode counts once only if it is not already watched.
- Explicit/manual watched marks and verified imported completion exclude an episode even when its actual playback position is partial or its watch date is unknown.
- Rewatching an already-watched episode must not increase the behind count. Keep active resume separate from completion.
- Use the existing shared watched projection/policy; do not add an Android-specific completion threshold.
- Future releases do not count. Missing release evidence, incomplete episode metadata, or truncated history must not produce a falsely exact count or zero.
- Ongoing status and release-driven card ordering are independent of this count. Updating `N` does not itself promote the card or imply the series is ongoing.

Example: E1–E12 are released, E1–E8 watched, E9 halfway through: **4 behind** (E9–E12). Mark E9 watched: **3 behind**. Announce future E13: still **3 behind**. Release E13: **4 behind**. Rewatch E3: still **4 behind**.

Open scope choice: excluding Specials (season 0) from the main count is recommended to match current normal next-episode selection, but has not been separately confirmed. Do not silently settle it during implementation.

Read-only inspection found release and watched/resume/import facts in canonical core and profile history in the backend; no behind-count DTO field exists yet. The inspected series-history endpoint caps results at 2,000, so exact counting needs complete-history handling. Revalidate these moving sources when work resumes.

## Ownership and sequencing

1. First preserve prior bug fixes in focused commits and integrate current main without discarding owner edits.
2. Record UX in canonical design; establish release/progress/order facts in backend; expose shared projection in canonical core, not vendor/core.
3. Regenerate Kotlin/WASM, commit core, and adopt identical core pins in Android and TV-web.
4. Render compact statuses without reimplementing shared policy in client adapters.
5. Run focused red/green regressions, full relevant local checks, and normal Android build/install. Owner performs interactive device checks; no screenshots/pictures.

## Current checkpoint and pause

Owner explicitly selected **Wait for Stremio task** before editing the overlapping backend continuation files. No Continue Watching implementation changes have been made. Resume only once that task has finished, then refresh Git state, current contracts, migrations, and tests; do not rely on the provisional draft inspected here.

Android main now contains seven focused checkpoints and merge `732941d`, retaining local fixes while integrating fetched upstream `44196c4`. A fresh fetch confirmed origin/main was an ancestor of HEAD. Merged JDK 17 / SDK 36 validation passed **246 unit tests, zero failures/errors/skips**, all three Android native ABIs, and normal/test APK assembly. Data-preserving install returned `Success`; launch returned `Status: ok`. No commits were pushed; no post-request interactive UI checks or pictures were taken.

## Provisional contract map

Read-only investigation of the moving drafts found:

- Backend `server/src/continuation.rs`: per-profile title grouping, hidden queue entries, cached successor resolution, release-date checks and queue statuses exist. The cache is not a durable release-promotion watermark; inspected successor resolution did not consult full episode history to skip watched successors.
- Backend `server/src/library.rs`, `handlers_profiles.rs`, and import/progress files are changing concurrently. Favorites/My List exists, but a distinct follow contract was not found. Decide on resume whether existing saved-list membership is the explicitly followed signal or a separate action is required.
- Canonical core `crates/viptv-core/src/dto.rs`, `domain/media.rs`, `policy/progress.rs`, and `policy/presentation.rs`: release dates and progress/next selection exist, but the inspected shared projection lacks a typed ongoing/ended/unknown status, behind count, and durable promotion facts. Recheck against completed concurrent work.
- DTO changes must originate in canonical Rust, regenerate Kotlin and TypeScript/wire outputs, and keep Android/TV-web pins aligned. WASM generation is separate.
- TV-web queue enrichment and card-text/status renderers currently consume queue facts. Its shelf card count is not an episode-backlog count.

## Regression priorities on resume

- Next released unwatched selection, watched successors skipped, opaque IDs and season/episode identity, one series card across corrections/playback.
- Behind counts: distinct released episodes only; partial current included; watched/manual-watched and future excluded. Unknown/malformed release dates need an explicit policy, never a falsely certain count.
- Ongoing caught-up versus ended watched versus unknown; actual last-episode playback progress retained.
- First eligible release promoted once; multiple later releases while behind do not re-bump; watching/marking the predecessor re-arms; repeated refresh and restart idempotence; per-profile isolation.
- Followed upcoming E1, unwatched/unfollowed new titles, unfollowing, queue removal/suppression, metadata refresh triggers.
- No focus/scroll stealing from release reorder; accessible compact icon/count; no screenshot-producing checks.

Keep unrelated concurrent Stremio work, IDE files, private configuration, account data, and credential-bearing artifacts out of this feature's commits.
