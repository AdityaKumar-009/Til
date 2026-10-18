# Five optional launch stories

Mosaic Settings → App launch animation now includes Lift, Door, Ripple, Page turn,
and Glide. The three original Windows choices and Default are preserved. The
choice is shared across Desktop, Windows Phone 8.1, and Windows 10 Mobile.

| Story | Physical idea | Surrounding scene | Duration |
|---|---|---|---|
| Lift | A pressed tile lifts out of the mosaic and opens into an app | Neighbors recede to give it depth | 520 ms |
| Door | A hinged tile opens into another space | Tiles part to either side | 560 ms |
| Ripple | A touch propagates through the mosaic before opening the app | Distance controls when each neighbor reacts | 560 ms |
| Page turn | A sheet lifts from its top edge and unfolds | Rows lift away in sequence | 600 ms |
| Glide | A sheet travels on a continuous sliding track | The old scene slides aside | 520 ms |

These are original adaptations of physical motion principles, not claims to
reproduce an additional historical Windows animation.

## Research

- [Microsoft: Connected animation](https://learn.microsoft.com/en-us/windows/apps/develop/motion/connected-animation): preserve the selected object's identity and coordinate related elements.
- [Microsoft: Directionality and gravity](https://learn.microsoft.com/en-us/windows/apps/design/motion/directionality-and-gravity): motion should maintain spatial continuity, support forward/back navigation, and respond to plausible forces.
- [Microsoft: Timing and easing](https://learn.microsoft.com/en-us/windows/apps/design/motion/timing-and-easing): acceleration and friction communicate intent while keeping the interface responsive.
- [Android: Compose animation performance](https://developer.android.com/develop/ui/compose/animation/quick-guide#optimize-animation-performance): defer animated reads to drawing/layer transforms rather than recomposition and layout.
- [Android: Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#composable-to-bitmap): record a real GraphicsLayer and snapshot its artwork before moving it. Mosaic caches one image before travel; it does not create a bitmap per frame.

## Implementation and boundaries

One shared display clock drives the selected surface and every surrounding tile,
row, letter, and participating toolbar. Spatial travel uses a measured cubic
Bezier curve `(0.22, 0.70, 0.30, 1)`: roughly two thirds of the lead movement happens
in the first quarter of its track, followed by a long, quiet deceleration. The
original 520–600 ms durations and overlapping story phases are retained. The
curve redistributes velocity instead of shortening the animation. Distance-based
neighbor delays preserve each story's physical order. Quintic envelopes blend
pressure and opacity while the surface moves. Back retraces the same spatial
poses, including an interrupted opening, and remaps the clock to settle gently
at the source. A full return keeps the same duration as the opening.
External returns retain a lightweight
launch description and reacquire the current source artwork. Internal Settings
mounts after the moving scene has finished, and closes using the same story.

The selected cell records its real drawing commands once. Before the motion
clock starts, one cached image freezes that artwork so nested graphics layers
cannot lose their contents when transparent or offscreen. Its grid location draws
nothing while the foreground plane owns it. This preserves native glyphs, the
current live contents, gradients, and tile wallpaper without a duplicate tile,
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
distance-based wave timing, original durations, measured release and calm return timing, numerical continuity, and wide/partially visible
sources. `StoryLaunchRenderTest` checks all five choices on the six real public
surfaces, including absence of a duplicate source, actual neighbor motion, exact
artwork at time zero, matching return poses, and compact emulator captures.
