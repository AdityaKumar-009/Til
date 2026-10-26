package com.flivoro.tile8auncher.ui.phone

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Back retains the separately fitted WinFuture Lumia 950/950 XL app-list
 * choreography (1072.44-1072.80s). For outgoing app launches, intentionally
 * use the same motion timing/curve as W10M Start per the requested experience.
 * See docs/motion/W10M_BACK_AND_APPS_REVIEW.md for the original measurements.
 */
internal object MobileAppsMotion {
    const val EXIT_TOTAL_MS = MobileStartMotion.EXIT_TOTAL_MS
    const val BACK_ROW_MS = 200
    const val BACK_STAGGER_MS = 120
    const val BACK_TOTAL_MS = BACK_ROW_MS + BACK_STAGGER_MS

    fun exitDelayMillis(row: Float, selected: Boolean = false): Int =
        MobileStartMotion.delayMillis(exiting = true, row = row, selected = selected)

    fun sample(
        exiting: Boolean, elapsedMillis: Int, row: Float,
        centerX: Float, centerY: Float, viewportCenterX: Float, viewportCenterY: Float,
        selected: Boolean = false, returningFromApp: Boolean = false,
    ): PhoneStartMotionFrame {
        if (returningFromApp && !exiting) {
            val delay = ((1f - row.coerceIn(0f, 1f)) * BACK_STAGGER_MS).roundToInt()
            val local = elapsedMillis - delay
            val p = (local.toFloat() / BACK_ROW_MS).coerceIn(0f, 1f)
            val scale = 1f + .65f *
                ((exp(6.0 * (1f - p)) - 1.0) / (exp(6.0) - 1.0)).toFloat()
            return PhoneStartMotionFrame(
                alpha = (local / 80f).coerceIn(0f, 1f), rotationY = 0f,
                translationXPx = (centerX - viewportCenterX) * (scale - 1f),
                translationYPx = (centerY - viewportCenterY) * (scale - 1f),
                translationZPx = 0f, scale = scale, pivotX = .5f,
            )
        }
        // Changing panorama pages does not replay app-return motion.
        if (!exiting) return PhoneStartMotionFrame(
            alpha = 1f, rotationY = 0f, translationXPx = 0f, translationYPx = 0f,
            translationZPx = 0f, scale = 1f, pivotX = .5f,
        )
        return MobileStartMotion.sample(
            exiting = true, elapsedMillis = elapsedMillis, row = row,
            centerX = centerX, centerY = centerY,
            viewportCenterX = viewportCenterX, viewportCenterY = viewportCenterY,
            selected = selected,
        )
    }

    /** Search stays stationary while the rows leave, then fades with the wallpaper. */
    fun chromeAlpha(exiting: Boolean, elapsedMillis: Int, returningFromApp: Boolean = false): Float =
        when {
            exiting -> MobileStartMotion.wallpaperAlpha(exiting = true, elapsedMillis = elapsedMillis)
            returningFromApp -> (elapsedMillis / 180f).coerceIn(0f, 1f)
            else -> 1f
        }
}
