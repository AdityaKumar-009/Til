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
| All Apps page behind Start | 100 ms delay, then 750 ms; `cubic-bezier(.05, 1, .1, 1)` | Second page turns from 45° to face-on and fades from 0 to 1; both its `perspective-origin` and `transform-origin` sit at the Start page's left edge |
| Start return from Back | 500 ms; same per-tile delay; `cubic-bezier(.05, 1, .1, 1)` | The DiscoLauncher back keyframe's −80° compound turn and X/Z offset, pivoted at the page's left edge; opacity follows its 0–1% reveal keyframes |
| Start return, inner content | 350 ms; same per-tile delay; `cubic-bezier(.2, .25, .25, 1)` | Forward/Home return only: icon/text begins 60 px to the right and at 45°, independently of the tile background |

Android Home intents use DiscoLauncher's forward-resume path. Returning from a
launched app with Android Back uses its separate back-resume path; the latter
does not run the independent inner-content animation. When the launched app was
opened from All Apps, Back still resets the panorama to Start before that tile
entrance; it does not leave the launcher on the app list.

When Android Home returns while All Apps was open, WP8.1 snaps the panorama
back to Start. The manual 270 ms Start/Apps slide does not run; instead, the
All Apps page performs its separate 750 ms perspective turn behind the Start
tiles. The CSS delay is written as invalid `calc(var(.1s) * animationDurationScale)`;
the renderer uses the evident intended 100 ms value. Manual Start↔Apps gestures
keep the reference panorama slide.

The exit and Back matrices preserve their X/Z translation and left-edge origin
in the Compose layer, projecting Z offsets with the same 2000px distance. Both
the inner Start tile content and All Apps rows use their source parent
perspective of 2000px. The All Apps page itself uses a projective transform from
the source slide page's 1000px camera at the Start page's left edge. The earlier
Compose version pivoted that page at the edge but kept the camera centered on
the page, which left too little of the list visible behind the entering tiles.
The Home entrance keeps the tile face and inner content on separate layers and
reserves index zero for the page icon banner before indexing visible tiles. The
Back entrance interpolates DiscoLauncher's compound start matrix to the resting
face-on pose; its test composes the CSS transform list and checks the resulting
X/Z pose. The old guessed 84° right-edge hinge, unreserved tile ranks, and
1000px Start camera have been removed.

## All Apps exit

The app-list path uses DiscoLauncher’s distinct 200 ms row turn, reversed
visible-item index, and `.75, 0, 1, 0` exit easing. Its selected row starts at
300 ms. Rows turn −90° around the list edge and travel by `1000px / -3`; letter
rows additionally leave by `-100vw`. Their perspective comes from the source
app list's 2000px camera. The overall handoff uses the same `launchHide()`
envelope as Start.

## Renderer checks

`PhoneStartChoreographyTest` checks the viewport-scaled total, selected and
stagger delays, both return paths, the separate Home inner-layer track, the
All Apps page's projected corners, terminal transforms, the composed CSS Back
matrix, app-list letter travel, and finite frame samples. The instrumented
renderer exports 16 ms WP8.1 frames for Home-return, Start exit/Back-return,
All Apps launch, and Back from an app opened in All Apps. Its Home-return check
looks for rendered Apps-page pixels behind Start rather than relying on the
page's semantic bounds. W10M still uses
`MobileStartMotion` and retains its own 534 ms exit capture.
