package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode

/**
 * Start screen choreography reconstructed from the user-supplied side-by-side recordings.
 *
 * Video 1000197577.mp4, ~144.75-145.18s (29.98fps):
 * - WP8.1 clears bottom/right tiles first, finishing upper/left tiles after ~0.30s.
 * - W10M retains the background image while tiles rise/fade, completing over ~0.33s.
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
    private const val MOBILE_EXIT_TILE_MS = 230
    private const val MOBILE_ENTRY_TILE_MS = 300

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
            5 * 18 + 2 * 6 + MOBILE_EXIT_TILE_MS
        } else {
            5 * 16 + 2 * 6 + MOBILE_ENTRY_TILE_MS
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
            return (if (exiting) rank else 12 - rank) * WP81_STAGGER_MS
        }
        val row = screenRow.coerceIn(0, 5)
        val col = (column.coerceIn(0, columns.coerceAtLeast(1) - 1) * 3 /
            columns.coerceAtLeast(1)).coerceIn(0, 2)
        return row * (if (exiting) 18 else 16) + col * 6
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
        val delay = delayMillis(mode, exiting, screenRow, column, columns)
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
                translationXPx = if (exiting) 32f * rotated else -24f * (1f - rotated),
                translationYPx = 0f,
                scale = 1f,
                pivotX = -.2f,
            )
        }
        // Measured W10M Start departure exposes persistent wallpaper underneath the
        // tiles; no unsupported 8.1-era 3D turnstile.
        return PhoneStartMotionFrame(
            alpha = if (exiting) 1f - ease else if (elapsedMillis < delay) 0f else ease,
            rotationY = 0f,
            translationXPx = 0f,
            translationYPx = if (exiting) -74f * ease else 52f * (1f - ease),
            scale = if (exiting) 1f + (if (selected) .105f else .03f) * ease
                    else .97f + .03f * ease,
            pivotX = .5f,
        )
    }
}
