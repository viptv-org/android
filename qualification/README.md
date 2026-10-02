# Native Android design qualification

## Vizio remote fixture

After starting the HTTPS fixture, run `node qualification/smartcast-fixture.mjs`.
In the phone app use Settings → Watch on TV → Set up a TV → Enter IP address:
`10.0.2.2`. The synthetic PIN is `1234`; another PIN exercises retry. The fixture
listens on loopback 7345 and records only synthetic key commands. Its HTTPS
`/__control` endpoint accepts `{"offline":true}` and `{"paired":false}` to test
network and credential recovery; GET returns recorded commands and pairing
start/cancel counters. `{"keyDelay":3000}` delays key responses; set it back to
zero after testing. Overlapping pairing requests return BUSY. Never use this
fixture's token on a device.

For the remote regression check, use the dedicated `viptv-native-qa` emulator
(5574), with the fixture-trusting debug APK and both HTTPS fixture servers.
Connect its virtual Wi-Fi with
`adb -s emulator-5574 shell cmd wifi connect-network AndroidWifi open` if needed.
Start unpaired on **Choose your TV**, then run
`python3 qualification/check-remote.py`. It verifies discovery of the synthetic
TV, background PIN retention, cancellation/New PIN, and ordered remote keys
without disabling the launch button. Background return focuses the existing
Android task, rather than starting a duplicate Activity. This tests the emulator's
virtual Wi-Fi probe path, not physical-LAN multicast or a physical TV.

These scripts exercise the native Compose/Media3 app over local HTTPS using the same content fixtures as TV-web. They do not connect to a real account or upstream stream. Node 22+ and sibling `tv-web` and `design` checkouts are required. The native adapter imports `tv-web/tests/preview/backend.ts`; changes to that fixture contract must be checked here too.

## Dedicated emulators

Use fresh AVDs for this work. The recorded API 36 AVDs are `viptv-design-phone` (Google APIs x86_64, Pixel 7) on port 5570 and `viptv-design-tv` (Android TV x86_64, 1080p) on port 5572. `drive.py` deliberately accepts only these serials and refuses to send input when VIPTV is not foreground. Never use or stop an unrelated emulator. Run one at a time when memory is constrained.

```sh
$ANDROID_HOME/emulator/emulator -avd viptv-design-phone -port 5570 -no-snapshot -no-audio -memory 1536 -cores 2
# TV: replace the AVD with viptv-design-tv and port with 5572.
adb -s emulator-5570 shell wm size 780x1688
adb -s emulator-5570 shell wm density 320
```

## Desktop keyboard, mouse and remote input

The `tv_1080p` device preset can create an AVD with `hw.keyboard=no` and
`hw.screen=no-touch`. That configuration rejects computer-keyboard input and
mouse clicks even though `adb shell input` can still move the app's focus.
For the dedicated interactive `viptv-design-tv` AVD, stop that emulator and set
these entries in `~/.android/avd/viptv-design-tv.avd/config.ini`, then cold boot:

```ini
hw.keyboard=yes
hw.keyboard.lid=no
hw.screen=multi-touch
hw.dPad=yes
```

Keep the television system image/UI mode. Do not edit the generated
`hardware-qemu.ini`, wipe the AVD, or change an unrelated device. This allows
mouse exploration while retaining the TV layout and remote navigation.

Acceptance must include actual input through the emulator window: arrows and
Enter, a mouse click on a visible control, and the Extended Controls D-pad.
ADB-injected keys are useful for repeatable app tests but do not qualify host
input forwarding. Check that sign-in survives the configuration restart.

## HTTPS fixtures and APK

```sh
bash qualification/start-fixture.sh
# In a second terminal:
ANDROID_FIXTURE_PORT=9444 bash qualification/start-fixture.sh --tv
./gradlew --no-daemon :app:assembleDebug -PfixtureCa=qualification/fixtures/tls/server.crt
adb -s emulator-5570 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5570 shell am start -n org.viptv.app/.MainActivity --es preview-origin https://10.0.2.2:9443
```

