#!/usr/bin/env bash
set -euo pipefail

air_repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v mpv >/dev/null
"$air_repo_dir/corpus/generate.sh"

AIR_MPV_INTEGRATION=1 \
AIR_VIDEO_CORPUS_DIR="$air_repo_dir/corpus/output" \
    "$air_repo_dir/gradlew" -p "$air_repo_dir" jvmTest \
    --tests '*MpvHeadlessBackendTest.realMpvOpensCorpusTracksAndLivePlaylistWhenEnabled'
