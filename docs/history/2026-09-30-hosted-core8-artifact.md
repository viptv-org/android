# Hosted Core8 handoff artifact — 2026-09-30

This record preserves qualification evidence previously in README.md. It describes
one build at the revisions below, not the current checkout, installed app, or
backend. The evidence has not been rerun for this documentation cleanup.

The manual build was dispatched once on `refactor/android-backend-cutover`.
[Run 36691357270](https://github.com/viptv-org/android/actions/runs/36691357270)
and APK job `109809085239` succeeded at exact source
`75bbacffe47eb96d7c93993e04b6a7c8f4ec722b`. Host preparation, library/app unit-test
tasks, all three Android release native ABIs, normal debug APK assembly and lint
passed. Lint retained 74 warnings and three baseline-filtered errors. The workflow
did not upload unit-test XML; its task success was separate from the prior local
158-test count recorded in [TESTING.md](../../TESTING.md).

Downloaded `viptv-android-phone-tv-debug-75bbacff.apk` SHA256:
`9127959d0aa0d5db1a4bcbabaf3154c6b8c472f1ed236c64b84b970d88109d44`.
It matched the artifact SHA256SUMS; build.json recorded Core
`8ae9f81bb753aaf2de53af5b594ead29845e1a8a`, the design pin at build time and stable
development signing. APK archive/signature checks passed (one signer, v2 scheme).
Manifest identity was `org.viptv.app`, version `0.1.0`, min SDK24/target SDK36.
Packaged arm64-v8a/armeabi-v7a/x86_64 libraries had the correct ELF architectures,
active Core/SmartCast bridges and no metadata exports for the eight retired
provider functions. Fixture CA was absent; packaged network security XML trusted
system certificates only. An undated local acceptance note in the same README
also recorded NDK `28.2.13676358`; that note was archived on 2026-10-06 and does
not establish a separate build date.

The APK and authoritative run/job evidence were retained in the ignored local
directory `qualification/artifacts/hosted-75bbacf-B9Jxt2`. Availability of those
private artifacts is not guaranteed by this tracked record. Nothing was installed
or deployed; no emulator/physical media, codec, remote or native playback
qualification was implied. Category UI integration and existing hardware gates
were open at the time. The evidence-only work left the controller/UI/frozen wire
and the owner's original Android checkout untouched.

See [README.md](../../README.md) for current build guidance and qualification
limits; this record is not a delivery or deployment authorization.
