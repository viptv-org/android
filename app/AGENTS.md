# VIPTV Android TV app

Read the repository `DESIGN_REF`, `SPEC.md`, and the pinned design contract before changing `:app`. This is the Android TV implementation of the Roku interaction contract, not a sample media application.

Keep product policy in `PlaybackPolicy`/`AppController` and backend I/O in `VipTvHttpGateway`; Compose renders state and translates remote input. Do not select a fallback source automatically. Resume may start only the persisted exact source; absent identity opens Sources. Preserve 700 ms hold semantics: one held action, no release activation after it.

Device tokens, refresh tokens, stream URLs, headers and parent PINs are secrets. Store tokens only in private app storage, retain PINs in memory only for a submitted unlock request, and never log any of them. Use the backend's direct/copy/remux/transcode decision; Android must not request a transcode merely because a container name looks unfamiliar.

Do not run emulators, Gradle, or connected tests when the workspace is resource-constrained. GitHub-hosted review CI runs automatically on main and pull requests with one worker; use its uploaded APK for device validation. A build or unit pass is not Android TV media/focus evidence.
