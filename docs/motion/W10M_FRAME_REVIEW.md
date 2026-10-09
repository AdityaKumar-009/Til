# Windows 10 Mobile motion correction — 9 October 2026

Target: **right-hand Windows 10 Mobile phone** in the user's
`1000197577.mp4`. The left phone and `1000197575.mp4` / `1000197576.mp4`
are Windows Phone 8.1 references, not interchangeable animation styles.

This supersedes the earlier claim in PHONE_MOTION_VIDEO_MEASUREMENTS.md that
Mobile Start mostly moves upwards over 332 ms. That observation was incomplete.

## What was inspected

The comparison is 1920×1080 with native frame timestamps roughly 33.4 ms apart.
The first two recordings are nominally ~60 fps but contain long runs of repeated
source images. Encoded frame rate is not independent visual sample rate.

After timeline scans, consecutive frames were extracted for these events:

| Event | Source interval (seconds) | Finding |
|---|---:|---|
| Store launch | 7.600–8.550 | Top rows enlarge first; the selected Store tile is delayed |
| Store return | 13.400–14.250 | Lower rows return first, smaller and closer to a common centre |
| Settings launch | 15.050–15.900 | Same outward zoom; selected gear remains longer |
| Settings return | 18.700–19.500 | Reverse row order and a long scale-settling tail |
| Calendar launch | 28.250–29.200 | Same row zoom, followed by wallpaper darkening |
| Calendar return | 32.750–33.600 | Same bottom-to-top reveal |
| Maps launch, scrolled Start | 144.550–145.300 | Upper tiles move up/right; lower tiles move down/out |

The right screen crop used for measurements is `(1026,60,508,840)`.
Glyphs were tracked by grayscale template correlation over scales 0.68–1.90
in 0.02 steps; pink tile masks provided a separate check. The accompanying CSV
records selected observed samples, not interpolated pseudo-frames. Its `frame`
is the original zero-based decoded frame index and `pts_seconds` is the original
presentation timestamp. Partially clipped or poorly correlated glyphs were omitted.

Resting glyph centres in that crop: Store (84.5,119), gear (254,121),
Edge (254,456), MixRadio (419,621). The fitted common zoom centre is about
(254,456). Edge stays there while scaling. MixRadio moves down/right during
exit and up/left during entry. A uniform upward translation cannot reproduce that.
The scrolled Maps event moves the same MixRadio tile up/right because it is then
above the common centre. This rules out a fixed per-tile translation direction.

## Implementation changes

- A dedicated `MobileStartMotion` samples independent scale, opacity and background
  tracks. Each tile scales around the same **window-space centre** using its full,
  unclipped geometry. There is no W10M Y rotation or arbitrary selected-tile overscale.
- Top visible row to bottom visible row spans 133 ms on exit. Selected tiles receive
  a bounded 67 ms delay. Each row's zoom lasts 250 ms, with a late 100 ms fade.
- The wallpaper remains after the tiles disappear, then fades during 400–534 ms.
- Return starts at scale 0.70, reverses row order over 180 ms, and settles over
  450 ms per row. The opacity fade finishes earlier than the scale movement.
- Row timing comes from **visible row positions**, not small-cell indices clamped
  at five. Dense layouts, partially visible rows and a scrolled Start retain a cascade.
- One Compose animation clock drives the rendered pose and completion callback.
  Handoff occurs after the final pose has been submitted, not after an independent
  coroutine delay. The finished pose and selected tile ID remain latched until return.
- Start/All Apps pane navigation no longer recreates an entrance clock. App-list
  launches identify their actual origin and finish their own row timeline. Repeated
  taps and scrolling cannot mutate the outgoing layout while it is animating.
- Classic WP8.1 remains a separate turnstile. Its visible right-edge hinge and
  retained selected tile were corrected from the classic reference; its generic
  app-page toolkit motion remains separate from W10M Start.

## Accuracy and limits

These are **fitted motion parameters**, not Microsoft's recovered source constants.
They are checked against multiple observed glyph trajectories, with tolerances
reflecting 30 fps sampling, repeated frames and lossy resizing. For example, the
new exit sends MixRadio towards the measured lower-right location; the previous
model sent it upwards and reduced its opacity almost immediately.

The Android launcher can reproduce its own Start choreography. It cannot control
another app's live window, loading time or system splash, or globally replace an
OEM's Home/Recents transition. This patch does not claim 100% pixel or timing
identity. A matched recording on the user's device remains necessary to measure
end-to-end visual error. Mathematical and emulator tests are not substitutes for
that device comparison.

Official API context (not used as evidence of private W10M shell timing):
- https://developer.android.com/develop/ui/compose/animation/value-based
- https://developer.android.com/reference/android/app/ActivityOptions
- https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.continuumnavigationtransitioninfo
