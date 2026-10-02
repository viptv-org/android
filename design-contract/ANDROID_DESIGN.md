# AND-035 — Native Android phone and TV design adoption

## CW-SOURCE-BACK-001 — Continue Watching episode source return (2026-10-01)

Status: proposed for Android phone and TV; source revision is the design commit
that introduces this section. From a Continue Watching episode card on Home or
the Continue Watching list, Choose source opens the manual picker for that exact
episode and retains the originating card/list position and focus. Back from that
picker cancels in-flight discovery and returns to the parent show's full title
details, with the originating episode's season selected and episode card revealed.
The details page exposes the other episodes for selection. If series metadata is
still loading, show the existing detail loading state before revealing episodes;
a failed load offers the existing retry and Back to the originating queue card.
The picker Back never starts a source or resumes playback. Back from the show's
details returns to the originating Home/Continue Watching card and restores its
focus and scroll position. A source selected and played from this route retains
the established exact-source playback return behavior; Source details closes to
the same focused source row. An explicit episode selection from details starts
that episode's ordinary manual source path. Profile/route replacement invalidates
late metadata or discovery callbacks. Live sources and source pickers opened from
other routes keep their existing returns. Phone uses system Back or the visible
Back control; TV uses remote Back. Hold/Info still opens the existing queue menu.

Acceptance CW-SOURCE-BACK-001: from a Home Continue Watching episode in season 2,
open Choose source, wait for a partial result, then Back. Discovery stops and the
parent show detail opens at season 2 with that episode visible; choose a different
episode and verify its own manual sources. Repeat from the Continue Watching list
and with slow/failed parent metadata. Back from details restores the same queue
card and focus. Verify source selection, Source details and live/other-route Back
remain unchanged. Phone, emulator and physical TV evidence are separate.

## AND-042 — phone presentation and player track menus (2026-09-30)

Status: approved implementation scope for design issue 6. Emulator visual
evidence is recorded in Android TESTING.md; physical-device proof stays in
Android issue 3. Phone rules apply to the phone layout only. TV changes are
limited to the player track panel. Visual sources are the Main, Live,
PhPlayerSubs and TvPlayerSubs canvas boards. Where the phone rules below
differ from those boards, these rules win.

- AND-042-HOME: Home has no header bar. Its first element is the Main hero
  card: 16dp gutters, 28dp radius on every corner, starting at the system top
  inset plus 8dp. The profile and Watch on TV entries leave Home. Settings
  stays reachable from the Discover, Live and My List headers. Watch on TV
  stays in Settings › This device. A paired TV's remote button stays in those
  three headers. This replaces only AND-038's Home header remote button.
- AND-042-NAV: the floating bar keeps the Main geometry: a 64dp glass bar,
  52dp segments and a separate 64dp Search disc. Segments show 22dp icons
  only, with no text, and keep accessible names (Home, Discover, Live TV,
  My List). The active segment keeps the off-white fill.
- AND-042-CARDS: phone media cards keep the caption under the art: the title,
  then one line of minimal context, S1 E1 for an episode, otherwise the year.
  They show no genres, runtime or resume times. Progress is 6dp with rounded
  ends, lifted 10dp off the art's sides and bottom edge. The first card of a
  row aligns with its shelf heading's 16dp edge. Shelves are 20dp apart with
  12dp between a heading and its row.
- AND-042-HEADINGS: a Home catalog shelf heading names the content type, then
  the catalog ("Series · Trending"), not the addon. Continue watching, My
  List, Live now and Recently watched live TV keep their names.
- AND-042-LIVE-TILES: phone Home live shelves show small logo tiles: 104×72dp,
  18dp radius, surface-1 with a hairline outline, and the channel logo fitted
  with 12dp padding. A channel without a logo shows a monogram. Tiles show no
  visible name; the channel name is the accessible label. Tap starts the live
  path; long press and the ⋯ action keep the existing channel menu.
- AND-042-SKELETON: loading uses layout-matching skeletons on surface-1 and
  never "Loading…"/"Finding…" copy. Home shows a hero block and two shelves
  of card placeholders until its first row arrives. Discover, catalog grids
  and Search show poster placeholders. The phone Live list shows channel-row
  placeholders. A grid that is appending its next page shows one row of
  placeholders at its end. More items load automatically; there is never a
  Load more control.
