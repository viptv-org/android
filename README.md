# Historical Android-only app and playback wrapper

The experimental branch retires the previous android-only app and playback wrapper.
Active Android, Android TV, Windows, Linux, macOS and ordinary web clients now
belong to [the KMP application repository](https://github.com/viptv-org/app/tree/experiment/kmp-v2).
Native clients support the local Rust/SQLite engine and remote servers; web
requires a backend. TV-web and Roku remain separate remote clients.

This branch contains only the retirement record and original root licenses.
The previous implementation is available at
[source revision `44792cac59e5`](https://github.com/viptv-org/android/tree/44792cac59e50f774e9622c2f4d5651fab6c076a).
Repository history, the main branch and local historical files are preserved.
No executable or build workflow from this branch participates in the KMP app.
