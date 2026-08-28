#!/usr/bin/env bash
set -euo pipefail

air_repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
air_bridge="$($air_repo_dir/scripts/build-jawt-linux.sh)"
"$air_repo_dir/corpus/generate.sh"

AIR_MPV_SURFACE_INTEGRATION=1 \
AIR_JAWT_BRIDGE="$air_bridge" \
AIR_VIDEO_CORPUS_DIR="$air_repo_dir/corpus/output" \
    "$air_repo_dir/gradlew" -p "$air_repo_dir" jvmTest \
    --tests '*LinuxMpvEmbeddedPlayerTest.realMpvRendersIntoEmbeddedCanvasWhenEnabled'
