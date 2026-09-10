package com.flivoro.tile8auncher.ui.animation

import kotlin.math.abs

/** Small, deterministic rules shared by the charms animation and edge observer. */
internal object CharmsMotion {
    // These durations are retained from the repository's recorded-reference fit. The fidelity
    // pass changes gesture commitment and pane behavior, not the measured rail/pane timeline.
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

    /**
     * Windows edge UI should not appear from a tiny diagonal twitch at the navigation edge.
     * Commit only after a deliberate inward horizontal pull. A vertical or materially reversed
     * gesture remains owned by the underlying content/system navigation instead.
     */
    fun shouldCommitEdgeSwipe(
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float,
        commitDistance: Float,
    ): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite() ||
            !commitDistance.isFinite()
        ) return false
        val threshold = maxOf(touchSlop.coerceAtLeast(0f) * 2f, commitDistance.coerceAtLeast(0f))
        val inward = -deltaX
        return inward >= threshold && inward > abs(deltaY) * 1.20f
    }

    fun hasCrossedTouchSlop(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite()) return false
        return maxOf(abs(deltaX), abs(deltaY)) >= touchSlop.coerceAtLeast(0f)
    }

    /** Once an edge gesture clearly turns vertical or reverses outward, stop tracking it. */
    fun shouldCancelEdgeSwipe(
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float,
    ): Boolean {
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite()) return true
        val slop = touchSlop.coerceAtLeast(0f)
        val verticalWins = abs(deltaY) >= slop && abs(deltaY) > abs(deltaX) * 1.10f
        val reversedOutward = deltaX >= slop
        return verticalWins || reversedOutward
    }
}
