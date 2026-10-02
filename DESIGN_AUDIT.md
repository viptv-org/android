# TV Guide source audit — 2026-10-02

Contract: design `85a20e918d44af28d20caa52997b1d307922cf29`, committed
`TvLive`, `TvLiveDetails` and `TvLiveSearch` HTML boards. Implementation:
Android `3f21e41` (`GuideScreen.kt`, `DesignUi.kt`, `AccountScreens.kt`). This is
a fresh comparison with the immutable board export, rather than an assumption
that the earlier device SSIM applied to the current Guide. All measurements
below use the 1920×1080 TV coordinate frame. TV layout remains unchanged.

| State | Pinned reference | Current Android | Disposition |
| --- | --- | --- | --- |
| TvLive top summary | x192/right96/y54, 300px high; LIVE chip plus channel; time/progress/remaining; next programme; 480×270 preview | Same outside gutters; stacked LIVE text/title/channel-time/description; no preview, progress, remaining or next-programme line | Presentation gap; requires a dedicated design-first implementation slice |
| TvLive filters | y374; 56px controls with 6px gaps; `All US channels` | Flow after summary; `All`, 14dp gaps; existing My channels/Recent/provider/Search controls | Copy/geometry gap; retained existing remote paging controls |
| TvLive grid | y460, 48px time header, 300px channel column, 100px rows; 72px logos; channel numbers | Flow-positioned header with Channels and Earlier/Now/Later; 266px channel blocks, 272px header label, 94px rows plus 10px spacing; 56px logos; no numbers | Presentation gap; outside gutters and proportional programme-time placement retained |
| TvLive programme cells | Time and programme title, current/future fills, current progress line; now marker through grid | Title-only 22px text in 16px-radius cells; selected fill, no cell-time/progress or now marker | Presentation gap; current/future activation remains separately testable |
| TvLive hints | Bottom-right OK Watch, Menu Details, Up/Down Channels, Left/Right Time | No visible Guide key-hint row | Discoverability gap; no TV layout change in this audit |
| TvLiveDetails | 820px right panel, padding 64/96/120/64; Watch channel now and Close, OK/BACK hints | Same 820px panel width, uniform 64px padding; title/channel-time/description and Watch live; Back dismissal, no explicit Close or key hints | Copy/actions/padding gap |
| TvLiveSearch | Full-screen Search Live TV dialog with canvas key grid | Native Android text field/IME and Done/Cancel through TextEntry | Native keyboard substitution is specified by AND-KEYBOARD-001; full search-result composition still needs separate comparison |

These are measured source differences, not a pixel similarity score or a
physical-device acceptance claim. The no-TV-layout-change standing constraint
preserves the present composition; the gaps remain open under design#6.

# Native design audit — 2026-09-26

Contract: AND-037 at `8c3b0e5` and the pinned VIPTV reference screens/components.
Scope: native Android phone and Android TV, API 36 x86_64 emulators. Phone
390×844dp portrait and 844×390dp landscape; TV 1920×1080 reference coordinates.
Screenshots stay private in ignored `qualification/artifacts`.

| Surface | Finding and final behavior | Evidence |
| --- | --- | --- |
| TV keyboard | The inherited Left rule overrode every key. Individual keys now own their grid neighbors; only the first column exits to the rail. Content focus also collapses the rail. | D-pad a → b → a stayed at x192/288 without opening the sidebar; first-column exit and return checked. |
| TV search results | Type grouping erased catalog identity, and spatial entry could choose a distant card. Each addon/catalog/type keeps a separate stable shelf, with type/count metadata to distinguish equal names. | First-result entry, final-card visibility and query reset matched the returned catalog order. Left returned to the remembered key. Fast-forward works from the keyboard's first row. |
| Progressive search | Successful catalogs remain usable while slower catalogs finish; duplicate titles across catalogs remain separate. | HTTP/native-core tests cover delayed catalog arrival, equal display names and per-catalog deduplication. Three catalog requests at most run concurrently. A delayed movie catalog arrived while the series result Naruto remained focused and fully visible. |
| Phone portrait player | Opaque tool discs and Material slider geometry differed from PhPlayer. Tools are now transparent 44dp targets, Play/Pause is the sole 54dp accent disc, and fullscreen stays at the right edge. | Compared rendered control hierarchy with PhPlayer; long title and episode metadata remain readable. |
| Phone landscape player | Portrait transport/tool rows had been stretched across landscape. Landscape now uses one bottom controls row with transport left, tools right, and the timeline above. | Rotation retained the paused decoded frame; cutout/system insets remained clear. |
| Playback timeline | Buffer was omitted; Material thumb/track constraints shifted the track relative to labels. One canvas now renders the real buffered range, played range and circular knob using the same seek coordinates. | Portrait track/time edges x32–748; landscape x128–1656 including the device cutout. Touch seek reached the decoded target and retained pause. |
| Up Next, phone/TV | Automatic continuation skipped the visible card. The resolved successor now has an episode still, title, ten-second countdown and Play now/Cancel. | Paused countdowns stayed fixed on both devices; Play now started E2 from a paused E1. Cancel suppressed reopening at completion. Expiry started E3 automatically. |
| Up Next input/lifetime | TV media buttons were swallowed by the card; a paused outgoing episode could leave Play now paused. Both paths are corrected. | Native remote Pause works while the card owns focus; explicit Play now sets new playback intent without changing Back recovery of the outgoing episode. |
| Episode/source context | Selecting an episode discarded parent artwork before opening Sources. The shared core enrichment now retains episode identity and series context. | Source/continuation artwork uses the appropriate episode and series fields, without substituting a poster for an episode still. |
| Home and Continue Watching | Existing hero layout and compact shelf spacing retained. | Inspected phone Home; previous full-hero/first-row focus behavior remains in the unchanged Home implementation. |
| Profiles, details, source picker, settings | Reviewed packaged assets, text hierarchy, action sizing, panel/field bounds and return navigation. Shared focus correction removes the lingering expanded rail on content. | Native profile selection, series/episode entry, source drawer and grouped phone settings inspected; long text truncates inside its layout rather than covering controls. |

This is functional and visual emulator evidence, not a claim of universal pixel
parity or qualification of physical remotes, legacy Android renderers, HDR/DRM,
all device sizes/font scales, or every upstream stream. The controlled local
video is test-only and is never bundled into the normal app or installed as the
source origin on the user's signed-in emulators.
