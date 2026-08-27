#!/usr/bin/env bash
set -euo pipefail

air_corpus_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
air_output_dir="$air_corpus_dir/output"
air_hls_dir="$air_output_dir/hls"
air_subtitle_dir="$air_corpus_dir/subtitles"

mkdir -p "$air_hls_dir"

air_video_input=(
  -f lavfi
  -i "testsrc2=size=1280x720:rate=24:duration=4"
)
air_audio_one=(-f lavfi -i "sine=frequency=440:sample_rate=48000:duration=4")
air_audio_two=(-f lavfi -i "sine=frequency=880:sample_rate=48000:duration=4")

ffmpeg -hide_banner -loglevel error -y -threads 4 \
  "${air_video_input[@]}" \
  "${air_audio_one[@]}" \
  "${air_audio_two[@]}" \
  -i "$air_subtitle_dir/en.srt" \
  -i "$air_subtitle_dir/es.ass" \
  -map 0:v:0 -map 1:a:0 -map 2:a:0 -map 3:0 -map 4:0 \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p -g 24 \
  -c:a aac -b:a 128k -c:s copy \
  -metadata:s:a:0 language=eng -metadata:s:a:0 title="English 2.0" \
  -metadata:s:a:1 language=spa -metadata:s:a:1 title="Spanish 2.0" \
  -metadata:s:s:0 language=eng -metadata:s:s:0 title="English SubRip" \
  -metadata:s:s:1 language=spa -metadata:s:s:1 title="Spanish ASS" \
  -disposition:a:0 default -disposition:a:1 0 \
  -disposition:s:0 default -disposition:s:1 0 \
  "$air_output_dir/h264-multitrack.mkv"

ffmpeg -hide_banner -loglevel error -y -threads 4 \
  "${air_video_input[@]}" "${air_audio_one[@]}" \
  -map 0:v:0 -map 1:a:0 \
  -c:v libx265 -preset ultrafast -x265-params pools=4:frame-threads=2 -pix_fmt yuv420p \
  -c:a aac -b:a 128k \
  "$air_output_dir/hevc.mkv"

ffmpeg -hide_banner -loglevel error -y -threads 4 \
  -f lavfi -i "testsrc2=size=640x360:rate=24:duration=2" \
  -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=2" \
  -map 0:v:0 -map 1:a:0 \
  -c:v libsvtav1 -preset 12 -crf 45 -pix_fmt yuv420p \
  -c:a libopus -b:a 96k \
  "$air_output_dir/av1.mkv"

ffmpeg -hide_banner -loglevel error -y \
  -i "$air_output_dir/h264-multitrack.mkv" \
  -map 0:v:0 -map 0:a:0 -c copy -f mpegts \
  "$air_output_dir/live.ts"

ffmpeg -hide_banner -loglevel error -y \
  -i "$air_output_dir/h264-multitrack.mkv" \
  -map 0:v:0 -map 0:a:0 -c copy \
  -f hls -hls_time 1 -hls_playlist_type event \
  -hls_segment_filename "$air_hls_dir/segment-%03d.ts" \
  "$air_hls_dir/event.m3u8"

sed '/#EXT-X-ENDLIST/d' "$air_hls_dir/event.m3u8" > "$air_hls_dir/live.m3u8"

cp "$air_subtitle_dir/en.srt" "$air_output_dir/external-en.srt"
cp "$air_subtitle_dir/en.vtt" "$air_output_dir/external-en.vtt"
cp "$air_subtitle_dir/es.ass" "$air_output_dir/external-es.ass"

"$air_corpus_dir/verify.sh"
