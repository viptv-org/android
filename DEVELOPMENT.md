# Windows-first Android / Android TV development

## Verified v2 setup — 2026-10-01

- Android main `c8f8ffa`, plus the local track-marker/subtitle fixes, is built and
  running on this Windows PC's TV emulator. Data/sign-in were not cleared.
- JDK 17 / SDK 36 local host-core, unit-test and three-ABI Android build flow
  passed: **206 unit tests, zero failures/errors/skips**. Two actual Compose
  rendering tests also passed on the TV emulator.
- The selected personal profile is preserved. Home, title details and the
  account's Cinemeta addon render. Choose source shows the expected empty state:
  Cinemeta supplies catalogs, not playable streams; a stream provider is still needed.
- Backend main `1b218e84`, with eight-digit pairing and preserved Stremio-preview
  work, is active on the VPS at loopback port **3000**, through the unchanged
  existing dev tunnel. Matching dashboard and TV-web assets are mounted.
- Public health, dashboard, pairing and TV-web routes passed; four public asset
  hashes match the reviewed build. Eight-digit pairing/PNG QR checks passed.
- Backup-first migration assigned the formerly unowned addon to the personal
  account, kept the other addon's owner unchanged, and encrypted both addons.
  Fifteen protected account/profile/history tables were preserved; SQLite checks
  passed. Durable encryption keys and separate backups remain owner-private.
- Public conversion-gateway activation, personal stream-provider configuration
  and Stremio import/apply remain **not completed**. Production is untouched.

## Where development runs

**This Windows PC is the Android development machine and TV emulator host.**
Edit and pull this local `android` checkout, build/test locally, and run the TV
app locally. The owner's VPS hosts the development backend and organization
workspace; SSH there only for backend work or explicitly requested remote
checks/builds. Do not start the TV emulator on the VPS by default.

Machine-specific settings are in this checkout's **git-ignored `.env`**:

- `VIPTV_DEV_HOST`, `VIPTV_DEV_USER`, `VIPTV_DEV_SSH_PORT`: the owner's correct
  SSH destination, matching the existing Windows SSH configuration.
- `VIPTV_DEV_REPO`: the organization workspace on that server.
- `VIPTV_DEV_ORIGIN`: the existing public HTTPS development backend.
- `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_NDK_HOME`: this PC's JDK 17 and SDK/NDK.
- `ANDROID_TV_AVD`, `ANDROID_TV_PORT`, `ANDROID_TV_SERIAL`, `TV_DNS_SERVERS`:
  this PC's dedicated TV emulator, port, and networking settings.

Agents: read this guide and load the private `.env` before choosing a host or
ADB target. Never copy its addresses, local paths, or credentials into Git.
The local parent folder is not the Linux workspace repository; do not assume
that its sibling repos or `update.sh` exist on Windows.

## Load the local tools

From this Android checkout in PowerShell:

```powershell
. .\scripts\windows-dev.ps1
java -version
node --version
cargo --version
cargo ndk --version
adb devices -l
```

The loader reads `.env` as data, prepends the installed local tools to PATH,
and sets `CARGO_TARGET_DIR` to this checkout's `vendor/core/target`. It changes
the current shell only, not machine-wide settings. Use JDK 17, Node 22+,
SDK Platform 36, Rust, cargo-ndk, and an Android NDK. The configured toolchain
already exists on this PC; it is not necessary to reinstall Android Studio.

The host Rust target is Windows MSVC; Android targets are
`aarch64-linux-android`, `armv7-linux-androideabi`, and
`x86_64-linux-android`. Native JVM tests need the real Windows
`vendor/core/target/debug/viptv_core.dll`, not the Linux `.so` from the VPS.

## Pull, test, and build locally

```powershell
git status --short --branch
git pull --ff-only
. .\scripts\windows-dev.ps1

node scripts/core-sync.mjs check
node scripts/design-sync.mjs check
bash scripts/prepare-core.sh host
.\gradlew.bat --no-daemon :testDebugUnitTest :app:testDebugUnitTest
bash scripts/prepare-core.sh android
.\gradlew.bat --no-daemon :app:assembleDebug
```

Run Gradle commands sequentially in the same checkout. Do not reset, clean,
stash, overwrite existing edits, or silently change branches to make a pull
or build work. Do not commit or push without the owner's request.

The normal APK is `app/build/outputs/apk/debug/app-debug.apk`. It contains
arm64-v8a, armeabi-v7a, and x86_64 native libraries and uses the committed
development signing key, so `install -r` updates preserve sign-in. Do not use
`-PfixtureCa` for normal backend use; no fixture CA should be in the APK.

