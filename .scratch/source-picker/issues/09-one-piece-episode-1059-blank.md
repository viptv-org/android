# 09: Diagnose blank One Piece episode 1059

**What to fix:** Episode 1059 must show usable episode artwork or the shared
fallback instead of an unexplained empty rectangle. Confirm source discovery
and actual episode identity independently of lazy scrolling.

**Status:** done
**Blocked by:** None.
**Owner:** episode_1059_research (GPT-6 Luna, max), then Sol implementation.

- [x] Reproduce on the owner's signed-in Windows Android TV emulator.
- [x] Distinguish episode artwork from source discovery and navigation.
- [x] Verify upstream and normalized thumbnail data for 1059 against early episodes.
- [x] Lock down the demonstrated failing boundary with a regression check.
- [x] Fix the demonstrated cause using shared artwork policy.
- [x] Repeat native acceptance and the required Windows build/test flow.
- [x] Commit and push narrowly; preserve personal sign-in and viewing data.

## Diagnosis evidence

On 2026-10-02, the actual Anime Kitsu One Piece details retained 1410 episodes.
Jumping to 1059 focused the correctly titled episode, but its image and nearby
images remained blank after settling. Jumping to 2 rendered episode thumbnails
normally. The same virtual LazyRow handles both ranges. Activating 1059 opened
a populated chooser with 19 sources, including torrents explicitly naming 1059.
No source was started and no playback/history write was performed.

Private native captures are under
`qualification/artifacts/native-final-acceptance-20261001/episode1059-*`.
Exploratory screenshots before `details-settled` include unrelated navigation;
use the settled episode and source captures as reproduction evidence.

Authenticated read-only metadata contains one entry for `kitsu:12:1059` among
1410 videos. The supplied thumbnails for 1058, 1059 and 1060 return HTTP 404
with JSON, while episode 2 returns HTTP 200 JPEG. Episode 1410 has no thumbnail.
The independent Luna inspection confirmed the normalized data retains those
facts. Neither a missing row nor virtual composition explains the blank image.

The actual EpisodeCard regression first requested a deterministic failed child
thumbnail and timed out waiting for the known parent landscape. The existing
card skipped both parent-art enrichment and image-failure observations; the
shared Rust projection already supports thumbnail-to-landscape fallback. Fix
scope is the Android rendering adaptation, with a compact parent-art context
and failure observations, retaining the existing shared policy and lazy row.

Implementation `9d99d15` is pushed to Android main. Both native regressions
passed: a 404 thumbnail loads the parent landscape (a green fixture pixel proves
it rendered), and two failed images produce a visible title placeholder. Shared
Core enrichment preserves exact episode/source identity and progress. The full
Windows JDK 17 / SDK 36 flow passed 223 unit tests with zero failures/errors/skips,
three Android core ABIs, normal/test APK assembly and lint. Independent Luna
review found no confirmed implementation gaps.

The normal APK was installed with data retained on the owner's API 36 Windows
TV. Actual episode 1059 and adjacent cards now show the known series landscape;
jumping back to 2 retains its real episode thumbnails. All 1410 episodes remain,
the same app process stayed alive, and source activation still uses the original
episode. Final inspected captures are `episode1059-fixed-fallback.png` and
`episode1059-fixed-original-thumbnails.png` in the private acceptance directory.
No personal playback was initiated. Upstream 404 images remain unavailable;
the fix provides the existing shared fallback instead of manufacturing stills.
