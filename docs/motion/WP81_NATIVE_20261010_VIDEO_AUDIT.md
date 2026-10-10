# WP8.1: 10 October 2026 new video audit and corrections

## Inputs

- Installed Til APK recording: `1000197733.mp4`, 2400 × 1080, 1440 frames (~61.5 fps), 23.43 s.
- Native Windows Phone 8.1 comparison: `1000197575.mp4`, 1920 × 1080, 24479 frames (~60 fps), 407.98 s. The reference phone sits approximately at source x=700..1220, not at x=400..780. Crop reference carefully.

These are **different tile arrangements, camera geometries and recording contexts**. Differences in individual pixels do not prove incorrect tile transform equations without registration; the coarse colored-area progression below is a timing diagnostic, not a certified perceptual score.

## Native vs installed progress

Saturated colored-tile occupancy was sampled near the phone viewport at 100 ms steps. Native reference x=732..1188/y=90..980; installed Til x=970..1432/y=70..1000. Each trace is normalized by its own settled colored occupancy.

| Normalized occupancy | Native video | Installed Til video |
| --- | ---: | ---: |
| ~10% | 47.55s | 13.85s |
| ~50% | 48.05s | 13.95s |
| ~90% | 48.55s | 14.15s |
| 10% to 90% reveal | ~1.0s | ~0.30s |

There is evidence that the installed entrance has a substantially steeper visible reveal than the native footage, although this combines Android task-switch presentation timing and different tile layouts. It is **not** evidence that simply multiplying every Microsoft/Disco keyframe duration will create a true 100% match.

Other observed symptoms: on the older APK, All Apps briefly showed black after navigation, and returning to Start could show displaced/overlapping tile sections; these were also reproducible as logical defects in the Compose panorama/projector, independent of the videos.

## Source-level fixes

1. **All Apps black screen:** The forward `projectAppsPagePoint()` homography added +one viewport to the Apps page even when users had swiped to Apps. The previous forward-animation predicate lacked a navigation/phase guard. Gate the homography to an active forward Start entrance only.
2. **Apps -> Start twitch:** Track `classicForwardEntranceActive` as lifecycle-bound state. When an entrance finishes, drop the homography entirely, preserving unprojected, continuously sliding Apps geometry on manual swipes.
3. **Runtime activity resume:** DiscoLauncher `src/script.js` defers `appTransition.onResume()` by 200 ms on `activityResume`. Android external Home/Back and Recents now share a deferred, cancellable classic entrance, avoiding animation progress while Android is still presenting its task switch. Internal-app Back remains separate.
4. **Forward entrance speed:** Classic entrance presentation uses an experimental **1.45x** stretched playback clock while all source tracks (tile background, inner glyph, and second Apps page) sample the same source time. This is a calibration adjustment based on the native footage's longer reveal, **not** a value taken from Microsoft or Disco code. It does not change app exit, manual horizontal swipe, or W10M.
5. **Tile gutters:** WP8.1 classic gap changed 4dp -> 3dp per user request. Tiles use the original first-fit packing, sizes, and independent saved positions. Native screenshot has a different default Start arrangement; we cannot claim exact physical gaps are calibrated to it.
6. **Visible All Apps tests:** Real Android Compose renderer must show an actual row and bright app-label pixels after manual navigation, not just leave an offscreen semantic element mounted. Extend its frame captures to 1280 ms to cover the longer entrance.

## Scope and release checklist

This is a source-driven correction pushed to `feature/windows-phone-launcher-modes`, PR #46; `main` is intentionally untouched. Verify the new branch with GitHub Actions `testDebugUnitTest`, `assemblePerformance`, and `connectedDebugAndroidTest`, and inspect the renderer's frame artifacts after a successful run. Run the updated APK on the user's actual device and align the newly recorded Start/Apps/app-exit/return sequences to the reference at common frame rate, viewport height and tile geometry. The old uploaded APK video is **not** a recording of these new commits. There is currently no proof of pixel-perfect matching, no independent post-fix screenshot, and no validated 100% figure.
