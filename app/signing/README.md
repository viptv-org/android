# Development APK signing

`viptv-development.p12` is a committed, intentionally non-secret development key. It gives every GitHub-hosted debug APK the same certificate so Android TV can install a later debug build with `adb install -r` and preserve its app data.

It is only configured for the `debug` build type. Production/release signing is intentionally absent from this repository and must use a separate protected CI signing configuration. This key must never be used for a release, store upload, backend authentication, or any other credential.
