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

`appTransition.js` computes `baseScale = innerHeight / 850 / 2 + .5` for its
`launchHide()` timeout. It filters visible DOM items, reverses their order, and
stores each index to two decimal places across `0..1`. The CSS stagger uses the
root `--app-transition-scale`, whose value is `1`; it does not use `baseScale`.
The Compose renderer keeps those scales separate and includes the page-navigation
banner as index zero, as DiscoLauncher does.

## Start tile exit and return

| Motion | Timing and curve | Transform |
| --- | --- | --- |
| Unselected Start exit | 175 ms; delay `index × 200 ms`; `cubic-bezier(.75, 0, 1, 0)` | Left-edge turn; compound −30°/−10° rotations; `−25vw` travel; opacity drops only at the final frame. Mismatched CSS transform lists interpolate as matrices, including linear X/Z translation |
| Selected Start exit | 300 ms; delay `200 ms`; same curve | Same tile transform and terminal opacity |
| Start handoff | `launchHide()`; viewport `baseScale` affects the overall timeout | Waits for the turned-away pose before requesting the app |
| Start return from Home | 500 ms; delay `index × 200 ms`; `cubic-bezier(.3, 1, .2, 1)` | 70° combined left-edge turn to face-on; forward-resume distance is zero |
| All Apps page behind Start | 100 ms delay, then 750 ms | Second page turns from 45° to face-on behind Start; opacity and angle use DiscoLauncher's `cubic-bezier(.05, 1, .1, 1)`. Both its `perspective-origin` and `transform-origin` sit at the Start page's left edge |
| Start return from Back | 500 ms; same per-tile delay; `cubic-bezier(.05, 1, .1, 1)` | The DiscoLauncher back keyframe's −80° compound turn and X/Z offset, pivoted at the page's left edge; opacity follows its 0–1% reveal keyframes |
| Start return, inner content | 350 ms; same per-tile delay; `cubic-bezier(.2, .25, .25, 1)` | Forward/Home return only: icon/text begins 60 px to the right and at 45°, independently of the tile background |

Android Home intents use DiscoLauncher's forward-resume path. Returning from a
launched app with Android Back uses its separate back-resume path; the latter
does not run the independent inner-content animation. When the launched app was
opened from All Apps, Back still resets the panorama to Start before that tile
entrance; it does not leave the launcher on the app list.

When Android Home returns while All Apps was open, WP8.1 snaps the panorama
back to Start. The manual 270 ms Start/Apps slide does not run; instead, the
All Apps page runs its own turn behind the Start tiles. The CSS delay is written
as invalid `calc(var(.1s) * animationDurationScale)`. The renderer uses 100 ms
as an explicit inference because the observed reference shows the page turning
after the initial tile reveal; it is not a valid source CSS value. Both the
page's opacity and 45° turn use the source forward-resume easing. Manual
Start↔Apps gestures keep the reference panorama slide.

Start tiles, inner tile content, App-list rows, and search icon use projected
planes instead of separate Android 3D cameras per element. The Start and
App-list camera distance is 2000px; the second slide page uses 1000px
perspective with its origin at the Start page's left edge. Nested tile-face and
inner-content transforms are composed before projection so the separate 45°
inner turn retains the source `preserve-3d` relationship. The Home entrance
reserves index zero for the page icon banner before indexing visible tiles. The
Back entrance interpolates DiscoLauncher's compound start matrix to the resting
pose. Rendered Android frames still need to be checked against the supplied
reference at matched viewport and timestamps; unit tests validate the ported
transform equations, not visual parity with the original handset.

## All Apps exit

The app-list page now also uses DiscoLauncher’s mobile geometry: a 42px search
circle at `(25px, 26px)`, rows starting at 81px and ending 19px before the
viewport edge, 64px row height, 52px icons, and 30px labels. This matters during
the Home return because the complete All Apps page is projected behind the
entering Start tiles. App launches from that page use DiscoLauncher’s distinct
200 ms row turn, reversed visible-item index, and `.75, 0, 1, 0` exit easing.
The selected row starts at 300 ms. Rows turn −90° around the list edge and
travel by `1000px / -3`; letter rows additionally leave by `-100vw`. Their
perspective comes from the source app list's 2000px camera. The overall handoff
uses the same `launchHide()` envelope as Start.

## Persistent panorama correction

The previous Compose `AnimatedContent(targetState = showApps)` removed the All Apps
page whenever Start was the selected page. DiscoLauncher's
`#main-home-slider > .slide-content` keeps **both** `.slide-page` descendants
in one slider at all times. This caused a visible omission on Home return
from Start (not merely a mistimed tile).

`PhoneLauncherSurface` now keeps Start and All Apps mounted simultaneously.
The two panes share one animation progress and remain one viewport apart on
manual swipes; Home/Back resets use a zero-duration snap of that offset.
During forward Home/intro only, the All Apps page occupies a viewport-wide
render layer at x=0 and its perspective transform projects the original page
from world x=+viewport into the Start viewport. Keeping the drawing layer at
x=0 prevents alpha offscreen compositing from clipping its incoming left edge.
The Start tile layer is rendered in front of the second page as in the
source's negative-z stacking. The home-page perspective is not added to the
normal horizontal swipe offset a second time.

The instrumented test now checks a Home-return **while already on Start**
as well as an All Apps-to-Start Home return, an All Apps launch, and an
Android Back return. A stable semantic tag identifies the bottom search
button now that the offscreen page remains in the semantics tree.

**Validation boundary:** the tests examine sampled Compose frames and
screen-state invariants. They do not compare those frames to an independent
DiscoLauncher recording at identical geometry/color density. This work is
a source-driven port rather than verified 100% perceptual equivalence. The
source's invalid CSS `var(.1s)` expression is still implemented as a
100ms inferred animation delay, not a browser-guaranteed behavior.

## Renderer checks

`PhoneStartChoreographyTest` checks the viewport-scaled `launchHide()` timeout,
fixed CSS stagger delays, both return paths, the separate Home inner-layer track,
the All Apps page's projected corners, source easing, terminal transforms, the
composed CSS Back matrix, app-list letter travel, and finite frame samples. The
instrumented renderer exports 16 ms WP8.1 frames for Home-return, Start
exit/Back-return, All Apps launch, and Back from an app opened in All Apps. Its
Home-return check samples the rendered All Apps search control during the
source-paced turn. The test fixture includes multiple alphabetical groups so
the turning page contains visible rows. W10M still uses
`MobileStartMotion` and retains its own 534 ms exit capture.