## Run Android TV on this PC

Use the existing dedicated TV AVD recorded in local `.env`. Leave unrelated
AVDs alone. Always pass the explicit serial to ADB.

```powershell
. .\scripts\windows-dev.ps1
adb devices -l
emulator -list-avds

# Start only if the intended AVD is not already running.
Start-Process -FilePath "$env:ANDROID_HOME/emulator/emulator.exe" -ArgumentList @(
  '-avd', $env:ANDROID_TV_AVD, '-port', $env:ANDROID_TV_PORT,
  '-no-snapshot-load', '-no-boot-anim', '-gpu', 'swiftshader',
  '-memory', '4096', '-cores', '4', '-dns-server', $env:TV_DNS_SERVERS
)

# Wait until this returns 1 before installing:
adb -s $env:ANDROID_TV_SERIAL shell getprop sys.boot_completed
adb -s $env:ANDROID_TV_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $env:ANDROID_TV_SERIAL shell am start -W -n org.viptv.app/.MainActivity
adb -s $env:ANDROID_TV_SERIAL shell pidof org.viptv.app
```

The existing API 36 TV AVD is an x86 image with ARM32 native-bridge support;
the normal APK includes armeabi-v7a. Do not replace its image or wipe its data
just to make the ABI label match the APK's x86_64 slice. Preserve its sign-in.
For desktop interaction, its stopped-AVD configuration uses `hw.keyboard=yes`,
`hw.keyboard.lid=no`, `hw.screen=multi-touch`, and `hw.dPad=yes`, keeping TV mode.
See [qualification/README.md](qualification/README.md) for input qualification.

Changing server intentionally signs the device out. Do not pass
`preview-origin` to an already signed-in interactive emulator. Use the app's
Server/Change server setting only when the owner wants to change backend.
Never uninstall or use `pm clear` to repair networking. Save private screenshots
and accessibility captures only in ignored `qualification/artifacts`.
A working emulator UI does not qualify physical hardware, HDR, codecs, or PiP.

## SSH to the owner's VPS from Windows

The actual endpoint is saved **locally** in `.env`, not only on the VPS.
Use its configured host/user/port rather than guessing from a short nickname.

```powershell
. .\scripts\windows-dev.ps1
ssh -p $env:VIPTV_DEV_SSH_PORT "$($env:VIPTV_DEV_USER)@$($env:VIPTV_DEV_HOST)"
```

Inside that SSH session, the organization workspace is `~/code/viptv-org`.
Each subdirectory is an independent repository. `bash ./update.sh` safely
fast-forwards the workspace and its clones; inspect their local changes first.
The existing development backend runs as the user service `viptv-dev.service`,
bound to loopback port **3000**. The existing enabled `cloudflared.service`
serves `VIPTV_DEV_ORIGIN`; reuse that tunnel rather than opening a new one:

```sh
systemctl --user start viptv-dev.service
systemctl --user is-active viptv-dev.service
```

Starting that existing service does not rebuild backend code or deploy
production. Preserve its database, profiles, providers, and history. Do not
replace the owner's existing TLS/tunnel configuration. Public HTTPS checks
should send the app's User-Agent and correct Origin; Cloudflare can reject
Python's default User-Agent even when app requests work.

Remote APKs can be copied to ignored `qualification/artifacts` and installed
locally if explicitly needed. Remote builds are an optional fallback, not the
normal location of editing or the Android TV runtime. Verify the copied APK's
commit/hash and never overwrite an unrelated local artifact.

## Local backend editing and development rollout

The canonical sibling `../backend` and VPS backend checkouts now use the
reviewed v2 base `1b218e84`. All local numeric-pairing edits are preserved; the
three Stremio-preview files remain present and untracked against that base.
The old preview commit is retained on `backup/backend-v1-stremio-preview-20261001`.
No new commits were made. Preserve this work when pulling future changes.

Backend v2 passed locked all-target tests in isolated targets: 218 on Windows,
222 on Linux, plus 103 dashboard and 242 TV-web tests. Two inherited real-gateway
fixtures remain opt-in; no production/gateway playback claim follows from these
counts. Use a separate backend Cargo target instead of the core target set by
`scripts/windows-dev.ps1`, for example:

```powershell
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'qualification/artifacts/backend-target-windows'
cargo test --locked --manifest-path ../backend/server/Cargo.toml --all-targets
```