- AND-042-TRACKS-PHONE: Audio and Subtitles open an anchored panel
  (PhPlayerSubs), not a bottom sheet. The panel is surface-1 with a 20dp
  radius and a hairline outline. It sits 16dp from the screen sides, its
  bottom 12dp above the timeline, at most 360dp tall, and scrolls. The header
  is the title (17 bold) with a 44dp close disc. Rows are 48dp tall with 16
  text: Off first for subtitles, then the tracks. The current row ends with a
  check and "Current" (13, secondary). An unsupported track reads
  "<label> (unavailable)" in tertiary text. Tapping it keeps the panel open
  and shows "This track is not supported on this device." Choosing a track
  applies it and closes the panel. Tapping outside, close or Back closes it.
  Playback, the controls and the subtitle layer stay visible behind it.
- AND-042-TRACKS-TV: the right 820px panel (TvPlayerSubs) lists rows, not pill
  buttons. Rows are 72px with a 12px radius and are transparent unless
  focused; the focused row has the off-white fill and on-light text. The
  current row adds " · Current". Unsupported rows use tertiary text plus
  " · unavailable". They stay focusable, and OK shows the existing notice.
  The footer shows key hints: ▲▼ Move, OK Select, BACK Close. Focus starts on
  the current row; Back closes and restores focus to the control that opened
  the panel.

Acceptance AND-042-01: phone Home starts with the rounded hero under the status
bar, shows no header, and keeps Settings and Watch on TV reachable. AND-042-02:
icon-only nav with accessible names, content-type headings, live logo tiles,
6dp lifted progress and year/episode captions. AND-042-03: skeletons for
Home, Discover, Search and Live, with no loading copy and automatic paging.
AND-042-04: on phone and TV, select, turn off and reject an unavailable
subtitle, select audio, then Back/close and confirm focus or controls are
restored. Emulator evidence is not physical-device parity.

## AND-041 — silent foreground backend validation (2026-09-30)

Status: approved implementation scope for Android issue 4; native media and
rendered qualification must be recorded independently. Applies to an already
authenticated phone/TV returning to the foreground. Fresh startup/sign-in and
SmartCast remote pairing keep their existing contracts.

- AND-041-PENDING: preserve the current profile, route, loaded rows, source
  selection, scroll and focused control while validating backend identity/token
  authorization. No global loading cover, pairing page, profile-picker flicker,
  spinner or new focus request. Coalesce repeated foreground callbacks and token
  refresh into one attempt; bound it to 30 seconds. Mutating network operations
  continue to enforce current server authorization. Do not clear credentials on
  transient network failure or replay old pairing requests.
- AND-041-RESTORED: matching account/profile authorization leaves the existing
  screen and focus intact. A refreshed token must not cause the ordinary startup
  presentation or refetch all Home rows. No automatic player restart on return.
- AND-041-RETRY: after final timeout/network failure, retain the current route,
  profile and selections. Show the existing inline error/action pattern with
  `Could not reconnect to VIPTV. Try again.` and `Try again`. Preserve safe server
  reasons for explicit denials; never display URLs, headers or token values.
  Retry validates the existing identity rather than starting pairing. Back leaves
  this recovery normally and cancels any pending validation for the old route.
- AND-041-REVOKED: an explicit rejected refresh/session or changed account clears
  protected presentation and returns to existing sign-in with `Your session
  expired. Sign in again.` A removed/unauthorized selected profile returns to the
  existing profile chooser with `This profile is no longer available. Choose a
  profile.` Do not retain another account's state or silently select a replacement.
- Background, sign-out, account/profile replacement and disposal cancel the
  pending attempt. Late validation/refresh cannot overwrite a newer route,
  resurrect the old profile or start media. Configuration rotation retains the
  established session; callback coalescing still prevents duplicate work.

Keep AND-036's background-stop contract: capture/persist the actual absolute VOD
clock, stop/detach Media3, retire the backend/gateway lease, and return to the
documented originating route. Explicit later Resume retains the saved source
fingerprint and nonzero absolute position; restarting an output does not turn
that position into a relative clip offset. No PiP or background-audio feature.
Native direct/copy/remux policy is unchanged; do not force transcode for testing.

Geometry/assets: existing phone and TV reference screens, fonts, buttons, inline
errors and safe insets are unchanged. Phone recovery uses 44dp minimum targets;
TV uses its existing 72px action/focus style. Tab/D-pad follows visual action
order; Retry does not steal focus from another control. No new hold gesture.

