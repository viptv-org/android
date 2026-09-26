# AND-035 — Native Android phone and TV design adoption

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
Production promotion requires tested immutable artifacts and verification of the
running backend/transcoder plus served TV asset hashes; Git push is not a deploy.
