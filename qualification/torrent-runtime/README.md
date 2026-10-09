# Owned shared-runtime media probe

Normal APKs contain no probe Activity or input. Use only an owned QA emulator;
this fixture neither signs in nor reads configured accounts.

Build with `:app:assembleDebug :app:assembleDebugAndroidTest
-PtorrentRuntimeMediaQa`. The separate package is
`org.viptv.app.runtimefixture`. Place a private Go Source JSON object at its
`no_backup/torrent-runtime-qa-source.json`, then run
`org.viptv.app.TorrentRuntimeMediaProbeTest` with its instrumentation runner.
Source JSON and local media endpoints must never appear in logs or commits.

The test opens the real shared worker, renews authority while preparing, attaches
Media3 to a TextureView, awaits the rendered-frame event, acknowledges it to the
runtime, awaits forward/backward seek completion and retires the owned worker.
It removes the source input and writes only numeric outcomes to
`no_backup/torrent-runtime-qa-result.json`. Retained cache data allows an explicit
warm/restart trial. The cache is 128 MiB; first-frame startup and seek deadlines
are bounded. This probe supplements the application control/lifecycle tests;
it does not exercise a live backend account or qualify physical devices.

The media probe waits for fresh TextureView updates after seek position/buffering
settle; an immediate position discontinuity is not decoded-seek evidence.

`TorrentRuntimeAuthorityMediaTest` accepts a private
`no_backup/torrent-runtime-authority-qa.json` containing an ephemeral backend
origin, bearer and stream ID. It exercises production control/core/coordinator,
app cache ownership, Go acquisition and Media3, and writes only numeric results.
For trusted local HTTPS, `-PfixtureCa=/path/to/public-ca.crt` is permitted only
with this isolated debug QA package. No fixture trust enters a normal APK.
`TorrentRuntimeWorkerIntegrationTest` additionally opens the real app cache with
its ownership marker and verifies Go uses a separate `pieces/` subdirectory.