Acceptance AND-041-01 through 04 covers silent delayed success; bounded failure
and retry without re-pairing; refresh/revocation/profile replacement and canceled
late responses; and actual authenticated media foreground/background/return with
required nonempty headers, nonzero saved Resume and final lease/native cleanup.
Record phone/TV emulator input, visible states/focus, decoded media, host/native
regressions and normal system-trust APK identity separately. Fixture-trusting
APKs cannot be distributed; emulator results are not ARM/physical-device proof.

## AND-039 — Phone remote reliability and responsiveness

Owner feedback, 2026-09-27; supersedes AND-038's background-dismissal and
outlined header-button treatment. No other platform UI changes.

- Background/foreground and rotation retain the setup page, entered IP and
  current pairing challenge. Returning must not start a second pairing request.
  The screen-awake flag is released in the background. Discovery pauses there;
  already queued remote keys are discarded, never replayed on return.
- Explicit Cancel/Back out of a PIN challenge and New PIN send the existing
  device-scoped cancelPair operation before another beginPair. Release a late
  challenge too. Keep only a pending-origin marker for process-death recovery;
  never persist the PIN or challenge. Preserve the stable phone device ID.
- Discovery consumes the actual shared-core Result envelope and probes both
  supported SmartCast ports. Results arrive progressively; a bad/unreachable
  host cannot abort the scan. Network, TLS, JNI client setup and credential
  operations run off the UI thread. No global TLS weakening.
- Key presses never toggle the launch button's disabled/alpha state, clear its
  label, or rebuild connection chrome. Serialize an eight-command bounded queue,
  give immediate local press/haptic feedback, and cancel queued keys on exit.
  Connection failures still show recovery; a rejected command alone is not an
  offline TV. The launch button is busy only for setup/launch operations.
- Header remote and profile use identical 44 dp touch/visual slots and circular
  avatar-ground styling, without an outline. The remote glyph is 20 dp, centered.

Acceptance: actual JNI discovery enumeration; automatic fixture discovery;
background/resume during PIN with exactly one beginPair; Cancel/re-pair and New
PIN against a TV that rejects overlapping challenges; delayed key responses
without launch-button flicker; ordered rapid taps; cancellation without replay;
native header size/appearance inspection. Physical LAN/device evidence remains
distinct from emulator and synthetic-network evidence.

## AND-038 — Android phone Vizio remote (2026-09-27)

Status: approved implementation contract; device qualification remains separate.
Source: owner's `viptv-design-system (1).zip`, exported 2026-09-27. The twelve
PhTv*/PhRemote*/PhHomeRemote reference states and components section 11 define
the phone remote. Existing AND-036/037 and TV corrections continue to apply.

Settings → This device → Watch on TV is the only entry before pairing. Setup
pushes native pages over the saved app route: introduction, bounded LAN search,
manual IPv4 address, four-digit PIN (submit automatically), connected. Back
cancels the pending operation and returns one step; leaving setup restores the
originating screen. New PIN starts a fresh challenge. A failed PIN stays on the
PIN page with editable input. Pairing tokens use the Android Keystore; selected
TV and preferences are device-local, never account credentials.

The target remains SDK 36: do not display a fictitious local-network permission
prompt. Intro copy is “VIPTV searches your local network only to find your TV.”
Actual access denial opens recovery; manual addressing cannot bypass denial.
Discovery is user-initiated, scoped to the connected local network, cancellable,
and falls back to manual entry. Timeouts and offline TVs have retry actions.

One paired TV enables a 44 dp header remote button on Home, Discover, Live and
My List. The bottom navigation is unchanged. Show the one-time “Your TV remote”
tip with “Got it”. The sheet uses the 390×844 reference proportionally with safe
insets and scrolling on smaller screens; it restores the underlying route when
closed. Its controls are D-pad/OK, Back, separate Play/Pause and volume −/+.
Each tap sends one key; each swipe past 32 dp sends one direction, tap sends OK.
No background repeat or queued gesture survives dismissal. Serialize commands.
Buttons is the initial mode, then retain the last mode. Buttons remain an
accessible equivalent to all swipe actions. Use native labeled touch targets.

“Open VIPTV on TV” launches the configured HTTPS Vizio receiver, never transfers
the phone video. A TV acknowledgment is not proof the receiver rendered.
Reconnect checks the saved token; authentication failure offers re-pairing.
Network failure shows PhRemoteOffline and disables commands until retry succeeds.
Remote button visibility, vibration, and keep-screen-on default on. Screen-on
is active only while the sheet is visible. Change TV retains the old selection
until a replacement pairs; Forget deletes the credential/selection and hides
the header button. Cancelling Forget preserves both. TV mode never exposes this
phone feature. No other platform UI adopts this update.

