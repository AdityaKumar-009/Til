# Launch animation analysis

See [the subsequent All Apps and performance analysis](all-apps-motion-and-performance.md)
for its distinct window entrance, horizontal columns, parallax and optimized APK.

Reference: `Screen Recording 2025-06-25 103046.mp4`, supplied locally by the user.
The recording is 1920 x 1080, 30 fps, 2,058 frames, 68.6 seconds.

## Measurements

`scripts/analyze_launch_video.py` extracts each frame in four launch sequences,
including the resting tile and the settled window. It writes full-resolution PNGs,
contact sheets, timestamps, colored-card outlines and adjacent-frame differences
to `build/animation-analysis/reference/`. The color masks measure the colored face;
they exclude black title bars and can include other same-colored tiles before launch.

| Sequence | Examined interval | Observation |
| --- | --- | --- |
| Mail | 6.10–6.84 s | Small initial lift, edge-on turn, splash expands while still rotating |
| Reading List | 18.30–19.10 s | Same turn, followed by the app content |
| Money | 33.90–34.64 s | Bottom tile follows the same screen-centered perspective |
| Help from All Apps | 59.40–60.07 s | A different window entrance, without the full tile-front turn |

The Start tile launches take approximately 600–670 ms in the recording. Several
frames repeat, including Mail 189/190, 194/195 and 196/197. The implementation uses
a continuous 650 ms reference timeline rather than reproducing capture stalls.
At 30 fps the recording cannot establish exact sub-frame timing or the original
Windows easing coefficients.

The vertical edges stay vertical. Their different heights explain the slanted top
and bottom edges without a Z rotation. Inverting the Mail and Money quadrilaterals
places the camera horizon near y=540. A camera distance near 2160 px gives matching
width and height growth fractions. This is a fitted model, not recovered Windows code.

## Implementation

`WindowsLaunchMotion` projects one continuous rotating plane around the viewport
center. Front and back use the same four projected corners. The texture changes
when winding changes, which is slightly different from a fixed 90-degree switch
for an off-center tile. The back texture is mapped upright. Both faces use fixed
layout sizes and a drawing transform, avoiding text/icon remeasurement every frame.

The original tile artwork is shared with the opening face. The selected tile is
hidden in the receding grid so it does not leave a duplicate behind. Window-relative
source coordinates are converted into the overlay's coordinates.

Wide/large tiles previously moved their logo when a bounding-box correction
activated and later released. Containment now scales about the projected content
center without translating it. The center follows a single path to its final
position. Tests cover nearly full-width wide/large tiles and screen corners.

The front and splash no longer own separate logos. The overlay measures the tile's
actual logo bounds and draws one logo with a continuous physical size and position
through both faces. A projective sub-rectangle preserves the card perspective;
the back-face coordinate reversal preserves the logo's physical center. This also
handles off-center artwork such as the weather tile. Built-in destinations still
reveal their real content on the back face.

The timing controls preserve the spatial path. The reference setting retains the
measured timing; other curves control angular progress through an inverse mapping
of the reference turn. Linear therefore has a constant turn rate. Curves that
intentionally bounce or step can reverse or pause progress, but remain contained.
Settings contain 48 curve choices, including the reference, standard easing
families, Android/Material presets, bounce/elastic/back, steps and custom cubic
Bézier, plus duration, applicable strength/step controls, a preview and reset.

## Horizontal Start and scroll work

Start now scrolls horizontally through lazily composed tile bands. A remembered
two-dimensional packing pass preserves SMALL 1x1, MEDIUM 2x2, WIDE 4x2 and LARGE
4x4 footprints. Cell size and row count use the available width and height, so
portrait phones, short landscape windows and tablets retain square grid units.
Named groups remain separate. The down arrow still opens the All Apps view.

Icon cache misses and installed-app queries run on IO coroutines. Decodes for the
same package share one request, and the icon cache is bounded at 8 MiB. All Apps
entrance progress is read during drawing instead of recomposing its list every
frame. The clock updates at minute boundaries. These changes follow the
[Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

## Verification

- `assembleDebug`, `testDebugUnitTest` and `lintDebug` completed successfully.
- 30 unit tests passed; lint reported zero errors.
- Geometry tests sweep tile sizes, edge positions and portrait/landscape/tablet
  viewports. Logo tests cover exact endpoints and continuity at the face change,
  including off-center logos. Packing tests check footprints and overlaps.
- On the connected 1080x2400 Android 14 phone, verified horizontal Start scrolling,
  the timing selector/preview, built-in Reading List content on the rotating back,
  a wide Store tile with one continuous logo, and return using Home/Back buttons.
- Phone recordings: `build/animation-analysis/builtin-final.mp4` and
  `build/animation-analysis/continuous-logo.mp4`; extracted frames sit beside them.
- Eight-swipe debug-build samples recorded a 95th-percentile frame time of 32 ms
  before and 25 ms after the scroll changes. Reported jank was about 14% in both
  samples. The layouts and scroll direction differ, so these are spot checks,
  not a controlled benchmark or a claim of stutter-free 120 Hz rendering.

## App windows and Android transitions

Built-in launcher screens are rendered on the back face during the turn. At the
final frame the same screen becomes interactive, without an added splash hold.

External apps belong to other Android processes. This normal APK cannot put their
live windows into its Compose drawing. It resolves the target before the animation,
starts it after the final submitted frame, and retains the completed face until
the launcher is covered. There is no fixed 80 ms hold or 120 ms fade afterward.
Cold-start time and the destination's splash screen remain controlled by Android.

Both day and night now inherit the same launcher window animation policy. The
launcher opts out of its own predictive Back animation and applies no-animation
options consistently to launches it initiates. Home intents clear internal screens
and pending flips. These controls cannot disable an external app's predictive Back
or an OEM's Home/Recents animation globally.

The user chose to keep this a normal APK. No root, Shizuku, screen capture service,
global animation setting, hidden-API workaround or privileged task organizer is used.

Relevant Android contracts:

- [Activity transition ownership and task restrictions](https://developer.android.com/reference/android/app/Activity#overrideActivityTransition(int,int,int))
- [No-animation intent flag](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_NO_ANIMATION)
- [Cross-app embedding trust model](https://developer.android.com/develop/ui/views/layout/activity-embedding#trust_model)
- [MediaProjection consent requirements](https://developer.android.com/media/grow/media-projection#user-consent)
- [Matrix four-point projection](https://developer.android.com/reference/android/graphics/Matrix#setPolyToPoly(float[],int,float[],int,int))
