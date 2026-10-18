# Five optional launch stories

Mosaic Settings → App launch animation now includes Lift, Door, Ripple, Page turn,
and Glide. The three original Windows choices and Default are preserved. The
choice is shared across Desktop, Windows Phone 8.1, and Windows 10 Mobile.

| Story | Physical idea | Surrounding scene | Duration |
|---|---|---|---|
| Lift | A tile leaves the mosaic with a slight gravity dip and rises into an app | Neighbors recede to give it depth | 420 ms |
| Door | A hinged tile opens into another space | Tiles part to either side | 440 ms |
| Ripple | A touch propagates through the mosaic before opening the app | Distance controls when each neighbor reacts | 430 ms |
| Page turn | A sheet lifts from its top edge and unfolds | Rows lift away in sequence | 460 ms |
| Glide | A sheet travels on a continuous sliding track | The old scene slides aside | 400 ms |

These are original adaptations of physical motion principles, not claims to
reproduce an additional historical Windows animation.

## Research

- [Microsoft: Connected animation](https://learn.microsoft.com/en-us/windows/apps/develop/motion/connected-animation): preserve the selected object's identity and coordinate related elements.
- [Microsoft: Directionality and gravity](https://learn.microsoft.com/en-us/windows/apps/design/motion/directionality-and-gravity): motion should maintain spatial continuity, support forward/back navigation, and respond to plausible forces.
- [Microsoft: Timing and easing](https://learn.microsoft.com/en-us/windows/apps/design/motion/timing-and-easing): acceleration and friction communicate intent while keeping the interface responsive.
- [Android: Compose animation performance](https://developer.android.com/develop/ui/compose/animation/quick-guide#optimize-animation-performance): defer animated reads to drawing/layer transforms rather than recomposition and layout.
- [Android: Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#composable-to-bitmap): record and draw a reusable GraphicsLayer. Mosaic reuses the display list directly; it does not create a bitmap per frame.

## Implementation and boundaries

One shared display clock drives the selected surface and every surrounding tile,
row, letter, and participating toolbar. Spatial travel uses a front-loaded cubic
Bezier curve `(0.16, 1, 0.30, 1)`: over three quarters of the distance happens in
the first quarter of the time, followed by a long, quiet deceleration. Tighter
neighbor delays keep each story connected to the touch. Quintic envelopes blend
pressure and opacity while the surface moves. Back retraces the same spatial
poses, including an interrupted opening, but remaps the clock to respond quickly
and settle gently at the source. A full return takes 86% of the opening duration.
External returns retain a lightweight
launch description and reacquire the current source artwork. Internal Settings
mounts after the moving scene has finished, and closes using the same story.

The selected cell transfers its real drawing commands to the foreground plane;
its grid location draws nothing while the plane owns it. This preserves native
glyphs, live contents, gradients, and tile wallpaper without a duplicate tile,
new icon decode, or a second color policy. Source and destination glyphs retain
their aspect ratios. Shortcuts without a physical cell use a cached icon plate.

All geometry changes use graphics layers; no layout sizes, bitmap decoding,
blur passes, random staggering, or separate per-tile animations run per frame.
The original three motion implementations are untouched. Android still owns
external app windows: the launcher holds its completed destination frame until
the OS presents the app, and animates its own scene again on return. Surviving
process death falls back to the normal startup path rather than retaining a
stale view or task snapshot.

`StoryLaunchMotionTest` checks source/destination endpoints, all neighbor motion,
distance-based wave timing, fast release and calm return timing, numerical continuity, and wide/partially visible
sources. `StoryLaunchRenderTest` checks all five choices on the six real public
surfaces, including absence of a duplicate source, actual neighbor motion, exact
artwork at time zero, matching return poses, and compact emulator captures.
