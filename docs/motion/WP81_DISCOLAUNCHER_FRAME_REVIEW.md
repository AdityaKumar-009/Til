# Windows Phone 8.1 motion update — 10 October 2026

## Reference

This correction follows the implementation in
[DiscoLauncher `appTransition.scss`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/styles/appTransition.scss)
and [`appTransition.js`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/scripts/appTransition.js)
at commit `4907389358708754521bf62a5b2048823edcd12e`. Its global flow perspective
is `1000px` in [`flowTouch.scss`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/styles/flowTouch.scss).

`appTransition.js` computes `baseScale = innerHeight / 850 / 2 + .5`, filters
the visible DOM items, reverses their order, and stores each index to two decimal
places. The Compose renderer applies the same viewport scale and reversed order
to visible Start tiles. It includes the page-navigation banner as index zero,
as DiscoLauncher does.

## Start tile exit and return

| Motion | Timing and curve | Transform |
| --- | --- | --- |
| Unselected Start exit | 175 ms; delay `index × 200 ms × baseScale`; `cubic-bezier(.75, 0, 1, 0)` | Left-edge turn; compound −30°/−10° rotations; `−25vw` travel; opacity drops only at the final frame |
| Selected Start exit | 300 ms; delay `200 ms × baseScale`; same curve | Same tile transform and terminal opacity |
| Start handoff | `selected delay + 300 ms + 200 ms` | Waits for the turned-away pose before requesting the app |
| Start return, outer face | 500 ms; delay `index × 200 ms × baseScale`; `cubic-bezier(.3, 1, .2, 1)` | 70° combined left-edge turn to face-on; forward-resume distance is zero |
| Start return, inner content | 350 ms; same per-tile delay; `cubic-bezier(.2, .25, .25, 1)` | Icon/text begins 60 px to the right and at 45°, independently of the tile background |

The exit matrix is folded into equivalent Compose Y rotation and X/Z offsets;
the entrance keeps the tile face and inner content on separate layers. The old
guessed 84° right-edge hinge and 15 ms spatial rank have been removed.

## All Apps exit

The app-list path uses DiscoLauncher’s distinct 200 ms row turn, reversed
visible-item index, and `.75, 0, 1, 0` exit easing. Its selected row starts at
300 ms. Rows turn −90° around the list edge and travel by `1000px / -3`; letter
rows additionally leave by `-100vw`. The overall handoff uses the same
`launchHide()` envelope as Start.

## Renderer checks

`PhoneStartChoreographyTest` checks the viewport-scaled total, selected and
stagger delays, separate inner-layer track, terminal transforms, app-list
letter travel, and finite frame samples. The instrumented renderer exports
16 ms WP8.1 frames for Start exit/return and All Apps launch. W10M still uses
`MobileStartMotion` and retains its own 534 ms exit capture.