`start-fixture.sh` creates a seven-day local certificate under ignored `qualification/fixtures/tls`. Never commit its private key. The Gradle property embeds only the public certificate, and only into the debug resource overlay. Omit `fixtureCa` for normal APK builds; verify `res/raw/viptv_fixture_ca.pem` is absent before sharing a normal APK. Release ignores `preview-origin`.

Flags: `--tv`, `--pairing` (wait for approval), `--parent-pin`, `--many-profiles`, `--empty` (no catalogs/queue), and `--search-failure`. The fixture-only PIN can be any four digits. Approve pairing with a JSON POST `{ "approved": true }` to loopback `/__control`; `{ "delayMetadata": 1800 }` introduces slow optional metadata. `/__requests` records only safe paths, media IDs and timestamps. The wrapper translates browser fixture URLs, adds explicit fixture avatars, provides the native favorites page and shifts guide schedules to the present.

## Input and private inspection

For Copy stream URL checks, POST `{"copyUrl":true}` to the HTTPS fixture's
`/__control`. Source preparation then returns a synthetic provider URL unique
to the selected stream (never real credentials). `{"failPlayback":true}` tests
safe failure/retry; `{"delayPlayback":10000}` tests closing/backgrounding before
resolution. Reset both afterward. Check POST `/api/playback` is followed by
DELETE for the returned lease, with no media request. Copy source one, then
cancel a delayed copy of source two and paste into an empty app search field:
the clipboard must still contain source one's exact URL. Do not perform this
inspection against production links, or commit clipboard/screenshot captures.

```sh
python3 qualification/drive.py snapshot home
python3 qualification/drive.py tap Discover
python3 qualification/drive.py texts
python3 qualification/drive.py --serial emulator-5572 key KEYCODE_DPAD_RIGHT 9
python3 qualification/drive.py --serial emulator-5572 snapshot episode-ten
```

Snapshots include PNG and accessibility XML under ignored `qualification/artifacts/<serial>`. Inspect the actual image, focus bounds, text and return path. Pointer taps on TV switch Android into touch mode; use only D-pad keys for focus acceptance. Do not infer focus success from a pointer-driven route.

The media fixture is a 12-second local H.264/AAC HLS test pattern. A decoded frame proves native surface/codec integration for that fixture; server-reported alternate tracks and live metadata are synthetic. It does not qualify real live sources, HDR, DRM, subtitles rendering, hardware decoders, managed remux/transcode or store delivery. Record those separately in `TESTING.md`.

When done, stop playback and terminate only the owned fixture processes and test emulator serials. Return any changed font scale to 1.0. No screenshots or secrets belong in Git.

