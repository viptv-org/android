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
- [ ] Fix slow HTTPS playback and native torrent startup, including Resume.
  The captured app launch took 16.8 seconds. Direct player probes identify serial
  header/index/resume requests and expensive seek-index parsing. In one TV
  measurement, 1.39 of 1.42 seconds spent reading the index was CPU work.
  HTTP/2 pooling and bounded in-memory redirect reuse are implemented. A physical
  exact-source Resume improved from 16.8 to 6.54 seconds in one matched capture;
  other cold captures remain slower. One redirect chain spent 2.73 seconds in
  DNS and additional time waiting for provider responses before any media bytes.
  The requested target is approximately one second, and is not yet achieved.
  A later emulator HTTPS probe removes DNS/redirect delay on repeats but still
  takes 5.66–5.74 seconds, reading about 32 MiB of preroll before exact Resume.
  ffprobe confirms the indexed keyframe is at 41:32.991 for a requested 41:41.
  Byte reuse for repeated Resume is now being investigated; exact media time
  must remain intact.
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
  The underlying resolver can still delay a genuinely cold lookup; the one-second
  cold playback item remains unchecked.
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
  On the physical TV, admission takes 961–1,036 ms; two actual app attempts
  then reach the 30-second peer-metadata deadline before Media3. A separate
  same-library TV probe obtains exact DTS metadata in 23.05 seconds and joins
  cancellation successfully; a host probe takes 2.66 seconds. Both pinned DHT
  bootstrap nodes respond from the actual app in 131–144 ms. Native cold peer
  discovery is therefore still under investigation, not accepted as fixed.
  Gateway native pin 31fea67 fixes two deterministic transport bugs: DHT lookup
  discarded the first response and issued a second request; payload startup
  discarded peers found during metadata acquisition. Both regressions fail
  before and pass after the fixes. Fresh host Unabomber metadata takes 2.49 s
  and first bytes another 1.33 s; the fixed three-ABI native APK is installed
  for physical acceptance. Public-swarm timing and the one-second target remain
  unaccepted until measured on the TV.
  Gateway pin be05c96 also fixes the four-hop discovery ceiling and sixty-second
  per-packet waits that exceeded native acquisition's thirty-second budget.
  Lost native UDP replies now retry within three seconds and lookup can reach
  sixteen hops; deterministic hop/loss regressions pass. Fresh emulator runs
  obtain DTS metadata in 1.5–2.3 seconds and decode both beginning/Resume with
  selected audio. Unabomber metadata and first bytes pass separately; its tested
  4K HEVC source exceeds this emulator's decoder support. Cold peer transfer
  still varies and the requested one-second cold target is not yet achieved.
  A later cold Resume probe reproduced Media3 opening timeout despite over
  100 MiB downloaded: the current piece could not be reassigned without a
  completed-piece speed sample. Native current-piece takeover now needs only
  two seconds and verifies the receiving peer advertises that piece. The same
  cold Resume case and beginning/other-resume cases now pass on the emulator,
  with metadata 1.47–1.79 s and Media3 preparation 1.19–14.0 s across those runs.
  The optimized three-ABI APK with these changes is installed on the onn. TV.
  Further cold probes still reproduce a twenty-second emulator opening timeout.
  Closed diagnostics identify slow peers occupying over 120 connection slots.
  Native TCP/message waits are now two/five seconds to release those slots;
  be05c96 resolves metadata in 0.90–1.24 s in three sequential probes, but exact
  Resume still takes 18.86 s. Gateway cd3e43e retains one reader throughout each
  HTTP body; its range/cancellation regression passes, but exact 41:41 Resume
  still reproduces the 20-second opening timeout. Gateway c790792 fixes an idle
  retry/socket-timeout race with a failing owned-seeder regression, passing
  three times at 2.36–2.38 s. Fresh exact Resume/start-from-zero now open with
  selected DTS audio (12.99/3.91 s Media3, 1.68/1.86 s metadata). The requested
  one-second startup remains unchecked; excess lookahead is under investigation.
- [x] Validate the implemented Android changes: Android unit checks, core native/WASM checks,
  APK assembly/lint/integrity, actual TV playback/focus/frame measurements and
  device behavior. Android passes 52 library/302 app unit tests, source/Home
  emulator checks, DTS audio-output and stalled-stream deadline regressions,
  assembly/lint and three-ABI/native/notice/alignment checks. Native public-swarm
  and one-second cold-start acceptance remain pending the server/network work.

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
