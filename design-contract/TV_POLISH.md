# TV navigation and performance corrections — TV-034

Status: owner-requested corrections, 2026-09-25. Applies to the shared Tizen,
Vizio and webOS canvas UI. Implementation evidence belongs in tv-web TESTING.md;
physical device qualification remains separate. Related: design issue #4,
tv-web issue #2. Existing reference screens remain the visual baseline except
for the explicit corrections below.

- Profile selection enters Home immediately after selection succeeds. Prevent
  repeated activation while selecting; keep a failed selection on the picker
  with its error and focus intact. Manage profiles uses the Lucide Settings cog.
  Its 40 px pencil badge sits 16 px inside the avatar. Profile captions do not
  move with focus. Remove passive Select/Move/More hints from browsing screens;
  retain remote actions, hold and context-menu equivalents.
- The rail expands over content on every browsing screen, including Settings.
  Icons retain their collapsed centres (72 px horizontally), size and vertical
  position. Right/Back closes it and restores the originating control.
- Every carousel scrolls enough to expose the entire selected card and its
  4 px ring. Scroll is clamped to content bounds: the last card ends at the
  right safe edge (1824 px), with no surplus trailing page. Home cards remain
  320 × 180 with 36 px horizontal gaps; consecutive shelf headings are 356 px
  apart (card captions included), replacing the excessive 470 px separation.
  Search clipping includes the first card's focus ring.
- Home Play/Resume and Details are both 72 px tall and 228 px wide, sharing a
  baseline. Episode text is clamped in its own 420 px column; progress starts
  after that column. No text may overlap progress or controls.
- Series detail retains all episodes, exposes a focusable Season control, and
  scrolls each season independently. Down from actions enters Season, Down
  enters episodes, Up returns, Left/Right or OK changes season. Back restores
  the originating card and shelf. Empty seasons expose a clear empty state.
- Live cards open channel sources directly. Live playback shows LIVE plus
  audio, subtitles and exit, with no elapsed/duration, seek bar, pause, skip or
  next controls. Back restores the selected live card or guide position.
- Player and navigation icons use packaged Lucide assets with rounded strokes,
  including focused colour variants. No text glyph approximations or hand-drawn
  substitutes. Asset source and license accompany the export.
- Retain text nodes and glyphs while their replacement is prepared; title
  changes and once-per-second clocks must not introduce a blank frame.
- Startup shows an immediately usable shell, then queue/favorites and catalogue
  shelves independently as each request completes. Do not wait for live or the
  slowest addon. Request each home resource once per load and reuse its result
  for hero/shelf. Stale profile/route completions cannot alter the current UI.

Acceptance: exercise >12 cards, >8 episodes, multiple seasons, first/last cards,
Settings rail exit/re-entry, profile selection and failed selection, long episode
titles, rapid focus changes, clock updates, live source/play/Back, slow/failing
catalogues, and cancellation during profile or route changes. Use 1920 × 1080
and scaled TV viewports. Record request counts and actual startup timing.

## CW-SOURCE-BACK-001 — Continue Watching episode source return

Status: proposed for responsive React and SolidTV; source revision is the design
commit that introduces this section. A Continue Watching episode's Choose source
action opens manual sources for that exact episode and remembers the originating
Home or Continue Watching card, scroll and focus. Back or Esc from the picker
cancels pending discovery and opens the parent show's full title details with the
original season selected and episode card revealed. Details offers the other
episodes; selecting one opens that episode's ordinary manual source picker. If
parent metadata is pending, show the existing detail loading state; failure offers
retry and Back to the saved queue card. Picker Back starts no source and does not
resume playback. Back or Esc from details returns to the original queue card and
focus. Late responses from an abandoned route/profile cannot reopen either view.
Source details returns to its focused row, and successful source playback keeps
its established return path. Live source and other source-picker routes retain
their current Back destinations. Remote hold/Info, pointer context menu and
visible overflow still offer the existing queue actions without new semantics.

Acceptance CW-SOURCE-BACK-001: from a Home Continue Watching season 2 episode,
choose sources, let one arrive, then Back; confirm the parent show details at
season 2, choose a different episode and inspect its own sources. Back through
details restores the original Home card/scroll/focus. Repeat from the Continue
Watching list, with pending/failed parent metadata, and with Esc on React. Verify
no source starts on cancellation and live/other-route Back remains unchanged.
Record browser and physical TV results separately.

# TV-038 — Vizio navigation aligned with Android TV

Status: owner requested on 2026-09-26. Applies to the hosted TV DOM entry
(`?platform=vizio`, also shared with Tizen) and Android TV's Resume emphasis.
These rules supersede the TV reference's older paging, key legends and white
Resume styling. Implementation/browser/device evidence remains separate.

