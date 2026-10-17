# Back return and fade clipping correction — 12 October 2026

## W10M navigation phase

The previous review misclassified a Home return as Back. In the supplied
`1000197577.mp4`, frame PTS 12.880 s has a yellow touch marker over the Windows
logo, immediately before the Store return previously labelled Back. This is
positive evidence for Home, not evidence for a Back transition.

Home keeps its measured 0.70 → 1.00 entrance. App Back again retraces the launch
zoom from outside the resting grid, including its selected-item delay, row
stagger and wallpaper track. Start, app rows, letter headings and the search
bar use this same Back phase. Ordinary panorama swipes remain static at the
row level, and the previously selected page/scroll position is retained.

The Back track reinstates the user's requested reverse behavior using the
existing fitted launch geometry. Its exact independent native All Apps timing
is not established by the Home footage and must not be labelled as such.

## WP8.1 rendering defect reproduced

The 7c0099e emulator captures show the first All Apps icon clipped at x=194 px
while fading. Its visible width is 107 px at 320 ms and 115 px at 336 ms. At
352 ms the icon suddenly starts at x=177 px and becomes 139 px wide. The
opacity transition to 1.0 removed an implicit offscreen-layer clip, causing the
visible pop. This is a rendering issue, not a change in the measured native turn.

The projection now wraps the opacity layer rather than drawing a projected
plane inside that layer's untransformed bounds. This preserves group opacity
and the complete projected icon/text plane throughout the fade. App rows,
letter anchors and the search icon use the corrected order. Existing turn,
stagger, camera and opacity curves are retained.

Android's documentation describes the offscreen clipping behavior:
https://developer.android.com/develop/ui/compose/graphics/draw/modifiers

## Verification

The renderer supports an internal exact-time sampling hook; normal app use
continues to use the display-synchronized clock. Instrumentation renders each
Start/All Apps Back pose every 10 ms. This is controlled animation sampling,
not a claim of 100 fps source video or 100 fps physical device performance.
The original native videos retain their own presentation timestamps.

The WP8.1 regression checks icon width before, during and after the opacity
boundary; the old rendering loses more than 20% of the icon and fails it.
W10M unit checks distinguish Home's below-unit scale from Back's above-unit
scale, check the reverse trajectory at 10 ms steps, and require exact resting
endpoints. Existing no-Start-bleed, page restoration, Home, launch handoff,
ordinary swipe and overscroll checks remain enabled.

Desktop behavior, WP8.1 Start curves, tile geometry and scroll physics are
unchanged. See the commit's Android Actions run for the APK and frame evidence.

## Additional online search

A new YouTube candidate was located: On MSFT's build 10149 animation demo,
https://www.youtube.com/watch?v=rdIumABr3e4 . The media request again returned a
195-byte HTML response, so it was not treated as decoded evidence. Dailymotion's
Gizbot Lumia 950 XL review was unavailable at its media endpoint (HTTP 403).

CHIP's Lumia 640 retrospective was successfully decoded: 1280×720, 25 fps,
309.04 seconds. Its sampled UI sections show Start, Cortana, personalization,
All Apps and Edge, but the inspected All Apps segment shows scrolling rather
than a confirmed app Back event. It is not a native timing calibration.
https://www.chip.de/news/Altes-Windows-Phone-zeigt-Deshalb-hatte-Windows-Mobile-keine-Chance-gegen-Android-iOS_177537860.html
