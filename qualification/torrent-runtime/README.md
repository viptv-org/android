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
