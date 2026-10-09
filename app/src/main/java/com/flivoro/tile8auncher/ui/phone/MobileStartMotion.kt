package com.flivoro.tile8auncher.ui.phone

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A fit to the RIGHT phone in 1000197577.mp4, not Microsoft's private shell code.
 * Rows zoom about one point in window coordinates. Upper rows leave first; lower
 * rows return first. The wallpaper has its own track. See the frame evidence in docs.
 */
internal object MobileStartMotion {
    const val EXIT_TILE_MS = 250
    const val EXIT_STAGGER_MS = 133
    const val SELECTED_DELAY_MS = 67
    const val WALLPAPER_FADE_START_MS = 400
    const val EXIT_TOTAL_MS = 534
    const val ENTRY_TILE_MS = 450
    const val ENTRY_STAGGER_MS = 180
    const val ENTRY_TOTAL_MS = ENTRY_TILE_MS + ENTRY_STAGGER_MS

    /** Fractions use the top/bottom VISIBLE rows, independent of small-cell density. */
    fun rowFraction(top: Float, firstVisibleTop: Float, lastVisibleTop: Float): Float =
        if (lastVisibleTop <= firstVisibleTop) 0f
        else ((top - firstVisibleTop) / (lastVisibleTop - firstVisibleTop)).coerceIn(0f, 1f)

    fun delayMillis(exiting: Boolean, row: Float, selected: Boolean = false): Int =
        if (exiting) {
            (row.coerceIn(0f, 1f) * EXIT_STAGGER_MS + if (selected) SELECTED_DELAY_MS else 0)
                .roundToInt().coerceAtMost(EXIT_STAGGER_MS)
        } else ((1f - row.coerceIn(0f, 1f)) * ENTRY_STAGGER_MS).roundToInt()

    fun sample(
        exiting: Boolean,
        elapsedMillis: Int,
        row: Float,
        centerX: Float,
        centerY: Float,
        viewportCenterX: Float,
        viewportCenterY: Float,
        selected: Boolean = false,
    ): PhoneStartMotionFrame {
        val local = elapsedMillis - delayMillis(exiting, row, selected)
        val duration = if (exiting) EXIT_TILE_MS else ENTRY_TILE_MS
        val p = (local.toFloat() / duration).coerceIn(0f, 1f)
        val scale = if (exiting) 1f + exponential(p) else 1f - .3f * exponential(1f - p)
        val alpha = if (exiting) 1f - ((local - 150f) / 100f).coerceIn(0f, 1f)
            else (local / 150f).coerceIn(0f, 1f)
        return PhoneStartMotionFrame(
            alpha = alpha,
            rotationY = 0f,
            translationXPx = (centerX - viewportCenterX) * (scale - 1f),
            translationYPx = (centerY - viewportCenterY) * (scale - 1f),
            translationZPx = 0f,
            scale = scale,
            pivotX = .5f,
        )
    }

    fun wallpaperAlpha(exiting: Boolean, elapsedMillis: Int): Float = if (exiting) {
        1f - ((elapsedMillis - WALLPAPER_FADE_START_MS).toFloat() /
            (EXIT_TOTAL_MS - WALLPAPER_FADE_START_MS)).coerceIn(0f, 1f)
    } else (elapsedMillis / 180f).coerceIn(0f, 1f)

    private fun exponential(p: Float): Float =
        ((exp(5.0 * p.coerceIn(0f, 1f)) - 1.0) / (exp(5.0) - 1.0)).toFloat()
}
