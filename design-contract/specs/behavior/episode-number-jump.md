# AND-EPISODE-JUMP-001 — Android TV episode-number jump

Status: **proposed** for Android TV. Source revision: design `8ce8971` for
the existing title-detail layout and AND-KEYBOARD-001 native input contract.
Implementation and device evidence are pending. The Roku and TV-web episode
views retain their pinned behavior until separately adopted.

## Intent and entry

On a series Details screen, a viewer can jump to an episode in the **currently
selected season** without paging through a long row. Place one compact,
focusable `Episode #` chip immediately to the right of the season badge in the
episode-row header. Preserve the badge, episode count, card size and row
placement. The chip is visible only when that row contains episodes. Its
visual height follows the existing AppChip in that header; exact width and
spacing are unmeasured and must fit without obscuring the count at TV size.
Its accessible name is `Jump to episode number`.

Pressing OK on the chip opens an app dialog titled `Jump to episode` with a
native Android numeric editable field labelled `Episode number`, plus `Go` and
`Cancel` actions. The field is initially empty and focused; the installed
device IME supplies its own key layout and action label. The app supplies no
custom digit grid. A connected keyboard may enter digits and submit with
Enter. Accept decimal digits only, including `0` when an episode with number
zero exists. Do not infer the valid range from a count or maximum.

## Resolution and focus

On `Go`, parse the complete input as a nonnegative whole number. Match only an
episode whose season and episode number metadata exactly equal the currently
selected season and entered number. Treat an absent season number as season
`1`, consistently with the displayed season grouping. Do not match a title,
list position, rounded number or episode in another season. If duplicate
metadata exists, choose its first occurrence in the displayed episode list.
The row may contain thousands of entries; locate the match in data and scroll
the lazy row to its index, then focus that card after it is composed. Do not
truncate or eagerly compose the row to support jumping.

Success closes the dialog and leaves the selected season unchanged, with the
matching card visible and focused. It does not open a source picker, begin
playback or write watch history. The shared Core `initialEpisode` remains the
sole owner of the hero Play/Resume choice; this navigation control never
changes that value. Normal OK/hold actions on the focused episode card remain
as specified for the episode row.

Empty input, malformed input, overflow, and a number with no exact match in
the current season keep the dialog open and show the inline error `Episode not
found in this season.` The field retains its text and focus for correction.
`Cancel` dismisses without changing season, row scroll position or card focus,
and restores focus to the invoking `Episode #` chip. The first Back while the
native IME is visible hides the IME and leaves the dialog open; a second Back
closes the dialog with the same restoration as Cancel. If the underlying title
or season changes while the dialog is open, dismiss stale input without
applying it to a different row.

OK release activates the chip once; a held OK has no extra action and must not
also submit the dialog. Left/Right/Up/Down move focus according to the
surrounding Details controls when the dialog is closed, and follow the native
field/action focus order while it is open. Directional key repeat follows the
platform's ordinary focus movement, without repeated submission. Pointer or
touch activation opens the same dialog and uses the same resolution. Screen
readers announce the field label, error and focused target card.

## Acceptance

1. Open a series with a season badge and episode row. Focus `Episode #`, press
   OK, enter an existing number and submit. The same season remains selected;
   the row scrolls to and focuses the exact card. Playback and watch history
   remain unchanged.
2. Use sparse episode metadata, including a missing number between listed
   episodes and a listed episode zero. The missing number shows the inline
   error; zero succeeds only when episode zero exists. A number present only
   in another season fails. Duplicate numbers focus the first displayed card.
3. Submit empty, malformed and overflow input. The dialog stays open with the
   error and editable text. Correct the value and succeed without reopening.
4. Cancel, then repeat with Back while the IME is open. The first Back hides
   the IME; the second dismisses. Both dismissal paths restore the chip and
   retain the row's previous scroll/card position.
5. With at least 1,410 episodes in one season, jump near the end and back to
   an early number. Confirm the target is focused without truncation or eager
   composition. Remote, connected keyboard, pointer and accessibility input
   use the same exact match; physical Android TV IME and focus behavior remain
   unverified until device testing.
