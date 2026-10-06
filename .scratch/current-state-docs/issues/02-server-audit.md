# 02: Server repository documentation audit — 2026-10-06

**Blocked by:** none for the read-only audit; Android/backend pulls have file collisions
**Status:** done (audit only; remote cleanup is not implemented)
**Owner:** Mimir
**Triage:** ready-for-agent

This is a dated audit record, not current operational guidance. The SSH endpoint
was loaded from the ignored local `.env`; no connection values are recorded here.
Scope: organization workspace plus all 12 clones on the configured development
server. No source cleanup, commits, pushes, deployment, service changes, emulator
runs or submodule updates were performed remotely.

## Pull outcomes

All checkouts stayed on `main`, tracking `origin/main`. Pulls used `--ff-only`
with submodule recursion disabled, after checking existing edits and incoming
path collisions. Owner changes were preserved.

| Repository | Observed HEAD | Outcome |
| --- | --- | --- |
| Workspace | `fa9e21bc2cf0` | Already current |
| `.github` | `5088ffe07007` | Already current |
| `android` | `44196c473b90` | Blocked; 11 commits behind |
| `backend` | `3ba58ec747dc` | Blocked; 23 commits behind |
| `core` | `851a4bd59a7a` | Fast-forwarded 1 commit |
| `design` | `b64c98e3a976` | Fast-forwarded 7 commits |
| `desktop` | `c5c5732ad4b2` | Fast-forwarded 10 commits; modified `tv` submodule preserved |
| `playback-gateway` | `d04a558e2b4f` | Already current |
| `roku` | `78d8d9767ce4` | Fast-forwarded 3 commits |
| `tauri-video-plugin` | `7a85ad46b5f1` | Fast-forwarded 13 commits |
| `tv-web` | `503db87be74c` | Fast-forwarded 19 commits |
| `video` | `13eb9535af29` | Fast-forwarded 19 commits |
| `web` | `a2474b75ecb3` | Fast-forwarded 1 commit |

The server Android checkout has owner-untracked `DEVELOPMENT.md` overlapping an
incoming tracked file. Backend has three overlapping owner-untracked paths:

- `docs/STREMIO_IMPORT.md`
- `scripts/stremio-preview.py`
- `tests/test_stremio_preview.py`

These files were not moved, deleted or overwritten. The blocked repositories'
latest fetched upstream trees were audited read-only: Android `42a90f127b3e`,
backend `3b44caf8071a`. Worktrees cannot become current until the owner approves
handling of those collisions. Fetching does not update their running services.

## Priorities and concrete candidates

Paths are relative to their named repository. Android/backend references use the
fetched upstream trees, not the blocked worktrees. This is a targeted audit, not
an exhaustive count of every comment.

1. **Backend — strongest concentration.**
   `server/src/app_state.rs:140–150` describes validation's former five queries.
   Preserve failure precedence/hot-path rationale, but describe the single-round-
   trip validation directly. `server/src/addon/discover.rs:3`,
   `server/src/addon/extras.rs:1`, `server/src/provider/candidates.rs:1` and
   `server/src/provider/normalize.rs:1` say pure logic "now lives" elsewhere;
   state the pure-provider versus network/cache/SQL ownership boundary instead.
2. **Core — shared comments.**
   `crates/viptv-core/src/domain/streams.rs:120–123` narrates duplicated polling;
   describe shared cursor/deduplication/budget/completion rules and platform
   transport ownership. `crates/viptv-core/src/policy/sources.rs:221–222`
   narrates retired quality caps; preserve the measured-device-only constraint.
   `adapters/tauri/README.md:21` can state adapter dependencies without narrating
   former host duplication. Fix source-owned prose before regenerating consumers;
   do not edit Android's vendor snapshot.
3. **Playback-gateway — API/resource docs and regression comments.**
   `API_V1.md:210–214` explains rendition-local subtitle indices, then compares
   them with an earlier map. Keep current index/native-track requirements.
   `docs/BOUNDED_MEDIA.md:63–67` narrates replacement of a 64 MiB ceiling;
   lead with current `LIVE_REPLAY_MAX_BYTES` limits/accounting.
   `engine/src/tests/compat.rs:64–65` and `engine/src/tests/lifecycle.rs:196–197`
   can describe asserted client/720p60 invariants without before/after wording.
4. **TV-web — current entry/sync guidance.**
   `AGENTS.md:12,34–38` narrates build/renderer transitions. Describe vendored
   synchronization and entry mapping directly; keep `lightning.html` compatibility.
   `src/tv-solid/EntryButton.tsx:110` cites removal by a ticket; explain the current
   labeling rule instead. This does not authorize removing the function.
5. **Android — covered by the local sweep.**
   Upstream `README.md:3–24` has branch-specific handoff and "now supports/wires"
   narration. Local ticket 01 covers current-state guidance and preserved evidence;
   it does not update the blocked server checkout.
6. **Smaller candidates.**
   Workspace `AGENTS.md:86–89` / `README.md:81–83`: turn historical deployment
   failures into served-asset verification requirements. Desktop
   `BUILDING.md:45–50`: state manual-only disposable smoke/network restrictions.
   Tauri plugin `README.md:12`: list current checks instead of workflow removal.
   Roku `roku/tests/focus_contract.py:47–48`: state sibling component scope instead
   of file relocation. Web `AGENTS.md:9–12`: name current design pins instead of
   "newer/now" prose. Design `BACKEND_V2.md:18–21`: state approved UI scope directly,
   preserving design-first adoption and authority. No actionable narration was
   found in `.github` during this audit.

## Instruction contradictions: prioritize before cosmetic wording

- **Video consumer workflow:** `video/AGENTS.md:26–29` and `video/README.md:59–61`
  prescribe a `file:../video` dependency and building `dist-js` first. TV-web uses
  vendored sources: `tv-web/tsconfig.json:26–27`, `tv-web/vite.config.ts:37`,
  `tv-web/package.json:9,19` and `tv-web/AGENTS.md:12`. Consumer guidance needs to
  describe immutable vendored synchronization.
- **Delivery policy:** video and tauri-plugin agent guides contain pending-approval
  restrictions below already-approved headers. Android's obsolete hosted
  app-review gate is addressed locally. Backend's reference to repository CI
  commands needs reconciliation with its local-check-only delivery policy.
- **Roku browsing versus playback:** `roku/roku/PLAYBACK.md:20–21` says raw catalog
  migration is pending, while `EpgScene.brs:31,55,136` and
  `HomeScene.brs:24,190–191` request v2 raw catalogs. Document browsing and playback
  separately; route adoption does not prove device/playback qualification.
- **Backend route retirement:** fetched `docs/CATALOGS_V2.md:4–5` says legacy
  routes coexist and clients have not cut over. Fetched `server/src/routes.rs:153`
  and `server/src/retired.rs:6–23,37–45` wire explicit retirement handlers.
  Describe route contracts separately from consumer/deployment qualification.

## Verification and limits

The audit combined broad first-party docs/comment screening with manual context
review. Generated/vendor trees, licenses and intentionally historical evidence
were not cleanup targets. Useful compatibility, stale-session, migration,
rollback, provenance, design-authority and qualification context should remain.

Parent independently rechecked all 13 branch/head/divergence snapshots, observed
the blocked untracked files and modified desktop submodule, and inspected the
core polling comment, gateway index explanation, video/TV-web dependency conflict
and fetched backend catalog/retirement evidence. The verification command exited
0. No runtime tests or deployment assertions follow from this audit.
