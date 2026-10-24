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
4. **Native forward cascade:** Initial 1.45x uniform time stretching still caused the 15-tile emulator entrance to reach ~90% colored coverage by 320ms, because Disco's total tile-to-tile stagger was only 200ms. Native handset footage takes about 1000ms from 10% to 90% color coverage. The updated model keeps each tile's original 500ms face and 350ms glyph keyframes but expands the **forward Start-only per-element stagger** by 5.5x (native-fit, experimental) and calculates the entire entrance deadline from the last staggered tile. The All Apps second page keeps its 750ms source entry; app exit/Back/W10M retain their source clocks. Start and glyph delays use the identical multiplier. This is video-inspired calibration, **not** a Microsoft-sourced timing constant or proof of pixel-perfect equivalence.
5. **Tile gutters:** WP8.1 classic gap changed 4dp -> 3dp per user request. Tiles use the original first-fit packing, sizes, and independent saved positions. Native screenshot has a different default Start arrangement; we cannot claim exact physical gaps are calibrated to it.
6. **Visible All Apps tests:** Real Android Compose renderer must show an actual row and bright app-label pixels after manual navigation, not just leave an offscreen semantic element mounted. Extend its frame captures to 1280 ms to cover the longer entrance.

## Scope and release checklist

This is a source-driven correction pushed to `feature/windows-phone-launcher-modes`, PR #46; `main` is intentionally untouched. Verify the new branch with GitHub Actions `testDebugUnitTest`, `assemblePerformance`, and `connectedDebugAndroidTest`, and inspect the renderer's frame artifacts after a successful run. Run the updated APK on the user's actual device and align the newly recorded Start/Apps/app-exit/return sequences to the reference at common frame rate, viewport height and tile geometry. The old uploaded APK video is **not** a recording of these new commits. There is currently no proof of pixel-perfect matching, no independent post-fix screenshot, and no validated 100% figure.

## 23:40 user-video 1000197735.mp4: follow-up timing diagnosis

New 46.8-second recording compared at ~60fps with 1000197575.mp4. Start was sampled at 0.6–2.2s and on Android return around 30.65–32s; reference Start introductions at 47.3–49.1s and 66.4–68.3s. Measured saturated-color coverage 10%→90%: new initial 1.0→1.65s (0.65s), new return 30.75→31.35s (0.60s); reference 47.5→48.5s (1.00s), and 66.65→67.45s (0.80s). Different Start tile layouts make normalized color metrics unsuitable for setting one global speed value. Visual comparison: latest 5.5x version shows mostly thin/isolated lower tiles from ~0.7–1.3s and full-width large top tiles only around 1.6s. Reference first bottom/All Apps line ~47.5s, large top tiles visibly begin around 47.9s. Thus the true problem is delayed top-tile onset / skewed visible area, **not** that the entire reveal is mathematically slow. Tighten classic forward tile stagger from 5.5x to 3x, preserving individual CSS .5s outer/ .35s inner transform durations and preventing any app-exit/W10M changes. Treat 3x as a provisional native-video calibration rather than a certified Microsoft constant.

App exit from external Android Settings around 27.85–28.2s has most tile colors disappear within ~0.35s, while Settings does not display until ~29.3s: this contains a **separate external-application startup/black handoff** not fixable by accelerating the Start tile compositor alone. Native reference near 76s transitions from Start to its first-party Settings app and uses the operating system's destination-page animation, which cannot be reproduced for arbitrary Android apps by the home launcher's renderer. Do not disguise this gap as a tile-duration accuracy issue.
