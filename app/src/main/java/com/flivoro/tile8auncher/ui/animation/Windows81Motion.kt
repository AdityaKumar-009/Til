package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs

/**
 * Windows 8 / 8.1 shell motion primitives.
 *
 * Values are centralized so shell interactions cannot silently fall back to
 * Material/Compose motion. Microsoft WinJS supplies the canonical Windows-era
 * fluid spline plus pointer/edge/panel/semantic-zoom timings. Tile8's bundled
 * 30 fps Windows 8.1 reference recording independently shows Start <-> Apps
 * completing in five source frames (~167 ms) for arrow-triggered navigation.
 *
 * App-opening motion is deliberately NOT represented here. Tile8's existing
 * FlipLaunchOverlay/WindowsLaunchMotion pipeline remains independent.
 */
internal object Windows81Motion {
    val Fluid: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val SemanticZoomEase: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val PointerDurationMillis = 167
    const val PointerPressedScale = 0.975f

    const val EdgeUiDurationMillis = 367
    const val PanelDurationMillis = 550
    const val RepositionDurationMillis = 367
    const val RepositionStaggerMillis = 33
    const val RepositionStaggerCapMillis = 250

    const val SemanticZoomDurationMillis = 333
    const val SemanticZoomFactor = 0.65f

    // Measured from test.mp4: Start -> Apps and Apps -> Start both settle
    // over about five 30-fps frames when invoked by the shell arrow.
    const val SurfaceSlideDurationMillis = 167
    const val MinimumDirectManipulationSettleMillis = 50

    /**
     * Direct manipulation follows the finger exactly. On release, the remaining
     * distance settles on the same Windows fluid track, shortened proportionally
     * and by release velocity when the user's gesture is already moving faster.
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
