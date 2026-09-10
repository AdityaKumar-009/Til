# Windows 8.1 Start and charms refinement

## References and measurements

The new reference is the **right Windows 8.1 side** of [jgadventure2's comparison video](https://www.youtube.com/watch?v=Rbn0z4ylKvc). Its downloaded stream is 1920×1080 at 60 encoded frames per second. The Start entrance begins around 32 seconds. `scripts/analyze_start_comparison.py` measures all 169 encoded frames from 32.000 through 34.800 seconds and writes [the measurements](start-comparison-recorded-frames.csv). Cropped frames and contact sheets stay in the ignored `build/animation-analysis/start-refinement/` directory.

There are repeated or nearly repeated source frames. Encoded 60 fps is not evidence of 60 independent animation samples, and 10 ms sampling would not add observed subframes. The split view crops the desktop; positions below are relative to its right 960-pixel crop. The implementation scales travel to the launcher viewport. It reproduces the timing in this edited video, which does not establish the original operating system's animation duration.

The large yellow Desktop tile provides a distinct region for tracking. Its settled bounds are approximately `(100,210,248,248)` in the crop. Translation must be calculated from the tile **center**, because the width changes during growth. Using its left edge alone counts part of the scaling twice.

| Time after 32.000 s | Tile bounds, x/y/w/h | Center displacement | Scale from width | Estimated opacity |
|---|---|---|---|---|
| 33 ms | 472/350/156/154 | 326 px | 0.629 | 0.60 |
| 50 ms | 456/332/168/168 | 316 px | 0.677 | 0.75 |
| 100 ms | 406/286/198/198 | 281 px | 0.798 | 1.00 |
| 200 ms | 332/245/226/225 | 221 px | 0.911 | 1.00 |
| 500 ms | 208/214/247/246 | 107.5 px | 0.996 | 1.00 |
| 1000 ms | 134/212/248/246 | 34 px | 1.000 | 1.00 |
| 2000 ms | 102/212/248/246 | 2 px | 1.000 | 1.00 |
| 2500 ms | 100/210/248/248 | 0 px | 1.000 | 1.00 |

Opacity is estimated from RGB blending of the yellow tile against the purple background. Compression, texture, and fading affect that estimate. The header appears later than the first tiles, around 800 ms, and settles around 1400 ms. Later tile groups follow the first group. Their delays are approximated from their first visible frames, rather than recovered from Windows source code.

The recordings now serve separate events, as requested:

- **Cold startup and screen unlock:** the comparison's 2500 ms first-group motion, with 120 ms stagger and a 2800 ms outer envelope.
- **Home and Back:** the separate `test.mp4` return fit, with 600 ms first-group motion, 36 ms group stagger and a 680 ms outer envelope. Its scale, opacity, and travel tracks remain independent of the startup curve.

The short-entrance measurements remain in `start-entrance-and-wallpapers.md`. Unlock uses screen-off/user-present events and keyguard state. Each band uses a single graphics transform, including gaps and labels, with delays relative to the saved viewport. Tapping during either entrance freezes the current pose for the launch overlay instead of waiting for the entrance to finish.

## Bottom controls and charms

The local `test.mp4` frame at 57 seconds shows a thin circled down arrow with a vertical stem at the left tile margin, and a small square minus control at the lower right. The launcher adapts their visible size for a phone while retaining larger invisible touch targets. The minus opens an overview of tile groups; choosing a group returns to its existing position.

The comparison video shows the two charms rails side by side around 70 seconds. The **rightmost** rail is Windows 8.1. It has a nearly black background, five white symbols with grey labels, and a dark purple perspective Windows symbol whose highlight is lighter purple. The Devices pane at 100 seconds and Settings pane at 150 seconds show dark purple rectangular panels, light typography, and flat selection rows. The launcher uses those as its visual reference.

The charm rail and panel opening curves are reconstructed. The comparison's title cuts and enlarged slow-motion segment do not provide an uninterrupted calibrated recording of every charm transition. These curves, Android font rendering, responsive spacing, and device actions are not independently verified as pixel-identical to Windows.

The rail is an overlay, so opening it does not resize or repack Start. The right-edge recognizer claims an inward horizontal gesture after direction lock. Vertical drawer drags and horizontal drags away from that edge retain their existing behavior. The avatar and accessibility action also open charms, including on devices that reserve the physical screen edge for Android Back.

Search works with installed apps. Start returns to the launcher, Settings connects to the existing launcher preferences, and Devices opens Android's available device settings. Windows-only system services are adapted to a normal APK. No privileged task backend or external app overlay permission is required.
