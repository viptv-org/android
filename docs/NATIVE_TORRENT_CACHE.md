# Private native transport storage

The app adapter constructs the strict generic engine through
`openNativeTorrentCache(Context)`. Its only storage root is
`applicationContext.noBackupFilesDir/viptv-native-transport`; the application
also disables backup. The root, random epoch directories and ownership receipts
contain no product identity. Source inputs and grants remain transient controller
objects, outside saved state, diagnostics and account storage. Engine-internal
payload/metadata is sensitive private cache data.

The controller owns one cache per stable authorization epoch. Rust supplies
authorization/generation decisions; the cache supplies effects. Playback
replacement and grant renewal retain the same cache. Sign-out, re-pairing,
server/account/profile/principal changes and resource revocation execute the
shared scope invalidation before another cache is opened. Never derive a cache
directory from these identities.

The cache holds an exclusive kernel file lock across manager ownership and
cleanup. Acquiring that lock is required before inspecting identified inactive
cache directories. Restart deletes only random directories identified by exact
app ownership markers/receipts, rejects symbolic links, preserves unrelated
storage, and restores no grants, source input, session or DHT state. Root receipts
remain until owned directory deletion succeeds, including partial cleanup.

The native facade enables a 256 MiB rolling piece cache per large input. Smaller
torrents reserve their full payload. The generic manager charges these reservations
against the supplied 2 GiB aggregate limit and measured available disk; a large
logical torrent or unselected file does not require full-payload device storage.
At least two pieces must fit. Eviction resets availability before re-download,
protects current reads/writes/checksums and advertises no evictable peer pieces. Its registry proves payload sharing and retains retiring/unsettled
reservations. Independent acquisitions/listeners retain separate grant authority.
Candidate capacity refusal does not retire the outgoing grant.

`reserveControl` accounts transient response buffers and retained private input
against one 64 MiB control/metainfo ceiling. `reserveMetainfo` also enforces 4 MiB
per raw metainfo. The controller reserves a conservative 4 MiB native metadata
allowance for each acquisition, plus its retained input and transient control
buffers, before their allocations. This allowance stays charged through joined
retirement; generic engine metadata must not be treated as a second unaccounted
64 MiB allocation. These are reservation bounds, not promises about exact
filesystem or JVM allocation size.

Register cancellation-addressable work with its reservation before starting
native IO. Keep the acquisition object alive after receiving a ready handle.
`retire` prevents that grant's reads, cancels and joins it within a single two
second deadline, disposes settled objects, and releases its control charge.
`closeScope` prevents all reads, cancels all work, joins against one shared
deadline, closes the manager, deletes owned storage, then releases the lock.
The production clock uses Android suspend-aware elapsed time.

Failed settlement or deletion quarantines the owner, manager references, work,
control accounting and entire assigned payload capacity. Quarantine prevents
reads and cancels remaining work, rejects further admission and keeps the lock
until process exit. A caller's coroutine completion, object disposal or token
`close()` cannot prove joined settlement. Independently authorized HTTP/HLS
eligibility is decided outside this native adapter.

The generic engine can report failed settlement for an operating-system IO call
that remains blocked beyond the deadline. Cache tests prove fail-closed ownership
and accounting; they do not qualify arbitrary OS IO, Media3, public peers or
physical-device lifecycle. Native advertisement remains governed by the separate
qualification gates.
