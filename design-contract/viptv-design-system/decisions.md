# Decisions

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