- Use explicit rail neighbors: Profile, Search, Home, Discover, Live TV,
  My List, Settings. Up/Down cannot escape into content across the Settings
  gap. At the first/last rail item, keep focus. Right/Back restores content.
- Move exactly one card per Left/Right press on every horizontal row, including
  episodes, search and library rows. Scroll only the distance required to show
  that card and its ring; do not recenter it to a one-third/two-thirds anchor.
  Clamp at content ends. Browser automatic focus scrolling must not fight the
  controlled animation. Up reveals earlier content in the upward direction.
- Home uses one scroll frame containing the hero and shelves. The hero and
  first Continue Watching row fit together; focusing that row leaves the
  viewport at the top. Lower shelves scroll naturally. Returning to the first
  row or hero reveals the complete hero. Cards remain 320×180, gap 36, and
  shelf spacing includes captions as in Android TV.
- Hero blur matches Android TV in the 1920px logical frame: the hero stage is
  664px high, the sharp art is 1120px wide at the right, and the full-size ambient
  image uses a 72px blur at 0.6 opacity. Shared left/bottom scrims keep copy legible
  and adapt to OLED ground. The art scrolls with the hero.
- Resume uses the selected accent with dark foreground on Android TV and
  Vizio, including while focused. Retain a white focus ring without changing
  its size. Other action/focus colors keep their established meaning.
- Hide native scrollbars throughout TV viewing surfaces while keeping scrolling
  and accessible focus operable. Do not reserve a scrollbar gutter on TV.
- Up from every episode returns to the Season selector, independent of horizontal
  offset. Down from Season restores the selected episode; Left/Right remain
  within the episode row. Season changes reset that season's row correctly.
- Discover has a content-type row first, followed below by a separately
  scrollable catalog/filter row, matching Android TV. All type/filter chips
  have an outline; selection adds a fill and stronger outline; focus remains
  explicit. Grid cards fit their cells and retain 16:9 art without cropping
  captions or creating horizontal overflow. Reuse the same card family.
- Remove passive Select/Options/Back/keycap legends from the TV app's screens,
  panels and player. Keep actual button labels, accessible names, remote key
  behavior, hold actions and actionable controls.
- Catalog pagination makes at most one request per in-flight query/page,
  preserves the chosen filter values, and aborts superseded work. Empty pages,
  repeated pages with no new items, or non-advancing cursors stop automatic
  paging even if an upstream incorrectly reports more. Missing required catalog
  filters show the existing input state and never start a request loop.

Acceptance: traverse the entire rail both ways; traverse >12 cards and >8
episodes forward/back at 1920×1080 and a scaled viewport; compare first/last
card bounds and vertical animation direction; reach Season from a middle/end
episode and return; exercise Movies/Series/Other catalogs and required search
filters; count requests for empty/repeating/cancelled pages; inspect all TV
scrollbars, chips, card captions and legends. Verify Android TV Resume and the
served Vizio asset hash after delivering the tested artifacts.

# TV-040 — SolidTV presentation and remote audit

Status: owner requested, 2026-09-26; implementation and hardware evidence remain
separate. Applies to the native SolidTV renderer delivered by TV-039. Android TV
at `b7e36df` is the comparison for Home composition and queue content.

- Keep glyphs visible across text, clock, color and focus updates. Prepare the
  replacement texture before swapping it; do not recreate visible text nodes.
  Center button labels and icons in the same vertical box, including Manage
  profiles. Use packaged Lucide controls, with a visible source-list icon.
- Android's hero content stage is 664px; its backdrop extends to 950px, with
  1120×720 sharp art, 72px ambient blur at 0.6 opacity, and the lower fade from
  440px to the ground at 950px. Preserve image aspect ratios. Home and detail
  share the same backdrop compositor and match the actual page ground.
- The first Continue Watching shelf remains fully visible without scrolling on
  focus. Subsequent shelf scrolling aligns a complete heading at the top safe
  edge; do not leave clipped heading fragments at either viewport edge. Keep
  selected cards and captions visible. Queue ordering and content use the same
  backend queue and shared core card projection as Android, with live content
  separate. Keep episode metadata and progress together; long titles wrap or
  truncate inside their own bounds without overlapping the progress indicator.
- Discover Up/Down traverses type, catalog/filter and result rows. Left/Right
  stays in the current row, scrolls to expose each complete focused chip, and
  reaches every catalog/filter (no fixed twelve-chip limit). Preserve selection
  and restore the catalog row when moving up from results.
- Every browsing screen, including the guide, retains the rail. Expansion keeps
  icon centers at x=72 and y=216/294/372/450/528/990, size28; the avatar stays
  56×56 at44,54. Only the label/panel area expands. Back/Right restores focus.
