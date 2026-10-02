# AND-TV-ROW-EDGE-001 — Android TV media rows reach the right edge

Status: **proposed** for native Android TV, owner requested 2026-10-02.
Source revision: design `6e74a9a` for the existing 1920 × 1080 TV layout,
Android adaptation and TV-034 carousel behavior. Implementation and device
evidence are pending. This rule is an Android TV exception to the older
96 px right safe inset for horizontal **media card rows**. The TV-034
1824 px final-card bound continues to govern Tizen/Vizio/webOS, and the
general 96 px right inset continues to govern Android TV headers, text,
actions, grids, filters, source lists and other non-media content.

## Geometry and entry

Every horizontally scrolling media-card row on native Android TV uses the
available viewport width through its right edge. This includes Home shelves
such as **Popular movies** and Continue Watching, Search result shelves,
Discover media shelves when present, My List and Continue Watching shelves,
and a series Details episode row.
The rule applies to the card viewport, including the row in its initial,
loading, populated and restored states. Its heading, count, season badge,
`Episode #` chip and other header/text controls retain their existing safe
alignment. Do not expand a whole screen or its text column to implement this.

At the 1920 px reference width, the row viewport begins at the established
content alignment of x=192 and reaches the right viewport edge at x=1920.
Keep each row's existing small content/focus padding inside that viewport;
the first card may begin after x=192, and Search may have additional small
outer focus padding. The final card and its focus treatment must be fully
visible within the viewport, without stopping at x=1824 or leaving a 96 px
blank tail. The existing Android focus border may draw inside the card;
do not assume an outside ring or force a particular final-card x coordinate.
Keep existing card dimensions,
including Home 320 × 180 and episode 360 × 200, the 36 px Home card gap,
caption layout and 4 px focus ring. Apply the same proportional geometry
when the TV reference is uniformly scaled to another viewport. Rows with
only a few cards keep their natural left alignment; do not stretch or center
cards to occupy the new width.

## Navigation and state

Remote Right/Left moves one card per press and ordinary key repeat; reaching
the first or last card does not wrap or create a blank trailing page. Scroll
only as far as needed to show the focused card and its ring, clamp to the
content bounds, and preserve the focused card identity and row scroll on
return from Details, Sources, playback, a dialog or rail navigation under
the existing screen rules. Pointer/touch horizontal scrolling, if available,
uses the same bounds. Loading, empty and error rows keep their current copy
and actions; they do not introduce a new right-edge affordance. Focus rings
remain visible at both ends and cannot be clipped by an outer right inset.

This is a viewport and scroll-bound change only. It does not change media
ordering, source selection, playback, episode selection, the hero Play/Resume
choice, row virtualization or non-media grids. Existing TV-web/Roku and
Android phone behavior remains governed by their own contracts.

## Acceptance

1. At 1920 × 1080, traverse a Home **Popular movies** shelf of more than
   twelve cards. The row viewport begins at x=192 and retains its small
   content/focus padding; the last card and focus treatment are fully visible
   within the right edge without a 96 px empty tail. Left and repeat
   navigation restore one-card movement.
2. Repeat on another Home media shelf, Search and Discover media shelves,
   a library shelf and a Details episode row with more than eight episodes. Headers,
   text and non-media controls remain inside their safe bounds, and card
   sizes and gaps do not change.
3. Enter Details, Sources, playback and the rail from a focused media card,
   then return. Its identity and visible row position are preserved. Test a
   one-card row, loading, empty and error states for stable left alignment.
4. Compare 1920 × 1080 with a uniformly scaled TV viewport and test remote
   focus on a native Android TV emulator or device. Record screenshot
   measurements as text; physical-device behavior remains unverified until
   exercised. No TV-web/Roku or phone parity claim follows from this rule.
