# Private native acquisition ownership

`NativeTorrentScopeOwner` sends transient server/account/profile/stable device
authorization-epoch facts to Rust's `nativeTorrent.authorizationScope` decision.
Tokens and playback generations are absent from those facts. A `keep` decision
retains the exclusively locked cache and random bridge scope. Changing authority,
revocation, sign-out or malformed identity closes the previous owner before any
new admission. Failed settlement prevents further native scope creation for that
owner. Facts and random scope receipts have redacted representations and are
never saved to account storage or used to derive directory names.

`ownControl(control, generation)` registers the control lifetime before any
negotiation/start request, including grants that have not produced an acquisition.
Back/start failure uses `retireControl` to dispose that pending authority.
`NativeTorrentCoordinator.prepare(control, generation)` reserves a 4 MiB native
metadata allowance and registers `NativeTorrentOwnedWork` before scheduling IO.
The owned work exists before `beginSelected` can return; cancellation during
that call attaches to the late acquisition and any late handle. The acquisition
stays alive through the complete grant lifetime. Blocking native effects use
independent worker threads, including cancellation joins, so an OS IO call cannot
consume the coroutine worker needed to cancel another grant.

The startup budget is measured from the first ready grant acceptance recorded by
the control owner. Kotlin subtracts elapsed suspend-aware time before the native
begin call and caps the result at the live grant remainder. Metadata decoding,
queue waits and readiness publication consume that same thirty-second budget;
renewal does not reset it. The main dispatcher rejects a prepared result whose
generation, total startup deadline or live grant no longer matches.

The strict adapter supplies Rust-validated hash/input/index and an optional
verified selected length to the generic engine. Unknown lengths resolve from
vetted exact-index metadata before storage allocation. Supplied lengths must
match. Full-payload charging includes unselected files. `metadataFileCount()`
reports the complete vetted cardinality separately from the selected-only
`files()` result, preserving nonzero metadata indices without exposing other
file lengths. Shared core validates these facts before the local URL is adapted.

`NativeTorrentCapability` accepts only the engine's exact literal IPv4 loopback
HTTP URL with its full random token, selected index and stream suffix. It rejects
credentials, aliases, other hosts, query parameters and fragments. The app's
Media3 adapter consumes it transiently with empty upstream headers, using the
safe position/preferences projection from `control.state()`. It must never
create a generic serializable native launch, source, card, history entry or
progress event from the URL or private engine input. Native byte completion is
not a player event or history completion.

`accept(candidate)` is the accepted player-open boundary. Preparation and
candidate failure leave outgoing playback intact. Accept revalidates the
candidate on main, prevents outgoing Media3 reads and retires outgoing native
authority before opening the replacement. `retire`, `stop` and `closeScope`
prevent Media3 reads first, invalidate control authority and launch acquisition
and handle joins concurrently. All grant/scope joins and registered control-body
consumption share one two-second suspend-aware deadline. Remote release or
request-ID cancellation is separately bounded and occurs while the cache still
owns its registered response buffers. Manager disposal/deletion follows joined
local work, then the exclusive lock is released.

An unsuccessful join quarantines the cache, all outstanding native work and
reservations. Another timer, coroutine cancellation, Java object disposal or
later completion cannot turn that failure into successful retirement. Candidate
retirement is idempotent and has one shared completion receipt. Public player
types remain free of UniFFI and Media3 implementation types.

Foreground effects first revalidate the active control with the backend, then
return through a main-thread active-candidate/live-grant fence before the caller
restores Media3. Background prevents player reads and sets the control's
foreground gate; it does not grant permission to restart transport.

The controller integration owns its main-thread player-stop callback and invokes
scope adoption when authenticated resource facts change. It must retire pending
starts on Back/close as well as active candidates, preserve existing recovery
and focus behavior, and keep native advertisement disabled until the separate
qualification gates pass.

The control read-prevention callback first stops the Media3 session belonging to
that control, then calls `coordinator.cancelNative(control)` immediately. Pending
candidates have no player reads to stop. Native cancellation records the initial
deadline before the later coroutine join; dispatch delay cannot restart the
two-second settlement budget or let a failed candidate stop the outgoing player.

Deterministic tests execute the owned-work/coordinator/scope-owner effects and
actual Rust grant validation with controlled HTTP fixtures. They establish effect
ordering, stale-result disposal, remaining-budget propagation, concurrent joins,
candidate preservation and failed-settlement retention. They do not qualify JNI
on Android, blocked OS IO, decoded video, audible audio, public peers or physical
device resource behavior. Those remain separate local qualification evidence.