When an emulator reports unknown-host errors while the host resolves the same public API, start it with explicit reachable DNS servers (`-dns-server`, using the host network's configured resolvers). Preserve its AVD data and origin. The API 36 phone/TV acceptance restart retained their real-server sessions; do not clear credentials to repair DNS.


## Player and progressive-search audit

Headless acceptance may use `viptv-native-qa` (5574) and `viptv-native-tv-qa`
(5576), separately from the signed-in interactive phone/TV. `drive.py` accepts
these serials too. Never change the interactive emulators to a fixture origin.
An optional `ANDROID_FIXTURE_MEDIA` points to a locally generated MP4;
`ANDROID_FIXTURE_DURATION` supplies its actual duration for the fixture envelope.
The fixture serves byte ranges and native playback still decodes through Media3.
No media bytes are packaged in the app. `POST /__control` with
`{"delaySearchMovies":9000}` delays only movie search catalogs; reset it to zero
after checking late-arrival focus. The fixture's continuation advances its
reference episode IDs so countdown expiry and Play now can be checked separately.

### Native multi-track TV return check

Generate a four-minute local MP4 with two actual AAC audio tracks and English/
Spanish `mov_text` subtitles (FFmpeg is required):

```sh
bash qualification/make-track-media.sh
ANDROID_FIXTURE_MEDIA="$PWD/qualification/fixtures/track-focus.mp4" \
  ANDROID_FIXTURE_DURATION=240 bash qualification/start-fixture.sh --tv
```

For an isolated worktree, set `ANDROID_FIXTURE_TV_WEB` to the sibling shared
TV-web checkout. The adapter accepts its legacy flat playback preview response
and current nested delivery response, supplies a v2 lease and renews the same
delivery during native heartbeat requests. Verify the actual player shows
`4:00`; a short HLS clip cannot establish this acceptance.

On the dedicated TV emulator 5576, install the fixture-trusting debug APK,
enter the local preview origin and start playback through the app. Close any
open panel, then run `python3 qualification/check-player-tracks.py`. It pauses
the loaded native media, resets subtitles to Off through the remote, verifies
Back and selection return by immediately reopening Subtitles without moving
focus, checks the actual `en · Current` row and resumes to a rendered English
cue. Private screenshots/XML are written under ignored artifacts for visual
inspection of the highlighted Subtitles control. This is actual Media3 direct
track acceptance; server-managed track replacement and physical TV remain
separate checks. Pointer setup is allowed; the acceptance sequence uses only
remote/media keys.

With the same four-minute native media loaded and no panel open, run
`python3 qualification/check-player-audio.py` for alternate AAC selection.
It verifies native en/es tracks, Back and selection return to Audio, and
immediate reopening with es · Current. It resumes playback at completion;
use the explicit Exit control afterward. This qualifies direct Media3 audio
on the owned emulator, not server-managed replacement or physical audio.

For source-discovery qualification, the HTTPS fixture control endpoint accepts
`{"sourceDelay":30000,"sourceError":true}`. Delay is bounded to 0–30000 ms
per newly created discovery job; healthy rows arrive immediately while done
remains false. Source error adds a named Failed provider with no playable rows
beside the 12 healthy rows. Check Still checking sources, the provider's safe
error view, All providers recovery and native Back to the invoking source
control. Reset sourceDelay to zero and sourceError to false after checking.

`GuideProgrammeFocusReturnTest` uses the actual Guide composition/controller,
one initial public focus setup and remote/native Back. Its synthetic EPG uses
the current clock so the future cell stays in the two-hour window. This is a
focused programme-details return regression; the broader opt-in chain below
extends functional acceptance, while visual parity remains separate.

For the full Guide functional chain, start the local TV HTTPS fixture on port
9444, install its explicitly fixture-trusting debug APK on the owned emulator
5576, and retain/select a synthetic paired profile. Build/install the app test
APK and run only `org.viptv.app.GuideRemoteFixtureTest` with instrumentation
argument `-e viptvGuideFixture true`. The class skips by default, asserts actual
television mode, and never connects outside the isolated fixture origin. It
uses one initial focus setup; News/Search/CNBC/future-details/native-Back are
then real controller/HTTP and ordinary remote/native-input actions. It checks
the exact restored programme node/bounds and zero playback admissions. This
qualifies that public composition/remote chain, not the app rail, actual gateway
delivery, pixel parity or physical input. Normal APKs must omit fixture trust.

# Reliability follow-up checks

On the paired remote of dedicated emulator 5574, run
`python3 qualification/check-reconnect.py` with the synthetic SmartCast fixture.
It verifies delayed silent reconnect, no re-pairing, bounded offline failure and
recovery. `authDelay` (0–3000 ms) and `authChecks` on its `/__control` support the
check. Power/Mute must record code sets/codes 11/2 and 5/4. The app HTTPS fixture
accepts `delayIdentity` (0–10000 ms) to inspect branded session restoration; reset
it to zero after inspection. Normal shared APKs must omit fixture trust.
