#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qualification/fixtures
for language in English Spanish; do
  printf '1\n00:00:00,000 --> 00:04:00,000\n%s fixture subtitle\n' "$language" > "qualification/fixtures/$language.srt"
done
ffmpeg -nostdin -y -v error \
  -f lavfi -i testsrc2=size=640x360:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -f lavfi -i sine=frequency=880:sample_rate=48000 \
  -i qualification/fixtures/English.srt -i qualification/fixtures/Spanish.srt \
  -map 0:v -map 1:a -map 2:a -map 3:s -map 4:s -t 240 \
  -c:v libx264 -threads 2 -preset ultrafast -pix_fmt yuv420p -c:a aac -c:s mov_text \
  -metadata:s:a:0 language=eng -metadata:s:a:1 language=spa \
  -metadata:s:s:0 language=eng -metadata:s:s:1 language=spa \
  -disposition:s:0 0 -disposition:s:1 0 -movflags +faststart qualification/fixtures/track-focus.mp4
printf 'Created ignored 240-second H.264/AAC/mov_text track fixture.\n'
