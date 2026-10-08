# Android playback and performance fixes

Owner requests tracked on `fix/android-playback-and-performance`.
Android and shared-core changes use this branch name. The TV-web core adoption
uses an isolated worktree so its owner's existing edits remain untouched.

## Checklist

- [x] Create a new branch before further changes.
- [x] Fix missing DTS audio and the empty audio menu. A software audio fallback
  passes real Media3 decode/output tests. The owner confirmed sound on the onn. TV.
- [ ] Fix slow HTTPS playback and native torrent startup, including Resume.
  The captured app launch took 16.8 seconds. Direct player probes identify serial
  header/index/resume requests and expensive seek-index parsing. In one TV
  measurement, 1.39 of 1.42 seconds spent reading the index was CPU work.
  HTTP/2 pooling and bounded in-memory redirect reuse are implemented. A physical
  exact-source Resume improved from 16.8 to 6.54 seconds in one matched capture;
  other cold captures remain slower. One redirect chain spent 2.73 seconds in
  DNS and additional time waiting for provider responses before any media bytes.
  The requested target is approximately one second, and is not yet achieved.
- [x] Support HTTP as well as HTTPS media. Cleartext media and cross-protocol
  redirects are enabled in the installed APK. The owner
  explicitly waived adding an HTTP-specific test.
- [x] Stop startup metadata fan-out. The actual gateway regression changes from
  12 unsolicited metadata requests to zero for 80 saved/history cards. TV
  enrichment follows settled hero/card focus and is cancelled with that interest.
  The actual TV startup records one settled hero metadata request, rather than
  a batch of requests for unfocused cards. Slow-network stress remains in validation.
- [x] Fix source-page CPU stalls and improve the slow loading spinner. The 1,000-source
  dispatcher regression improves from 233 ms to 8 ms by parsing/projecting off
  the UI thread. Unchanged display rows are reused. The physical TV source-page
  navigation median improves from 81 ms to 19 ms, and jank from 79.7% to 14.7%.
  The marquee exposes one stationary accessibility description, and changing
  providers returns to the first source. Nine corresponding emulator regressions pass.
- [x] Separate source discovery/Torrentio time from client lag. One authenticated
  backend capture returned Torrentio rows within 377 ms; its final other-provider
  event arrived at 9.57 seconds. Distinguish provider/network time from Android
  polling and processing before changing discovery behavior. Plain Torrentio
  plus Torbox returned their cached rows within 495 ms in another capture;
  unchanged preview snapshots no longer repeat ranking/publication work.
- [x] Remove duplicate source rows without merging different providers or unknown
  identities. The capture contained nine duplicate addon/fingerprint identities.
  Core hash-set deduplication passes native and actual-WASM vectors, preserving
  the first row handle/order. Android and TV-web adopt the same core revision.
  The physical TV list changes from 175 to 166 rows before adding plain Torrentio.
- [x] Remove the floating TV Home hero progress bar. The resume time is on its
  own line, below the episode label when present. The remaining text/actions
  move down to use that vertical space. The physical movie screenshot and twenty
  emulator source/Home/focus/marquee regressions pass.
- [x] Deliver an optimized development APK for the TV. Its observed Android
  runtime is 32-bit, and the installed debug APK runs without optimized compiled
  code. A non-debuggable, optimized development variant with the same signing
  key is installed with sign-in/data preserved. Cold activity launch improves
  from 5.58 seconds to 0.79–0.91 seconds. CI packages this development variant.
- [x] Install the owner's supplied plain Torrentio addon. It is enabled and
  returns 59 torrent sources; existing Torbox/provider configuration is retained.
- [ ] Enable the compatible native-torrent server path. A real selected
  x264/DTS torrent fails at server admission with HTTP 409 before Media3 opens.
  The running October 4 backend returns 404 for the native protocol endpoint.
  The compatible candidate d9ca1fc passes 281 backend tests and serves protocol
  v1 with native version 1 over trusted local HTTPS. The complete candidate image
  is built, health checked, and verified with copied data. Production replacement
  requires the explicit approval documented in backend/DEPLOYMENT.md; approval is pending.
- [x] Validate the implemented Android changes: Android unit checks, core native/WASM checks,
  APK assembly/lint/integrity, actual TV playback/focus/frame measurements and
  device behavior. Android passes 49 library/296 app unit tests, source/Home
  emulator checks, DTS audio-output and stalled-stream deadline regressions,
  assembly/lint and three-ABI/native/notice/alignment checks. Native public-swarm
  and one-second cold-start acceptance remain pending the server/network work.

## Evidence and privacy

Raw timings, captures and credentials stay in the ignored private qualification
artifacts directory. No stream URLs, provider credentials or tokens belong in
this file. Emulator results do not establish physical TV playback or performance.
One fast cached provider response does not establish cold-provider latency.

## Production candidate awaiting approval

The live backend is based on `0d5bc32` (October 4), before the native-torrent API.
The candidate source is `d9ca1fc27b6f4fed5bd75d68ede4143aeef62085`, with the matching
TV-web core consumer pin. Image digest:
`sha256:fb04756e32c6da83bcca5ed75215b6ba61ce10a6312c3b822455263060106350`.
An isolated image serves health and its packaged dashboard. Authenticated local
HTTPS returns protocol version 1 and `native_torrent_versions: [1]`.

The copied database comparison checks 90 existing tables. Account/profile/history,
source configuration and other protected values remain intact. Four operational
auth/refresh tables change during isolated startup/authentication; no production
database was changed by this qualification. Rollout must take a fresh consistent
backup, preserve the private keyring/configuration and current rollback image,
and verify the new native endpoint plus actual device playback afterwards.

Diagnostic playback accidentally continued while the server candidate was prepared.
It is stopped, and the single test-affected Iron Man entry was corrected to its
pre-test 41:41 position and original DTS source, with `watched=false` verified.
Future device timing probes pause automatically after capture.