- Guide channels show their real contained logos, with monograms only on missing
  or failed artwork. Category chips are keyboard/remote focusable and scroll
  horizontally within the content safe edges. Guide program focus never moves
  outside the visible time window without advancing that window first.
- Live playback has a read-only program progress line labelled “Now” and the
  current program's end time; unknown guide data shows “Live” as the end label.
  This supersedes TV-034's omission of the line. It is never focusable or
  seekable; show audio, captions and exit, without pause/skip/next controls.
- Preparing playback shows the accent spinner above a horizontally centered
  label. Back cancels preparation. Search preserves separate addon/catalog rows
  with their source labels; remove result counts. Slow addons cannot hold up
  rows already available from other addons.
- SRC-OVERFLOW-001 (proposed, design revision of this commit): In the responsive
  React Choose a Source drawer and SolidTV source panel, apply the shared source
  row's fixed two-line description window to every provider. Wrap long tokens;
  slowly scroll only overflowing text from top to bottom while that row is
  keyboard/remote focused or pointer hovered. Reset to the top on blur, hover
  exit, replacement or filter change. Reduced motion holds the first two lines;
  assistive technology and Source details expose the complete description. Keep
  the accent spinner with `Finding sources` before any row and `Still checking
  sources` after rows arrive while real discovery is pending. Progressive rows
  remain selectable, and late arrivals never steal focus. Clear the indicator on
  finish or cancellation. Preserve Back, hold/Info, source identity and return
  focus behavior.

Acceptance: inspect 1920×1080 and 1280×720 Home, detail, profiles, Discover,
search, guide, preparation and live/VOD playback. Capture consecutive frames
across clock and focus changes, not just settled screenshots. Traverse >12
catalog chips and >12 guide categories, return from results and rail, repeat
horizontal movement through program windows, and verify live Up never focuses
or seeks the timeline. Include missing logos, long episode titles, a delayed
addon, cancellation and Back restoration. Record browser results separately
from physical TV qualification; never commit screenshots.
For SRC-OVERFLOW-001, inspect one-line, two-line and long unbroken descriptions
from multiple providers at responsive and TV widths. Verify constant row height,
first-to-last-line travel only for the active row, reset on exit/re-entry, static
reduced-motion display, full accessible text, and a spinner that persists during
partial results but clears on completion or Back. Exercise empty, filtered-empty,
error, source selection and return focus; physical TV behavior remains unverified
until a device run.

# TV-041 — Bounded Home loading and player inactivity

Owner requested 2026-09-27. SolidTV must fetch content only for visible Home
shelves and one shelf ahead, with at most two shelf requests in flight. Keep the
catalog descriptor list and profile queue/favorites, but retain catalog payloads
only for the visible/prefetch window and one preceding row (at most five rows).
Evict distant payloads, cancel obsolete requests, and re-fetch on return while
restoring remembered row/card positions. Empty results are skipped; a failed row
has an actionable retry state. No unbounded background sweep through catalogs.

Mount only that small visible/prefetch row window, with at most six horizontal
cards per mounted row. Destroy distant rows and the offscreen hero rather than
merely clipping them, and unmount Home when leaving it. Configure a 64 MiB GPU
texture cleanup threshold with an earlier idle target; this is a renderer cache
budget, not a claim about total process RAM or video-decoder memory. Preserve
focus through loading, eviction, profile changes and cancellation.

Player chrome hides after five seconds of no user interaction during playback.
Time/buffer updates never reset this deadline. Pause, active seeking, track
selection, dialogs and Up Next keep their controls visible; resuming starts a
fresh inactivity interval. The first remote action after hiding reveals controls
without also activating an invisible action. Hide the player shade with chrome.

VOD seekbar focus shows its round handle even before seeking. Draw actual buffered
ranges behind played progress; keep gaps, clamp them to the title timeline and
never invent buffering when the engine supplies none. Live progress stays passive.
Move the lower player information, timeline and control group 48px downward on
the 1920×1080 frame, retaining the 54px bottom safe area (largest control ends at
1004px). Keep the top title/status fixed.

Acceptance: initial idle Home with 100 catalogs must not fetch all catalogs;
traverse twenty shelves and return, measuring request concurrency, cached rows,
mounted nodes, image requests and GPU cache use. Slow/error/empty rows and route
changes must not steal focus or leak pending requests. Check idle/live/VOD controls
under continuous time updates, reset by input, paused/panel/seek exceptions,
first-key reveal, focused thumb, real disjoint buffered ranges, and 1080p/720p
bottom geometry. Browser measurements do not qualify physical low-memory TVs.
