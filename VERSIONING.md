# Kotlin Multiplatform versioning

`com.getair:video` artifacts use stable Semantic Versioning. All KMP target
artifacts and root Gradle metadata for one release share the exact same version.

- `MAJOR` changes a public playback contract incompatibly.
- `MINOR` adds backward-compatible contracts or capabilities.
- `PATCH` fixes behavior without changing the supported public surface.

Stable releases use immutable `vMAJOR.MINOR.PATCH` GitHub tags and publish only
to `air-tv/video` GitHub Packages. Published versions are never overwritten.
The historical `@get-air/video` npm package is versioned independently in
[`get-air/video`](https://github.com/get-air/video); its legacy tags do not name
KMP Maven releases in this repository.
