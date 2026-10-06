# Decisions

## Android completed episode indicators (AND-EPISODE-WATCHED-001)

Proposed from the 2026-10-02 owner request. Android TV and phone episode cards
show `Watched` beside their episode-number caption using the current profile's
saved completion fact. Core and backend own completion semantics; the renderer
does not infer completion from a progress percentage or another episode.
Press, hold and repeated remote input retain existing card actions; the badge
is informational and does not change source selection, Back or restored focus.
No artwork readiness gate or animation is introduced. Loading/error progress
does not fabricate completion; details remain browsable. Reopening details and
switching profiles must project fresh profile progress. A completed fact hides
the partial progress bar; resetting that fact removes the badge.

On narrow phones, the badge wraps immediately below the episode-number caption
when both cannot fit at full width. Keep the full label, existing artwork and
44 dp options target; use layout constraints rather than a fixed phone breakpoint.

Acceptance: render completed, partial, unwatched and missing-progress episodes
together on TV and phone; verify only completed cards display and announce
`Watched`. Replace a watched item with an incomplete item and switch profiles;
no old badge remains. Verify a 1,410-episode lazy row near its end and ordinary
source Back keep identity, selected episode and focus. Native emulator evidence
and physical TV compatibility are recorded separately. Other platforms retain
their pinned episode presentation until they explicitly adopt this addition.

## Settled with the product owner

1. **Up Next:** a card with a countdown ("Starts in [8]") plus Play now / Cancel. It does not take over the whole screen.
2. **TV subtitles and audio:** one scrolling list in the right panel. The list opens focused on the current track.
3. **TV pairing code expiry:** a persistent expired state (the code struck through, "This code expired.", Try again focused, QR dimmed), plus a web page where the code is typed ("Link your TV").
4. **Protected profiles:** a lock badge only. The PIN is asked for when the profile is opened.
5. **Hiding from Continue Watching:** one menu item, "Remove from Continue Watching", followed by an Undo dialog.
6. **Touch overflow:** a visible ⋯ on phone cards (long-press still works).
7. **Sign-out copy:** "Sign out of this device?" (TV: "Sign out of this TV?").
8. **Desktop keyboard focus:** an off-white 2 px ring with a 2 px ground-coloured gap, shown on `:focus-visible` only. Never accent.
9. **Naming:** "My List" everywhere. It has two segments, My List | Continue Watching. History and counts are dropped from the tabs.
10. **Avatars:** hide broken categories and compute the count shown in the header.
11. **TV focus:** focused tiles, profiles, rows and controls keep their size. Use the white ring or off-white fill without scale.
12. **TV reliability corrections:** [TV-034](../TV_POLISH.md) defines carousel bounds, stable rail geometry, profile activation, season navigation, live-only controls and progressive startup. These owner-requested corrections supersede conflicting static reference hints and spacing.
13. **Native Android:** [AND-035](../ANDROID_DESIGN.md) adopts the current phone and 1920×1080 TV design, replacing the historical Android Roku reconstruction while retaining native Compose, Media3 and shared core behavior.

## Design decisions made during the redesign

- The phone nav floats, with a separate search button. Search docks its field above the nav so typing stays in thumb reach.
- Desktop tiles have fixed widths. Wider windows (up to 2560 × 1080 ultra-wide) show more tiles and more hours in the guide, and never stretch art.
- Desktop overlay scrims cover the body row only, so the title bar (window controls) and rail stay usable.
- TV "More info" is its own button on the title screen. It opens a full-screen text panel, because the synopsis is clamped to 2 lines.
- Live programme details show the channel and time slot ("CNBC · [9:00] – 12:00") above the description.
- Manage profiles on TV shows a pencil cue on every tile, so it is clear that OK opens the editor.
- Skeletons match each screen's real layout. TV instead shows a "Starting VIPTV…" cover, then content.
- Buttons get a CSS reset (`background: transparent; border: 0`) so no browser default styling shows through.

## Open items (values to confirm in the app)

- `[Bracketed]` values are placeholders: times, counts, file names, track names, IP addresses, the device URL, the Up Next countdown.
- Whether the parent PIN length is fixed or 4–8 digits (drawn with 6 boxes, error copy says 4–8).
- The phone PIN helper copy ("Enter the parent PIN to continue.") replaces the TV-only "Use your remote or a connected keyboard." on phone and desktop.

