#!/usr/bin/env bash
set -euo pipefail

air_repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
air_manifest="$air_repo_dir/corpus/cases.json"
air_output_dir="$air_repo_dir/benchmark/output"
air_report="$air_output_dir/mpv-linux.json"

command -v mpv >/dev/null
command -v jq >/dev/null
command -v timeout >/dev/null

"$air_repo_dir/corpus/verify.sh" >/dev/null
mkdir -p "$air_output_dir"

air_results='[]'
while IFS=$'\t' read -r air_id air_relative_path air_kind
do
  air_path="$air_repo_dir/corpus/$air_relative_path"
  air_started=$(date +%s%N)
  if timeout 20s mpv \
      --no-config \
      --no-terminal \
      --vo=null \
      --ao=null \
      --frames=24 \
      --aid=auto \
      --sid=auto \
      "$air_path" >/dev/null 2>&1
  then
    air_passed=true
  else
    air_passed=false
  fi
  air_finished=$(date +%s%N)
  air_elapsed_ms=$(( (air_finished - air_started) / 1000000 ))
  air_results=$(jq \
    --arg id "$air_id" \
    --arg kind "$air_kind" \
    --argjson passed "$air_passed" \
    --argjson elapsedMillis "$air_elapsed_ms" \
    '. + [{id: $id, kind: $kind, passed: $passed, elapsedMillis: $elapsedMillis}]' \
    <<<"$air_results")
done < <(jq -r '.cases[] | [.id, .path, .kind] | @tsv' "$air_manifest")

air_multitrack="$air_repo_dir/corpus/output/h264-multitrack.mkv"
air_track_switch_passed=true
for air_audio in 1 2
do
  for air_subtitle in 1 2
  do
    if ! timeout 20s mpv \
        --no-config --no-terminal --vo=null --ao=null --frames=12 \
        --aid="$air_audio" --sid="$air_subtitle" \
        "$air_multitrack" >/dev/null 2>&1
    then
      air_track_switch_passed=false
    fi
  done
done

air_version=$(mpv --version | head -1)
jq -n \
  --arg generatedAt "$(date --iso-8601=seconds)" \
  --arg engine "$air_version" \
  --arg platform "linux-x86_64" \
  --argjson trackSelectionPassed "$air_track_switch_passed" \
  --argjson cases "$air_results" \
  '{generatedAt: $generatedAt, engine: $engine, platform: $platform, trackSelectionPassed: $trackSelectionPassed, cases: $cases}' \
  > "$air_report"

if jq -e '.trackSelectionPassed and ([.cases[].passed] | all)' "$air_report" >/dev/null
then
  echo "MPV Linux corpus benchmark passed."
else
  echo "MPV Linux corpus benchmark failed; inspect the ignored local report." >&2
  exit 1
fi
