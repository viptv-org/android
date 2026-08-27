# Air playback corpus

This corpus is generated locally from FFmpeg test sources. It contains no
third-party video, audio, artwork, or subtitles.

```bash
./corpus/generate.sh
```

Generated files are ignored by Git. `cases.json` is the capability contract
used by backend acceptance tests. A backend result must report unsupported
capabilities explicitly; opening one sample is not evidence for the entire
container or codec family.

The initial set covers:

- Matroska with H.264, HEVC, and AV1;
- two selectable audio tracks with language/default metadata;
- embedded SubRip and ASS subtitles plus external SRT, WebVTT, and ASS;
- HLS event and duration-less live playlists;
- MPEG-TS IPTV-style playback.
