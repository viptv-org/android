# Components

Each section gives the rules first, then sizes per platform, then the states that are drawn on the component sheets (`reference/components/CmpPhone1-3`, `CmpDesk1-3`, `CmpTv1-3`). The sheets are the visual truth: when this file and a sheet disagree, the sheet wins.

Platform keys: **P** = phone, **D** = desktop app and web, **T** = TV.

---

## 1. Buttons

**Rules**
- There is one accent **primary** per screen. TV has no accent buttons: the primary action wins by its position and by getting default focus.
- The **pill rule**: radius = height ÷ 2.
- Kinds: `primary` (accent fill, `on-accent` text), `secondary` (surface-3), `light` (off-white fill, `on-light` text), `outline` (transparent, 1 px 0.14 line, secondary text), `destructive` (surface-3 fill, danger text), `quiet` / text link.
- Weights: primary 700, others 600. All TV buttons are 700.
- Icons: P 20, D 18, T 28. The Play icon is a filled triangle.

| | P | D | T |
|---|---|---|---|
| Height | 54 (58 on title detail) | 48 | 72 (small pill 52) |
| Text | 17 primary / 16 | 16 | 26 |
| Side padding | 22 | 22 | 34 |
| Icon-only | 54 round | 48 round | 72 round |

**States**
- Hover (D only): secondary / destructive move to surface-4, the primary brightens 8%, light goes to pure white, outline / quiet get a 0.06 white wash.
- Pressed: scale 0.97. The primary darkens to 90%, secondary / destructive move to surface-2, light goes to `#DDDBD6`.
- Keyboard focus (D): `0 0 0 2px bg, 0 0 0 4px text-primary`. Never accent.
- TV focus: off-white fill, `on-light` text (destructive: `#B42318`), 4 px white ring and 0 24 60 shadow. Geometry stays fixed.
- TV unfocused: white at 0.12. TV pressed (OK held): `#DDDBD6`.
- Disabled: opacity 0.4.
- Loading: a spinner replaces the icon and the label reads "Saving…" / "Signing in…".

**Special buttons**
- **Split button (D):** Play plus a chevron that opens the source drawer. Each half hovers separately, and there is one focus ring around the pair.
- **TV source pill:** quality badge + provider name ("1080p LordStreams"). It opens the source panel.

---

## 2. Chips and segmented controls

- **Filter chip:** unselected = transparent with a 0.10 border and secondary text. Selected = surface-3 with a 0.22 border and primary text. P 44 tall, D 40 tall (36 inside the source drawer), T 56 tall.
- **Dropdown chip:** "Genre: Any ⌄". Once a value is set it reads "Genre: [Comedy]" in primary text.
- **Required chip:** carries a small "Required" tag. An empty required filter shows the empty state "Choose the required filters to browse this catalog."
- **Catalog chip (D):** always reads "Addon · Catalog", for example "Cinemeta · Popular".
- **Quality chips:** "All 12 · 4K · 1080p 5 · 720p 4". The count is in tertiary text.
- **Segmented control:** a surface-1 track with 4 px padding. The selected segment is an off-white fill with `on-light` text. P 44 (full width), D 40.
- **Group divider:** a 1 px × 20 vertical hairline between chip groups (type chips | sort chips).
- **TV:** selected but not focused = 0.16 fill. Focused = off-white fill with dark text.

---

## 3. Badges and indicators

