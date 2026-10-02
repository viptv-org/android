# 12: Hero Resume Back opens the episode list

**What to build:** The large Home hero's Continue Watching Resume entry must
return from Choose a source to the parent show's info and resumed episode.
The next Back returns to Home and restores the originating hero focus.

**Status:** in progress
**Owner:** Luna research, Sol implementation.
**Related contract:** CW-SOURCE-BACK-001; this entry point was missed by ticket 03.

- [ ] Reproduce the hero Resume entry separately from the queue card.
- [ ] Cover both episode-shaped and series-shaped saved episode cursors.
- [ ] Preserve exact Resume behavior, parent identity and asynchronous cancellation.
- [ ] Cover the hero's manual source action and non-episode Home returns.
- [ ] Verify the native hero -> source -> info -> Home flow without changing history.
- [ ] Run the Windows gate, independent review, narrow commit and push.
