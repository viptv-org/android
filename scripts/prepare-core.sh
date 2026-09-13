#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node scripts/core-sync.mjs check
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-2}"
# Host library supports JVM unit tests; Android packages compile the exact same pin.
cargo build --locked --manifest-path vendor/core/Cargo.toml -p viptv-core --features native --lib
cd vendor/core
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 24 -o ../../app/src/androidMain/jniLibs build --locked -p viptv-core --features native --lib --release
