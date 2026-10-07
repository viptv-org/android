# Android native torrent runtime defaults

Normal debug and release builds enable native torrent capability for supported
Android phones and Android TVs. There is no receipt asset, device/model allowlist,
account allowlist, Gradle enablement property or user setting.

`NativeTorrentRuntime` checks API 24+, a bundled process ABI (armeabi-v7a,
arm64-v8a or x86_64), actual generated facade initialization, cleartext permission
for literal IPv4 loopback, and a coherent suspend-aware elapsed clock.
`AppController.nativePlaybackEffects` additionally requires current authorized
server/account/profile/device facts and an available exclusively owned private
cache before negotiating native support. A failed prerequisite omits native
capability and retains independently authorized ordinary delivery.

Backend native admission is available by default for supported Android clients.
Authenticated protocol negotiation, exact authorized VOD/source/file selection,
producer ownership, resource limits, private grants and expiry remain mandatory.
An account's ordinary authorization does not authorize an arbitrary torrent.

Normal artifacts use public DHT/TCP policy and system trust. Owned-private
constructors, seed peers, fixture trust and configuration remain confined to the
separate fixture application. Never install a fixture APK or change the origin
on the shared emulator. Preserve data and sign-in with the normal APK's existing
signing identity and `install -r`.

Local cancellation still prevents reads, cancels and joins owned work under the
common settlement deadline before closing or deleting its cache. Arbitrary stalled
synchronous OS IO has no proven unconditional two-second completion bound. Failed
settlement retains quarantine, reservations and ownership; the normal default
does not reinterpret failure as completion or authorize another native owner.

Actual ABI loading, owned Media3 playback, physical audible/remote behavior,
public-peer resource tests and distribution review remain separately recorded
evidence. An offline device or unfinished wider qualification matrix does not
disable every supported Android runtime. See
[runtime availability and evidence](NATIVE_TORRENT_READINESS.md).
