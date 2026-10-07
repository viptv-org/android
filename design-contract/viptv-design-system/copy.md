# UI copy

These are the exact strings used on the screens. Text in `[brackets]` is a placeholder or proposed value, not final copy. Sentence case everywhere except eyebrows (uppercase) and product names.

## Names

- **My List** is the saved-titles list, everywhere (the nav tab, page title, menu item and toasts). Do not use "Library" or "Watchlist".
- **Continue Watching** is the in-progress list: the second segment of My List, and the Home shelf ("Continue watching" as a shelf heading).
- **Watch on TV** is the pairing / remote feature (the desktop app). The rail item is "On TV".
- Search placeholder: **"Search movies and series"** (desktop and web show no keyboard-shortcut hint).
- Android TV native input: **"Results"**, **"Done"**, **"Unlock"**, **"Cancel"**. The device owns IME action labels; the app shows no custom keyboard or jump-to-results hint.
- Android TV episode jump (AND-EPISODE-JUMP-001): chip **"Episode #"**; dialog **"Jump to episode"**; field **"Episode number"**; actions **"Go"**, **"Cancel"**; invalid input **"Episode not found in this season."**
- Android completed episode (AND-EPISODE-WATCHED-001): visible and accessible badge **"Watched"**.

Responsive player picture mode (WEB-PLAYER-FIT-001): visible toggle **"Fit"** / **"Fill"**; accessible actions **"Fill video"** / **"Fit video"**.

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
| Live search, phone | No channels match your search. |
| Live search, desktop / TV | No channels match your search. |
| Sources, finding | Finding sources. Sources appear here as they arrive. |
| Sources, provider pending | Still checking [provider] |
| Sources, provider empty | No playable sources from [provider] |
| Sources, provider unsupported (proposed, SRC-TORRENT-GATEWAY-001) | [provider] returned formats this app cannot play. Choose another source. |
| Sources, unsupported API format (proposed, SRC-TORRENT-GATEWAY-001) | This source format is not supported. Choose another source. |
| Sources, in-progress label before rows | Finding sources |
| Sources, in-progress label after rows | Still checking sources |
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

## Native playback failure reasons (SRC-TORRENT-NATIVE-001)

These strings replace the generic native startup explanation only when a
platform reports the corresponding observed fact to shared Rust. Unknown
failures use `native_playback_failed`. A deadline is not proof of absent peers
or seeders. Raw engine/player text, hashes, URLs, paths and credentials are never
presentation inputs. Existing recovery actions and geometry remain as specified
in [the native contract](../specs/behavior/torrent-native-android.md).

| Observed reason | Exact copy |
|---|---|
| `native_playback_failed` | The selected source could not start on this device. Try another source or retry playback. |
| `native_acquisition_timeout` | This device took too long to prepare the selected source. Try another source or retry playback. |
| `native_metadata_timeout` | Torrent file information did not arrive in time. Try another source or retry playback. |
| `native_payload_limit` | The device's playback cache has no room for this stream. Stop another stream, choose another source or retry playback. |
| `native_storage_unavailable` | This device could not reserve storage for playback. Free some space, choose another source or retry playback. |
| `native_cache_unavailable` | The device's playback cache is unavailable. Choose another source or retry playback. |
| `native_metadata_invalid` | This source has invalid or unsupported torrent file information. Choose another source. |
| `native_file_unavailable` | The exact file selected by this source is missing or does not match its file information. Refresh the sources or choose another source. |
| `native_authorization_expired` | This playback session has expired. Start playback again to reconnect. |
| `native_network_unavailable` | This device could not connect to the selected source. Check your connection, choose another source or retry playback. |
| `native_codec_unsupported` | This device cannot decode the selected source's audio or video format. Choose another source or retry playback. |

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


## Watch on TV (phone remote)

- Settings row: **Watch on TV** · "Use this phone as a Vizio TV remote" · value "Off" until a TV is paired.
- Intro: "Use this phone as your TV remote" / "Works with Vizio SmartCast TVs on the same Wi‑Fi as this phone. It's off until you set it up." Points: "Arrows, OK, Back, play and volume for your TV." · "Opens VIPTV on the TV in one tap." · "What's playing on this phone stays here. Nothing is sent to the TV." Note: "VIPTV searches your local network only to find your TV." Button: **Set up a TV**.
- Choose your TV: "Looking for Vizio TVs on your Wi‑Fi…" · "Don't see your TV?" / "Turn the TV on, then check it's on the same Wi‑Fi as this phone. It can take a few seconds to show up." · **Enter IP address**.
- Permission off: "VIPTV can't look for TVs" / "Local network access is off for VIPTV. Turn it on in your phone's settings, then come back here." · **Open phone settings** (Manual IP entry does not bypass a network permission denial).
- IP entry: "Enter your TV's IP address" / "Find it on the TV under Settings › Network." · **Connect** · **Cancel**.
- PIN: "Enter the PIN on your TV" / "[TV name] is showing a 4-digit PIN." · "Pairs as soon as all 4 digits are in." · **New PIN** · **Cancel**.
- Connected: "Connected to [TV name]" / "You can control it from this phone now." · Remote button: "Shows at the top of Home, Discover, Live and My List." · **Open the remote** · **Done**.
- One-time tip: "Your TV remote" / "It stays up here on Home, Discover, Live and My List. Turn it off in Settings › Watch on TV." · **Got it**.
- Remote: "Connected · [IP]" · "Not reachable" · **Open VIPTV on TV** · Buttons / Swipe · "Swipe to move · Tap for OK" · Back · Play · Pause · Volume. Offline: "Can't reach [TV name]" / "Turn the TV on and check it's on the same Wi‑Fi as this phone." · **Try again**.
- Settings: Remote button ("At the top of Home, Discover, Live and My List") · Vibrate on press · Keep screen on ("While the remote is open") · Change TV ("Pair a different Vizio TV") · Forget this TV ("Turns off Watch on TV").
- Forget: "Forget [TV name]?" / "The remote button goes away. You can set up a TV again at any time." · **Forget TV** (danger) · **Cancel**.
