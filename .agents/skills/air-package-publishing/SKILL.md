---
name: air-package-publishing
description: >-
  Documents the legacy @get-air/video release boundary while this repository
  publishes only Kotlin Multiplatform Maven artifacts.
version: 1.1.0
---

# Publishing boundary for `@get-air/video`

Use this skill for version preparation, release workflow changes, GitHub
Releases, npm publication, provenance verification, or credential cleanup.

## Release boundary

- The restored `get-air/video` repository independently publishes `@get-air/video`.
- This `air-tv/video` repository must never publish the npm package; its release workflow is KMP Maven-only.
- The npm gates below are historical guidance for work performed in `get-air/video`. Stop if this skill is invoked from an `air-tv/video` checkout; never recreate an npm publishing workflow here.
- Follow `VERSIONING.md`. Do not bump it merely to match a platform adapter.
- Publish core before an adapter that needs a new core API or compatibility
  floor.
- Never release from an unexplained dirty tree or a stale tarball.

## Required gates

1. Update `package.json`, both root version views in `package-lock.json`, and
   the exact `## X.Y.Z` changelog entry.
2. Run `npm run check:release`, type/Effect diagnostics, tests, build, pack
   inspection, the Air framework example, audits, and boundary scans.
3. Run the real 3840x2160 MediaBunny qualification with the repository's FPS,
   drop, range-request, geometry, and zero-copy thresholds.
4. Exercise the affected workflow locally with `act`.
5. Push the exact clean commit and wait for hosted CI on that commit.
6. Create a stable `vX.Y.Z` GitHub Release only after those gates pass.

The release workflow must validate the tag, rerun release gates, publish with
npm OIDC/provenance, and upload UHD evidence. Never overwrite or retry an
ambiguous immutable version without first querying npm.

## Authentication and verification

- Steady-state publication uses GitHub Actions OIDC. Do not add `NPM_TOKEN`,
  `NODE_AUTH_TOKEN`, a local `.env`, or a persistent npm login.
- Verify `latest`, version, `gitHead`, integrity, shasum, attestations, and OIDC
  publisher metadata after registry propagation.
- Install the exact version in a fresh directory and run signature/audit
  verification before declaring the release complete.

## Maintenance

This is the clone-safe, package-specific projection of the organizational
`air-package-publishing` skill, upstream version 1.1.0. Review it when the
upstream authentication, verification, or Air-video release policy changes.