- **Quality badge:** "1080p", "4K", "SD". 4 px radius (D) or 8 px (T), with a 0.16 white fill.
- **LIVE:** red `#FF5A4E` is used only here. It appears as a glass badge over art (a 6 px dot + "LIVE"), as a row label, and on TV as a solid red block.
- **UP NEXT** and **WATCHING:** uppercase eyebrow badges on episodes.
- **Best match:** an accent eyebrow on the first source row.
- **Progress:** P/D 4 px (3 px on small tiles), T 6 px. The track is white at 0.22 and the fill is accent.
- **Live dot:** 8 px, or 12 px on TV.
- **Carousel dots:** the active dot is an 18 × 6 pill, the others are 6 × 6.
- **Spinner:** 12 (P/D) or 18 (T). The track is `#45454B` and the arc is accent.
- **Buffering ring:** 48, centred over the video.
- **Key hints:** D uses `kbd` keycaps. T uses a legend: bottom-right, keycaps with a 2 px 0.28 outline and radius 10, for example "OK Select · BACK Close".
- **Status words (TV player):** PLAYING / PAUSED / BUFFERING / LOADING, 20/700 with +0.1em tracking.

---

## 4. Inputs

- **Text field:** surface-1 with a hairline border. P 54 tall with radius 16, D 48 with radius 12, T 80 with radius 20 (0.07 fill). Focus raises the border to 1.5 px at 0.22 and shows an accent caret. D also adds the keyboard ring. Error = a 1.5 px danger border plus an inline error line (an alert icon and danger text).
- **Password:** masked, with a show / hide eye button.
- **PIN:** 4–8 boxes (the drawn example has 6). Filled boxes show a dot, the active box shows the accent caret, and errors turn the boxes' borders danger. Error copy: "Incorrect PIN. Try again." / "Enter a 4–8 digit parent PIN".
- **URL field:** monospace value. Errors: "Enter an HTTPS manifest URL." / "That does not look like an addon URL." / "This addon is already installed."
- **Search:** P is a pill field docked above the nav, with a fade behind it. D has a title-bar search (460 × 30, radius 9, "Search movies and series", a clear button once there is text, no shortcut hint) and a page search on web. T has a field with a caret plus the on-screen keyboard; native Android TV uses its device IME under AND-KEYBOARD-001.
- **TV text entry:** Android TV uses a native editable field and device IME with Done/Cancel (AND-KEYBOARD-001); the app renders no character or PIN key grid. Other TV platforms use the full-screen design: the field on the left, the keyboard on the right (a–z, 0–9, `: / . - _ @`, Aa, Space, Delete, Done, Cancel). Digits use the **PIN keypad** (1–9, ⌫, 0, Done, Cancel).
- **Android TV episode jump (proposed, AND-EPISODE-JUMP-001):** a compact `Episode #` chip sits directly right of the season badge above the lazy episode row. It opens a native numeric-input dialog; a successful exact current-season match scrolls to and focuses the card. See [episode-number-jump.md](../specs/behavior/episode-number-jump.md) for input, error and Back rules. Roku and TV-web keep their existing episode controls.
- **Toggle:** 52 × 32 (T 72 × 42). On = accent track. Off = surface-3 (T 0.2 white). Disabled = 0.4 opacity.

---

## 5. Selection and settings rows

- **Choice list:** the current value shows a check and "Current". Unavailable options read "· unavailable" in tertiary text and cannot be selected. On TV, focus opens on the current option.
- **Radio rows:** used for settings choices such as Subtitle size (Small / System default / Large).
- **Settings group:** a surface-1 card with rows split by hairlines. Each row is icon + title (+ note) + value + chevron. Values never wrap. Rows that hold a toggle have no chevron.
- **Destructive row:** danger text, for example "Sign out" or "Remove addon".
- **Section nav (D settings):** a left list with uppercase group labels (Profile, Playback, This device, Account). The current item has a surface-2 fill.
- **TV settings rows** are 80 tall with radius 22. The focused row is off-white. A description panel on the right explains the focused row.

---

## 6. Cards and tiles

| Card | P | D | T |
|---|---|---|---|
| Poster | 3-column grid, 4:5, radius 16 | 172 × 258 (web 164 × 246), radius 14 | grid 360 × 202 still |
| Continue watching | 292 × 96 (thumb + text + play disc) | still 256 × 128 + caption | still 320 × 180 + caption |
| Episode | row | 272 × 150 + number, title, 2-line synopsis | 360 × 200 |
| Live | live-now card 200 wide | live tile 220 × 124 with a text monogram | guide blocks |
| Source row | quality badge + provider + file line + ▶ | same, in the drawer | same, in the panel (focused = off-white) |
| Profile tile | rounded square, radius 20% | same | same, focused = ring without scale |

