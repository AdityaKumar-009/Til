package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Geometry in overlay-local pixels. No Android camera units or per-frame layout changes. */
internal object WindowsLaunchMotion {
    const val DurationMillis = 650
    const val ModernDurationMillis = 300

    // Fitted to the Mail and Money launches in the supplied 30 fps recording.
    // Duplicate capture frames are not animation holds. Monotone Hermite interpolation
    // keeps velocity continuous between observations, including the face change.
    private val times = floatArrayOf(0f, 33f, 67f, 100f, 133f, 167f, 200f, 233f,
        267f, 300f, 333f, 400f, 467f, 533f, 600f, 650f)
    private val turn = MotionCurve(times, floatArrayOf(0f, 18f, 42f, 60f, 76f, 90f,
        104f, 119f, 142f, 160f, 166f, 173.5f, 177.5f, 179.1f, 179.8f, 180f))
    private val growth = MotionCurve(times, floatArrayOf(0f, .05f, .13f, .20f, .26f,
        .34f, .49f, .60f, .72f, .80f, .85f, .92f, .968f, .987f, .998f, 1f))
    private val travelX = MotionCurve(times, floatArrayOf(0f, .085f, .25f, .38f, .465f,
        .515f, .563f, .64f, .79f, .88f, .916f, .954f, .979f, .991f, .998f, 1f))
    private val travelY = MotionCurve(times, floatArrayOf(0f, .065f, .18f, .27f, .36f,
        .50f, .735f, .81f, .867f, .908f, .928f, .959f, .986f, .993f, .999f, 1f))

    fun rotationFractionAtProgress(progress: Float): Float =
        turn.at(progress.coerceIn(0f, 1f) * DurationMillis) / 180f

    /** Retimes the same spatial path, so a linear preset gives a constant turn rate. */
    fun progressForRotation(rotationFraction: Float): Float {
        val target = rotationFraction.coerceIn(0f, 1f)
        if (target <= 0f || target >= 1f) return target
        var low = 0f
        var high = 1f
        repeat(20) {
            val mid = (low + high) * .5f
            if (rotationFractionAtProgress(mid) < target) low = mid else high = mid
        }
        return (low + high) * .5f
    }

    /** One physical logo size and position throughout both faces of the turn. */
    fun logoQuad(frame: LaunchFrame, source: Rect, width: Float, height: Float,
                 sourceLogo: Rect, finalSize: Float): Quad {
        val growth = frame.expansion
        val cardWidth = mix(source.width, width, growth)
        val cardHeight = mix(source.height, height, growth)
        val logoWidth = mix(sourceLogo.width, finalSize, growth)
        val logoHeight = mix(sourceLogo.height, finalSize, growth)
        val offsetX = (sourceLogo.center.x - source.width * .5f) * (1f - growth)
        val offsetY = (sourceLogo.center.y - source.height * .5f) * (1f - growth)
        // The back texture is upright, so its horizontal coordinates are reversed.
        // Preserve the physical logo center when that texture changes winding.
        val centerX = .5f + offsetX / cardWidth * if (frame.isFrontFace) 1f else -1f
        val centerY = .5f + offsetY / cardHeight
        val halfWidth = logoWidth / cardWidth * .5f
        val halfHeight = logoHeight / cardHeight * .5f
        return frame.quad.subQuad(Rect(centerX - halfWidth, centerY - halfHeight,
            centerX + halfWidth, centerY + halfHeight))
    }

    fun sourceBounds(source: Rect, width: Float, height: Float): Rect {
        val valid = source.left.isFinite() && source.top.isFinite() &&
            source.right.isFinite() && source.bottom.isFinite() &&
            source.width > 1f && source.height > 1f
        if (valid) {
            val visible = source.intersect(Rect(0f, 0f, width, height))
            if (visible.width > 1f && visible.height > 1f) return visible
        }
        val side = min(width, height) * .24f
        return Rect(width * .5f - side * .5f, height * .5f - side * .5f,
            width * .5f + side * .5f, height * .5f + side * .5f)
    }

