package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode

/**
 * Start screen choreography reconstructed from the user-supplied side-by-side recordings.
 *
 * Video 1000197577.mp4, ~144.75-145.18s (29.98fps):
 * - WP8.1 clears bottom/right tiles first, finishing upper/left tiles after ~0.30s.
 * - W10M uses screen-centred row zoom and a separate wallpaper fade (MobileStartMotion).
 * These are measured presentation-frame observations, not claims about private OS source.
 *
 * The archived Windows Phone Toolkit's 250/350ms turnstile curves still apply to app
 * navigation via PhoneMotionTimeline, but were not faithful for Start's spatial cascade.
 */
internal data class PhoneStartMotionFrame(
    val alpha: Float,
    val rotationY: Float,
    val translationXPx: Float,
    val translationYPx: Float,
    val scale: Float,
    val pivotX: Float,
)

internal object PhoneStartChoreography {
    private const val WP81_EXIT_TILE_MS = 90
    private const val WP81_ENTRY_TILE_MS = 280
    private const val WP81_STAGGER_MS = 15
    private const val MOBILE_EXIT_TILE_MS = MobileStartMotion.EXIT_TILE_MS
    private const val MOBILE_ENTRY_TILE_MS = MobileStartMotion.ENTRY_TILE_MS

    private fun spatialRank(screenRow: Int, column: Int, columns: Int): Int {
        val row = screenRow.coerceIn(0, 5)
        val across = (column.coerceIn(0, columns.coerceAtLeast(1) - 1) * 3 /
            columns.coerceAtLeast(1)).coerceIn(0, 2)
        // Native phone 8.1's observed Start exit peels from lower/right towards upper/left.
        return (5 - row) * 2 + 2 - across
    }

    fun totalMillis(mode: LauncherUiMode, exiting: Boolean): Int =
        if (mode == LauncherUiMode.PHONE_8) {
            if (exiting) 12 * WP81_STAGGER_MS + WP81_EXIT_TILE_MS
            else 12 * WP81_STAGGER_MS + WP81_ENTRY_TILE_MS
        } else if (exiting) {
            MobileStartMotion.EXIT_TOTAL_MS
        } else {
            MobileStartMotion.ENTRY_TOTAL_MS
        }

    internal fun delayMillis(
        mode: LauncherUiMode,
        exiting: Boolean,
        screenRow: Int,
        column: Int,
        columns: Int,
    ): Int {
        val rank = spatialRank(screenRow, column, columns)
        if (mode == LauncherUiMode.PHONE_8) {
            // The 60fps classic-phone clip at 47.8s also reveals lower tiles first
            // on entrance; reversing the direction was a visible prior mismatch.
            return rank * WP81_STAGGER_MS
        }
        val row = screenRow.coerceIn(0, 5)
        return MobileStartMotion.delayMillis(exiting, row / 5f)
    }

    fun sample(
        mode: LauncherUiMode,
        exiting: Boolean,
        elapsedMillis: Int,
        screenRow: Int,
        column: Int,
        columns: Int,
        selected: Boolean = false,
    ): PhoneStartMotionFrame {
        if (mode == LauncherUiMode.MOBILE_10) return MobileStartMotion.sample(
            exiting, elapsedMillis, screenRow.coerceIn(0, 5) / 5f,
            0f, 0f, 0f, 0f, selected,
        )
        val delay = if (exiting && selected) 12 * WP81_STAGGER_MS
            else delayMillis(mode, exiting, screenRow, column, columns)
        val tileDuration = when {
            mode == LauncherUiMode.PHONE_8 && exiting -> WP81_EXIT_TILE_MS
            mode == LauncherUiMode.PHONE_8 -> WP81_ENTRY_TILE_MS
            exiting -> MOBILE_EXIT_TILE_MS
            else -> MOBILE_ENTRY_TILE_MS
        }
        val p = ((elapsedMillis - delay).toFloat() / tileDuration).coerceIn(0f, 1f)
        val ease = PhoneMotionTimeline.exponentialEaseOut6(p)
        if (mode == LauncherUiMode.PHONE_8) {
            // The reference shows a *spatial* one-by-one turn away, without the old
            // whole-screen fade and without an arbitrary 0.55s index-based wait.
            val rotated = if (exiting) PhoneMotionTimeline.exponentialEaseIn6(p) else ease
            return PhoneStartMotionFrame(
                alpha = if (exiting) {
                    if (elapsedMillis < delay + tileDuration) 1f else 0f
                } else {
                    if (elapsedMillis < delay) 0f else 1f
                },
                rotationY = if (exiting) 84f * rotated else -80f * (1f - rotated),
                translationXPx = 0f,
                translationYPx = 0f,
                scale = 1f,
                pivotX = 1f,
            )
        }
        error("Start choreography requires a phone mode")
    }
}
