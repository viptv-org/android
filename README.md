# Air Video KMP

Backend-neutral Kotlin Multiplatform playback contracts and platform adapters
for Air across Android, JVM desktop, Apple, Windows, Linux, JavaScript, and
Wasm. Application controls, focus, in-app picture-in-picture layout,
fullscreen layout, and playback policy remain outside this library.

The historical TypeScript/React implementation now lives only in
[`get-air/video`](https://github.com/get-air/video). Its observable behavior
and fixtures remain useful porting references, but this repository contains no
npm package source and can never publish `@get-air/video`.

## Current backends

- Android/Android TV: Media3-backed playback with native live-policy mapping,
  external subtitles, track selection, request headers, and runtime capability
  reporting.
- Apple: AVFoundation playback behind an app-owned `AVPlayerLayer`.
- JVM/Linux: internal MPV conformance and embedding harnesses; production
  desktop surface work lives in the `air-tv/mediamp` fork.
- Browser/Wasm: an `HTMLVideoElement` adapter with runtime codec/container
  capability reporting and external WebVTT support.

All public state is session-scoped and platform-neutral. Playback sources,
headers, cookies, licenses, local paths, and credential-bearing URLs must never
appear in logs, errors, analytics, or `toString()` output.

## Build and test

JDK 17 is required. Use the checked-in Gradle wrapper:

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest testReleaseUnitTest --max-workers=2
CHROME_BIN=/path/to/chrome ./gradlew wasmJsBrowserTest --max-workers=2
```

Native, Apple, Android-device, HDR, PiP, power, and codec claims require their
own host or physical-device gates. Engine and surface acceptance fixtures live
under [`corpus/`](corpus/), while measured budgets and backend decisions live
under [`docs/`](docs/).

## Publishing

Kotlin Multiplatform artifacts use the `com.getair:video` coordinate family and
publish only from explicit stable releases in `air-tv/video` to:

```text
https://maven.pkg.github.com/air-tv/video
```

GitHub Packages requires authenticated consumption. npm publication is not
configured here; the `@get-air/video` scope and release ownership remain in the
separate historical TypeScript repository linked above.

The existing `v0.1.x` and `v0.2.x` tags/releases belong to that legacy npm
history and are not KMP Maven releases. A KMP version becomes available only
after a future explicit stable release from this repository completes every
host verification and publication job.

Consumers configure the authenticated repository outside the project source:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/air-tv/video")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
```

Local consumers use a classic personal access token with `read:packages`.
GitHub Actions consumers grant `packages: read` and use the workflow
`GITHUB_TOKEN` after the package grants that repository access. Never commit a
package token or place one in repository `gradle.properties`.

## License

Air Video KMP is available under either the Apache License 2.0 or the MIT
License, at your option. Both license texts are included in published archives.