    fun frame(progress: Float, source: Rect, width: Float, height: Float,
              modern: Boolean = false): LaunchFrame {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val src = sourceBounds(source, width, height)
        if (p == 0f) return LaunchFrame(Quad.of(src), true, 0f, 0f)
        if (p == 1f) return LaunchFrame(Quad.of(Rect(0f, 0f, width, height)), false, 180f, 1f)
        if (modern) {
            val t = 1f - (1f - p) * (1f - p) * (1f - p)
            val rect = Rect(mix(src.left, 0f, t), mix(src.top, 0f, t),
                mix(src.right, width, t), mix(src.bottom, height, t))
            return LaunchFrame(Quad.of(rect), false, 0f, t)
        }

        val ms = p * DurationMillis
        val angle = turn.at(ms)
        val expansion = growth.at(ms)
        val cardWidth = mix(src.width, width, expansion)
        val cardHeight = mix(src.height, height, expansion)
        val origin = Offset(width * .5f, height * .5f)
        val center = Offset(mix(src.center.x, origin.x, travelX.at(ms)),
            mix(src.center.y, origin.y, travelY.at(ms)))
        // 2160 px at the reference's 1920 x 1080 resolution. Scaling with the
        // viewport preserves perspective at different densities and orientations.
        val camera = max(width, height) * 1.125f
        val radians = angle * (PI / 180).toFloat()
        val cosine = cos(radians)
        val sine = sin(radians)
        fun project(x: Float, y: Float): Offset {
            val perspective = camera / (camera + x * sine)
            return Offset(origin.x + (center.x - origin.x + x * cosine) * perspective,
                origin.y + (center.y - origin.y + y) * perspective)
        }
        val halfW = cardWidth * .5f
        val halfH = cardHeight * .5f
        val tl = project(-halfW, -halfH)
        val tr = project(halfW, -halfH)
        val br = project(halfW, halfH)
        val bl = project(-halfW, halfH)
        // Off-center cards become edge-on at an angle other than 90 degrees.
        // Winding is the actual visibility test. Both textures use this same quad.
        val front = tr.x >= tl.x
        val quad = if (front) Quad(tl, tr, br, bl) else Quad(tr, tl, bl, br)
        val nearestEdge = min(min(src.left, width - src.right),
            min(src.top, height - src.bottom)).coerceAtLeast(0f)
        val margin = min(nearestEdge, min(width, height) * .012f) * sin(PI.toFloat() * p)
        return LaunchFrame(quad.contained(width, height, margin, center), front, angle, expansion)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
}

internal data class LaunchFrame(
    val quad: Quad,
    val isFrontFace: Boolean,
    val rotationDegrees: Float,
    val expansion: Float,
)

/** Corners always ordered for an upright texture: TL, TR, BR, BL. */
internal data class Quad(val topLeft: Offset, val topRight: Offset,
                         val bottomRight: Offset, val bottomLeft: Offset) {
    val bounds: Rect get() = Rect(
        min(topLeft.x, bottomLeft.x), min(topLeft.y, topRight.y),
        max(topRight.x, bottomRight.x), max(bottomLeft.y, bottomRight.y))

    fun writeTo(points: FloatArray) {
        points[0] = topLeft.x; points[1] = topLeft.y
        points[2] = topRight.x; points[3] = topRight.y
        points[4] = bottomRight.x; points[5] = bottomRight.y
        points[6] = bottomLeft.x; points[7] = bottomLeft.y
    }

    /** Project a normalized texture rectangle with the plane's perspective intact. */
    fun subQuad(rect: Rect): Quad {
        // Launch planes have vertical edges. Their height ratio is the projective
        // denominator; bilinear interpolation would make the logo slide sideways.
        val leftHeight = bottomLeft.y - topLeft.y
        val rightHeight = bottomRight.y - topRight.y
        val ratio = leftHeight / rightHeight.coerceAtLeast(.0001f)
        fun map(u: Float, v: Float): Offset {
            val denominator = 1f + (ratio - 1f) * u
            return Offset(
                (topLeft.x + (topRight.x * ratio - topLeft.x) * u) / denominator,
                (topLeft.y + (topRight.y * ratio - topLeft.y) * u + leftHeight * v) / denominator,
            )
        }
        return Quad(map(rect.left, rect.top), map(rect.right, rect.top),
            map(rect.right, rect.bottom), map(rect.left, rect.bottom))
    }

    fun contained(width: Float, height: Float, margin: Float, anchor: Offset): Quad {
        val b = bounds
        // Fit around the projected content center, never the bounding-box center.
        // Perspective makes the bounding box asymmetric. Translating that box back
        // inside the screen moves the logo twice on nearly full-width tiles.
        val scale = min(1f, min(
            min((anchor.x - margin) / max(anchor.x - b.left, .001f),
                (width - margin - anchor.x) / max(b.right - anchor.x, .001f)),
            min((anchor.y - margin) / max(anchor.y - b.top, .001f),
                (height - margin - anchor.y) / max(b.bottom - anchor.y, .001f)),
        )).coerceAtLeast(0f)
        fun fit(point: Offset) = anchor + (point - anchor) * scale
        return Quad(fit(topLeft), fit(topRight), fit(bottomRight), fit(bottomLeft))
    }

    companion object {
        fun of(rect: Rect) = Quad(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft)
    }
}

/** Monotone cubic Hermite curve with zero endpoint velocity. */
internal class MotionCurve(private val x: FloatArray, private val y: FloatArray) {
    private val slope = FloatArray(x.size).also { result ->
        for (i in 1 until x.lastIndex) {
            val before = (y[i] - y[i - 1]) / (x[i] - x[i - 1])
            val after = (y[i + 1] - y[i]) / (x[i + 1] - x[i])
            result[i] = if (before * after <= 0f) 0f else 2f * before * after / (before + after)
        }
    }

    fun at(time: Float): Float {
        if (time <= x.first()) return y.first()
        if (time >= x.last()) return y.last()
        var i = 0
        while (time > x[i + 1]) i++
        val duration = x[i + 1] - x[i]
        val t = (time - x[i]) / duration
        val t2 = t * t
        val t3 = t2 * t
        return ((2f * t3 - 3f * t2 + 1f) * y[i] + (t3 - 2f * t2 + t) * duration * slope[i] +
            (-2f * t3 + 3f * t2) * y[i + 1] + (t3 - t2) * duration * slope[i + 1])
            .coerceIn(min(y[i], y[i + 1]), max(y[i], y[i + 1]))
    }
}
