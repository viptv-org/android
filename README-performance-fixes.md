# Android playback and performance fixes

Owner requests tracked on `fix/android-playback-and-performance`.
Android and shared-core changes use this branch name. The TV-web core adoption
uses an isolated worktree so its owner's existing edits remain untouched.

## Checklist

- [x] Create a new branch before further changes.
- [x] Fix missing DTS audio and the empty audio menu. A software audio fallback
  passes real Media3 decode/output tests. The owner confirmed sound in an earlier
  build. The optimized APK regression was R8 removing FFmpeg's native-called
  `growOutputBuffer` method. Module consumer rules preserve the JNI surface, and
  the packaged-DEX check fails on the broken APK and passes on the corrected one.
  The TV now reports FFmpeg available, DTS support handled, and selected six-channel
  48 kHz audio; the owner reports audio appears restored.
- [ ] Verify reliable HTTPS/native torrent playback and forward/backward seeks,
  including the remaining metadata failure. The owner waived the
  one-second startup target. A matched physical HTTPS Resume improved from
  16.8 to 6.54 seconds. Native SurfaceView probes render video and DTS audio
  from the beginning, 41:41 and 73:26 and after forward/backward seeks.
  A lost-bootstrap-query retry fix passes all 25 DHT checks; its final APK
  passes fresh original-source playback and seeking from zero and 41:41.
  A distinct torrent advertising 1,202 seeders still times out on emulator
  and host after its discovered TCP peers fail to connect. Native v1 uses
  DHT/TCP and disables trackers. Broader discovery remains under investigation.
  The verified optimized candidate is installed on the onn. TV; its installed
  APK hash matches and the actual 32-bit process loads the native capability.
  Detailed timing history is in [TESTING.md](TESTING.md).
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
- [x] Keep Continue Watching/hero enrichment, stop lower-shelf hover enrichment,
  and proxy public artwork through wsrv with bounded dimensions, cache and IO.
  Bounded public-image URL/cache regressions and the actual lower-shelf hover
  request-count regression pass. One public poster shrinks from 25,631 to
  7,220 bytes. The shared image loader bounds fetches/decodes and caches sizes.
- [x] Make transport seek buttons compound while retaining focus; reduce held
  seek-bar speed and show the buffered range. The actual player regression
  queues 30/60/90/120 seconds, commits, then adds another 30 while preserving
  Forward selection. It fails on the old controls and passes on the fix. Held
  bar movement is bounded to five seconds per 250 ms repeat step. Scrub preview
  moves the thumb independently of actual playback/buffered ranges.
- [x] Diagnose repeated DNS delay. A Torbox capture spends 5.46 seconds across
  three system DNS calls. IPv4-only comparison also encounters slow/lost replies,
  ruling out the proposed AAAA-only fix. Artwork/control/player HTTP clients now
  share a bounded 30-second system-DNS cache, invalidated by network identity.
  Family preservation, expiry, network change and failure-retry checks pass.
  The underlying resolver can still delay a genuinely cold lookup; cold playback
  still depends on network responses.
- [x] Deliver an optimized development APK for the TV. Its observed Android
  runtime is 32-bit, and the installed debug APK runs without optimized compiled
  code. A non-debuggable, optimized development variant with the same signing
  key is installed with sign-in/data preserved. Cold activity launch improves
  from 5.58 seconds to 0.79–0.91 seconds. CI packages this development variant.
- [x] Install the owner's supplied plain Torrentio addon. It is enabled and
  returns 59 torrent sources; existing Torbox/provider configuration is retained.
- [x] Enable the compatible native-torrent server path. The October 4 server
  refused the selected native source before Media3 opened. Following explicit
  owner approval, candidate d9ca1fc is deployed and healthy. Authenticated public
  HTTPS now returns protocol v1 with native version 1. All 90 existing tables
  match the stopped-writer preservation snapshot. Physical public-swarm startup
  acceptance remains part of the unchecked playback-startup item.
- [x] Validate the implemented Android changes: Android unit checks, core native/WASM checks,
  APK assembly/lint/integrity, actual TV playback/focus/frame measurements and
  device behavior. Android passes 52 library/302 app unit tests, source/Home
  emulator checks, DTS audio-output and stalled-stream deadline regressions,
  assembly/lint and three-ABI/native/notice/alignment checks. Native public-swarm
  playback/seek repeat qualification remains pending for the final native pin.

## Evidence and privacy

Raw timings, captures and credentials stay in the ignored private qualification
artifacts directory. No stream URLs, provider credentials or tokens belong in
this file. Emulator results do not establish physical TV playback or performance.
One fast cached provider response does not establish cold-provider latency.

## Approved production deployment — 2026-10-08

The previous backend was based on `0d5bc32` (October 4), before the native-torrent API.
The deployed source is `d9ca1fc27b6f4fed5bd75d68ede4143aeef62085`, with the matching
TV-web core consumer pin. Image digest:
`sha256:fb04756e32c6da83bcca5ed75215b6ba61ce10a6312c3b822455263060106350`.
An isolated image serves health and its packaged dashboard. Authenticated local
HTTPS returns protocol version 1 and `native_torrent_versions: [1]`.

The copied database comparison checks 90 existing tables. Account/profile/history,
source configuration and other protected values remain intact. Four operational
auth/refresh tables change during isolated startup/authentication; no production
database was changed by this qualification. The approved rollout took fresh consistent online and stopped-writer backups,
retained the exact old image/container and private environment/keyring, and
reused the existing data volume. The replacement became ready in 1.63 seconds.
All 90 existing tables match the stopped-writer snapshot afterwards. Public
health, dashboard `index-CiF8XBO-.js`, `/tv/` and the watch API proxy respond
successfully; unauthenticated native protocol requests return 401 and
authenticated ones return v1/native `[1]`. Account, profiles, addons, history
and Continue Watching are readable with the existing device session. The watch
bundle and proxy configuration were preserved; its separate bundle was not
updated by this backend-only rollout.

Diagnostic playback accidentally continued while the server candidate was prepared.
It is stopped, and the single test-affected Iron Man entry was corrected to its
pre-test 41:41 position and original DTS source, with `watched=false` verified.
Future device timing probes pause automatically after capture.
