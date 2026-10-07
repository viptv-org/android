#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-all}"
case "$mode" in host|android|all) ;; *) echo 'Use host, android, or all' >&2; exit 2;; esac
node scripts/core-sync.mjs check
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-2}"
# JVM tests run before slower ABI builds in CI; both compile the same source pin.
# The native feature builds the backend-owned viewing contract and SmartCast
# bridge.
if [[ "$mode" == host || "$mode" == all ]]; then
  cargo build --locked --manifest-path vendor/core/Cargo.toml -p viptv-core --features native --lib
  # Cargo may use a shared target directory while Gradle deliberately loads
  # the canonical host library under this snapshot. Publish the new product,
  # rather than accidentally loading an older ABI left at that path.
  core_target=$(cargo metadata --locked --no-deps --format-version 1 --manifest-path vendor/core/Cargo.toml | node -e 'let s="";process.stdin.on("data",x=>s+=x);process.stdin.on("end",()=>process.stdout.write(JSON.parse(s).target_directory));')
  case "$(uname -s)" in
    Darwin) core_library=libviptv_core.dylib ;;
    MINGW*|MSYS*|CYGWIN*) core_library=viptv_core.dll ;;
    *) core_library=libviptv_core.so ;;
  esac
  core_destination="vendor/core/target/debug/$core_library"
  mkdir -p "$(dirname "$core_destination")"
  if [[ ! "$core_target/debug/$core_library" -ef "$core_destination" ]]; then
    cp "$core_target/debug/$core_library" "$core_destination"
  fi
fi
if [[ "$mode" == android || "$mode" == all ]]; then
  cd vendor/core
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 24 -o ../../app/src/androidMain/jniLibs build --locked -p viptv-core --features native --lib --release
fi
