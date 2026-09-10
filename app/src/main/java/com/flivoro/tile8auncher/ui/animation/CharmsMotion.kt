package com.flivoro.tile8auncher.ui.animation

import kotlin.math.abs

/** Small, deterministic rules shared by the charms animation and edge observer. */
internal object CharmsMotion {
    const val RailDurationMillis = 300
    const val RailContentDelayMillis = 35
    const val PanelDurationMillis = 260
    const val ClockDurationMillis = 300

    /** Quadratic deceleration used by the right rail and its delayed contents. */
    fun decelerate(progress: Float): Float {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        return 1f - (1f - p) * (1f - p)
    }

    fun isWithinRightEdge(startX: Float, viewportWidth: Float, edgeWidth: Float): Boolean {
        if (!startX.isFinite() || !viewportWidth.isFinite() || !edgeWidth.isFinite()) return false
        if (viewportWidth <= 0f || edgeWidth <= 0f) return false
        return startX in (viewportWidth - edgeWidth).coerceAtLeast(0f)..viewportWidth
    }

    /** Returns true only after horizontal movement crosses slop toward the screen interior. */
    fun isInwardHorizontalSwipe(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite()) return false
        val slop = touchSlop.coerceAtLeast(0f)
        return deltaX <= -slop && abs(deltaX) > abs(deltaY)
    }

    fun hasCrossedTouchSlop(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite()) return false
        return maxOf(abs(deltaX), abs(deltaY)) >= touchSlop.coerceAtLeast(0f)
    }
}