## 14. Native Android follow-up (AND-036)

The 2026-09-26 owner request replaces phone device-code-only authentication with
native username/password sign-in, keeps pairing optional, and requires original
source playback on Android. Source feedback, player lifetime, provider groups,
stateful library actions, insets and the scrolling/blurred TV hero are specified
in `../../ANDROID_DESIGN.md#and-036--native-sign-in-direct-playback-and-interaction-corrections`.

## 15. Native Roku complete TV audit (ROK-043)

The owner extends the later TV corrections to Roku, including accent Resume,
all addon catalog shelves, bounded lazy loading, three lower Home shelves,
TvTitle composition and TvLive guide. See [ROK-043](../ROKU_DESIGN.md#rok-043--complete-tv-screen-audit-and-catalog-correction).


## Phone TV remote (Watch on TV)

Android owner follow-up AND-039 keeps pairing visible across backgrounding,
cancels abandoned TV challenges, and uses a 20 px glyph in the same borderless
44 px avatar-style header slot as the profile picture. Key traffic does not
change the launch button's appearance. See the Android contract for acceptance.

- **Opt-in, in Settings.** Most people never use it, so it is off by default and adds nothing to the app until a TV is paired.
- **The button goes in the tab-screen headers**, next to the avatar. The bottom nav and search stay identical for everyone. Remotes are opened occasionally, and the controls themselves sit in the thumb zone of the sheet, so reaching up for the button once is fine. A one-time tip shows where it went.
- **The remote is a sheet, not a page**, so the app stays underneath and closing it returns you where you were.
- **Two modes:** big buttons (the default) and a swipe touchpad. The last-used mode is remembered.
- **Honest setup:** the intro says nothing is mirrored before discovery. Android SDK 36 has no runtime LAN prompt; denied access gets recovery, never a fake permission prompt.
- **The PIN pairs automatically** on the 4th digit.
- **Forget this TV turns the feature off**, so there's no separate master switch.
# Reliability follow-up — REL-001 (proposed, 2026-09-28)

Owner-approved scope: backend, shared core, Android, native Roku, browser/TV and
desktop. Extend existing surfaces; do not change source selection or auto-play.

- API errors retain stable `error_code` and a safe, actionable message. Provider
  connection capacity is distinct from request rate limiting, upstream access
  denial, expired sources, network failure and unsupported delivery. Never show
  URLs, cookies, credentials, stack traces or raw response markup. Unknown errors
  use a bounded safe message or status-specific fallback; retain Retry/Choose
  another source/Back and the selected source/time. Auth policy codes keep their
  existing meaning. Connection-limit copy: `This IPTV provider has reached its
  connection limit. Stop another stream or choose another provider.`
- Home renders usable saved/initial rows immediately. Remove Roku's metadata and
  artwork readiness cover, including re-entry triggers; loading images must not
  intercept input. Keep account restoration truthful and retryable. Refreshes
  preserve visible rows and focus. Metadata work follows visible items plus a
  bounded lookahead; no artwork-completion gate. Missing art uses existing ground.
- Roku time labels use a verified numeric font path without `monospacedDigits`
  substitution. Position/duration stay readable, including hours and zero values;
  validate on hardware before claiming physical acceptance.
- TV Home scroll-out/return must recreate visible text, controls and images after
  texture cleanup. Source/provider panels remain opaque surface-1 with the
  existing scrim. Verify repeated scroll and panel opening under memory pressure;
  do not hide a renderer defect by increasing memory without bounds.
- Browser header profile buttons open Settings. Switch profile remains an
  explicit Settings action; keep dedicated TV profile-selection navigation.
- Native SmartCast remotes expose Power and Mute with accessible labels, existing
  44dp-or-larger phone / desktop button geometry, and the TV's reported name
  (fallback `Vizio TV`). Only explicit presses send power/mute; no repeat/hold.
  Power off is not treated as expired pairing. On app return, silently verify the
  retained pairing with bounded retries, keeping the current remote layout/name.
  Show offline recovery only after verification fails; show pairing recovery only
  for rejected credentials. Background discards queued keys, never the PIN page.
- Android launch uses the bundled VIPTV mark on the existing dark ground, with
  matching system-bar colors and a compact in-app branded restoration state.
  No artificial minimum delay, artwork wait, or replayed splash on resume.

Acceptance: capacity/429/auth/404/5xx/timeout/malformed responses show distinct
safe outcomes on each client; slow or failed artwork never blocks Home; repeated
TV scroll/panel cycles survive cleanup; profile-to-Settings-to-profile navigation
works; delayed reconnect has no offline flash, eventual failure is recoverable,
and power/mute/name work via the synthetic SmartCast boundary. Measure Android
initial/offscreen requests and retain first-frame, device and fixture evidence
separately. Existing typography, palette and focus/Back contracts remain in force.

## Native Android TV keyboard (AND-KEYBOARD-001)

The 2026-10-01 owner request replaces Android TV custom character and PIN grids
with native Android editable fields and the installed device input method.
Search keeps separate progressive catalog shelves; Back dismisses native input
before leaving its screen/dialog. Roku and TV-web retain their current inputs.
The Android contract records focus, Results, secret input and return acceptance.

## Native Android TV media-row edge (AND-TV-ROW-EDGE-001)

The 2026-10-02 owner request extends horizontal Android TV media card rows,
including Popular movies and Details episodes, through the right viewport edge.
This explicitly supersedes the general 96 px right safe inset and TV-034's
1824 px final-card bound **for native Android TV media rows only**. Headings,
text and non-media content keep their safe inset. The
[media-row contract](../specs/behavior/android-tv-media-rows.md) defines the
geometry, focus restoration and acceptance cases.

## Android TV episode-number jump (AND-EPISODE-JUMP-001)

The proposed compact `Episode #` chip beside the season badge opens a native
numeric-input dialog. Exact current-season episode metadata controls the jump;
success only scrolls and focuses the card. Invalid input remains editable in
the dialog, and Back first hides the IME. [The behavior contract](../specs/behavior/episode-number-jump.md)
defines the full state and acceptance scenarios. This does not change the
shared Core hero Play/Resume rule or require Roku/TV-web adoption.

## Desktop pointer and playback corrections (DESK-PLAYBACK-2026-10-03)

Owner approved 2026-10-03: the native desktop client uses pointer interaction.
Disable desktop keyboard shortcuts, Tab navigation, spatial navigation and
programmatic button arrival focus globally; text fields remain editable by
clicking and typing. Remove keyboard legends from desktop source pickers.
Clicking the player backdrop reveals controls and never hides them; existing
inactivity hiding and popup dismissal remain.

Relative skip clicks accumulate from the latest requested target until the
engine confirms it, in either direction. Ignore transient zero/old clock
reports while a seek is pending. Duration becomes available independently of
seeking, and the timeline draws actual reported buffered ranges. New source
selection retires the outgoing presentation before preparing the replacement.
If that explicit replacement fails, keep the player empty: do not restore the
previous video's duration, tracks, picture or audio. Managed seek/track/next
operations retain their separate recovery rules.

Returning from playback to the same open source selector retains its provider
and quality filters, including browser/titlebar Back. Opening a different title
or episode starts with All. Double-clicking the desktop titlebar during playback
uses the player's fullscreen action. Both it and the fullscreen button hide app
chrome and resize the native picture and controls to the fullscreen viewport;
exit restores the windowed layout. Outside playback, titlebar double-click keeps
the normal maximize action.

Render player controls as crisp vector icons with legible skip numbers. Native
desktop source rows use a smooth 16px radius and a 3px accent border for the best
match/selected source. Show configured provider artwork; missing artwork uses
a named monogram rather than an unknown-quality dash. All providers includes
both observed add-on and IPTV producers. Native track titles take precedence;
unknown-language tracks receive numbered labels, with codec facts when known.

Acceptance: rapid forward/backward presses and pointer scrubbing retain the
latest target without flashing zero; buffer/duration work before a skip; new
source preparation shows no outgoing video/audio; selecting All includes both
producer kinds; source artwork/borders remain smooth; desktop Tab/arrows/media
keys do not activate or navigate controls; text editing still works. Home uses
frame-bounded scroll measurement, cached card geometry and offscreen rendering
containment. Verify scrolling with many loaded shelves on the desktop host.
