# Frame-derived phone Start transitions

These notes describe the user's **uploaded reference footage**, not Microsoft's
private Windows 10 Mobile animation engine. All three recordings were decoded locally,
and motion frames were compared with the prior Tilt launcher implementation.

| Source | Playback duration | Video frame interval | Scope |
|---|---:|---:|---|
| 1000197575.mp4 | 408.04 seconds | ~16.7 ms (60fps) | Early classic Windows Phone demonstrations |
| 1000197576.mp4 | 308.71 seconds | ~16.7 ms (60fps) | Classic Start, app list, settings and in-app navigation |
| 1000197577.mp4 | 178.72 seconds | ~33.4 ms (29.98fps) | WP8.1 (left) and Windows 10 Mobile (right) in parallel |

## Additional classic 60fps animation evidence

- 1000197575.mp4, 47.80–48.30s: a 3D Start entrance shows lower
  tiles facing the viewer while upper tiles are still mostly edge-on.
  Entrance therefore also uses a bottom-to-top, right-to-left spatial cascade.
- 1000197576.mp4, 88.00–88.50s: People tile animates its **own inner face**
  while other Start tiles remain fixed. Do not conflate live-tile flips with
  app/page navigation, which is handled by a different motion subsystem.

## Classic all-apps panorama transition

- 1000197576.mp4, 134.90–135.17s at ~60fps: the Start pane
  translates left out of view as the adjacent app-list pane is exposed.
  List entries stay fully visible, with no independent Y-axis flip and
  no compounded fade.
- Implemented as two full-width horizontally translating panes with
  270ms (classic) / 260ms (Mobile) easing. The home pane suppresses
  per-tile re-entry when returning from the app list; launching an
  actual application still runs its dedicated per-item navigation motion.

## Annotated event: side-by-side Start departure, 144.65–145.18 seconds

Frames were read at their native ~33.4ms capture intervals.

- 144.651–144.718: both phone Start grids mostly stable.
- 144.751–144.851: Windows Phone 8.1 lower/right tiles start turning away
  in visible spatial succession. Upper-left tiles remain comparatively stable.
  Windows 10 Mobile tiles start moving upward/receding while the wallpaper is
  already visible beneath the grid.
- 144.885–144.985: WP8.1 lower/right and middle tiles are gone; upper/left
  remain. Windows 10 Mobile's wallpaper expands across most of the Start view.
- 145.018–145.051: WP8.1's last upper-left tiles disappear.
- 145.085–145.185: Windows 10 Mobile's remaining background/dim-out completes.

**Conclusions:**

1. WP8.1 Start `FORWARD_OUT` is **not** the generic 250ms + (index*50ms)
   page feather applied indiscriminately to every Start tile. It has screen-
   position-based timing with bottom/right exiting before top/left.
2. Based on 144.751–145.051 (~300ms including uncertainty at the trigger),
   choose **270ms** for a Start exit from the onset of clear motion; observe that
   the recorded source may omit frames due to capture or editing. This is a
   tuned approximation, not an extracted proprietary timing constant.
3. Windows 10 Mobile uses a flatter **~332ms** wallpaper-reveal tile exit with
   mostly upward motion and opacity reduction, without classic turnstile Y
   rotation. Preserve the wallpaper throughout.
4. The app-page transition remains distinct: use Microsoft's documented
   turnstile/feather keyframes for those, not the Start-specific behavior.

**Limits:** Measured footage is 30/60fps, so 10ms samples of the implementation
are deterministic interpolation between actual frame observations. The recordings
show pre-recorded emulator/device demos and may include cuts, loading hitches and
video compression. Animation equivalence requires capturing the APK on a real
Android device and comparing at equivalent screen sizes/frame rates.

No updates to the Windows 8.1 Desktop UI/animation system.

## Regression analysis from installed APK recording 1000197579.mp4

- The uploaded capture is 2400×1080, 4055 frames at approximately 61.17fps, 66.29 seconds.
- Near 7.55–7.68s the orange People tile occupies approximately 29,925 orange pixels.
- By 7.746s that tile has exited and the orange pixel count reaches zero.
- At 7.91–8.02s the full orange tile returns for roughly 130ms while Android starts another Activity.
- At 8.04s the Android target's white launch surface finally covers the launcher.
- Cause: after startActivity() the launcher immediately called onDismissFlip(), resetting the
  phone's tile exit, so the tiles painted again during Android's asynchronous window swap.
- Fix: the phone compositor now latches its completed exit until the launcher receives an
  entrance/Home request; Windows 8.1 Desktop branch is untouched.
- Official Microsoft WPF ExponentialEase.EaseInCore code computes
  (exp(exponent * t) - 1)/(exp(exponent) - 1). The earlier code incorrectly
  implemented base-two exponential instead of exp (natural base e).

A different Android app's system splash, frame rate and first-frame timing are outside
an ordinary launcher's control. Same-device follow-up filming is necessary to validate
these source-code fixes and quantify remaining visual error.
