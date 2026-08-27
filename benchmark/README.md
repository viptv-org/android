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
