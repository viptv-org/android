# Native delivery at the player boundary

`AppControllerPlayback` uses the existing Rust playback-intent construction for
both ordinary and native-capable starts. Qualification defaults to false. A
qualified VOD start first adopts the Rust-approved authorization scope, then
registers its private control before negotiation or admission. Live and ordinary
unqualified starts keep the existing delivery path. Unavailable native storage
prevents native admission while independently authorized ordinary delivery
remains eligible.

`NativePlaybackEffects` keeps native grants and byte capabilities outside
`PlaybackLaunch`, application state, saved state and history. Only its accepted
open callback adapts the vetted exact-file capability into a transient
`PlaybackSource`: literal loopback, empty headers, on-demand timeline, granted
media position and local audio/subtitle preferences. Native metadata/download
completion produces no player Ended event or watched-history update. Existing
Media3 media-time persistence, source/title/episode/queue identity and UI focus
flows serve both branches.

Candidate preparation leaves outgoing playback and its lease intact. At an
accepted open the coordinator stops outgoing player reads and joins native
retirement; the controller retires its ordinary outgoing lease at the same
boundary. A negotiated ordinary response transfers its lease to V2 control and
disarms the temporary native control's remote authority before local disposal.
Back cancels pending controls as well as accepted native ownership. Scope changes,
sign-out, revocation and controller close fence controls synchronously before the
scope owner joins and deletes the cache. Controller scope shutdown does not race
individual retirement against the complete scope-close operation.

Private native control captures its outgoing credential at local retirement.
The separately bounded generated DELETE uses only that transient credential and
the fixed backend origin, without refresh or adoption of another principal's
credential. Joined local transport precedes remote cleanup and cache deletion.
Successful DELETE completion is shared across repeated cleanup. Failed local
settlement retains native ownership and prevents Retry from authorizing a new
native or gateway attempt.

Native admission disables automatic managed-delivery recovery. Existing Retry
waits for retirement and asks Rust's native recovery decision whether a fresh
request for the same opaque source may force gateway. Authorization refusal uses
auth recovery; invalid or ambiguous selection evidence returns to source choice.
Decode/network failures with settled authority can use explicit gateway Retry,
preserving media position, pause intent and local language/subtitle intent.
Resume, seek and native track effects recheck the live grant. Existing background
behavior exits and retires playback; it does not reopen a cancelled byte URL.

The internal qualification and scope-owner injection fields exist for isolated
debug/test fixtures. Production callers leave them at their disabled defaults;
owned-seeder constructors belong only in debug/instrumented sources. Host tests
exercise the actual controller effect seam and canonical Rust private bridge
against controlled HTTP and acquisition effects. JNI, decoded video, audible
audio, physical resource behavior and public-peer qualification remain separate
device gates.
