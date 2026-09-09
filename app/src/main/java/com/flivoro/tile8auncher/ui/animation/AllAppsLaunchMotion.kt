package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fitted to the Help launch at 59.400–60.067 s, not the Start tile's 180° turn. */
internal object AllAppsLaunchMotion {
    const val DurationMillis = 650
    private val times = floatArrayOf(0f, 33f, 100f, 133f, 167f, 200f, 233f,
        300f, 367f, 400f, 467f, 533f, 567f, 633f, 650f)
    private val scale = MotionCurve(times, floatArrayOf(.50f, .60107f, .75542f,
        .84664f, .88474f, .92165f, .93924f, .96547f, .97513f, .98185f,
        .98951f, .99190f, .99428f, .99619f, 1f))
    private val yaw = MotionCurve(times, floatArrayOf(30f, 23.742f, 14.656f,
        9.133f, 6.759f, 4.495f, 3.463f, 1.873f, 1.262f, .906f,
        .446f, .277f, .221f, 0f, 0f))

    fun opacity(progress: Float): Float = (progress * DurationMillis / 33f).coerceIn(0f, 1f)

    fun expansionFraction(progress: Float): Float =
        (scale.at(progress.coerceIn(0f, 1f) * DurationMillis) - .5f) * 2f

    fun progressForExpansion(fraction: Float): Float {
        val target = fraction.coerceIn(0f, 1f)
        if (target == 0f || target == 1f) return target
        var low = 0f
        var high = 1f
        repeat(20) {
            val mid = (low + high) * .5f
            if (expansionFraction(mid) < target) low = mid else high = mid
        }
        return (low + high) * .5f
    }

    fun frame(progress: Float, width: Float, height: Float): LaunchFrame {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        if (p == 1f) return LaunchFrame(Quad.of(Rect(0f, 0f, width, height)), false, 0f, 1f)
        val s = scale.at(p * DurationMillis)
        val angle = yaw.at(p * DurationMillis)
        val radians = angle * (PI / 180).toFloat()
        val halfW = width * s * .5f
        val halfH = height * s * .5f
        // Reconstructing both edge heights gives a camera distance of 1920px
        // in the 1920px reference. Width-relative distance preserves normalized
        // perspective on portrait screens, without crossing their boundaries.
        fun project(x: Float, y: Float): Offset {
            val perspective = width / (width + x * sin(radians))
            return Offset(width * .5f + x * cos(radians) * perspective,
                height * .5f + y * perspective)
        }
        return LaunchFrame(Quad(project(-halfW, -halfH), project(halfW, -halfH),
            project(halfW, halfH), project(-halfW, halfH)), false, angle, s)
    }
}
