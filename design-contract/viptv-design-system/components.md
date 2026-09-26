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
- **Search:** P is a pill field docked above the nav, with a fade behind it. D has a title-bar search (460 × 30, radius 9, "Search movies and series", a clear button once there is text, no shortcut hint) and a page search on web. T has a field with a caret plus the on-screen keyboard.
- **TV text entry** is always full screen: the field on the left, the keyboard on the right (a–z, 0–9, `: / . - _ @`, Aa, Space, Delete, Done, Cancel). Digits use the **PIN keypad** (1–9, ⌫, 0, Done, Cancel).
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
