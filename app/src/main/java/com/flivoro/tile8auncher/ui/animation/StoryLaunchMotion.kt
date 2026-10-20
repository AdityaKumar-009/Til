package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Optional, physical launch stories. A shared clock drives every actor;
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
        MasterLaunchAnimation.FOCUS -> 520
        MasterLaunchAnimation.ARC -> 560
        MasterLaunchAnimation.UNFOLD -> 580
        else -> error("Not a story animation: $style")
    }

    // Smooth envelopes join with zero velocity and acceleration at either end.
    fun ease(start: Float, end: Float, progress: Float): Float {
        val x = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return (x * x * x * (10f + x * (-15f + 6f * x))).coerceIn(0f, 1f)
    }

    // Match the launch timing preset's MATERIAL_EMPHASIZED_DECELERATE curve
    // (cubic-bezier(0.05, 0.70, 0.10, 1.00)) exactly in the optional stories.
    // A single continuous easing replaces the stitched Desktop sample data,
    // with no late reacceleration or join point. Preserve each story's duration,
    // geometry, neighbor choreography, and the three native Windows animations.
    private val emphasizedDecelerate = CubicBezierEasing(.05f, .70f, .10f, 1f)

    // All actors and fades use the same traveled phase, including during return.
    fun settle(start: Float, end: Float, raw: Float): Float {
        val p = ((progress(raw) - start) / (end - start)).coerceIn(0f, 1f)
        return emphasizedDecelerate.transform(p).coerceIn(0f, 1f)
    }

    // Back previously inverted the emphasized cubic curve with 18 separate
    // Bézier evaluations for every frame. A small shared monotone inverse table
    // retains the exact path to sub-pixel accuracy without repeated curve solves.
    private val inverseSteps = 2048
    private val inverseEasingSamples by lazy(LazyThreadSafetyMode.NONE) {
        FloatArray(inverseSteps + 1) { index ->
            emphasizedDecelerate.transform(index.toFloat() / inverseSteps)
        }
    }

    private fun inverseEmphasizedDistance(distance: Float): Float {
        val target = distance.coerceIn(0f, 1f)
        if (target <= 0f || target >= 1f) return target
        val table = inverseEasingSamples
        var low = 0
        var high = inverseSteps
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (table[mid] < target) low = mid else high = mid
        }
        val span = table[high] - table[low]
        val fraction = if (span > 0f)
            ((target - table[low]) / span).coerceIn(0f, 1f) else 0f
        return (low + fraction) / inverseSteps
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
                // Preserve the existing soft Back motion from the previous
                // release. Only the foreground icon's graphics scaling changes.
                val target = distance * (1f - ease(0f, 1f, t))
                (1f - inverseEmphasizedDistance(target) / start).coerceIn(0f, 1f)
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

    fun panel(style: MasterLaunchAnimation, raw: Float, source: Rect, viewport: Rect): StoryPanelFrame =
        panelAtTravel(style, settle(0f, 1f, raw), source, viewport, raw)

    // Share the already-eased display phase with the neighbors; no cubic Bézier
    // inversion needs to run separately for every tile and overlay layer.
    fun panelAtTravel(style: MasterLaunchAnimation, travel: Float, source: Rect, viewport: Rect,
        rawProgress: Float = travel): StoryPanelFrame {
        val arc = if (travel > 0f && travel < 1f) sin(PI * travel).toFloat() else 0f
        val lift = viewport.height * .045f
        var widthProgress = travel
        var heightProgress = travel
        var dx = 0f
        var dy = 0f
        var rx = 0f
        var ry = 0f
        var rz = 0f
        var pivotX = .5f
        var pivotY = .5f
        val press = (256f / 27f) * travel * (1f - travel) * (1f - travel) * (1f - travel)
        when (style) {
            MasterLaunchAnimation.LIFT -> {
                // A pressed tile lifts and opens into a broad landing.
                dy = lift * (.32f * press - arc)
                rx = -9f * arc
            }
            MasterLaunchAnimation.DOOR -> {
                widthProgress = shape(travel, -.16f)
                heightProgress = shape(travel, -.24f)
                ry = -64f * arc
                pivotX = 0f
                dx = -viewport.width * .055f * arc
            }
            MasterLaunchAnimation.RIPPLE -> {
                dy = lift * .12f * press
            }
            MasterLaunchAnimation.PAGE_TURN -> {
                widthProgress = shape(travel, -.32f)
                heightProgress = shape(travel, .12f)
                rx = 58f * arc
                pivotY = 0f
                dy = -lift * 1.25f * arc
            }
            MasterLaunchAnimation.GLIDE -> {
                widthProgress = shape(travel, .12f)
                heightProgress = shape(travel, -.16f)
                dx = viewport.width * (.055f * arc - .025f * press)
                dy = -lift * .35f * arc
            }
            MasterLaunchAnimation.FOCUS -> {
                // Direct, level expansion. The receding neighbors supply depth
                // without a press, wobble or a second movement of the source.
            }
            MasterLaunchAnimation.ARC -> {
                // A shallow, source-aware bow towards the center. Left/right
                // sources mirror each other; a centered source never flips its
                // direction at an arbitrary sign threshold.
                val offset = ((source.center.x - viewport.center.x) /
                    viewport.width.coerceAtLeast(1f)).coerceIn(-.5f, .5f)
                dx = -offset * viewport.width * .32f * arc
                dy = -viewport.height * .035f * arc
                rz = offset * 12f * arc
            }
            MasterLaunchAnimation.UNFOLD -> {
                // Width leads and height follows on the SAME clock, so opening
                // the sheet never pauses at a seam between two stages.
                widthProgress = shape(travel, .65f)
                heightProgress = shape(travel, -.55f)
                rx = -10f * arc
                dy = -lift * .40f * arc
            }
            else -> error("Not a story animation: $style")
        }
        val compression = if (style == MasterLaunchAnimation.FOCUS ||
            style == MasterLaunchAnimation.ARC || style == MasterLaunchAnimation.UNFOLD) 1f
            else 1f - .035f * press
        val width = mix(source.width * compression, viewport.width, widthProgress)
        val height = mix(source.height * compression, viewport.height, heightProgress)
        val cx = mix(source.center.x, viewport.center.x, travel) + dx
        val cy = mix(source.center.y, viewport.center.y, travel) + dy
        // The spatial motion is strongly front-loaded: at half of the clock
        // it has almost finished expanding. Revealing the app using traveled
        // distance made the original tile vanish mid-launch and exposed a
        // second, unrelated full-window panel. Keep the entire photographed
        // tile face until near the end of the ACTUAL duration; only its contents
        // crossfade, while the one physical rectangle keeps expanding.
        val reveal = ease(.68f, .96f, progress(rawProgress))
        // A full-width app row must not grow its leading glyph offscreen.
        val artworkScale = minOf(1.30f, viewport.width / source.width,
            viewport.height / source.height).coerceAtLeast(1f)
        return StoryPanelFrame(
            bounds = Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f),
            rotationX = rx, rotationY = ry, pivotX = pivotX, pivotY = pivotY,
            sourceAlpha = 1f - reveal,
            surfaceAlpha = ease(0f, .30f, travel),
            contentAlpha = reveal,
            sourceScale = compression * mix(1f, artworkScale, travel),
            backdropAlpha = .50f * ease(0f, 1f, travel),
            rotationZ = rz,
        )
    }

    fun neighbor(style: MasterLaunchAnimation, raw: Float, bounds: Rect,
        source: Rect, viewport: Rect): StoryNeighborFrame =
        neighborAtTravel(style, settle(0f, 1f, raw), bounds, source, viewport)

    fun neighborAtTravel(style: MasterLaunchAnimation, travel: Float, bounds: Rect,
        source: Rect, viewport: Rect): StoryNeighborFrame {
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
        // Propagation is still distance ordered, but each neighbor accelerates
        // smoothly from rest rather than starting a second fast-out clock.
        val move = ease(delay, 1f, travel)
        val fade = 1f - ease(delay + .10f, 1f, travel)
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
            MasterLaunchAnimation.FOCUS -> StoryNeighborFrame(fade,
                -dx * w * .14f * move, -dy * h * .14f * move,
                scale = 1f - .20f * move)
            MasterLaunchAnimation.ARC -> {
                val offset = ((source.center.x - viewport.center.x) / w).coerceIn(-.5f, .5f)
                StoryNeighborFrame(fade,
                    (dx * .08f + offset * .12f) * w * move,
                    (.11f + dy * .05f) * h * move,
                    scale = 1f - .08f * move, rotationZ = -offset * 6f * move)
            }
            MasterLaunchAnimation.UNFOLD -> {
                // Rows above/below the touch separate gradually. A continuous
                // vertical weight also keeps actors on the same row level.
                val vertical = (dy * 4f).coerceIn(-1f, 1f)
                StoryNeighborFrame(fade, dx * w * .10f * move,
                    vertical * h * .22f * move, scale = 1f - .05f * move,
                    rotationX = -vertical * 6f * move)
            }
            else -> error("Not a story animation: $style")
        }
    }

    private fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p

    // A gentle lead/lag along the same path, never a delayed start or stop.
    private fun shape(phase: Float, bias: Float) = phase + bias * phase * (1f - phase)
}

internal data class StoryPanelFrame(
    val bounds: Rect,
    val rotationX: Float, val rotationY: Float,
    val pivotX: Float, val pivotY: Float,
    val sourceAlpha: Float, val surfaceAlpha: Float, val contentAlpha: Float,
    val sourceScale: Float, val backdropAlpha: Float,
    val rotationZ: Float = 0f,
)

internal data class StoryNeighborFrame(
    val alpha: Float, val x: Float, val y: Float,
    val scale: Float = 1f, val rotationX: Float = 0f, val rotationY: Float = 0f,
    val pivotX: Float = .5f, val pivotY: Float = .5f,
    val rotationZ: Float = 0f,
)
