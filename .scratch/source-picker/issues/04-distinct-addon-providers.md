# 04: Keep separately installed source add-ons distinct

**What to build:** The source picker offers Torrentio, TorrentsDB and TorrentioTB
as separate configured add-on providers. A shared upstream branding string must
not merge their choices or make their source rows indistinguishable.

**Blocked by:** Research must establish where identity or display names are lost.
**Status:** in-progress
**Triage:** needs-triage
**Owner:** provider_anime_research (GPT-6 Luna, max); implementation assigned after findings.

- [x] Read-only inspection confirms the affected account's installed add-ons.
- [ ] Trace configured identity/name through discovery, shared core and both clients.
- [ ] Group by stable installed add-on identity and show the configured provider name.
- [ ] Filtering selects only that add-on's sources; exact Resume identity remains intact.
- [ ] Regression checks cover distinct add-ons sharing upstream Torrentio branding.

## Commits and evidence

Read-only inspection confirms separate enabled Torrentio, TorrentsDB and
Torrentio TB add-ons on the signed-in TV account. Their configured names and
IDs are distinct. The current shared projection and generated DTO retain the
add-on ID and source name, so an identity collision has not been established.
One live title returned 38 sources from a single visible provider. Research is
checking a common title and discovery results before changing grouping logic.
No configuration or personal viewing data changed.