Acceptance: first setup, empty search/manual fallback, invalid IP, wrong/new PIN,
cancel/stale response, paired restart, every button and swipe, offline/retry,
revoked credential/re-pair, change-TV cancellation, preferences, Forget/cancel,
rotation/font scaling/keyboard insets, background cleanup, unchanged phone
playback and Android TV navigation. Record functional, visual and physical TV
evidence separately in Android TESTING.md.

Status: owner requested on 2026-09-25; implementation and emulator acceptance
must be recorded in Android TESTING.md. Related: Android issue #3, design #6.

Android uses the current VIPTV design system and TV-034 corrections. This
supersedes the old 1280×720 Roku reconstruction for Android only. Native
Compose and Media3 remain the renderer/player; Rust remains the authority for
normalization, artwork, source identity, progress and continuation rules.

## Layout and input

- Android TV follows the 1920×1080 reference with 96×54 safe insets, the 144px
  rail and 520px expanded menu, 72px actions, 320×180 shelf cards and stable
  4px focus rings. Scale the complete TV reference uniformly to the viewport.
  Share rail coordinates between collapsed/expanded states. Focused items do
  not move or resize; rows scroll enough to reveal every selected item.
- Phones follow the phone references at native density: Onest body text,
  Bricolage display type, 16dp gutters, rounded hero, 54dp primary controls,
  44dp minimum touch targets, and a floating Home/Discover/Live/My List bar
  with a separate Search button. Honour status/navigation/keyboard insets and
  system font scaling. Portrait and landscape layouts scroll without clipping.
- Select layout from the native television UI mode, not screen width alone.
  Phones are not forced to landscape; TV remains landscape. Media3 keeps its
  native video surface and adapts controls to phone portrait/landscape and TV.
- Phone text entry uses the system keyboard. TV supplies the design's remote
  keyboard and also accepts a connected physical keyboard. Password/PIN input
  is masked, transient, and cleared on submission/cancel.
- TV activation fires on release; a 700ms hold fires once and suppresses tap.
  Touch long press and visible overflow actions expose the same menu. Back
  closes the innermost sheet/dialog/keyboard, restores focus, then returns to
  the originating screen. Tab routes retain their scroll positions.

## Complete surfaces

Adopt pairing/sign-in, profiles and editor/avatar grid, Home, Discover and
catalog filters, Search, movie/series details and season/episode selection,
source selection/provider filters/details, My List/Continue Watching and item
menus, live guide/programme details, player/seek/tracks/recovery, preferences,
addons, parent PIN and sign-out. Use one shared component family for actions,
fields, media cards, rows, sheets and progress. Phone sheets become TV side
panels; no duplicate product policy or network stack is introduced.

Phones default to native username/password sign-in as specified in AND-036 below.
Device-code sign-in remains an optional alternative; TV keeps QR/device pairing.
OLED/accent preferences are local display settings. Watch on TV is not claimed
until native discovery/pairing is separately implemented and tested.

Home shows its shell and saved queue before optional artwork/catalogue work
finishes; publish independent results without a slowest-provider barrier.
Returning to Home reuses loaded rows. Pending requests must not navigate over
a newer route or apply data from a previous profile. Live channels start their
live path, never an empty VOD detail. Live players omit pause, seeking and next;
keep available audio/subtitle options and exit. VOD retains exact-source
Resume, explicit source selection, controlled Next and cancellation.

## Visual and functional acceptance

Package the fonts and current Lucide assets from design with their licenses.
Use the generated Kotlin tokens. Inspect matching-content private screenshots
on the API 36 phone and TV emulators for every route family, including long
titles, empty/error/loading states, >12-card rows, >8 episodes, multiple seasons,
profile selection, Settings/rail return, source arrival during focus, touch
overflow, font scaling, keyboard insets and live/VOD player controls.

Run host/native unit tests and build the debug APK after the integrated UI is
implemented. Record emulator remote/touch and actual media evidence separately
from compilation and fixture tests. Physical HDR/DRM, decoder compatibility,
signing/store publication and production deployment remain separate gates.

## AND-036 — Native sign-in, direct playback and interaction corrections

Owner requested on 2026-09-26. This supersedes the phone pairing-only exception
in AND-035; implementations record adoption and measured evidence separately.

- Phone sign-in provides Username and masked Password fields, a Sign in action,
  inline authentication/loading feedback, and an optional Use device code action.
  The server validates credentials through its existing bounded password verifier
  and issues the same revocable device grant as pairing. Passwords remain transient
  and never enter saved state, logs or URLs. TV continues to offer its QR/code.
