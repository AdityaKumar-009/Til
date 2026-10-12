package com.flivoro.tile8auncher.ui.phone

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * All apps measured separately from Start: WinFuture Lumia 950/950 XL,
 * 1023.20-1023.64s (Continuum launch), 1072.44-1072.80s (Back).
 * The 25fps source constrains phase order and approximate durations; these are
 * fitted values, not recovered Microsoft constants. See docs/motion/W10M_BACK_AND_APPS_REVIEW.md.
 */
internal object MobileAppsMotion {
    const val EXIT_ROW_MS = 160
    const val EXIT_STAGGER_MS = 100
    const val SELECTED_DELAY_MS = 80
    const val CHROME_FADE_START_MS = 320
    const val EXIT_TOTAL_MS = 400
    const val BACK_TOTAL_MS = MobileStartMotion.BACK_TOTAL_MS

    fun exitDelayMillis(row: Float, selected: Boolean = false): Int =
        (row.coerceIn(0f, 1f) * EXIT_STAGGER_MS).roundToInt() +
            if (selected) SELECTED_DELAY_MS else 0

    fun sample(
        exiting: Boolean, elapsedMillis: Int, row: Float,
        centerX: Float, centerY: Float, viewportCenterX: Float, viewportCenterY: Float,
        selected: Boolean = false, returningFromApp: Boolean = false,
    ): PhoneStartMotionFrame {
        if (returningFromApp && !exiting) return MobileStartMotion.backFrame(
            elapsedMillis, row, centerX, centerY, viewportCenterX, viewportCenterY,
        )
        // Changing panorama pages does not replay app-return motion.
        if (!exiting) return PhoneStartMotionFrame(
            alpha = 1f, rotationY = 0f, translationXPx = 0f, translationYPx = 0f,
            translationZPx = 0f, scale = 1f, pivotX = .5f,
        )
        val local = elapsedMillis - exitDelayMillis(row, selected)
        val p = (local.toFloat() / EXIT_ROW_MS).coerceIn(0f, 1f)
        val scale = 1f + ((exp(5.0 * p) - 1.0) / (exp(5.0) - 1.0)).toFloat()
        return PhoneStartMotionFrame(
            alpha = 1f - ((local - 80f) / 80f).coerceIn(0f, 1f),
            rotationY = 0f,
            translationXPx = (centerX - viewportCenterX) * (scale - 1f),
            translationYPx = (centerY - viewportCenterY) * (scale - 1f),
            translationZPx = 0f, scale = scale, pivotX = .5f,
        )
    }

    /** Search stays stationary while the rows leave, then fades with the wallpaper. */
    fun chromeAlpha(exiting: Boolean, elapsedMillis: Int, returningFromApp: Boolean = false): Float =
        when {
            exiting -> 1f - ((elapsedMillis - CHROME_FADE_START_MS) /
                (EXIT_TOTAL_MS - CHROME_FADE_START_MS).toFloat()).coerceIn(0f, 1f)
            returningFromApp -> (elapsedMillis / 180f).coerceIn(0f, 1f)
            else -> 1f
        }
}
