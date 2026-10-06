# Development v2 setup and qualification — 2026-10-01

This record preserves the dated setup report and associated rollout/provider
notes formerly in DEVELOPMENT.md. The setup report was dated **2026-10-01**;
the associated notes were not individually dated and were archived on
**2026-10-06**. All statuses and counts describe the reported work at that time,
not the current service, checkout or account. No checks were rerun as part of
this documentation cleanup. See [DEVELOPMENT.md](../../DEVELOPMENT.md) for current
procedures and migration/rollback precautions.

## Verified setup report (2026-10-01)

- Android main `c8f8ffa`, plus local track-marker/subtitle fixes, was built and
  running on the Windows PC's TV emulator. Data/sign-in were not cleared.
- JDK 17 / SDK 36 local host-core, unit-test and three-ABI Android build flow
  passed: **206 unit tests, zero failures/errors/skips**. Two actual Compose
  rendering tests also passed on the TV emulator.
- The selected personal profile was preserved. Home, title details and the
  account's Cinemeta addon rendered. Choose source showed the expected empty
  state: Cinemeta supplied catalogs, not playable streams; a stream provider
  was still needed.
- Backend main `1b218e84`, with eight-digit pairing and preserved Stremio-preview
  work, was active on the VPS at loopback port **3000**, through the unchanged
  development tunnel. Matching dashboard and TV-web assets were mounted.
- Public health, dashboard, pairing and TV-web routes passed; four public asset
  hashes matched the reviewed build. Eight-digit pairing/PNG QR checks passed.
- Backup-first migration assigned the formerly unowned addon to the personal
  account, kept the other addon's owner unchanged and encrypted both addons.
  Fifteen protected account/profile/history tables were preserved; SQLite
  checks passed. Durable encryption keys and separate backups remained
  owner-private.
- Public conversion-gateway activation, personal stream-provider configuration
  and Stremio import/apply were **not completed**. Production was untouched.

## Associated backend rollout and pairing notes

The canonical sibling `../backend` and VPS backend checkouts used reviewed v2
base `1b218e84`. Local numeric-pairing edits were preserved; the three
Stremio-preview files remained present and untracked against that base. The old
preview commit was retained on `backup/backend-v1-stremio-preview-20261001`.
No new commits were made by that rollout work.

Backend v2 passed locked all-target tests in isolated targets: 218 on Windows,
222 on Linux, plus 103 dashboard and 242 TV-web tests. Two inherited real-gateway
fixtures remained opt-in; no production/gateway playback claim followed from
those counts.

The reviewed candidate, migration tools, private backups and durable keyring
were retained under the VPS backend's ignored `artifacts/backend-v2-20261001`
directory. At the time of the report, the service executed its release binary,
mounted its frontend assets and loaded its private keyring environment file.
Operational plans/evidence also existed locally under
`qualification/artifacts/backend-v2-evidence`. These are private artifact
locations, not a guarantee of current availability or authorization to remove
them. Verify service dependencies before cleanup.

The eight-digit pairing change was verified on the development service with
62 Windows auth tests, 63 Linux auth tests and five browser/device HTTP-flow
tests. Live loopback/public code generation and QR responses passed; the TV
remained signed in. Desktop sign-in handoff rendered the full code. The existing
narrow-screen account page clipped at a 390-pixel viewport; no mobile layout fix
was claimed.

## Associated personal-account, import and provider notes

The owner selected the development VPS, not production, for the personal
account. Public registration was reported enabled. The initial Stremio importer
lived in the backend/account dashboard, with deployment to be qualified before
activation. No personal import was performed by implementation or testing.
The first version supported saved IMDb titles, movie history/resume and exact
episode resume; bulk episode watched history, unsupported IDs and likes/loves
were deferred. Existing favorites/newer history/manual corrections were
retained, and repeat imports could not resurrect locally removed imported
favorites. Version-specific guidance was in `backend/docs/STREMIO_IMPORT.md`.
The Windows checkout's ignored `.env` held saved Stremio credentials for later
manual use; they were not embedded in bundles or auto-filled by the site.

The v2 dashboard managed account-owned sources. Installation/configuration
writes required a full account/browser session, not paired-TV credentials.
Catalog-only addons did not provide streams. The supplied TMDB key was not
configured; no TMDB integration was claimed.

The playback-gateway candidate at `d34047b` was prepared and tested under ignored
qualification artifacts but was **not publicly activated**. Relay/remux/transcode
sources required separately qualified HTTPS gateway integration, a private
scoped integration key and media/renew/release checks. The handoff required
backend port 3000 and the existing tunnel to remain unchanged for initial direct
testing and warned against exposing bootstrap key management. The corrected
handoff was recorded at
`qualification/artifacts/gateway-v2-integration/.qualification/REPORT.md`.

At the time, follow-up testing called for Home/Discover/title-details browsing,
private configuration of a legitimate personal stream provider, real-source
playback/seek/audio/subtitle/foreground-return evidence, and optional Stremio
preview/confirmation after importer activation. These were pending steps, not
proof that the corresponding work is still pending now.
