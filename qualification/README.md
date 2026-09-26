# Native Android design qualification

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
