# Android video specification

## Purpose

`viptv-org/android` is the Android and Android TV playback module. Its interface gives an application one session-scoped player, explicit source opening, live/DVR facts, track lists, track selection, subtitles, and capability facts. The implementation adapts AndroidX Media3.

## Product seam

The application and the design repository own screens, focus, remote-button handling, overlays, next-episode policy, resume prompts, and user-visible wording. This module must not invent an Android-only UX. It reports end-of-media and playback facts so the application can apply the shared VIPTV behavior.

## Media behavior

- Android API 24+; Android TV is a first-class target.
- Preserve request headers and external subtitle sources.
- Model audio, subtitle, and video tracks independently.
- A non-seekable live stream never offers seeking. A DVR stream exposes only its current seekable range. On-demand media exposes its duration and seekability.
- Capability reporting is runtime/device-specific. Codec, HDR, DRM, and UHD support are never inferred from dependency presence.
- A new source replaces the current session. Stale native callbacks cannot update the replacement session.

## Boundaries

No Apple, desktop, browser, WebAssembly, JavaScript, MPV, or publishing implementation is in scope. Transcoding is a server/application decision after direct playback and client capability checks fail.

## Acceptance

The Android unit suite passes on JDK 17. Connected tests and manual Android/Android TV checks cover Media3 surface attachment, remote focus, live/DVR seek behavior, tracks, subtitles, source replacement, and capability reporting before a release claim.
