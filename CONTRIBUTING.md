# Contributing to Air Video KMP

Use JDK 17 and the checked-in Gradle wrapper. Keep changes inside the
backend-neutral Kotlin player boundary; UI controls and application playback
policy belong in consuming applications.

Start with focused tests, then run the portable suite:

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest --max-workers=2
```

Run `testReleaseUnitTest` for Android mapping changes and
`wasmJsBrowserTest` in a real browser for DOM behavior. Native/Apple/Windows
changes require their owning host gates, and playback claims require the corpus
and physical-device evidence described in `AGENTS.md`.

The historical TypeScript package is maintained at
[`get-air/video`](https://github.com/get-air/video). Do not reintroduce its npm
source, tests, manifests, locks, or release tooling into this repository.
