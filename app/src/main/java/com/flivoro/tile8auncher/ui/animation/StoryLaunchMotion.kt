package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Five optional, physical launch stories. A shared clock drives every actor;
 * returning retraces the same poses with its own measured, decelerating timing.
 * These samples never modify any of the three original Windows compositors.
 *
 * Design references: Microsoft Connected animation, Directionality and gravity,
 * and Timing and easing. A selected surface keeps its identity; its neighbors
 * react to the same event. Nothing loops, collides, or acquires random motion.
 */
internal object StoryLaunchMotion {
    fun duration(style: MasterLaunchAnimation): Int = when (style) {
        MasterLaunchAnimation.LIFT -> 520
        MasterLaunchAnimation.DOOR -> 560
        MasterLaunchAnimation.RIPPLE -> 560
        MasterLaunchAnimation.PAGE_TURN -> 600
        MasterLaunchAnimation.GLIDE -> 520
        else -> error("Not a story animation: $style")
    }

    // Alpha/pressure envelopes join gently. Spatial travel uses a DIFFERENT
    // curve below: a measured release followed by a long, calm deceleration.
    fun ease(start: Float, end: Float, progress: Float): Float {
        val x = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return (x * x * x * (10f + x * (-15f + 6f * x))).coerceIn(0f, 1f)
    }

    // Keep the original duration/choreography. Redistribute velocity rather
    // than rushing the story: moderate early momentum, no overshoot, soft rest.
    private val release = CubicBezierEasing(.22f, .70f, .30f, 1f)

    fun settle(start: Float, end: Float, raw: Float): Float {
        val p = ((progress(raw) - start) / (end - start)).coerceIn(0f, 1f)
        return release.transform(p).coerceIn(0f, 1f)
    }

    /**
     * Retrace the SAME spatial poses with a measured return and a soft landing.
     * Reversing a front-loaded linear clock directly would do the opposite:
     * hesitate at first, then slam into the source. Invert the lead curve once
     * per display tick, never once per tile, to preserve both path and pacing.
     */
    fun returnEasing(startProgress: Float): Easing {
        val start = progress(startProgress)
        if (start <= 0f) return Easing { it }
        val distance = settle(0f, 1f, start)
        return Easing { raw ->
            val t = progress(raw)
            if (t <= 0f) 0f else if (t >= 1f) 1f else {
                val target = distance * (1f - settle(0f, 1f, t))
                var low = 0f
                var high = start
                repeat(18) {
                    val mid = (low + high) * .5f
                    if (settle(0f, 1f, mid) < target) low = mid else high = mid
                }
                (1f - (low + high) * .5f / start).coerceIn(0f, 1f)
            }
        }
    }

    fun progress(raw: Float): Float = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f

    fun source(bounds: Rect, viewport: Rect): Rect {
        if (bounds.width > 1f && bounds.height > 1f &&
            bounds.left.isFinite() && bounds.top.isFinite()) return bounds
        // Settings shortcuts have no tile bounds. Give their source a modest,
        // centered plate instead of inventing a giant tile or dividing by zero.
        val side = minOf(viewport.width * .28f, viewport.height * .18f).coerceAtLeast(1f)
        return Rect(viewport.center.x - side / 2f, viewport.center.y - side / 2f,
            viewport.center.x + side / 2f, viewport.center.y + side / 2f)
    }

