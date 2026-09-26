# Android video specification

## Purpose

`viptv-org/android` contains the Android/Android TV playback module and the `:app` VIPTV native phone and Android TV application. The application pins the shared design contract at `DESIGN_REF`; the playback module gives it one session-scoped player, explicit source opening, live/DVR facts, track lists, track selection, subtitles, and capability facts. The implementation adapts AndroidX Media3.

## Product seam

The application and the design repository own screens, focus, remote-button handling, overlays, next-episode policy, resume prompts, and user-visible wording. `:app` uses the Rust backend's device pairing/refresh, profile, catalog, source-discovery, playback/heartbeat cleanup, and progress routes. Exact-source Resume compares the server-supplied add-on and source fingerprint carried in profile progress; ephemeral stream job IDs and display names are never used. Missing or unavailable identity opens the manual picker and never starts a substitute. The library reports end-of-media and playback facts so the application can apply shared VIPTV behavior.

## Media behavior

- Android API 24+ phones and TV. AND-035 supersedes the old Roku reconstruction: Compose renders TV in a 1920×1080 logical frame, uniformly scaled to the viewport; phones use native density, system font scaling, touch and keyboard insets.
- Preserve request headers and external subtitle sources.
- Model audio, subtitle, and video tracks independently.
- The native viewing UI omits pause, seek and next controls for all live channels. The playback adapter still reports live/DVR range facts independently. On-demand media exposes its duration and seekability.
- Capability reporting is runtime/device-specific. Codec, HDR, DRM, and UHD support are never inferred from dependency presence.
- A new source replaces the current session. Stale native callbacks cannot update the replacement session.

## Boundaries

No Apple, desktop, browser, WebAssembly, JavaScript, MPV, or publishing implementation is in scope. Transcoding is a server/application decision after direct playback and client capability checks fail.

## Acceptance

The Android unit suite covers the product-policy seam: exact-source Resume, final-ten-second Next eligibility, and the 700 ms release/hold distinction. Connected tests and manual Android/Android TV checks cover Media3 surface attachment, remote focus, live/DVR seek behavior, tracks, subtitles, source replacement, and capability reporting before a release claim. Hardware/emulator checks remain required and are not implied by an APK build.

## Review-candidate scope and remaining validation

The application now provides device pairing/token refresh, profile selection and edit/create/delete routes, parent PIN retry, Home shelves, Discover/Search, My List, queue removal/Undo, explicit incremental and cancellable source discovery, exact-identity Resume, Media3 playback, Live/Guide, preferences, addon enable/remove and sign-out. The controller maps `parent_required` errors into a masked PIN prompt, retains the attempted action in memory, and replays it only after the server grants the current session authority. PIN values are neither logged nor persisted.

Native API 36 phone/TV emulator observations are recorded in the current TESTING.md entry. Physical Android TV and phone acceptance remains required before broad media/device parity claims. In particular: spatial focus/rail restoration, artwork loading, full series/season/episode metadata rendering, source arrival while focused, player overlay auto-hide and first/second Back behavior, track dialogs, server-managed seek replacement, controlled Next source selection/cancellation, live guide timing, profile avatar asset grid, decoder/DRM/HDR, and physical remote/media keys. The hosted review workflow runs on main pushes, pull requests and manual dispatch. It uses one Gradle worker, runs library/app tests, assembles a debug APK and uploads test XML plus a review APK. It does not substitute for Android TV acceptance; current device and build evidence is recorded in TESTING.md.

## App modules and interfaces

`AppController` is the application module: it coordinates persisted device credentials, profile selection, route state and native player lifetime. `VipTvHttpGateway` is its only backend adapter and sends Bearer credentials over the versioned `/api` contract. `PlaybackPolicy` and `HoldPolicy` are pure product modules whose interface is exercised by unit tests. Compose renders state and forwards remote actions; it does not select a source, continuation candidate or fallback. Android TV's `SurfaceView` attaches to the Android-only Media3 adapter. This preserves the direct-first media ladder: server policy decides any remux/transcode result and the app never asks for conversion merely because a source extension is unfamiliar.
