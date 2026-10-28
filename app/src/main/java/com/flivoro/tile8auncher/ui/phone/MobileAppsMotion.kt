package com.flivoro.tile8auncher.ui.phone

/**
 * Outgoing app launches AND app Back returns deliberately share the W10M Start
 * timing/curve. Only the page and its row geometry differ; normal panorama
 * swipes remain still, without replaying a launch/return sequence.
 * See docs/motion/W10M_BACK_AND_APPS_REVIEW.md for the original measurements.
 */
internal object MobileAppsMotion {
    const val EXIT_TOTAL_MS = MobileStartMotion.EXIT_TOTAL_MS
    const val BACK_TOTAL_MS = MobileStartMotion.BACK_TOTAL_MS

    fun exitDelayMillis(row: Float, selected: Boolean = false): Int =
        MobileStartMotion.delayMillis(exiting = true, row = row, selected = selected)

    fun sample(
        exiting: Boolean, elapsedMillis: Int, row: Float,
        centerX: Float, centerY: Float, viewportCenterX: Float, viewportCenterY: Float,
        selected: Boolean = false, returningFromApp: Boolean = false,
    ): PhoneStartMotionFrame {
        if (returningFromApp && !exiting) {
            return MobileStartMotion.sample(
                exiting = false, elapsedMillis = elapsedMillis, row = row,
                centerX = centerX, centerY = centerY,
                viewportCenterX = viewportCenterX, viewportCenterY = viewportCenterY,
                selected = selected, returningFromApp = true,
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
            returningFromApp -> MobileStartMotion.wallpaperAlpha(
                exiting = false, elapsedMillis = elapsedMillis, returningFromApp = true)
            else -> 1f
        }
}