    fun panel(style: MasterLaunchAnimation, raw: Float, source: Rect, viewport: Rect): StoryPanelFrame {
        val p = progress(raw)
        val travel = settle(0f, 1f, p)
        val arc = if (travel > 0f && travel < 1f) sin(PI * travel).toFloat() else 0f
        val lift = viewport.height * .045f
        var widthProgress = travel
        var heightProgress = travel
        var dx = 0f
        var dy = 0f
        var rx = 0f
        var ry = 0f
        var pivotX = .5f
        var pivotY = .5f
        val press = ease(0f, .12f, p) * (1f - ease(.12f, .30f, p))
        when (style) {
            MasterLaunchAnimation.LIFT -> {
                // A pressed tile lifts and opens into a broad landing.
                dy = lift * (.32f * press - arc)
                rx = -9f * arc
            }
            MasterLaunchAnimation.DOOR -> {
                widthProgress = settle(.10f, .92f, p)
                heightProgress = settle(.20f, 1f, p)
                ry = -64f * arc
                pivotX = 0f
                dx = -viewport.width * .055f * arc
            }
            MasterLaunchAnimation.RIPPLE -> {
                widthProgress = settle(.13f, 1f, p)
                heightProgress = widthProgress
                dy = lift * .12f * press
            }
            MasterLaunchAnimation.PAGE_TURN -> {
                widthProgress = settle(.23f, 1f, p)
                heightProgress = settle(.10f, .90f, p)
                rx = 58f * arc
                pivotY = 0f
                dy = -lift * 1.25f * arc
            }
            MasterLaunchAnimation.GLIDE -> {
                widthProgress = settle(.08f, .82f, p)
                heightProgress = settle(.20f, 1f, p)
                dx = viewport.width * (.055f * arc - .025f * press)
                dy = -lift * .35f * arc
            }
            else -> error("Not a story animation: $style")
        }
        val compression = 1f - .035f * press
        val width = mix(source.width * compression, viewport.width, widthProgress)
        val height = mix(source.height * compression, viewport.height, heightProgress)
        val cx = mix(source.center.x, viewport.center.x, travel) + dx
        val cy = mix(source.center.y, viewport.center.y, travel) + dy
        return StoryPanelFrame(
            bounds = Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
            rotationX = rx, rotationY = ry, pivotX = pivotX, pivotY = pivotY,
            sourceAlpha = 1f - ease(.30f, .68f, p),
            surfaceAlpha = ease(.08f, .34f, p),
            contentAlpha = ease(.66f, .96f, p),
            sourceScale = compression * mix(1f, 1.30f, travel),
            backdropAlpha = .50f * ease(.04f, .90f, p),
        )
    }

    fun neighbor(style: MasterLaunchAnimation, raw: Float, bounds: Rect,
        source: Rect, viewport: Rect): StoryNeighborFrame {
        val p = progress(raw)
        val w = viewport.width.coerceAtLeast(1f)
        val h = viewport.height.coerceAtLeast(1f)
        val dx = (bounds.center.x - source.center.x) / w
        val dy = (bounds.center.y - source.center.y) / h
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceIn(0f, 1f)
        val delay = when (style) {
            MasterLaunchAnimation.RIPPLE -> .035f + distance * .23f
            MasterLaunchAnimation.PAGE_TURN -> .03f +
                ((bounds.center.y - viewport.top) / h).coerceIn(0f, 1f) * .15f
            else -> .025f + distance * .10f
        }
        val move = settle(delay, .88f, p)
        val fade = 1f - ease(delay + .10f, .94f, p)
        val side = if (bounds.center.x < source.center.x) -1f else 1f
        return when (style) {
            MasterLaunchAnimation.LIFT -> StoryNeighborFrame(fade,
                dx * w * .12f * move, dy * h * .12f * move,
                scale = 1f - .15f * move, rotationX = 5f * move)
            MasterLaunchAnimation.DOOR -> StoryNeighborFrame(fade,
                side * w * .58f * move, dy * h * .05f * move,
                rotationY = side * 23f * move, pivotX = if (side < 0f) 0f else 1f)
            MasterLaunchAnimation.RIPPLE -> {
                val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.12f)
                val wave = sin(PI * move).toFloat()
                StoryNeighborFrame(fade, dx / length * w * .18f * move,
                    dy / length * h * .14f * move,
                    scale = 1f - .075f * wave - .04f * move,
                    rotationX = -dy * 12f * wave, rotationY = dx * 12f * wave)
            }
            MasterLaunchAnimation.PAGE_TURN -> StoryNeighborFrame(fade,
                dx * w * .04f * move, -h * .32f * move,
                scale = 1f - .06f * move, rotationX = 28f * move, pivotY = 0f)
            MasterLaunchAnimation.GLIDE -> StoryNeighborFrame(fade,
                -w * .58f * move, dy * h * .04f * move, scale = 1f - .04f * move)
            else -> error("Not a story animation: $style")
        }
    }

    private fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
}

internal data class StoryPanelFrame(
    val bounds: Rect,
    val rotationX: Float, val rotationY: Float,
    val pivotX: Float, val pivotY: Float,
    val sourceAlpha: Float, val surfaceAlpha: Float, val contentAlpha: Float,
    val sourceScale: Float, val backdropAlpha: Float,
)

internal data class StoryNeighborFrame(
    val alpha: Float, val x: Float, val y: Float,
    val scale: Float = 1f, val rotationX: Float = 0f, val rotationY: Float = 0f,
    val pivotX: Float = .5f, val pivotY: Float = .5f,
)