- **Missing art:** a surface-2 block with a film icon and the title set in display type. Never stretch a small image to fill.
- **Source description window (proposed, SRC-OVERFLOW-001):** Every source row, for every provider and on phone, desktop/web and TV, reserves exactly two visible text lines for the file/description beneath its quality and provider facts. Wrap at the row's available width, including long unbroken filenames, hashes or URLs; clip overflow within that fixed two-line window. Increase the row's fixed height where needed so the title/badges, two full description line heights, padding and focus ring fit without overlap; all rows in a given picker use that same height, including blank and one-line descriptions and the Best match row. If the full wrapped description exceeds two lines, only the focused TV/keyboard row or hovered pointer row moves its text *upward inside the stationary two-line viewport*, revealing the final lines below. Start at the top, dwell 1.5 seconds, traverse at approximately one text line per 1.5 seconds, dwell 1.5 seconds at the bottom, then reset to the top and repeat while active. Stop and reset immediately on blur, pointer exit, row replacement or filter change. Touch rows do not animate without focus. Motion never changes row height, focus order or list position. With reduced motion enabled, leave the first two lines static. The full description remains available to assistive technology and Source details, without placing raw source URLs or secrets in logs.
- **Source discovery status (proposed, SRC-OVERFLOW-001):** Keep the existing accent spinner visible beside **Finding sources** while the picker is open and discovery is pending with no rows. Once at least one row arrives, show the same spinner beside **Still checking sources** until the actual in-flight discovery finishes or is cancelled. The status occupies a stable place above the list, is not focusable and does not displace or disable arriving rows. On completion remove the spinner/status; if no sources arrived, use the existing empty state. Announce state changes politely to assistive technology without repeatedly announcing each animation frame.
- **Producer outcomes (proposed, SRC-PROVIDERS-001):** The Provider choice includes each distinct account-owned producer observed in this discovery, including producers reporting zero playable sources or a safe failure. Identify installed add-ons by their stable configured add-on ID and display their configured name; never group on upstream branding or source title. A producer with no row is still selectable and its filtered view explains its observed outcome. While discovery is pending, say **Still checking [provider]** when no final outcome is known; after completion, say **No playable sources from [provider]** for a zero-result producer. For the safe `source_format_unsupported` outcome, say **[provider] returned formats this app cannot play. Only HTTP(S) streams are supported here.** Other safe producer failures use their server-projected message without exposing raw source data. A producer with playable rows keeps those rows usable even if another producer fails. Preserve the selected provider and focused source identity as events arrive; a disappearing account-owned producer must not leave a stale selectable filter. Do not show fabricated percentages, pending counts, torrent/magnet playback promises, or an automatic replacement source. The global discovery spinner follows the actual in-flight job, and Back cancels it.
- **Channel logos** are always text monograms (CNN, CNBC, abc) in display type.
- **Hover (D):** art dims to 0.38, an accent play disc appears, and an inset 2 px off-white ring is drawn (inset, so the card's clipping never cuts it).
- **Focus (D):** the keyboard ring outside the tile. **Focus (T):** a 4 px white ring without scale; the caption brightens.
- **Touch overflow (P):** a ⋯ button (44 target) on posters and continue cards opens the title menu. Long-press does the same.
- **Profile tiles:** a lock badge on PIN-protected profiles. "Add profile" shows as a dashed tile and is disabled at 12 profiles.
- **Avatar tiles:** the selected tile shows a check badge. Worlds are shown as chips, one grid page at a time (P 15, D and T 18) with Previous / Next.

---

## 7. Navigation

- **Phone bottom nav:** a floating glass bar (`rgba(32,32,35,0.94)`, 20 px blur), 64 tall, 28 px above the edge, 16 px side insets. Tabs are Home, Discover, Live, My List. The current tab is an off-white pill with dark text. Search is a separate 64 round button. It appears on tab screens only, with a 150 px fade behind it.
- **Phone headers:** a wordmark + avatar (Home), a screen title, back + sub-page title, or a glass back button over art.
- **Desktop title bar (Tauri):** 40 tall + hairline. It holds the wordmark, Back / Forward, a centred 460 search, and window controls. It is hidden in the fullscreen player. It has a pairing variant for sign-in (no search).
- **Rail (D):** 84 wide. Items are 64 × 58 with radius 14: Home, Discover, Live, My List (web adds Search). The bottom holds On TV, Settings and the avatar. The current item has a surface-2 fill. "On TV" lights up while the Watch on TV dialog is open.
- **TV rail:** 144 wide, icons only. Focusing it expands a 520 labelled menu over a 0.55 scrim, with the profile on top, then Search, Home, Discover, Live TV, My List, and Settings at the bottom.

---

## 8. Overlays

| | P | D | T |
|---|---|---|---|
| Container | Bottom sheet, auto height, radius 28 top, grabber | Centred dialog 460 (radius 18, padding 24); right drawer 460; anchored popover | Right panel 820, padding 64 / 96 / 120 / 64 |
| Scrim | 0.62 black | 0.55, body row only (title bar and rail stay bright) | 0.6 |
| Actions | Stacked full width, 54 tall | Stacked 48 tall | Rows 80 tall |
| Close | Grabber, or tap the scrim | × disc top-right, Esc | BACK |

- A destructive action uses danger text on surface-3. Cancel stays neutral and gets default focus on D and T.
- A **popover** belongs to one control (a menu for a card, the catalog, the genre, the provider or the engine) and has no scrim.
- **TV full-screen text panel:** long text (More info, Source details) scrolls inside an opaque surface-1 box with a scroll bar and the legend "▲▼ Scroll".
- The **title menu** (⋯ / right-click / long-press / TV hold OK) holds: Resume previous episode, Choose source, Mark watched, Watch from the beginning, Remove from Continue Watching, Add to My List, Cancel.

---

## 9. Feedback

- **Toast:** P sits above the nav (bottom 116), D sits bottom-centre 24 from the edge, T sits top-centre. Surface-3, radius 20. Notices last 5 s with text only. Errors last 4 s and carry Dismiss (a startup error carries "Try again"). Maximum width is 358 (P) or 560 (D).
- **Player notice pill:** 4 s, for example "The stream could not seek there."
- **Preparing playback:** a pill on P/D ("Preparing playback…") and a centred panel with a spinner on T.
- **Backend banner:** "Can't reach the backend", with the explanation and "Backend unreachable since [time]". It has Dismiss and clears itself when the backend answers.
- **Inline error:** an alert icon + danger text under the field.
- **Status line:** a spinner + tertiary text, for example "Still checking 2 addons".
- **Empty state:** a 52 round icon, a title and one line of help, sometimes with one action (for example "Browse Discover").
- **Loading more:** a spinner + "Loading more titles…" / "Loading more channels… [120] of [860]" at the end of a list. There are never Load more buttons.
- **Skeletons:** these have the real layout's shapes (featured card, posters, continue cards, list rows) and appear on P and D. TV has no skeletons: it shows a "Starting VIPTV…" cover at launch, then real content.

---

## 10. Player controls

| | P (portrait) | D | T |
|---|---|---|---|
| Layout | Header (back, title, S·E), video, timeline, transport row, tools row | Overlay: header top-left, timeline, left group (−10, play, +30, next) and right group (audio, subs, volume, info, fullscreen) | Status top-right, NOW PLAYING + title, timeline, controls row, legend |
| Play / Pause | 54 accent disc | 52 accent disc | 72 pill (focused = off-white) |
| Other buttons | 44 transparent | 44 transparent | 72 round, white at 0.12 |
| Volume | Hidden | Slider (disabled when the engine cannot set it) | None |
| Track popups | Full-width card above the tools row | 340 wide, above their button, clear of the timeline | Right panel (scrolling list) |

- **Timeline:** accent played fill, a lighter buffered segment, and a white knob. The time labels are `[12:48]` / `[52:10]`. Hovering (D) or seeking (T) shows a preview bubble above the bar.
- **Up Next:** a card at the bottom-right (D/T) or above the controls (P) with a still, "NEXT EPISODE", the title, "Starts in [8]", a progress bar, and Play now / Cancel.
- **Playback info:** key / value rows in monospace (decoder, transport, container, delivery, codecs, resolution).
- **Errors:** "This source could not be played" with Details (it expands to show HTTP status, request, error code and engine), then Retry / Choose another source / Back. "Playback could not be restored" appears on resume.
- **Live:** no timeline or next. It shows the channel name, "Live TV" and the live dot, and audio / exit only on TV.


## 11. TV remote (phone, Watch on TV)

**Rules**
- Off by default. The only way in is **Settings → This device → Watch on TV** (the row reads "Off" until a TV is paired). Nothing else in the app changes for people who never set it up.
- Setup is a pushed page flow, not a dialog: intro → choose your TV → PIN → connected. The intro says what the remote does and does NOT do (nothing playing on the phone moves to the TV) before discovery starts. Android SDK 36 does not require a runtime local-network prompt.
- **Remote button:** once a TV is paired, a 44 round button (surface-1, 1 px `line-outline` border, remote icon 20) sits in the header of the four tab screens (Home, Discover, Live, My List), left of the avatar. Its label is "TV remote: [TV name]". It never goes in the bottom nav. It can be switched off in Watch on TV settings. The first time it appears, show a one-time tip (off-white bubble with `on-light` text, "Got it") pointing at it.
- **The remote** is a bottom sheet (top 118, radius 30, `scrim-sheet`) with every control in the lower half, for one-handed use:
  - Header: TV tile 44 + TV name (17/700) + status line ("Connected · [IP]" / "Not reachable" with a wifi-off icon) + a 44 close button.
  - "Open VIPTV on TV": the accent primary, full width.
  - A Buttons / Swipe segmented control (232 wide, 40 tall).
  - Buttons mode: a 272 round pad (surface-2, hairline) with 88 arrow targets and a 104 off-white OK. Swipe mode: a 358 × 272 touchpad (surface-2, radius 28, faint dot grid, "Swipe to move · Tap for OK").
  - Bottom row: Back, Play, Pause (64 round, surface-3, labels 12/600 in `text-tertiary`), and a 2-part volume rocker (− / +).
- Vibrate on every press and keep the screen on while the sheet is open. Both are on by default and can be switched off in settings.
- **TV not reachable:** the Open button is replaced by a surface-2 card ("Can't reach [TV name]", "Turn the TV on and check it's on the same Wi‑Fi as this phone.", a light "Try again" button), and every control drops to opacity 0.4.
- **Pairing PIN:** 4 boxes 64 × 72 (radius 18). It pairs automatically when the 4th digit goes in, so there's no confirm button. Offer a "New PIN" text link.
- **Watch on TV settings (once set up):** a TV card (name, status, "Open the remote"), then Remote (Remote button, Vibrate on press, Keep screen on) and TV (Change TV; Forget this TV, danger). Forgetting the TV turns the feature off and removes the button.
- Phone **browsers** can't pair: they show the "Watch on TV" dialog pointing to the desktop app (`PhCastUnavailable`).

**Screens:** PhTvSetup, PhTvSearch, PhTvNoAccess, PhTvManual, PhTvPin, PhTvDone, PhHomeRemote, PhRemote, PhRemoteSwipe, PhRemoteOffline, PhTvSettings, PhTvForget.
