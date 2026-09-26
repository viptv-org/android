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
