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
row, letter, and participating toolbar. Spatial travel uses a cubic Bezier curve
`(0.16, 0, 0.18, 1)`: it accelerates from rest, travels about halfway in the first
quarter, and leaves less than four percent for the calm final quarter. The
original 520–600 ms durations are retained. Position, dimensions, hinge angles,
pressure, and opacity all follow that same traveled phase. Gentle dimension
lead/lag replaces the previous independently delayed expansion curves, which
introduced velocity steps partway through travel. Distance-based neighbor
propagation has a smooth onset rather than another abrupt fast-out clock.
Complementary artwork/app opacity avoids a blank beat during the handoff.
Back retraces the same spatial and visual poses, including an interrupted
opening, and remaps the clock to settle gently at the source. A full return
keeps the same duration as the opening.
External returns retain a lightweight
launch description and reacquire the current source artwork. Internal Settings
is composed during source preparation, appears within the moving plane, and
retains that same content through Back. No splash-to-window replacement occurs
at the end of the opening or partway through the return.

The selected cell records its real drawing commands during preparation. Before the motion
clock starts, one cached image freezes that artwork so nested graphics layers
cannot lose their contents when transparent or offscreen. Its grid location draws
nothing while the foreground plane owns it. This preserves native glyphs, the
current live contents, gradients, and tile wallpaper without a duplicate tile,
new icon decode, or a second color policy. Source and destination glyphs retain
their aspect ratios; wide row artwork is capped to the viewport width so its
leading icon remains visible. Shortcuts without a physical cell use a cached icon plate.

All geometry changes use graphics layers; no layout sizes, bitmap decoding,
blur passes, random staggering, or separate per-tile animations run per frame.
The original three motion implementations are untouched. Android still owns
external app windows: the launcher holds its completed destination frame until
the OS presents the app, and animates its own scene again on return. Surviving
process death falls back to the normal startup path rather than retaining a
stale view or task snapshot.

`StoryLaunchMotionTest` checks source/destination endpoints, all neighbor motion,
distance-based wave timing, original durations, smooth release and calm return,
velocity continuity at the old stage boundaries, overlapping identity, first
Back-frame opacity, and wide/partially visible sources. `StoryLaunchRenderTest`
also verifies that the internal window stays mounted across opening and Back,
and checks all five choices on the six real public
surfaces, including absence of a duplicate source, actual neighbor motion, exact
artwork at time zero, matching return poses, and early/midpoint emulator captures.
