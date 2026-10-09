# Owned seek clock fixture

`seek-clock.m4a` is a locally generated 300-second mono sine wave. It contains
no captured or third-party programme audio. The real Media3 player opens it
paused for transport-button preview/commit and focus tests.

Generation:

```sh
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=8000' -t 300 \
  -c:a aac -b:a 16k -movflags +faststart seek-clock.m4a
```
