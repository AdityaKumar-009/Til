package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs

/**
 * Windows 8 / 8.1 shell motion primitives.
 *
 * Values are intentionally centralized so shell interactions do not silently
 * fall back to Compose's Material springs/tweens. The timings below are taken
 * from Microsoft's WinJS animation implementation used by Windows 8-era apps:
 * pointerDown/pointerUp 167 ms, edge UI 367 ms, panel 550 ms, semantic zoom
 * 333 ms, and the fluid cubic-bezier(0.1, 0.9, 0.2, 1).
 *
 * App-opening motion is deliberately NOT represented here. Tile8's existing
 * FlipLaunchOverlay/WindowsLaunchMotion pipeline remains independent.
 */
internal object Windows81Motion {
    val Fluid: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val SemanticZoomEase: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f) // CSS ease-in-out

    const val PointerDurationMillis = 167
    const val PointerPressedScale = 0.975f

    const val EdgeUiDurationMillis = 367
    const val PanelDurationMillis = 550
    const val RepositionDurationMillis = 367
    const val RepositionStaggerMillis = 33
    const val RepositionStaggerCapMillis = 250

    const val SemanticZoomDurationMillis = 333
    const val SemanticZoomFactor = 0.65f

    // Windows' staggered page-slide primitive uses a 350 ms surface move.
    const val SurfaceSlideDurationMillis = 350
    const val MinimumDirectManipulationSettleMillis = 90

    /**
     * Direct manipulation follows the finger exactly. On release, continue
     * toward the chosen page using the user's current velocity when useful,
     * otherwise use the Windows page-slide duration for the remaining travel.
     */
    fun settleDurationMillis(progress: Float, target: Float, progressVelocityPerSecond: Float): Int {
        val remaining = abs(target - progress).coerceIn(0f, 1f)
        if (remaining <= 0.001f) return 0

        val distanceDuration = (SurfaceSlideDurationMillis * remaining)
            .toInt()
            .coerceIn(MinimumDirectManipulationSettleMillis, SurfaceSlideDurationMillis)
        val speed = abs(progressVelocityPerSecond)
        if (speed < 0.35f) return distanceDuration

        val velocityDuration = ((remaining / speed) * 1000f)
            .toInt()
            .coerceIn(MinimumDirectManipulationSettleMillis, SurfaceSlideDurationMillis)
        return minOf(distanceDuration, velocityDuration)
    }
}
