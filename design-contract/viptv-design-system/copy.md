# UI copy

These are the exact strings used on the screens. Text in `[brackets]` is a placeholder or proposed value, not final copy. Sentence case everywhere except eyebrows (uppercase) and product names.

## Names

- **My List** is the saved-titles list, everywhere (the nav tab, page title, menu item and toasts). Do not use "Library" or "Watchlist".
- **Continue Watching** is the in-progress list: the second segment of My List, and the Home shelf ("Continue watching" as a shelf heading).
- **Watch on TV** is the pairing / remote feature (the desktop app). The rail item is "On TV".
- Search placeholder: **"Search movies and series"** (desktop and web show no keyboard-shortcut hint).

## Empty states

| Where | Copy |
|---|---|
| My List | Your list is empty. Add titles with the + button. |
| Continue Watching | Nothing in progress. Titles you start watching appear here. |
| Discover, no catalogs | No catalogs are available. Add or enable a catalog addon in Settings. |
| Discover, required filter | Choose the required filters to browse this catalog. |
| Discover, no titles | No titles yet |
| Search, no results | No matching titles |
| Live, empty category | No channels here yet. Choose another category. (TV / desktop: "…another filter.") |
| Live search, phone | No channels or programmes match your search. |
| Live search, desktop / TV | No matching US channels or current programmes. Try a channel name, section, or another title. |
| Sources, finding | Finding sources. Sources appear here as they arrive. |
| Sources, none | No sources available. Check your add-ons in Settings. |
| Sources, filtered out | No matching sources. Choose another provider or quality. |
| Live details, no guide | No guide information. You can still watch this channel. |
| Local mode, no addons | Install an addon to start browsing. |

## Toasts, notices and loading

- Added to My List
- Removed from Continue Watching (a dialog with Undo / Done)
- Could not save your profile. Please try again. (error, Dismiss)
- [VIPTV could not start.] (startup error, Try again)
- The stream could not seek there. (player notice)
- Preparing playback…
- Loading more titles… / Loading more channels… / Loading more channels… [120] of [860]
- Still checking [2] addons
- Can't reach the backend. The connection was refused, so the backend is down or unreachable. Retrying every 10 seconds; this clears itself once the backend answers.

## Errors

- The username or password is incorrect.
- Incorrect PIN. Try again.
- Enter a 4–8 digit parent PIN
- Enter a name to continue.
- Enter a value for this required filter.
- Enter an HTTPS manifest URL.
- That does not look like an addon URL.
- This addon is already installed.
- This source could not be played / The selected source did not become ready in time.
- Playback could not be restored
- The TV could not complete this request.
- VIPTV receiver is unavailable (HTTP [status]). The TV was not launched.
- VIPTV receiver did not serve its app bundle. The TV was not launched.
- Could not check the VIPTV receiver. Check your connection and try again.
- The TV accepted the launch request. Check its screen to confirm VIPTV opened.
- This code expired. (TV pairing, with "Try again")

## Dialogs

| Dialog | Title | Actions |
|---|---|---|
| Sign out | Sign out of this device? (TV: "Sign out of this TV?") | Sign out (danger) · Cancel |
| Delete profile | Delete profile | "Delete [name]? This permanently removes this profile's watch history, favorites and preferences." Cancel · Delete profile (danger) |
| Remove addon | Remove [Addon]? | "Its catalogs leave this device." (local mode) · Cancel · Remove (danger) |
| Manage addon | Manage [Addon] | Enable / Disable · Remove addon (danger) · Cancel |
| Parent PIN | Enter parent PIN (TV sign-out: "Enter parent PIN to sign out") | "Enter the parent PIN to continue." (TV: "Use your remote or a connected keyboard.") · Done · Cancel |
| Watch on TV | Watch on TV | "Connect to a Vizio SmartCast TV on the same network." |
| Watch on TV, browser | Watch on TV | "TV pairing is available in a VIPTV desktop app with SmartCast support…" · Close |

## Sign-in

- Sign in to VIPTV. Your shows, channels and progress. All in one place.
- Create an account or recover access
- Use another device · Reconnect
- Use without an account: "Your addons and playback stay on this device. No account, profiles, or sync." (local builds only)
- TV: "Visit this address, then enter the code shown below." [viptv.syek.tech/device] [AB12CD34EF]
- Desktop: Continue in browser. "Sign in securely in your browser. This window will connect automatically."
- Profiles: Who's watching? · Manage profiles · Add a profile · Edit profile · "A space for their favorites, shows, and discoveries." · Find your favorite · "[468] avatars. Pick a world, then pick your character."
