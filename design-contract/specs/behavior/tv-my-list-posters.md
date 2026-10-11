# TV-MYLIST-POSTER-001 — TV My List uses 2:3 poster cards

Status: **proposed**, owner requested 2026-10-11 for every TV renderer
(Android TV, TV-web on Tizen/Vizio/webOS, native Roku). Source revision:
design `b5ad0f4` for the 1920 × 1080 TV frame, the card table in
[components.md](../../viptv-design-system/components.md#6-cards-and-tiles),
TV-034 carousel rules and the TV-044 Android TV Home trial. Implementation and
device evidence are pending; each renderer adopts this through its own pin.

## Intent

My List saves one image per title: the poster the viewer saw when adding it
(`favorites.poster`; no backdrop or still is stored). Posters are portrait art,
normally 2:3. The TV card table draws every poster as a 360 × 202 landscape
still, so the shared card projection falls back to the poster and
center-crops it to 16:9, leaving a strip of about 37 % of its height. My List
on TV shows that art at its own shape instead.

This changes TV **My List** cards only: the My List segment of the My List
screen and the Home **My List** shelf. Continue Watching (its segment and Home
shelf), Discover, Search, catalog shelves, episode rows and Live keep their
current TV cards. Phone, desktop and web cards are unchanged by this rule.

## Artwork

- Art is the title's poster (role `poster`), using the shared core poster
  projection (`presentation.posterImage`, including its IMDb poster fallback),
  not the landscape card art order (background → thumbnail → poster).
- The frame is exactly 2:3 and uses `cover` fit, so a poster that is not
  exactly 2:3 loses only a sliver. A landscape image is never cropped into the
  portrait frame.
- Image requests ask for portrait dimensions at the card's size (with the
  renderer's usual density scaling), never a 16:9 resize.
- Missing or failed poster: the existing missing-art block (surface-2, film
  icon, title in display type) filling the 2:3 frame.
- A live channel saved to My List keeps its logo or text monogram, contained
  and centred on surface-2 inside the same 2:3 frame; it is never cropped.
- Radius 16, no progress bar (My List entries carry no progress).

## My List screen (My List segment)

Reference coordinates are the 1920 × 1080 frame. Heading, segments, rail and
the grid's left edge keep their current positions (TV-web: heading y=54,
segments y=135, grid left x=192, grid top y=240; Android TV keeps its existing
content start and header layout).

| Property | Value |
|---|---|
| Card artwork | 200 × 300, radius 16 |
| Columns | 7 |
| Horizontal gap | 36 (column stride 236) |
| Caption | Title 24 px Onest 600, then subtitle 20 px Onest, one line each, ellipsis, max width 200; colours and focus brightening unchanged |
| Row stride | artwork height + the existing library caption/gap block (TV-web: 300 + 122 = 422) |
| Focus | Existing TV focus ring (4 px white, no scale) around the 200 × 300 artwork |

From x=192 the seventh column ends at x=1808, inside the 1824 px safe edge.

**Scrolling.** The grid scrolls by whole rows. The first row sits at the grid
top. Moving Down to a later row scrolls so the focused row's top is at the grid
top; the next row shows partly below it as the scroll cue. Up reverses this one
row at a time. Rows never stop part-way, and captions of the focused row are
always fully visible.

**Navigation.** Left/Right move one card, with key repeat, and do not wrap
between rows. Left from the first column opens the rail; Right from the last
card in a row does nothing. Down moves to the same column in the next row, or
to the last card when that row is shorter; Down on the last row does nothing.
Up from the first row focuses the selected segment. Short OK opens the title;
a 700 ms hold opens the existing My List options (`Remove from My List`,
`Cancel`) and suppresses release activation.

**Segments.** My List uses this poster grid; Continue Watching keeps its
4-column 360 × 202 grid. Switching segment focuses that segment's first card
and resets its scroll to the first row.

**Return and removal.** Returning from Title, Sources, playback, options or the
rail restores the same title identity with its row at the position described
under Scrolling. After `Remove from My List`, the grid reflows and focus moves
to the card now at the same index, or the new last card when the removed card
was last; an emptied list shows the existing empty state (`Your list is empty.`
/ `Add titles with the + button.`) with focus on the My List segment.

**States.** Loading, error (`Try again`) and paging behave as today: TV shows
no skeletons, pages remain 40 items and the next page loads as focus nears the
end of the loaded items.

## Home My List shelf

Where a TV renderer shows the **My List** shelf on Home, its cards use the same
2:3 poster artwork at **160 × 240**, radius 16, 36 px gap, with the same
caption block limited to 160 px width. Shelf heading, scrolling and focus rules
are unchanged for each renderer: TV-034 one-card movement and right-edge bounds
on TV-web, AND-TV-ROW-EDGE-001 and the TV-044 carousel on Android TV.

- TV-web (TV-034 Home): the shelf grows by 60 px, so the next shelf heading is
  416 px below the My List heading instead of 356.
- Android TV (TV-044 trial): the shelf fits the fixed 400 px Home shelf
  viewport (16 top + 20 px heading and 18 gap + 4 row padding + 240 artwork +
  caption). The focused card still supplies the hero.

## Acceptance

1. **TV-MYLIST-POSTER-01:** with fifteen saved titles whose posters are 2:3,
   open My List on a 1920 × 1080 TV. Seven 200 × 300 cards fill each row from
   the grid left edge, the seventh ending at or before x=1824; each shows its
   whole poster with title and subtitle beneath.
2. **TV-MYLIST-POSTER-02:** move Down twice and Up twice. Each Down scrolls one
   whole row with the focused row at the grid top and the next row partly
   visible; Up restores the previous row; Up from the first row focuses the
   My List segment. Left from column one opens the rail; Right/Back restore the
   card.
3. **TV-MYLIST-POSTER-03:** include a title with no poster, one whose poster
   fails to load, a live channel and a non-2:3 poster. They show the missing-art
   block, the missing-art block, a contained logo/monogram and a lightly
   cropped poster respectively; none shows a landscape image.
4. **TV-MYLIST-POSTER-04:** switch to Continue Watching and back. Continue
   Watching keeps 360 × 202 cards in 4 columns; My List returns to the poster
   grid with focus on its first card.
5. **TV-MYLIST-POSTER-05:** open a title from row 2, go to Sources and
   playback, then Back to My List: the same title is focused with its row at
   the grid top. Hold OK, cancel, then remove a middle title and the last
   title; focus follows the removal rule. Remove all titles and see the empty
   state.
6. **TV-MYLIST-POSTER-06:** on Home with a My List shelf, traverse it to its
   last card. Cards are 160 × 240 with whole posters; TV-web's next heading is
   416 px below; Android TV's shelf fits its 400 px viewport with captions
   visible. Other shelves keep their cards.

Record measurements as text per renderer. Emulator or browser evidence does not
establish physical-TV behaviour; Roku adoption is tracked separately and may
lag explicitly.
