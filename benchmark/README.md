# Backend benchmarks

Benchmarks consume `corpus/cases.json` and write ignored machine-local reports.
They establish backend facts on the current machine; they do not generalize to
other operating systems or devices.

```bash
./benchmark/run-mpv-linux.sh
```

The MPV run is the first Linux engine baseline for the planned mediamp-derived
adapter. Wrapper integration, state mapping, track enumeration, and rendering
still require separate acceptance tests through the Air `VideoPlayer` API.

Live-player acceptance uses `corpus/live_hls_server.py`, not an application
downloader or segment queue. The reproducible server can delay a selected
segment by two seconds, add deterministic jitter, close one request, return one
HTTP error, advance a sliding playlist, and publish a discontinuity marker.
See `corpus/README.md` for desktop/Android invocation and the distinction
between measured live offset, buffered-ahead media, and rebuffer count.
