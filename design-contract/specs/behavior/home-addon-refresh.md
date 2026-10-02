# HOME-ADDON-001 — Automatic account add-on refresh

Status: owner-requested behavior, specified before implementation on 2026-10-01.
Implementation and device qualification remain separate. Initial adapters are
Android and the responsive/SolidTV viewing client; Roku adoption is pending.

## Intent and scope

After an account imports, adds, enables, disables, edits or removes an add-on,
Home reflects the committed configuration without restarting the app. Existing
catalog eligibility, artwork, source and continuation policy remain authoritative.
A stream-only add-on does not invent a Home shelf. Catalogs requiring user input
remain in Discover rather than becoming empty synthetic Home rows.

## Entry, timing and lifetime

An authenticated, selected-profile Home checks a private account-scoped opaque
configuration revision immediately on entry and foreground return, then every
15 seconds while Home is visible and the app is foreground. Hidden/background
clients and other screens stop checking. Returning checks immediately. An
unchanged revision does not fetch catalogs or their contents. Checks do not
overlap; committed changes during an in-flight refresh cause a follow-up check
and refresh rather than being acknowledged as already rendered.

Only committed add-on configuration changes advance the revision. A canceled,
failed or library-only import does not. Changes in another account do not.
Session, account, profile or origin replacement cancels old work and rejects
late responses. Revision failures retain the usable Home and retry at the next
scheduled check. Unsupported older servers retain ordinary Home behavior.

## Presentation, input and recovery

There are no new controls, copy, assets or geometry. Keep the current shelves
usable while refreshing; do not display a global loading cover, reset scroll,
take focus from a rail/modal or interrupt playback. Add and remove source-labeled
shelves using existing catalog identity and ordering. Preserve the focused
shelf/card identity and scroll position when it survives. If it is removed,
choose the nearest surviving card at the previous position, then the Home rail
when no card survives. Hero follows the retained selection rather than resetting
to the first catalog. My List and Continue Watching retain their profile scope
and local mutations. Empty or unavailable catalogs use existing empty/error
behavior; a failed configuration refresh leaves the prior usable shelves intact.

Tap, click, keyboard, remote press/repeat, 700 ms hold and Back retain their
existing meanings. Leaving Home cancels the watcher; returning restores Home
focus and immediately checks configuration. Background refresh must not cancel
an unrelated active detail, source picker, playback or profile interaction.

## Acceptance

- HAF-01: Import a new eligible catalog add-on while Home remains open. Its
  shelf appears after the next successful 15-second check plus normal loading.
- HAF-02: Multiple unchanged checks make revision requests only; no full catalog
  or catalog-content reload occurs. Concurrent changes coalesce safely.
- HAF-03: Disable/remove a focused catalog. Its shelf disappears and focus falls
  back deterministically; adding another shelf preserves a surviving selection.
- HAF-04: Background or leave Home: no polling. Foreground/re-entry checks
  immediately, retaining scroll and focus until fresh results are ready.
- HAF-05: Switch account/profile or sign out during a delayed check/load: old
  results cannot populate the new Home. Other accounts' revisions stay unchanged.
- HAF-06: Fail a check or refresh: current shelves remain usable. Recovery
  reconciles the latest committed revision. A change racing a load is not lost.
- HAF-07: Roll back an add-on transaction, or import only viewing data: no add-on
  revision change. Commit selected new add-ons: revision becomes observable.
- HAF-08: Catalog-only, stream-only, empty and required-filter add-ons follow
  existing shared policy. No provider alias or source choice is added by adapters.

Browser/controller tests establish effects and focus behavior. Physical TV,
codec, HDR and playback qualification are outside this change's claims.
