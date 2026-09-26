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
