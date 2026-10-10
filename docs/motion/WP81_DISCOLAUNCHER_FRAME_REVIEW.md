# Windows Phone 8.1 motion update — 10 October 2026

## Reference

This correction follows the implementation in
[DiscoLauncher `appTransition.scss`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/styles/appTransition.scss)
and [`appTransition.js`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/scripts/appTransition.js)
at commit `4907389358708754521bf62a5b2048823edcd12e`. Its global flow perspective
is `1000px` in [`flowTouch.scss`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/styles/flowTouch.scss).
The Start tile page overrides this with `perspective: calc(flow-perspective * 2)`
in [`tileList.scss`](https://github.com/cherryhoax/DiscoLauncher/blob/4907389358708754521bf62a5b2048823edcd12e/src/styles/pages/tileList.scss),
so the tile layers use a matching 2000px camera distance and project the X/Z
matrix offsets through Compose.

`appTransition.js` computes `baseScale = innerHeight / 850 / 2 + .5`, filters
the visible DOM items, reverses their order, and stores each index to two decimal
places across `0..1`. The Compose renderer applies the same viewport scale and
reversed order to visible Start tiles. It includes the page-navigation banner
as index zero, as DiscoLauncher does.

## Start tile exit and return

| Motion | Timing and curve | Transform |
| --- | --- | --- |
| Unselected Start exit | 175 ms; delay `index × 200 ms × baseScale`; `cubic-bezier(.75, 0, 1, 0)` | Left-edge turn; compound −30°/−10° rotations; `−25vw` travel; opacity drops only at the final frame |
| Selected Start exit | 300 ms; delay `200 ms × baseScale`; same curve | Same tile transform and terminal opacity |
| Start handoff | `selected delay + 300 ms + 200 ms` | Waits for the turned-away pose before requesting the app |
| Start return from Home | 500 ms; delay `index × 200 ms × baseScale`; `cubic-bezier(.3, 1, .2, 1)` | 70° combined left-edge turn to face-on; forward-resume distance is zero |
| Start return from Back | 500 ms; same per-tile delay; `cubic-bezier(.05, 1, .1, 1)` | The DiscoLauncher back keyframe's −80° compound turn and X/Z offset, pivoted at the page's left edge; opacity follows its 0–1% reveal keyframes |
| Start return, inner content | 350 ms; same per-tile delay; `cubic-bezier(.2, .25, .25, 1)` | Forward/Home return only: icon/text begins 60 px to the right and at 45°, independently of the tile background |

Android Home intents use DiscoLauncher's forward-resume path. Returning from a
launched app with Android Back uses its separate back-resume path; the latter
does not run the independent inner-content animation.

When Android Home returns while All Apps was open, WP8.1 snaps the page state
back to Start and runs only the Start tile return. It does not replay the manual
270 ms Start/Apps panorama underneath the tile turn. Manual Start↔Apps gestures
keep the reference panorama slide.

The exit and Back matrices preserve their X/Z translation and left-edge origin
in the Compose layer, projecting Z offsets with the same 2000px distance. The
Home entrance keeps the tile face and inner content on separate layers. The
Back entrance interpolates DiscoLauncher's compound start matrix to the resting
face-on pose. The old guessed 84° right-edge hinge, offset tile ranks, and
1000px Start camera have been removed.

## All Apps exit

The app-list path uses DiscoLauncher’s distinct 200 ms row turn, reversed
visible-item index, and `.75, 0, 1, 0` exit easing. Its selected row starts at
300 ms. Rows turn −90° around the list edge and travel by `1000px / -3`; letter
rows additionally leave by `-100vw`. The overall handoff uses the same
`launchHide()` envelope as Start.

## Renderer checks

`PhoneStartChoreographyTest` checks the viewport-scaled total, selected and
stagger delays, both return paths, the separate Home inner-layer track, terminal
transforms, app-list letter travel, and finite frame samples. The instrumented
renderer exports 16 ms WP8.1 frames for Start exit/Back-return and All Apps
launch. W10M still uses
`MobileStartMotion` and retains its own 534 ms exit capture.
