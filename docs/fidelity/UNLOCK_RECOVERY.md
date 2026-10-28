# Blank HOME after screen-off/unlock

## Failure mechanism

`MainActivity` intentionally sets `entranceReady=false` when the display turns off or
the Activity resumes behind Android's keyguard. The Desktop root multiplies its
content alpha by this flag, leaving its wallpaper visible. The phone root sets its
entire surface alpha to zero, leaving black. The flag also disables interaction.
This explains why the system bars can remain visible on an otherwise empty HOME.

Before this fix, a locked `onResume` only scheduled recovery if this Activity had
already observed `ACTION_USER_PRESENT`. It did not listen for `ACTION_SCREEN_ON`
or recover when window focus returned. A missed notification, a no-keyguard wake
without another resume, or a delayed `SCREEN_OFF` after the last wake event could
therefore leave the gate closed indefinitely. The retry, when it did start, was
tied to the window's animation callbacks and retried every frame.

The separate WP8.1 200 ms resume delay also left `entranceReady=false` when its
callback found a newly locked device: it returned without arranging recovery.
Android documents that window-focus callbacks and Activity lifecycle callbacks
have no guaranteed relative order:
https://developer.android.com/reference/android/app/Activity#onWindowFocusChanged(boolean)

These are demonstrable code paths; the original phone's exact event sequence
cannot be determined from screenshots alone without a device log.

## Repair

- Every entrance gate starts reconciliation with the current interactive/keyguard
  state. `USER_PRESENT` is a hint rather than a prerequisite.
- Resume, screen-on, user-present, window-focus, and Home delivery can all recover
  an outstanding gate. A delayed screen-off event also checks the current state.
- There is at most one main-thread Handler retry, at 100 ms intervals while HOME
  is resumed and the display is interactive but still keyguard-locked. Recovery
  is independent of drawing. Sleep, pause, stop, completion, and destruction
  remove pending retries; settled HOME does no polling.
- A lock during the WP8.1 return delay transfers ownership to the unlock gate.
  Screen-off cancels obsolete return/launch state and invalidates old callbacks.
- The existing entrance request resets the phone handoff pose. Layouts, saved
  workspaces, wallpapers, native animation curves and the ordinary 200 ms WP8.1
  return delay are retained.

## Regression coverage

`LauncherEntranceRecoveryTest` checks missing/early/duplicate notifications,
late screen-off, no-keyguard wake, focus-only wake, long keyguard transitions,
pause/resume, repeated locks, recreation, cancellation, and timer coalescing.

`LauncherUnlockRecoveryTest` drives the real Activity and verifies the content
gate plus rendered, non-flat output across Desktop, WP8.1 and W10M. It covers
late screen-off with no following notification, focus-only recovery, duplicate
wake signals, paused Activity recreation with the same workspace, interruption
of the WP8.1 delay, and repeated real Android display sleep/wake. Screenshots are
exported under `motion-frames/unlock-recovery` in the device evidence artifacts.

The Android 10/API 29 and Android 14/API 34 CI jobs run these tests before their
existing motion suite. Emulator results do not establish OEM-specific physical
device behavior; the original phone still needs an install-and-unlock check.
