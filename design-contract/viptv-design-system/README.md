# VIPTV Design System

The design language for VIPTV on phone, desktop (Tauri app), web and TV. It is dark-only, with one accent colour and the thumb (phone) or the D-pad (TV) in mind.
Everything here comes from the redesign canvas: 26 updated original screens, 129 new screens and 9 component sheets.

```
viptv-design-system/
├── README.md               ← you are here: principles, tokens at a glance, how to use
├── components.md           ← every component: sizes per platform, states, rules
├── copy.md                 ← exact UI strings (empty states, toasts, errors, dialogs)
├── decisions.md            ← UX decisions made during the redesign, and open items
├── tokens/
│   ├── tokens.json         ← source of truth (W3C design-tokens format)
│   ├── tokens.css          ← CSS variables + type-role classes + focus recipes
│   ├── tailwind.preset.js  ← Tailwind preset
│   └── tokens.ts           ← flat TS object for React / React Native / Tauri
├── reference/
│   ├── components/         ← the 9 component sheets (phone / desktop & web / TV), WebP
│   ├── screens/img/        ← every screen rendered at 1:1, by platform, WebP
│   ├── screens/html/       ← every screen as standalone HTML (open in a browser; links work)
│   ├── screens/index.json  ← name, title, platform, size of every screen
│   └── assets/             ← images the HTML screens use
└── claude-code/            ← drop-in files so Claude Code builds UI with this system
```

## Principles

1. **Thumb first on phone.** Things you tap often sit in the bottom third: the nav, Play, the source picker and filters. The top of the screen is for looking at, not tapping. Every target is 44 px or more.
2. **One accent primary per screen.** The accent is for the main action, progress, spinners, the caret and the "Best match" label, and nothing else. Red is only for LIVE, and danger text for destructive actions.
3. **Fewer taps to play.** Play starts the best source. Changing the source opens a sheet or drawer, not a new page.
4. **Never stretch art.** Tiles keep fixed widths, so wider windows show more tiles. Low-resolution art is never scaled up to fill space.
5. **Exactly one focused element on TV.** Focus is an off-white fill (buttons and rows) or a 4 px white ring (image tiles). Focus never changes an element's size. TV has no accent buttons.
6. **Loading is a skeleton, never a "Loading…" screen.** There are no "Load more" buttons: more items load as you scroll.
7. **Accessible as drawn.** Use real `<button>`, `<a href>` and `<input>` + `<label>`. Icon-only buttons get an `aria-label`. Text contrast is at least 4.5:1.

## Platforms at a glance

| | Phone | Desktop (Tauri) / Web | TV |
|---|---|---|---|
| Frame | 390 × 844 | 1440 × 900 (web 1280 × 800, ultra-wide 2560 × 1080) | 1920 × 1080, safe area 96 × 54 |
| Navigation | Floating 64 px glass nav, 28 px above the edge, with a separate search button | 40 px custom title bar (back, forward, search, window controls) + 84 px rail | 144 px icon rail that expands to a 520 px labelled menu |
| Overlays | Bottom sheets over a 0.62 scrim, actions stacked full width | Centred 460 dialog, right 460 drawer, anchored popovers | Right 820 panel over a 0.6 scrim; full-screen text entry |
| Focus | None. The pressed state replaces hover | `:focus-visible` = 2 px off-white ring with a 2 px ground gap | Off-white fill or white ring, without scaling |
| Buttons | 54 tall (58 on detail) | 48 tall | 72 tall (52 small) |
| Minimum text | 11 px (eyebrows), 12 px captions | 11 px (rail labels, eyebrows) | 18 px |

## Colour

| Token | Value | Use |
|---|---|---|
| `bg` | `#0B0B0C` | App ground (`#000000` in OLED mode) |
| `surface-1` | `#161618` | Cards, fields, sheets, dialogs |
| `surface-2` | `#212124` | Hover / pressed rows |
| `surface-3` | `#2A2A2E` | Secondary buttons, selected chip |
| `surface-4` | `#34343A` | Secondary hover (desktop) |
| `text-primary` | `#F4F2EE` | Titles, labels, off-white fills |
| `text-body` | `#DAD8D3` | Synopsis and long text |
| `text-secondary` | `#B6B4AF` | Meta, values, unselected nav |
| `text-tertiary` | `#8F8D89` | Captions, hints, placeholders |
| `on-light` / `on-accent` | `#111113` / `#15130F` | Text on off-white / on accent |
| `accent` | `#F5C542` (default) | User can pick coral `#FF8B5C`, mint `#62D9BC`, periwinkle `#A3BCFF` |
| `live` | `#FF5A4E` | LIVE badge and dot only |
| `danger` | `#FF7A6E` (TV `#FF8A7E`) | Destructive text, errors |
| Lines | white at 0.07 / 0.10 / 0.14 / 0.22 | Hairline / chip / outline / strong (selected, focused field) |