The reviewed candidate, migration tools, private backups and durable keyring are
retained under the VPS backend's ignored `artifacts/backend-v2-20261001` directory.
Do not delete that directory: the service currently executes its release binary,
mounts its frontend assets and loads its private keyring environment file.
Operational plans/evidence also exist locally under
`qualification/artifacts/backend-v2-evidence`.

Future upgrades must preserve the existing database, canonical HTTPS auth origin,
loopback bind and tunnel. Offline credential migrations require stopped database
readers/writers, fresh private backups/exports and explicit ownership maps.
Never print source credentials or keyring contents, and do not run destructive
retirement for an ordinary update. After committed encryption, blindly restarting
the old v1 binary is not a safe rollback; it cannot read the sealed credentials.
Never restore an old database over later account/history activity.

Human TV pairing codes use eight decimal digits, stored/displayed as strings so
leading zeroes survive. Secret device tokens remain separate. Pending legacy
codes retain their existing expiry; QR, account-bound approval, single use and
rate limits must remain covered by backend tests. Do not sign the owner's TV out
merely to inspect a new code format.
The eight-digit change was verified on the development service with 62 Windows
auth tests, 63 Linux auth tests and five browser/device HTTP-flow tests. Live
loopback/public code generation and QR responses passed; the TV remained signed in.
Desktop sign-in handoff rendered the full code. The existing narrow-screen
account page clips at a 390-pixel viewport; no mobile layout fix is claimed.


## Personal account, Stremio history, and providers

The owner chose the **development VPS** for this personal account; production
is separate. Use a personal account/profile, not the shared development/test
owner. Register on the development HTTPS dashboard, choose the password
privately in the browser, create a profile, and pair the local TV to that account.
Do not put personal passwords, provider URLs/keys, or Stremio exports in Git.
Keep existing TV sign-in until an intentional account switch is requested.

The current VPS backend has public registration enabled. The initial Stremio
import implementation lives in the backend/account dashboard; deployment is
qualified separately before activation. After activation, use **Account → Import
from Stremio** on the dev site, select an unrestricted destination profile, read
the preview counts, then explicitly confirm. No personal import was performed
by implementation or testing. See `backend/docs/STREMIO_IMPORT.md` on the VPS.
The first version supports saved IMDb titles, movie history/resume and exact
episode resume; bulk episode watched history, unsupported IDs and likes/loves
remain deferred. Existing favorites/newer history/manual corrections are retained,
and repeat imports cannot resurrect locally removed imported favorites. The
Windows checkout's ignored `.env` holds the saved Stremio credentials for later
manual use; they are not embedded in bundles or auto-filled by the site.
The v2 dashboard manages account-owned sources;
installation/configuration writes require a full account/browser session, not
paired-TV credentials. Configure personal providers on the dev dashboard while
signed into the personal account. A catalog-only addon does not provide streams.

TMDB/API keys belong in private backend configuration, never the APK or docs.
The supplied TMDB key was not configured; no TMDB integration is claimed.
Do not assume a variable alone enables a provider or switch branches silently.

## What to test next

1. On the Windows TV, browse Home/Discover and open a title's details.
2. On the dev dashboard, sign into the personal account and configure a legitimate
   stream addon/provider privately. Then reopen Choose source on the TV.
3. Exercise direct playback, seeking, audio/subtitles and foreground return with
   that source. Real-source/device behavior still needs its own evidence.
4. After importer activation, open Account → Import from Stremio on the dev
   dashboard. Verify the selected destination and read-only preview before
   choosing whether to confirm. Unsupported/ambiguous entries remain for review.

The separate playback-gateway candidate at `d34047b` is prepared and tested under
ignored qualification artifacts, but is **not publicly activated**. Sources
requiring relay/remux/transcode need a separately qualified HTTPS gateway, private
scoped integration key and media/renew/release checks. Keep backend port 3000 and
the existing tunnel unchanged for initial direct testing; do not expose bootstrap
key management. The corrected gateway handoff is under
`qualification/artifacts/gateway-v2-integration/.qualification/REPORT.md`.

## Shared code and delivery

Read `AGENTS.md`, `SPEC.md`, `DESIGN_REF`, and `CORE_REF` before behavior changes.
Shared rules belong in `core`, UX in `design`, Android adaptation in `android`.
Never edit `vendor/core` or generated design files. Regenerate/commit shared
changes in the owning repository, then sync the Android consumer and keep the
TV-web core pin aligned. Pulling sibling repos does not update Android's pins.

Retain local checks. Actions produce build artifacts on main pushes/manual
runs for Android, desktop, Roku, and TV-web only. No PR gates, automatic
releases, publishing, or deployments. Production delivery is separately authorized.
