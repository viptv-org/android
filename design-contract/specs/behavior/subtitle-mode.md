# PLAY-SUBTITLE-MODE-001 — Subtitles: Always, Foreign audio only, Never

Status: **proposed**, owner requested 2026-10-11. Source revision: design
`4f3a1ea`; shared rule in core `b6ee054` (`subtitleSelection`,
preferences `subtitleMode`). Android adopts it first; TV-web, Roku and the
server-managed output path follow through their own pins. Implementation and
device evidence are recorded per platform.

## Intent

The profile's on/off **Subtitles** preference becomes a three-way choice so
viewers who prefer, say, English audio get subtitles automatically when a title
plays in another language (dual-audio anime played in Japanese, foreign films)
without turning them on for everything.

## Setting

Playback preferences row **Subtitles** opens the existing one-choice dialog:

| Label | Value | Behaviour |
|---|---|---|
| `Always` | `always` | Start with subtitles in the **Subtitle language**. |
| `Foreign audio only` | `foreign` | Start with subtitles only when the audio that plays is not the **Preferred audio** language. |
| `Never` | `never` | Start without subtitles. |

The row shows the chosen label. Saving shows the existing message **`Applies
to your next playback. Manual track choices take priority.`** Profiles that
stored only on/off read as `Always` (on) or `Never` (off).

## Decision

Core `subtitleSelection` decides from the facts a player reports once it knows
its tracks: the mode, preferred audio and subtitle languages, the selected
audio track's reported language and the subtitle tracks.

- Language matching accepts codes and names (`en`, `eng`, `en-US`, `English`).
  An empty preference means English, matching source ranking.
- `foreign`: audio is foreign only when its reported language is known and
  differs from the preferred audio. Missing, `und`, `mul` or `zxx` audio counts
  as not foreign, so subtitles stay off.
- The subtitle track is the first non-forced track in the subtitle language
  (or the only non-forced track when that preference is empty). If none
  matches, nothing is selected.

## Playback behaviour (native/direct playback)

1. In `Foreign audio only`, playback starts with subtitles off. When the player
   reports its selected audio, the decision runs once and turns on the matching
   subtitle track if the audio is foreign.
2. Choosing another audio track in the player re-runs the decision (for
   example, switching a dual-audio title to Japanese turns English subtitles
   on; switching back to English turns them off).
3. Once the viewer picks a subtitle track or Off in the player, automatic
   choices stop for that playback. Seeking, retry and source-replacement keep
   the current tracks.
4. `Always` and `Never` keep their existing start behaviour.

Server-managed (converted) output and older clients only know on/off; for them
`Foreign audio only` behaves as **on** until they adopt this rule. This favours
readable foreign titles over hiding subtitles on native-language ones.

## Storage

The profile stores `subtitle_mode` (`always` | `foreign` | `never`) with
`subtitles_enabled` kept equal to `subtitle_mode != never` for older clients.
A request without `subtitle_mode` derives it from `subtitles_enabled`. Until a
server stores the mode, a client may keep `foreign` on the device per profile
while the server's `subtitles_enabled` is on.

## Acceptance

1. **PLAY-SUBTITLE-MODE-01:** choose each option; the row shows its label,
   the message appears, and the choice survives an app restart and a profile
   switch back.
2. **PLAY-SUBTITLE-MODE-02:** `Foreign audio only` with English preferred:
   a dual-audio title playing English audio starts without subtitles; a title
   whose audio is Japanese starts with English subtitles.
3. **PLAY-SUBTITLE-MODE-03:** during playback switch audio English → Japanese
   → English; subtitles turn on, then off. After picking a subtitle track or
   Off manually, a further audio switch leaves it unchanged.
4. **PLAY-SUBTITLE-MODE-04:** audio without a language tag starts without
   subtitles; a title with no subtitle in the subtitle language shows none.
5. **PLAY-SUBTITLE-MODE-05:** `Always` and `Never` behave as before; an older
   client sees `Foreign audio only` as subtitles on.
