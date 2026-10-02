# 04: Keep separately installed source add-ons distinct

**What to build:** The source picker offers Torrentio, TorrentsDB and TorrentioTB
as separate configured add-on providers. A shared upstream branding string must
not merge their choices or make their source rows indistinguishable.

**Blocked by:** None; research established hidden zero/error producer outcomes.
**Status:** in-progress
**Triage:** ready-for-agent
**Owner:** source_picker_design and source_picker_tv (GPT-6 Sol, low), after Luna research.
**Contract:** SRC-PROVIDERS-001 at design d088071106e4d5479aea761469152e037f011a3c.

- [x] Read-only inspection confirms the affected account's installed add-ons.
- [x] Trace configured identity/name through discovery, shared core and both clients.
- [ ] Group by stable installed add-on identity and show the configured provider name.
- [ ] Filtering selects only that add-on's sources; exact Resume identity remains intact.
- [ ] Regression checks cover distinct add-ons sharing upstream Torrentio branding.
- [ ] Zero-result and failed observed producers stay visible with safe reasons;
      unsupported torrent formats do not claim playable support.

## Commits and evidence

Read-only inspection confirms separate enabled Torrentio, TorrentsDB and
Torrentio TB add-ons on the signed-in TV account. Their configured names and
IDs are distinct. The current shared projection and generated DTO retain the
add-on ID and source name, so an identity collision has not been established.
The read-only Interstellar discovery returned unsupported-format outcomes for
Torrentio and TorrentsDB, and 100 HTTP sources for Torrentio TB. Only providers
with usable rows reached the old filters. This is hidden outcome feedback, not
an established identity collision. Existing Rust streamPoll events retain each
observed producer and safe errors; clients join configured names and display
zero/error outcomes. Android implementation `81b4b3b` passed focused wire/name
regressions and app compilation. TV-web implementation and final acceptance
are pending. No configuration or personal viewing data changed.
