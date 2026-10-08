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
  share the same backdrop compositor and match the actual page ground. Android TV
  draws this composition only as the static fallback of
  [TV-042](#tv-042--shader-hero-backdrop); TV-web keeps it as its backdrop.
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
- SRC-PROVIDERS-001 (proposed, design revision of this commit): React and
  SolidTV source pickers list every separately observed account-owned producer
  in the Provider choice, including zero-result and safe failed producers.
  Stable installed identity and configured name distinguish add-ons that share
  upstream branding. Selecting a producer with no playable row shows the
  outcome copy in `../../viptv-design-system/components.md`; unsupported source
  formats explain the HTTP(S)-only limit. Pending producers and global discovery
  use the actual job state, without fake progress percentages. Partial playable
  rows remain selectable, late events retain filter/focus, and Retry/Back keep
  their existing cancellation and return behavior.

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
For SRC-PROVIDERS-001, use three distinct configured add-on IDs whose upstream
branding overlaps: two report zero playable HTTP(S) sources with safe unsupported
format outcomes while the third reports playable rows. Verify three named filter
choices, truthful empty outcomes, usable third-party rows, delayed/failure/Retry
states, focus stability and Back cancellation on React and SolidTV separately.

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

# TV-042 — Shader hero backdrop

Status: proposed. Implemented on Android TV in `viptv-org/android`
`683904d63fc86eb0482bfc10892cb93dda2a374d` (Home) and
`0e96bec39485a25c8aa2c9a6ca0aca41f7709c4d` (Details), branch
`feat/hero-shader-backdrop`. TV-042 applies to Android TV only. Shared TV-web
(Tizen, Vizio, webOS and browsers) and Roku do not adopt it: their GPUs and
runtimes are too weak for continuous shader rendering, so they keep the static
backdrops of TV-038/TV-040 and ROK-042.
Source revision is the design commit that introduces this section. Shader
sources, catalog and the renderer contract are in
[assets/hero/](../../assets/hero/README.md). Device and visual evidence is
recorded separately.

The TV Home hero and TV Details show the title's art in full 16:9 framing with a
slow drift, change it through a visual transition, and keep the left copy column
legible. The backdrop is decorative: it never takes focus, has no accessible
name, and changes no remote, hold, Back or focus-restoration behavior.

Geometry, in the 1920 × 1080 logical frame with the origin at the backdrop's
top-left:

- Backdrop: 1920 × 950. On Home it starts at the top of the 664px hero stage,
  extends beneath the first shelves and scrolls with the hero. On Details it is
  anchored to the top of the screen behind the scrolling page.
- Ambient fill: the whole backdrop. The current art, including a transition in
  progress, is cover-fitted to 1920 × 950, heavily blurred and laid at 0.6
  opacity over the page ground. Android's blur samples the art's mip chain at
  level 6 (64 art pixels per texel) through a 7-tap ring of radius 3.5% of the
  art size.
- Art box: 1280 × 720 at x 640–1920, y 0–720. It is 16:9, so 16:9 backdrops are
  not cropped; other aspect ratios are cover-fitted about the centre.
- Edge fade: inside the art box, the art dissolves into the ambient fill along
  its left and bottom edges; its top and right edges meet the backdrop edge. The
  selected edge style shapes the left band, which rises from x 666 to x 1024
  (2–30% of the art width) and always reaches the ground softly within x 640–768.
  The bottom fades plainly for every style, from ground at y 720 to full art at
  y 418 (42% of the art height), so no style draws a horizontal seam above the
  shelves. The `linear` baseline is a straight ramp over x 640–1075 and
  y 720–446.
- Text scrim, full backdrop, left to right: ground at x 0, ground at 0.9 opacity
  at 22% (x 422), 0.35 at 40% (x 768), transparent at 52% (x 998). It clears
  before the art's fade band ends and leaves the subject undimmed.
- Lower fade, full width: transparent at y 440 to ground at y 950.
- Ground is the actual page ground, including OLED black.

Motion and selection:

- Drift: each art starts at full frame and zooms toward 1.04×, with zoom
  `1 + 0.04 × (1 − e^(−t / 22 s))` where t counts from that art's appearance. It
  pans along a direction chosen at random for that art by at most half the zoom
  margin, so the art's own edge never enters the box. Both arts keep drifting
  during a transition.
- Transition: a hero change plays one transition from the catalog for that
  entry's `duration` (0.75–0.9 s). Every transition front-loads its motion: the
  incoming art leads within the first third of the duration and the rest is
  settling. The `fade` dissolve (0.4 s) is the baseline and is never drawn into
  the rotation. The first art a backdrop shows appears without a transition;
  unchanged art never transitions. A hero change on Home is a new hero title; on
  Details it is a different episode still or the return to the series art.
- Edge style: every hero change draws an edge style from the title's category
  pool. A different style replaces the current one through a soft noise wipe
  over the transition's duration; the same style stays in place.
- No-repeat selection: transitions use one shuffle bag and edges one bag per
  category. A bag holds each pool member once in random order and refills only
  when empty. A draw never returns the style currently shown while another pool
  member exists.
- Interruption: a change that arrives during a transition starts at once. The
  frame on screen becomes the outgoing art, so motion never jumps and nothing
  waits; superseded art never completes.
- Browsing: a change that arrives within 450 ms of the previous change plays the
  `fade` dissolve instead of a catalog transition, keeping fast focus movement
  calm. The catalog transition plays once focus rests.
- Frame rate: transitions and edge wipes render every display frame. At rest,
  drift and animated edge styles render every second frame to leave headroom for
  focus motion.

Category: shared Core owns the category rule and the genre pools through
`hero_edge_pool`. Android passes the title's type and genres and uses the
returned edge ids; it keeps no genre table of its own. The rule:

1. A genre of Animation or Anime (case-insensitive) selects Anime for a series
   and Animation for any other type.
2. Otherwise the category is the first genre, in metadata order, that has a pool.
3. Without a category the pool is every catalog edge except `linear`.

Pool ids missing from the catalog are ignored. On Details the category always
comes from the series or movie, including while an episode still is shown.

Details episode stills: when focus rests on an episode for 350ms, the backdrop
loads that episode's still; moving on sooner cancels it, so traversing episodes
queues no transitions. The still is decoded at no more than the art box size,
without upscaling, and is shown only if its decoded width is at least 60% of the
art box (768 logical px). A smaller, missing or failed still shows the series
backdrop instead. The last focused episode stays the subject while focus is on
Season or the actions; changing season returns to the series backdrop without the
settle delay. A movie shows only its own backdrop. If art fails to decode, the
current art stays.

Static compositor: the GL-free form of the same 1920 × 950 backdrop. The hero art
is cover-fitted to the whole backdrop with a 72px blur at 0.6 opacity over the
ground, and the sharp art is 1120 × 720 at x 800–1920, y 0–720, centre-cropped,
with no edge fade. Its text scrim runs from ground at x 0 through 0.92 opacity at
x 960 to transparent at x 1920; the lower fade is the same 440–950 ramp. Without
hero art it is the ground under both scrims. It has no drift, transition or edge
style. Android TV uses it:

- when system animations are disabled, evaluated as the screen opens: Android's
  animator duration scale is 0 or animators are disabled;
- when OpenGL ES (Android prefers ES 3 and accepts ES 2) is unavailable, the context or surface cannot be created, or a frame fails to
  render; the backdrop stays static for the rest of that screen visit;
- always on the Sources screen, so the GL renderer does not compete with player
  startup on weaker TVs.

A transition program that fails to compile plays as the crossfade; an edge style
that fails to compile uses `linear`. Returning from the background restores the
latest art without a transition. A GLES 2 renderer must provide the
ambient blur as described in the asset README; Android's GLES 2 path samples
without mipmaps and has not been measured against it. Android's shader path draws
no frame before its first art, so a title without hero art there is unverified
against the static ground.

Acceptance TV-042: at 1920 × 1080 and a scaled TV viewport, open Home with a 16:9
backdrop and measure the uncropped art box, ambient extent, both scrims and the
edge band. Move through more than 15 hero titles: each change plays a non-crossfade
transition, no transition repeats before the bag empties, and no consecutive edge
style repeats where the pool has two or more. Change heroes faster than a
transition and confirm each change starts at once from the frame on screen and
rapid changes dissolve. Scroll to the shelves and back; the
backdrop scrolls with the hero and the full hero returns. Compare edge pools for an
Animation movie, an Animation series, Horror + Drama, a genre without a pool
followed by a pooled genre, and no genres against Core `hero_edge_pool`. On series
Details, traverse episodes quickly (no queued transitions), rest on one with a
1280px still, one below 768px and one without a still, move to Season, then change
season; check that the edge pool stays the series'. Repeat with animations disabled,
forced GL context failure, a failing transition and edge program, background and
return, the Sources screen and OLED ground. Record rest and transition frame times
during focus movement on the lowest supported device; emulator runs do not qualify
physical TVs.
