# All Apps motion, horizontal layout and scrolling

This report records the earlier optimized build. The later entrance, elastic
scrolling, drawer gesture, larger icons, and wallpaper changes are documented in
[Start entrance and wallpapers](start-entrance-and-wallpapers.md). The performance
numbers below are measurements of that earlier build, not the later interaction changes.

## What the reference shows

The supplied recording is 1920 × 1080 at 30 fps. The relevant Help launch starts
between frames 1782 and 1783, at 59.400–59.433 seconds. All 21 frames through
60.067 seconds were inspected. The recording cannot supply new observations every
10 ms: its frames are approximately 33.33 ms apart. The accompanying
[10 ms samples](all-apps-interpolated-10ms.csv) are explicitly interpolated model
samples; [recorded measurements](all-apps-recorded-frames.csv) remain separate.

Unlike a Start tile launch, this sequence never shows a tile front turning over.
The app face enters near the center as a reduced, tilted window. Its left edge is
taller than its right edge. The window expands while its tilt decreases, then
settles slowly into the viewport. Vertical edges remain vertical throughout.
Repeats such as frames 1783/1784 and 1789/1790 are capture duplicates, not intentional
holds to add to the Android animation.

| Elapsed time | Window scale | Yaw | Evidence |
| --- | ---: | ---: | --- |
| 0 ms | 0.500 | 30° | Inferred, initially transparent; not resolved by the recording |
| 33 ms | 0.601 | 23.742° | Frame 1783 |
| 100 ms | 0.755 | 14.656° | Frame 1785 |
| 133 ms | 0.847 | 9.133° | Frame 1786 |
| 200 ms | 0.922 | 4.495° | Frame 1788 |
| 300 ms | 0.965 | 1.873° | Frame 1791 |
| 400 ms | 0.982 | 0.906° | Frame 1794 |
| 533 ms | 0.992 | 0.277° | Frame 1798 |
| 633 ms | 0.996 | 0° | Frame 1801 |
| 650 ms | 1.000 | 0° | Exact Android viewport endpoint |

The colored-face outlines exclude the reference's 30 px title bar. Correcting for
that bar lets both edge heights determine the perspective. For colored left/right
heights `L` and `R`, `t = (L − R)/(L + R)` and
`scale = 2LR/((L + R) × 1050)`. A camera distance equal to the reference width
reconstructs the measured edges; yaw is `asin(2t/scale)`. Tests compare projected
corners with measured reference frames within a 5 px tolerance at 1920 × 1080.

The resemblance to Material emphasized deceleration comes from the quick expansion
and long settling tail. A named easing alone does not reproduce the independent
perspective and size changes. `AllAppsLaunchMotion` therefore uses measured scale
and yaw tracks with continuous interpolation. It does not reuse the 180° Start
flip. The initial unrecorded interval is a fitted approximation, not recovered
Windows implementation code.

## Behavior and settings

- Start and All Apps have independent timing preferences. All Apps defaults to its
  fitted reference timing, preserving the user's existing Start settings.
- Both timing editors offer the existing 48 choices, duration, applicable controls,
  curve preview and reset. Linear controls angular progress on Start and expansion
  progress in All Apps, without applying the fitted ease a second time.
- All Apps now scrolls horizontally. Fixed-width columns fill top-to-bottom with
  multiple alphabetical groups, as in the reference. Headers are not orphaned at
  the bottom; continuing groups get a quieter repeated header.
- Search, app actions and icon loading remain available. Narrow screens put search
  below the title; column height follows the available viewport.

## Parallax

Windows 8.1 introduced additional Start backgrounds with motion, as described in
[Microsoft's announcement](https://blogs.windows.com/windowsexperience/2013/05/30/continuing-the-windows-8-vision-with-windows-8-1/).
Tile8 retains its purple artwork and adds three moving layers: glow, ribbon and
highlight. Their scroll rates differ; smooth viewport-relative bounds keep the
decorations visible even at the far end of All Apps. This recreates the layered
behavior, not a recovered original Windows wallpaper asset or motion coefficient.

Each layer caches its drawing and reads scroll position in a graphics-layer
transform. Scrolling does not recompose the wallpaper or rebuild its paths.
The static base always covers the screen. **PC settings → Wallpaper parallax**
toggles the effect and persists the choice.

Phone verification compared the same exposed wallpaper strip before and after a
horizontal swipe. With parallax off, its mean pixel difference was **0.0**; with it
on, **5.831** on the 0–255 scale. Reopening settings retained the disabled state;
the test restored parallax to enabled afterward.

## Performance findings

The installed development APK was debuggable. The glyph renderer also rebuilt
vector paths during redraws. The changes include an optimized, non-debuggable R8
build, a bounded 4 MiB glyph-texture cache, cached wallpaper layers, and removal of
All Apps' redundant per-item entrance animation. Existing asynchronous package-icon
loading remains in place. This follows
[Android's Compose performance guidance](https://developer.android.com/develop/ui/compose/performance).

The connected Android 14 phone was running **1080 × 2400 at 60 Hz**. No system
animation or refresh-rate settings were changed. Ten alternating 400 ms swipes
produced these spot-check results, with parallax enabled in the optimized build:

| Measurement | Previous Start APK | Optimized Start | Optimized All Apps |
| --- | ---: | ---: | ---: |
| Rendered frames | 404 | 442 | 485 |
| Median frame time | 15 ms | 11 ms | 10 ms |
| 95th percentile | 28 ms | 17 ms | 16 ms |
| 99th percentile | 69 ms | 32 ms | 32 ms |
| Reported janky frames | 5.69% | 4.07% | 2.89% |
| UI work, p95, last 120 frames | 9.95 ms | 6.54 ms | 6.44 ms |
| Render submission, p95, last 120 frames | 13.84 ms | 5.63 ms | 5.54 ms |

The reduction affects both UI work and rendering. This comparison measures the
combined changes, not an isolated attribution to one optimization. Some missed
frames remain; these results are not a guarantee of zero jank or 120 Hz behavior.
The final column packing also fills otherwise unused space with following groups.

## Build and verification artifacts

Use `gradlew.bat :app:assemblePerformance` for the installable optimized APK. This
variant uses the existing development signing key so it updates the previous APK
without clearing pins or settings. `release` also enables code/resource shrinking
and requires a production signing configuration for distribution.

Unit tests cover measured window geometry, 10 ms containment sweeps, curve
retiming, all tile sizes, alphabetical packing, identities and parallax bounds.
The final build passed 43 unit tests and reported zero lint errors.
The phone recording `build/animation-analysis/all-apps-launch.mp4` confirms the
app-face entrance without a front/back tile flip. Frame contact sheets, raw
`gfxinfo` captures and parallax screenshots are in `build/animation-analysis/`.

External app content still belongs to Android: the normal APK cannot transform
another process's live window. It hands off after the completed frame, so cold
startup and destination splash behavior remain outside the fitted animation.
