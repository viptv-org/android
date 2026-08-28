#!/usr/bin/env bash
set -euo pipefail

air_corpus_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
air_output_dir="$air_corpus_dir/output"

air_assert_stream_count() {
  local air_file=$1
  local air_selector=$2
  local air_expected=$3
  local air_actual
  air_actual=$(ffprobe -v error -select_streams "$air_selector" -show_entries stream=index -of csv=p=0 "$air_file" | wc -l)
  if [[ "$air_actual" -ne "$air_expected" ]]; then
    echo "Corpus verification failed for a generated stream count." >&2
    exit 1
  fi
}

air_assert_codec() {
  local air_file=$1
  local air_expected=$2
  local air_actual
  air_actual=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=nw=1:nk=1 "$air_file")
  if [[ "$air_actual" != "$air_expected" ]]; then
    echo "Corpus verification failed for a generated codec." >&2
    exit 1
  fi
}

air_assert_codec "$air_output_dir/h264-multitrack.mkv" h264
air_assert_codec "$air_output_dir/hevc.mkv" hevc
air_assert_codec "$air_output_dir/av1.mkv" av1
air_assert_stream_count "$air_output_dir/h264-multitrack.mkv" a 2
air_assert_stream_count "$air_output_dir/h264-multitrack.mkv" s 2

test -s "$air_output_dir/live.ts"
test -s "$air_output_dir/hls/event.m3u8"
test -s "$air_output_dir/hls/live.m3u8"
test -s "$air_output_dir/external-en.srt"
test -s "$air_output_dir/external-en.vtt"
test -s "$air_output_dir/external-es.ass"

air_hls_segment_count=$(grep -Ec '^[^#[:space:]]' "$air_output_dir/hls/event.m3u8")
if [[ "$air_hls_segment_count" -lt 14 ]]; then
  echo "Corpus verification failed for the resilient live-window fixture." >&2
  exit 1
fi

echo "Air playback corpus verified."