- Android phone and TV request original-URL playback. Media3 fetches the original
  stream with its explicit source headers and owns decoding, tracks and VOD seeks.
  The API connection stays HTTPS; provider-authorized HTTP media is allowed.
  Direct mode must not start an FFmpeg/transcode job or wait for server probing.
  Unsupported streams expose the actual safe error and an explicit source choice;
  no automatic alternate source or silent server transcode is introduced.
- Selecting a source immediately marks that row as Opening, shows a spinner and
  prevents duplicate starts. Cancel/Back invalidates preparation; late completions
  cannot play audio or replace the current page. Preparation/decoder/network errors
  show safe actionable details and retain Retry, Choose another source and Back.
- Leaving the player or backgrounding the app stops native playback and releases
  the server lease. Rotation and an open player menu preserve the active session.
  VOD resume and seek use the native title clock and full available title range.
- Provider filtering lists every normalized source provider, including Stremio
  addons and IPTV providers. Missing provider IDs must never merge unrelated rows;
  shared Rust supplies stable group identity/display facts. All providers resets
  the filter, and arrivals add groups without stealing focus.
- SRC-OVERFLOW-001 (proposed, design revision of this commit): On the Android
  phone Choose a Source sheet and TV source panel, render every provider's source
  description in the shared two-line fixed-height window defined in
  `viptv-design-system/components.md`. Wrap long tokens. Overflow starts at its
  first line and slowly scrolls downward only while the row has TV/keyboard focus
  or pointer hover; blur, hover exit, replacement and filter changes reset it.
  Reduced motion keeps the first two lines still; accessibility exposes the full
  description and Source details remains reachable by its existing action.
  Keep a spinner and `Finding sources` until the first row arrives, then a spinner
  and `Still checking sources` while discovery actually remains pending. Partial
  rows stay selectable. Completion or cancellation clears the status. An empty
  final list retains the existing no-sources message. These presentation states
  never take focus or change Back, hold/Info, exact-source play or return focus.
- Both hero + actions reflect current My List membership (+ / check), including
  immediately after a toggle and across refreshed shelves/profile changes.
- Phone Home starts at its system top inset without an extra top spacer. Global
  progress indicators stay inside the system safe area, including status bars.
- The first Continue Watching row can receive and move focus while the complete hero remains visible. Vertical scrolling begins at the next shelf; returning to Continue Watching or hero controls restores the top.
- TV Home art belongs to the scrolling hero, with the design's blurred ambient
  fill and readable scrims. It scrolls away with the hero. Returning focus to a
  hero action reveals the complete hero, not just the action row. Shelf focus
  continues to reveal the full selected card and caption without scale changes.

Acceptance includes native password success/failure and optional pairing; every
provider group including blank provider IDs; immediate/cancelled/failed source
starts; real original-URL MP4/MKV/HLS playback with source headers; nonzero resume,
forward/back seeks and clock stability; exit/background audio silence; phone
rotation; stateful membership; safe insets; TV scroll-away and full-hero restore.
SRC-OVERFLOW-001 acceptance: with a one-line, two-line and long unbroken source
description from different providers, all rows keep one height and show no more
than two lines at once; focus a long TV row, watch it traverse to the last line,
move away/back and confirm it restarts at the first line. Repeat for pointer hover,
touch accessibility and reduced motion. Delay one provider until after another
row arrives: the spinner changes from `Finding sources` to `Still checking sources`,
the first row stays playable and focused, and the status clears only on finish or
Back cancellation. Verify empty, filtered-empty, failure and return navigation.
Production promotion requires tested immutable artifacts and verification of the
running backend/transcoder plus served TV asset hashes; Git push is not a deploy.

# AND-037 — Search focus, native player controls and Up Next

Status: owner requested on 2026-09-26; Android implementation and emulator audit
are recorded separately in TESTING.md. Related: Android #3 and design #6.

## Search

- Keep one TV result shelf per returned catalog, labelled with its addon and
  catalog name. Live remains its own shelf. Do not collapse catalog results
  into media-type groups or deduplicate titles across different catalogs.
- Each new query resets all result offsets. Progressive arrivals preserve the
  focused catalog/item and do not request focus. Catalog identity includes
  addon, catalog ID and type, even when display names collide.
