package com.flivoro.tile8auncher

/**
 * Reconciles a hidden HOME with the current device state, not a particular broadcast order.
 * All calls run on the main thread. At most one timer exists, only while a resumed launcher
 * is waiting behind an interactive keyguard; asleep/background/ready launchers do no polling.
 * A Handler timer is intentional: recovery must not depend on the hidden window drawing.
 */
internal class LauncherEntranceRecovery(
    private val isPending: () -> Boolean,
    private val isResumed: () -> Boolean,
    private val isInteractive: () -> Boolean,
    private val isKeyguardLocked: () -> Boolean,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val onReady: () -> Unit,
) {
    private var disposed = false
    private val retry = Runnable { recheck() }

    fun recheck() {
        cancel()
        if (disposed || !isPending() || !isResumed() || !isInteractive()) return
        if (isKeyguardLocked()) {
            postDelayed(retry, 100L)
        } else {
            onReady()
        }
    }

    fun cancel() {
        removeCallbacks(retry)
    }

    fun dispose() {
        disposed = true
        cancel()
    }
}
