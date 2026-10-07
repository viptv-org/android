# Physical native Fire TV qualification

This procedure targets the privately configured Amazon AFTLAS01, Android 11/API 30,
armeabi-v7a. The device address and ADB executable come only from the ignored workspace
`.env` and `.local-device-testing.md`. The runner has no serial override, rejects
emulators and refuses a different model/API/ABI. It does not connect devices, configure
wireless debugging, change Tailscale or create an emulator.

Coordinate the device window and wait for the owned-pipeline artifact handoff from
ticket 12. A compiled APK, host test, selected audio track or advancing position is
not physical decoding, audible sound, physical remote input or sustained resource
evidence. Preserve PASS, FAIL and NOT-RUN separately. This procedure always leaves
native capability qualification off; public peers, blocked OS IO, other ABIs,
distribution review, HDR, DRM and PiP retain separate gates.

## Preparation and immutable inputs

Run only the runner's safety/evidence regressions; they contact no device:

```sh
python3 -m unittest discover -s qualification/native-torrent -p test_physical_fire_tv.py -v
```

Create one fresh private directory below `.scratch/native-torrent`, then initialize
its evidence. Keep all files private and ignored. The runner uses directory mode
0700 and file mode 0600, rejects symlink/hard-link output aliases, and records design,
core, native and owning repository revisions. Reusing a directory must retain the
same configured device fingerprint.

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY initialize
```

Use the sealed normal APK and probe APK from ticket 07, rather than fixture build
output that can overwrite the ordinary APK filename. Normal source inputs are
Android `e1da3d1ea48c4820f04bb616bc362d3663acd38a`; gateway
`764e518b66e024ac18d6dd7f14e489b696b8301e`, core
`91c5a4f53845ce0579f47caf1d57a74446710247` and design
`83d338b6ffc1fc5e7f14ad4059f6159b8ee84509`. Later verifier/docs commits do not
change those APK inputs. Their prior build/policy checks are recorded in the local
07 ticket and private `ticket07-evidence.json`; do not count or repeat them as a
physical device run.

| APK | SHA-256 |
| --- | --- |
| `.scratch/native-torrent/artifacts/normal-app-e1da3d1.apk` | `6b0e7be3a3d95bc0eed57a38e222da4c32bfe604396286d2c52ec52ee65c15ab` |
| `.scratch/native-torrent/artifacts/normal-app-probe-e1da3d1.apk` | `2c7ec77f63da9d10cc80ccbdd528a6adbae953a6bcb41505887dedc674fabc70` |

Save ticket 12's exact fixture APK/test APK hashes and immutable artifact/backend/
Android revisions inside the private run directory. Their generated facade uses
the same gateway source with the additional test-only owned network feature.
That constructor, Activity, CA and configuration must remain absent from normal
artifacts. The fixture wire still carries `public_dht_tcp_v1`; only the test engine
injection receives explicitly reversed literal-loopback peers with DHT disabled.

The ticket 12 final handoff records Android runtime `9f07a3b`, verifier `7394c14`,
runbook `460741a`, backend `ef7f2a7`, gateway `764e518b` and core `91c5a4f`.
Its sealed fixture APK SHA-256 is
`9db7109eb1e1dff47fd10e55706cdb9087323b9eafcd19b7a8e4d8af710abec0`;
the instrumentation APK SHA-256 is
`b02e7f0cc8a452e65668a4a968d408b68110dfce7d2d57b465e4393235fa3dfb`.
These artifacts preserve build evidence; their finite backend session has stopped.
Create a fresh owned directory, backend session, matching configuration and matching
APK/test APK using `README.md` before executing a selector. Record those newly
built APK hashes instead of using the stopped session's artifacts for a runtime run.

At the final ticket 13 handoff, the configured TV's last observed connection was
offline and no availability update had arrived. The runner's 13 host safety/evidence
regressions and private preparation passed. No ticket 13 device query, installation,
instrumentation, playback or state change occurred. All physical cases remain
NOT-RUN, including audible sound and the owner's final signed-in state. Ticket 12's
real Linux authorized Core/FFI/owned-TCP run measured 119 ms readiness and 4 ms common
local join; those measurements supply host pipeline evidence only. Native capability
qualification remains off.

## Normal APK identity, JNI and runtime policy

After the owner makes the existing connection available, follow the private
connection notes without creating a new debugging configuration. The runner only
queries the configured existing serial. An unavailable device stops the action
with private diagnostics and supplies no loading evidence.

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY device
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY install --package org.viptv.app --apk .scratch/native-torrent/artifacts/normal-app-e1da3d1.apk --sha256 6b0e7be3a3d95bc0eed57a38e222da4c32bfe604396286d2c52ec52ee65c15ab
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY install --package org.viptv.app.test --apk .scratch/native-torrent/artifacts/normal-app-probe-e1da3d1.apk --sha256 2c7ec77f63da9d10cc80ccbdd528a6adbae953a6bcb41505887dedc674fabc70
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY normal-probe
```

Every installation checks the recorded file hash and APK package first, then uses
only `install -r`. Android must accept the existing signing identity. A mismatch
must fail without uninstall, `pm clear`, resetting, replacing the backend origin,
reading the normal package's stored preferences, or changing credentials/accounts.
Both actual `NativeTorrentArtifactLoadTest` cases must execute and pass on ARMv7;
a skipped test or a success summary without completed test statuses fails the
loading verdict. This proves actual generated gateway/core/JNA initialization and
Android runtime cleartext policy on this ABI, rather than only ELF structure.

## Owned fixture pipeline and bounded observation

