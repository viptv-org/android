# Development APK signing

`viptv-development.p12` is a committed, intentionally non-secret development key. It gives every GitHub-hosted APK the same certificate so Android TV can install a later build with `adb install -r` and preserve its app data.

It signs the `debug` and `performance` build types, including the beta APKs published to GitHub Releases from version tags. Because the key is public, the signature does not prove an APK came from this repository: the in-app updater only installs APKs whose SHA-256 matches the release's `update.json`, fetched over HTTPS from GitHub. A Play Store or other store release must use a separate protected signing key. This key must never be used for backend authentication or any other credential.