- Left/Right moves through individual keyboard keys. Only Left at the first
  column enters the sidebar. Right at any row's last key, remote Play/Fast
  Forward, or the visible Results action enters the first result, scrolling it
  into view before focus. With no results, stay on the keyboard. Left from a
  shelf's first result restores the last keyboard key; subsequent cards move
  left within their row. Physical text input remains supported.

## Phone player

- Portrait follows PhPlayer: 16dp side gutters, a readable title/episode header,
  a fitted video surface, shared timeline, transport row and compact tools row.
  Phone status words do not compete with the title. Live omits transport/timeline.
- Landscape uses the desktop overlay arrangement at phone density: header at
  top-left, one timeline above one controls row; transport controls left and
  audio/subtitle/info/fullscreen controls right. Keep 16dp safe side gutters
  and system/cutout insets. Never stack portrait tool rows across the video.
- Only Play/Pause uses a 54dp accent disc. Other controls have transparent
  44dp touch targets and 22–24dp glyphs. Tools group on the left in portrait,
  with fullscreen at the right edge. Use existing licensed rounded icons.
- Timeline is a 4dp rounded track with played accent, actual lighter buffered
  range, and a consistently circular 14dp white knob. It has a 44dp touch target,
  with times aligned to the track ends. Seeking supports tap/drag/accessibility,
  excludes only its own area from edge Back gestures, and preserves pause.

## Up Next

- Eligible final-ten-second series playback resolves one server-authorized next
  episode, then displays a 10-second countdown card. It does not immediately
  replace playback. The card contains landscape art, NEXT EPISODE, episode
  title, Starts in {seconds}, a remaining-time line, Play now and Cancel.
- Place the card above controls on portrait phone, and bottom-right above the
  timeline on landscape/TV. Phone card is at most 358dp wide; TV is 480px wide.
  It remains visible when player chrome auto-hides. TV initially focuses Play now;
  Left/Right selects its actions; Back cancels and restores player focus.
- Countdown advances during playback or the ended frame, freezes on pause,
  buffering and track/info menus. Seeking, Cancel, Back, source replacement,
  profile change or exit cancels it; it must not reopen for the same playback.
  Explicit Resume in the last ten seconds waits for completion before offering it.
- Play now/expiry uses the existing controlled continuation and exact-source
  affinity; reuse the resolved metadata rather than requesting it twice. Retain
  the outgoing frame while preparing, and preserve Back/cancel recovery.
  Missing/unreleased successors never produce an invented card or a guessed episode.

Acceptance: navigate left/right in every keyboard row; enter results from the
first row; verify catalog collisions, late arrivals and query reset; compare
portrait/landscape seek/time/buffer geometry; inspect a paused scrub and rotated
player; show, pause, cancel and accept Up Next on both phone and TV; verify
cancel/exit prevent delayed playback. Audit Home, profiles, details, sources,
settings and search against their pinned reference screens. Keep the full hero
visible while Continue Watching is focused, as specified by AND-036.
# AND-040 — copy a stream URL (proposed, 2026-09-28)

Owner request: add Copy stream URL to Android stream actions. Applies to the
native phone/TV Source details sheet only; web remains unchanged. Entry remains
the source row's overflow or existing hold/Info action. Keep source metadata and
Close; add a full-width secondary `Copy stream URL` button above Close using
existing 54dp phone/72dp TV buttons, 12dp gap and the existing sheet padding.
Focus starts on Copy; Back/Close returns to the originating source without
playing it or changing filters, progress or source selection. A press copies
once; repeats while resolving do nothing. No new hold gesture is introduced.

Resolve only the explicitly selected stream through the existing authenticated
native direct-URL contract. Do not start the local player or fetch media. Retire
the temporary server lease before reporting success. Show `Getting URL…` while
resolving, disable only Copy, and leave Close available. Closing or backgrounding
cancels delivery to the clipboard; a late response still retires its lease.
Failure says `Could not copy the stream URL. Try again.` and permits retry.
Success says `URL copied` without closing the sheet; announce it accessibly.
Keep this hint visible: `Links may expire or require provider headers. Share only
with people you trust.` Copy the exact URL, never headers/cookies or a guessed
stream identifier. Mark clipboard contents sensitive and never log, display,
persist or include the URL in errors or UI-state descriptions.

Acceptance: copy two different sources and verify exact clipboard targets;
repeated activation has one pending request; success leaves playback stopped;
failure is safe/retryable; Close/Back/background during delayed resolution never
overwrites clipboard and releases a returned lease; keyboard/remote access and
focus return remain usable. Provider-header-dependent external playback and
physical TV clipboard usability remain unqualified.