Wire the accent through one variable (`--viptv-accent`) so the user's choice changes it everywhere.

## Type

There are two families: **Bricolage Grotesque** for display (titles, wordmark, channel monograms) and **Onest** for all UI text. Both are free on Google Fonts.

| Role | Phone | Desktop / web | TV |
|---|---|---|---|
| Screen / page title | D 34/700, −0.02em | D 40/700, −0.02em | D 56/700, −0.02em |
| Sheet / dialog / panel title | D 24/700 | D 24/700 | D 44/700 |
| Section heading | D 20/650 | D 22/650 | D 32/650 |
| Button | 17/700 primary, 16/600 other | 16/700 primary, 16/600 other | 26/700 |
| Body | 15/400, lh 1.45 | 16/400, lh 1.5 | 26/400, lh 1.45 |
| Label / row | 14/600 | 14–15/600 | 28/600 rows, 24/600 labels |
| Meta | 13/400 | 13/400 | 22/400 |
| Eyebrow (uppercase) | 11/700, +0.08em | 12/700, +0.06em | 18/700, +0.08em |

"D" means display (Bricolage). The full role list is in `tokens.json → type` and in `tokens.css` as `.viptv-type-<platform>-<role>`.

## Shape and space

- **Pill rule:** a pill-shaped control has radius = height ÷ 2 (a 54 button gets 27, a 40 chip gets 20).
- **Card radii:** phone 16–22, sheets 28; desktop 12–14, dialogs 18; TV rows and panel items 22.
- **Avatars** are rounded squares with a radius of 20% of their size.
- **Spacing** is 4-based, with the half-steps the screens use (2, 6, 10, 14, 18, 22). Phone gutter 16, desktop page padding 36 / 48 / 48, TV row gap 36.

## How to use it

### A. In the app code (web / Tauri / React Native)

- Plain CSS: `@import "tokens/tokens.css";`, then use `var(--viptv-color-surface-1)` and the `.viptv-type-*` classes. Set `--viptv-accent` from the user's setting and `data-oled` on `<html>` for OLED mode.
- Tailwind: `presets: [require('./tokens/tailwind.preset.js')]`, which gives `bg-surface-1`, `text-text-secondary`, `text-desktop-page-title`, `shadow-focus` and so on.
- React Native or other JS: `import { tokens } from './tokens/tokens'`.
- `tokens.css` also carries the base reset the screens assume: buttons have no browser background or border.

### B. With Claude Code

Copy this folder into your repo (for example as `design/viptv-design-system/`). Then do both of these:

1. Paste `claude-code/CLAUDE.md.snippet` into your repo's `CLAUDE.md`.
2. Copy `claude-code/.claude/skills/viptv-ui/` into your repo's `.claude/skills/`.

Claude Code then reads the tokens, the component rules and the matching reference screen before it builds or changes any UI. You can ask things like "build the TV source panel", and it will look at `reference/screens/img/tv/TvSources.webp` and match it.

### C. As a Claude design system

Claude can turn this into a **Design System artifact**: a browsable page of tokens and components that Claude's Design canvases, decks and prototypes can load. Once it exists, it can be installed into the VIPTV canvas so the colour pickers and text styles offer these tokens. Ask Claude to "make the VIPTV design system artifact from the export" to set it up.

## Screens

`reference/screens/index.json` lists every screen. File names follow the canvas: `Ph*` is phone, `Desk*` is the desktop app, `Web*` is the browser, `Tv*` is TV, and the originals keep their short names (`Main`, `Title`, `DeskHome`, `TvHome` and so on). Values in `[brackets]` are placeholders or proposals, not real data.
