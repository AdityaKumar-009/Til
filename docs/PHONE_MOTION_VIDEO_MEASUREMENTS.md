# Frame-derived phone Start transitions

These notes describe the user's **uploaded reference footage**, not Microsoft's
private Windows 10 Mobile animation engine. All three recordings were decoded locally,
and motion frames were compared with the prior Tilt launcher implementation.

| Source | Playback duration | Video frame interval | Scope |
|---|---:|---:|---|
| 1000197575.mp4 | 408.04 seconds | ~16.7 ms (60fps) | Early classic Windows Phone demonstrations |
| 1000197576.mp4 | 308.71 seconds | ~16.7 ms (60fps) | Classic Start, app list, settings and in-app navigation |
| 1000197577.mp4 | 178.72 seconds | ~33.4 ms (29.98fps) | WP8.1 (left) and Windows 10 Mobile (right) in parallel |

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
