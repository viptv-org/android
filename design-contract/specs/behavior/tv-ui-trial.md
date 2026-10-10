# TV-044 — Fixed Home and compact title controls

Status: proposed owner-requested Android TV trial. Source: owner's October 10,
2026 request. Other TV renderers remain pending approval; this proposal overrides
Home hero actions and season navigation only for the trial. Existing immutable
Android design pin remains the baseline until the owner approves a design commit.

Home uses the 1920 × 1080 TV frame. The backdrop and hero stay fixed and have no
focus targets or buttons. The focused card in any Home shelf supplies hero data.
The bottom 400px viewport shows one shelf, beginning at x=104; Up/Down changes
shelves. Left/Right slides the selected card to the leading edge until the end
of the row, where focus moves across the remaining visible cards. Continue watching remains the first populated
shelf. Remove Recently watched live TV and all live-TV shelves from Android TV Home.
Live TV remains available through the sidebar. Empty/loading Home retains its
noninteractive explanatory state and sidebar access.

The carousel name sits above its cards in 20px bold uppercase secondary text,
with an 18px bottom gap. The hero has no shelf label. Its content starts at
x=104 and is vertically centered within y=54..680, with 12px vertical gaps: 118px title or logo, episode text when supplied, progress on its own line, facts,
and up to five synopsis lines at 675px width. No hold hint is displayed. All
carousel cards start at x=104, without an additional horizontal inner gutter.
Progress sits below episode text for series, or in its place for movies. Core
still determines progress eligibility and card actions.

Android TV row changes use a 180ms EaseOutCubic entering slide (32px in the
navigation direction) and fade from 65% opacity. Only cards and their captions
move; the carousel heading stays fixed. Horizontal movement uses a 220ms
FastOutSlowIn tween without spring overshoot. Focus moves immediately; a newer
direction cancels the prior animation, and disabled system animations use
immediate changes. The fixed hero does not animate. Short OK retains the card
action; a 700ms hold opens its existing queue/title options and suppresses release
activation. Back closes options and restores the originating card. Returning
from title/source/player preserves row/card selection.

Android TV Home uses the original feed data and never requests replacement
metadata when a card is focused. Its image, logo and text do not switch to another
metadata source. Continue Watching history entries resolve their title metadata
with bounded concurrency before the row is published, retaining saved progress
through the shared Core merge. This initial history hydration does not run on focus
or replace populated catalog feed data. Phone enrichment remains separate. Large Home enrichment inputs
use compact episode identity/display facts; shared Core still performs the joins.

Fresh Home waits for Continue Watching before assigning initial card focus,
or uses the first available row after loading completes when the queue is empty.
Vertical navigation tracks row IDs, targets a visible leading card, reclaims
keyboard mode and coalesces rapid input to the latest request. TV controls remain
focusable when mouse/touch input mode is active. Sidebar return targets the visible
row rather than an off-screen first card.

Sidebar focus uses one white capsule moving over 220ms. Dark icon/text ink is
clipped to that capsule's actual geometry, so colors follow its position rather
than switching early or using independent timers. Navigation labels use a stable
font weight and do not become bold when focus changes. Disabled system animations
remain immediate.

Hero and TV Title logos crop transparent outer padding in the cached image-load
pipeline and align their visible pixels to the left. Backdrop geometry is unchanged.
A nonfocusable vertical dash indicator sits beside the carousel: the active dash
is brighter and three times longer, inactive dashes are muted. It tracks the visible
row and adapts spacing to the row count; it is hidden for a single row.

TV Title exposes Play/Resume and an icon-only plus/check control with accessible
Add to My List/Remove from My List labels. Remove the source-summary button and
More info button. Hold Play/Resume for 700ms opens manual source selection;
release does not also play. Failed metadata keeps retry and list access.

Replace previous/next season cards and TV season dropdown with one horizontally
scrollable pill row: `Episode #`, then available seasons in numeric order,
including `Season 0` when supplied (extras/OVAs), then `Season 1` and later seasons.
Do not fabricate absent seasons. Episode # opens numeric entry; invalid numbers
show `Episode not found in this season.` Cancel restores its pill; success focuses
the matching episode. Selecting a season focuses its first available episode.
Empty seasons retain the selector. Episode short/hold actions remain unchanged.

Collapsed sidebar buttons span x=20..84; page content begins at x=104, providing
20px between screen/button and button/content. Expanded sidebar overlays content
and retains its remote navigation and Right-to-restore behavior.

## LIVE-044 — Playlist switch, all platforms

Proposed shared design: place `Playlist: <name>` above category filters on Live TV.
TV uses a pill and remote choice panel; phone uses a touch pill and bottom sheet;
web/desktop uses a labelled selector. Show account-owned enabled live playlists
only, using safe display names and exact server catalog IDs. Keep selection local
to browsing; never modify the account default. Short OK/tap opens the chooser,
Up/Down selects, OK applies, Back/cancel returns to the selector without changes.

Changing playlist cancels pending channels/categories/guide requests, clears their
cursor/window state, resets category/search to All, and loads the selected
playlist. Never reuse an old playlist's categories/cursors or silently substitute
another playlist. Loading preserves the selector; failure shows retry for the
same selection, empty shows `No channels here yet.` No playlists shows
`No playlists available`. Return from playback preserves the selected playlist.
The current backend exposes catalog selection but lacks a paired-device-readable
playlist inventory; Android implementation needs that safe read-only contract.

## Acceptance

TV-044-01: focus cards across two shelves; hero stays fixed and follows every card,
with exactly one visible row and no hero focus stops.
TV-044-02: series/movie progress occupies a separate line without overlapping
facts; no Home action buttons or Recently watched shelf remain.
TV-044-03: hold a card, dismiss options, open/return from title/source/player, and
restore the same card; rapid directional input cancels stale restoration.
TV-044-04: season 0/1/many seasons scroll as pills; episode jump handles success,
invalid input and cancellation; no season cards remain.
TV-044-05: Title exposes only Play/Resume and plus/check in loaded states; manual
sources remain reachable by hold and focus returns to Play/Resume.
TV-044-06: collapsed sidebar has equal 20px gutters on Home, Title, Discover,
My List, Search, Live TV and Settings.
LIVE-044-01: switch between two playlists; old async responses cannot change the
selected playlist, categories or channels. Test failure, empty, cancel and player
return separately per platform. No platform parity claim follows from this trial.
