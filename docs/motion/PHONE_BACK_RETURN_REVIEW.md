# Phone Back-return correction — 11 October 2026

Applies to `feature/windows-phone-launcher-modes`, after `b104698`.

The preceding change made Back an exact time reversal of the launch. That was
mathematically consistent but disagreed with the real-phone footage. These are
separate shell phases, with different visible scale, opacity and row order.

## Source and scope

Decoded the original supplied files again, preserving their presentation timestamps:

| Recording | Phase | Measured interval |
| --- | --- | --- |
| `1000197577.mp4`, right phone | W10M Store return | 13.547–14.181 s |
| `1000197577.mp4`, right phone | W10M Settings return | 18.719–19.586 s |
| `1000197576.mp4` | WP8.1 Alarms → All Apps | 140.506–141.050 s |
| `1000197576.mp4` | WP8.1 Calculator → All Apps, independent validation | 152.270–152.900 s |

Additional online footage downloaded and inspected:

- [Golem, Microsoft Lumia 950 XL test, 15 December 2015](https://video.golem.de/mobil/16366/microsoft-lumia-950-xl-test.html).
  Decoded 1280 × 720, 30 fps; consecutive frames at 53.167–53.800 s show the
  Settings launch. Upper tiles enlarge outward while the selected gear remains
  longer, supporting the existing W10M launch family. Hand/camera movement and
  screen-refresh ghosting prevent precise corner fitting. This clip does not show
  the requested All Apps Back phase.

YouTube sources located during this review:

- [Aster, Lumia 820 / Project My Screen UI animations](https://www.youtube.com/watch?v=RNSggiWpmxM)
- [Nomad Tech Project, Lumia system-animation comparison](https://www.youtube.com/watch?v=POIE3hIpwhQ), explicitly described as W10M preview build 10549.

Their page metadata was accessible, but the media downloads returned a 195-byte
"Site Unavailable" HTML response and browser playback did not initialize. These
online videos are **not** represented as newly inspected frame evidence. W10M All
Apps uses the measured W10M Start motion family; this review does not establish
independent native All Apps timing parity from a new online capture.

The supplied W10M recording has roughly 33.4 ms frame intervals. The nominal
60 fps WP8.1 recording contains repeated images, usually leaving roughly 33 ms
between independent poses. A 16 ms emulator capture does not increase the source
recordings' temporal resolution.

## Changes

- W10M Start Back now uses the measured 0.70 → 1.00 entry scale and bottom-to-top
  reveal. Reversing launch had returned from scale 2.00 and retained a blank
  launch-only tail. The selected tile no longer changes the returning cascade.
- W10M All Apps Back uses that separate entry track. Its search toolbar now joins
  launch and Back motion instead of remaining static until the entire pane hides.
- W10M panorama pages clip their own content. Frame review caught offscreen
  Start tiles shrinking across the pane boundary into the All Apps left gutter;
  the render test now checks that gutter on every Back frame. WP8.1 retains its
  overlapping, projected Home entrance.
- WP8.1 All Apps Back has a fitted negative turn, bottom-to-top 250 ms stagger,
  270 ms row track, and a separate opacity ramp. A selected top app no longer
  appears before all the lower rows. Outline letter anchors precede filled icons.
- Active page and scroll-state restoration are preserved. An ordinary W10M
  Start → All Apps swipe does not replay row entrance. Desktop code, WP8.1 Start
  motion, tile gaps, and elastic scrolling are unchanged.

## WP8.1 measurements

`WP81_APPS_BACK_MEASUREMENTS.csv` contains observed blue icon corners and estimated
opacity in the 512 × 846 crop `(704,62,512,846)`. White glyph holes are filled before
fitting the four icon edges. Tiny partial components are rejected; near-identical
repeated poses are deduplicated. These are decoded observations, not synthetic
16 ms source frames.

Fitted model: camera distance = 8 × viewport width, starting angle −80°,
exponential remaining-progress exponent 3.53, horizontal/depth pivot coefficients
0.107 / 0.532. Filled rows fade over 120 ms after a 44 ms hidden phase. All of
these are estimates from footage, not recovered Microsoft source constants.

With rounded production parameters, the Alarms return has 2.48 px RMS corner
component error in the 512 px crop. The independent Calculator return gives
2.34 px RMS across 47 retained icon observations; maximum corner-component error
is 11.47 px. Only the clip alignment (152.2426 s) is fitted for that second event;
geometry, easing, duration and stagger remain fixed. Compression, uneven source
frame delivery and partially faded edges limit these measurements.

## Verification

The unit suite compares against independent observed coordinates and opacity,
replacing tests that compared a reversed equation to itself. The actual Compose
instrumentation captures Start launch, Home return, Back return, All Apps launch
and All Apps Back every 16 ms. It also retains panorama, resting geometry,
no-flash handoff, page restoration and elastic-scrolling checks.

The GitHub Actions artifact `phone-motion-render-evidence` contains the PNGs and
the device test results. Native timing fit and Android render checks are separate
checks; neither establishes complete end-to-end identity on every Android device.

Initial verified code commit: `5ef358b950eff6209e0121d09feec07226d8309e`.
[Actions run 38129019846](https://github.com/AdityaKumar-009/Til/actions/runs/38129019846)
passed unit tests, the performance APK build, and all three emulator tests
(0 failures, 0 errors). The downloaded render artifact contains 519 PNG captures,
including both W10M Back sequences and the WP8.1 All Apps Back sequence. The
separate Home preview artifact still depicts Home; use the named Back PNG
sequences for this change.

Review of those captures exposed the W10M pane-boundary leak described above.
The follow-up commit adds clipping and a regression assertion; its Actions run
must pass that assertion before the APK is considered verified.
