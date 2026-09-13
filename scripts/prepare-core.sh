#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-all}"
case "$mode" in host|android|all) ;; *) echo 'Use host, android, or all' >&2; exit 2;; esac
node scripts/core-sync.mjs check
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-2}"
# JVM tests run before slower ABI builds in CI; both compile the same source pin.
if [[ "$mode" == host || "$mode" == all ]]; then
  cargo build --locked --manifest-path vendor/core/Cargo.toml -p viptv-core --features native --lib
fi
if [[ "$mode" == android || "$mode" == all ]]; then
  cd vendor/core
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 24 -o ../../app/src/androidMain/jniLibs build --locked -p viptv-core --features native --lib --release
fi
