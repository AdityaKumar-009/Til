# Windows Phone 8.1 reference calibration, 11 October 2026

Inputs: installed APK `1000197738.mp4`; references `1000197575.mp4`, `1000197576.mp4`, and the LEFT handset in `1000197577.mp4`. The attachments' names, rather than their order in an uploader, identify the footage.

## Evidence and corrections

- Decoded reference sequences frame by frame. In 7575 at 47.3–49.4s, visible changes occur in steps separated by 83–167ms (many repeated frames). In 7576, motion updates occur approximately every 33.3ms. The slow orange recording is useful for geometry, but its wall-clock duration is not a real-time shell timing target. This supersedes the earlier 3x/5.5x entrance-delay calibration.
- Forward Home return: 7576 at 112.70–113.20s. The All Apps sliver appears first, then lower Start faces, then the top phone face. The top face moves LEFT into place from a displaced RIGHT position while its far edge rotates into view. The old fixed hinge misses this translation.
- Extracted the top phone face corners from a 508 x 846 crop. At 112.895s its left edge is x=96, versus x=28 at rest. By 113.095s it is x=30. A model with a fixed x=28 hinge has an immediate 68px horizontal error. A shared camera at 3.2 times viewport width, 80-degree starting angle, positive quarter-width X displacement, negative 0.12-width depth, and a 250ms exponential decay with exponent 3 fits these six measured frames with RMS coordinate errors approximately 1.3–4.3px (maximum sampled coordinate error 8.3px). This is a fitted reconstruction with quantization/occlusion uncertainty, not Microsoft's private compositor source.
- Forward stagger is bounded at 200ms regardless of Android dp height; the last face/glyph settles at 450ms. Returning Home exposes the Apps page after 50ms, then turns it for 350ms. Its fade no longer consumes most of the visible page turn.
- Back return: 7576 at 69.4–69.9s visibly turns from the opposite direction. Android returns from an app launched by Til now select the Back route unless an explicit Home intent was received. The existing leftward Back transform is retained with its own 350ms face clock. Android Recents and Back can share lifecycle signals; this routing is an inference when no Home intent is delivered.
- App exit: the 7577 left handset at 144.51–145.05s has a reverse-order wave and a held selected tile. Kept those established tracks, removed the unconditional additional 200ms of black time after the final visible track. The final pose remains held until return, protecting against the earlier tile flash.
- Native Start geometry: the four-column blue reference has roughly 26px side margins and 12–13px gutters in a 508px viewport; the six-column reference halves the side margins. Use width-relative 5%/2.5% margins and 2.5% gutters, replacing the unrelated 10dp/3dp values. Saved tile sizes/order remain intact.
- The in-app Windows/Home button now requests the forward Home animation; it previously only changed the panorama target, skipping the tile/page return altogether.
- Horizontal WP8.1 panorama drags now follow the pointer before release, with distance/velocity-based settling. The former implementation waited for pointer-up before starting the whole slide.
- The preview encoder now uses 62.5fps for frames sampled at 16ms. Encoding those frames at 60fps had added a 4.17% slowdown to the review artifact.

## Verification

Independent native corner coordinates are committed as regression expectations (10px maximum tolerance on the 508px crop); tests are not merely a second copy of the interpolation equation. Android instrumentation captures actual Compose output every 16ms, including held-finger navigation, Home from Start and Apps, Start exit, Apps exit, opposite-direction Back return, no premature handoff, retained exit, and settled geometry. The WP8.1 fixture uses mixed 4-column tile sizes like the native reference; the W10M fixture remains unchanged.

Windows 10 Mobile's motion model, Windows 8.1 Desktop renderer, and their settings/layouts were not edited. Changes remain on feature/windows-phone-launcher-modes; main is not merged.

The available videos have different layouts, icons, and recording cadence. The measured coordinate fit concerns the native tile face, not a whole-experience percentage. Arbitrary Android destination-app windows and OS task-switch frames are outside this launcher renderer. Do not label the result a verified 100% match without matching post-build on-device captures.