Start ticket 12's finite loopback HTTPS/backend/seeder environment using its own
runbook and private synthetic configuration. Install its separately stamped APKs
with the exact handed-off hashes:

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY install --package org.viptv.app.nativefixture --apk PRIVATE_FIXTURE_APK --sha256 FIXTURE_APK_SHA256
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY install --package org.viptv.app.nativefixture.test --apk PRIVATE_FIXTURE_TEST_APK --sha256 FIXTURE_TEST_APK_SHA256
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY fixture
```

The runner checks both existing reverse mappings before changing either one. It
refuses conflicts, creates only ports 19445/19446 that were absent and remembers
only the mappings it owns. The full `OwnedNativePipelineTest` result must contain
completed cases with no failures or unplanned skips. Only the separately requested
`manualOwnedNativeObservation` may skip during the regular class run; its physical
sound/remote verdicts stay NOT-RUN. A class result never makes every NT acceptance
ID pass automatically.

Collect the five safe fixture JSON files through `run-as` for the isolated package
only: `owned-native-evidence.json`, `owned-expiry-evidence.json`,
`owned-ordinary-evidence.json`, `owned-controller-evidence.json` and
`owned-manual-evidence.json` under `no_backup`. Raw instrument output and fixture
configuration remain private. Missing files stay missing facts; they do not become
passing cases. Preserve failed native cache trees for diagnosis.

For an actual person at the TV, prepare a **fresh finite backend, configuration and
matching private fixture APK** using ticket 12's runbook, then initialize a new
physical run directory and install the fresh matching fixture APK/test APK. The
regular class intentionally disables its synthetic producer and signs out in its
final scope test; MainActivity may rotate synthetic session credentials during
manual playback. Reusing either backend/configuration for another selector would
test an invalid session rather than owned playback. The runner refuses to execute
a second fixture selector in the same run directory, including after a failure.
Preserve and reference earlier normal JNI/device evidence as an earlier observation;
do not silently relabel it as a new execution.

Run the dedicated 180–300 second observation selector in one terminal. It opens the actual MainActivity, real controller and
normal player/Sources UI in the isolated package. The bounded test may rewind the
same owned title at its end; user Back and pause remain authoritative.

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY fixture --selector org.viptv.app.OwnedNativePipelineTest#manualOwnedNativeObservation --hold-seconds 300
```

In a second terminal, capture only while the fixture package is foreground:

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY capture native-playing
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY resources --seconds 180 --interval 10
```

Inspect actual decoded frames and subtitle cues, and ask the person at the TV to
confirm sound while playback is active. They must identify what they heard on the
physical output. Selected audio, sample metadata, decoder events, waveform bytes,
position or a screenshot cannot supply that confirmation. Do not change the owner's
volume/audio settings to create an assumed verdict.

Use the physical paired remote for physical input/focus acceptance: reveal controls,
pause/resume, navigate Audio/Subtitles, confirm Back/selection returns to the invoking
control, reopen the chosen track, and leave the source/episode context intact.
The runner's optional `key KEYCODE_DPAD_RIGHT` and other bounded keys send input only
while the fixture is foreground; those injections are useful automation evidence
and never certify the actual paired remote. Screenshots/XML must show the relevant
focused control and rendered cue. A pointer-driven route supplies no remote focus
proof.

Resource samples retain elapsed seconds, app PSS KiB, isolated no-backup storage
KiB, owned-process thread count and open file-descriptor count. They read only the
isolated process through its own debug identity. Counts do not infer continuous decoding, audible sound, thread/socket completion,
budget release or sustained public-peer behavior. Record actual observation duration,
title replays, pauses, initial/final storage, retained failed-settlement trees and
instrumented worker/HTTP joins. Compare before/after measurements rather than only
the final counter.

## Individual verdicts and cleanup

Record each measured outcome against its actual private evidence file. Verify the
specific assertions in ticket 12's output, including exact selected index/bytes,
real pending metadata/pieces before cancellation, one total startup deadline,
joined cancellation/replacement/expiry/revocation, scope cleanup and failed
settlement ownership. Keep two-second cooperative join evidence distinct from
unconditional blocked OS IO qualification.

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY record --case seek_missing_pieces --verdict PASS --basis measured --evidence owned-native-evidence.json
```

Use PASS only when that file actually proves this individual case. Use FAIL for a
measured violation and NOT-RUN for unavailable/unfinished work. Preserve startup
cancel, replacement, expiry/revocation, background/resume, profile/sign-out cleanup,
exact decoded file, tracks/cues, pause/resume and sustained owned-resource verdicts
separately. No generic fixture success substitutes for a missing specific fact.

Save the actual authorized person's answer in a private confirmation file before
recording human sound, physical remote/focus or the normal app's final unchanged
signed-in state:

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY record --case audible_audio --verdict PASS --basis human --observer human --evidence human-audio-confirmation.json
```

Never invent that answer or substitute a host/emulator inference. An unavailable
person or uncertain sound is NOT-RUN; a confirmed absent/incorrect output is FAIL.

Wait for instrumentation to complete and record its controller/engine cleanup.
Then stop only the isolated fixture package and reverse mappings created by this
run. Stopping the process ensures playback ends; it does not prove successful
joined settlement. Preserve its private cache rather than deleting it.

```sh
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY stop
python3 qualification/native-torrent/physical_fire_tv.py --directory PRIVATE_RUN_DIRECTORY report
```

Stop ticket 12's owned finite server processes through their own owner/runner.
Retain pre-existing reverse mappings. Have the owner confirm the normal app still
uses the original server/profile, remains signed in and retains history without
re-entering credentials or changing an account. If that cannot be observed, leave
the final normal-state case NOT-RUN. If no device run occurred, record no installation
or state changes and keep final device-state observations NOT-RUN. No captures,
addresses, tokens, configuration or raw device logs belong in tracked documents.
