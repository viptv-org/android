# 04: Keep separately installed source add-ons distinct

**What to build:** The source picker offers Torrentio, TorrentsDB and TorrentioTB
as separate configured add-on providers. A shared upstream branding string must
not merge their choices or make their source rows indistinguishable.

**Blocked by:** Research must establish where identity or display names are lost.
**Status:** in-progress
**Triage:** needs-triage
**Owner:** provider_anime_research (GPT-6 Luna, max); implementation assigned after findings.

- [ ] Read-only inspection confirms the affected account's installed add-ons.
- [ ] Trace configured identity/name through discovery, shared core and both clients.
- [ ] Group by stable installed add-on identity and show the configured provider name.
- [ ] Filtering selects only that add-on's sources; exact Resume identity remains intact.
- [ ] Regression checks cover distinct add-ons sharing upstream Torrentio branding.

## Commits and evidence

Research in progress. No configuration or personal viewing data changed.
