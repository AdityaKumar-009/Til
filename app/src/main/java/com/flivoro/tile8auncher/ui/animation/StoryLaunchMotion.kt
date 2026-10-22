package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.data.LaunchTiming
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.data.TimeCurve
import kotlin.math.PI
import kotlin.math.abs
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
        MasterLaunchAnimation.PASSAGE -> 580
        MasterLaunchAnimation.CASCADE -> 600
        else -> error("Not a story animation: $style")
    }

    /** Resolve saved timing for every optional preset, preserving the established
     * native reference duration until the user changes the duration slider. */
    fun effectiveDuration(style: MasterLaunchAnimation, timing: LaunchTiming): Int =
        timing.playbackDuration(duration(style))

    /** One user-selected spatial phase for the tapped tile AND neighbors. */
    fun traveledPhase(raw: Float, timing: LaunchTiming): Float =
        if (timing.curve == TimeCurve.REFERENCE) settle(0f, 1f, raw)
        else timing.transform(raw)

    // Smooth envelopes join with zero velocity and acceleration at either end.
    fun ease(start: Float, end: Float, progress: Float): Float {
        val x = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return (x * x * x * (10f + x * (-15f + 6f * x))).coerceIn(0f, 1f)
    }

    // Source text is not part of the moving panel's silhouette. Its early
    // disappearance on launch, and late reappearance on Back, both use the
    // SAME raw progress. Icons keep their separate, larger bounded scaling.
    fun titleOpacity(raw: Float): Float = 1f - ease(.12f, .50f, progress(raw))

    // At exactly half the animation clock the expanding tile must show
    // ONLY its centered glyph, regardless of built-in tile templates (Money,
    // Weather, etc.) whose extra information is not a simple title Text.
    // The same channel reverses continuously on Back.
    fun iconOnlyOpacity(raw: Float): Float = ease(.18f, .50f, progress(raw))

    /** Exactly one glyph is shown for the entire connected transformation.
     * Its source offset eases to the panel's center without an alpha swap
     * or a jump at the 50% clock boundary. Back traverses the same path. */
    fun iconCenterProgress(raw: Float): Float = ease(0f, .50f, progress(raw))

    fun iconOriginOffsetX(origin: LaunchOrigin, sourceWidthPx: Float,
        density: Float): Float = if (origin == LaunchOrigin.ALL_APPS)
        // Phone All Apps icons start within the leading glyph plate, not at
        // the center of the entire row. This remains a continuous translation.
        -sourceWidthPx * .5f + (26f * density) else 0f

    fun iconOriginOffsetY(origin: LaunchOrigin, size: TileSize,
        density: Float): Float =
        if (origin == LaunchOrigin.ALL_APPS || size == TileSize.SMALL) 0f
        else -6f * density

    // Transfer source-cell ownership only when the animated rectangle is
    // geometrically indistinguishable from the cell. Previously the crossover
    // occupied the last 3.5% of travel: the real tile appeared while the
    // moving panel was still visibly larger, which looked like a bounce.
    internal const val RETURN_HANDOFF_TRAVEL = .001f
    fun returnToSourceOpacity(travel: Float): Float =
        1f - ease(0f, RETURN_HANDOFF_TRAVEL, progress(travel))

    /** Only one surface can own the selected tile's material. After launch
     * preparation its stationary cell draws NOTHING until the animated plane
     * reaches the exact original bounds at the end of Back. */
    fun shouldDrawOriginalCell(prepared: Boolean, returning: Boolean, travel: Float): Boolean =
        !prepared || (returning && progress(travel) <= RETURN_HANDOFF_TRAVEL)

    // These three quieter stories used to finish their last few return frames
    // while still resizing/rotating the moving panel. The real Start tile
    // then took over at its exact bounds, making the last frame feel bouncy.
    // Smooth their reverse-only GEOMETRY into the original pose *before* the
    // renderer handoff. Ripple and the original Windows modes are unaffected.
    private const val QUIET_RETURN_SETTLE_START = .12f
    private const val QUIET_RETURN_SETTLE_END = .02f
    private fun quietReturnStyle(style: MasterLaunchAnimation): Boolean =
        style == MasterLaunchAnimation.FOCUS ||
            style == MasterLaunchAnimation.DOOR ||
            style == MasterLaunchAnimation.UNFOLD

    private fun isFlowStory(style: MasterLaunchAnimation): Boolean =
        style == MasterLaunchAnimation.PASSAGE || style == MasterLaunchAnimation.CASCADE

    private fun exactSourceHandoff(style: MasterLaunchAnimation): Boolean =
        quietReturnStyle(style) || isFlowStory(style)

    // Passage/Cascade have zero endpoint velocity in their spatial path in
    // BOTH directions. This avoids a special reverse-only correction, so even
    // an immediate Back retraces the exact current pose without a jump.
    private fun geometryPhase(style: MasterLaunchAnimation, travel: Float,
        returning: Boolean): Float = when {
        isFlowStory(style) -> ease(0f, 1f, progress(travel))
        returning -> returnGeometryTravel(style, travel)
        else -> travel
    }

    fun returnGeometryTravel(style: MasterLaunchAnimation, travel: Float): Float {
        val value = progress(travel)
        if (!quietReturnStyle(style) || value >= QUIET_RETURN_SETTLE_START) return value
        if (value <= QUIET_RETURN_SETTLE_END) return 0f
        val distance = QUIET_RETURN_SETTLE_START - QUIET_RETURN_SETTLE_END
        val u = ((value - QUIET_RETURN_SETTLE_END) / distance).coerceIn(0f, 1f)
        // Cubic Hermite, f(0)=f'(0)=0, f(1)=start, f'(1)=1.
        // No extra easing discontinuity at the seam and no reverse overshoot.
        return (3f * u * u - 2f * u * u * u) * QUIET_RETURN_SETTLE_START +
            (u * u * u - u * u) * distance
    }

    fun returnHandoffTravel(style: MasterLaunchAnimation): Float =
        if (quietReturnStyle(style)) QUIET_RETURN_SETTLE_END else RETURN_HANDOFF_TRAVEL

    // A crossfade of two separate semi-transparent tile layers is not fully
    // opaque under normal source-over blending: at 50/50 it leaks 25% of the
    // wallpaper and looks like a little second bounce. For Focus/Door/Uncover,
    // geometry is already identical to the Start tile in the final tail, so
    // simply transfer ownership at EXACT progress=0. Ripple remains untouched.
    fun returnToSourceOpacity(style: MasterLaunchAnimation, travel: Float): Float =
        if (exactSourceHandoff(style))
            if (progress(travel) <= 0f) 1f else 0f
        else returnToSourceOpacity(travel)

    fun shouldDrawOriginalCell(style: MasterLaunchAnimation,
        prepared: Boolean, returning: Boolean, travel: Float): Boolean =
        !prepared || (returning && if (exactSourceHandoff(style))
            progress(travel) <= 0f else progress(travel) <= RETURN_HANDOFF_TRAVEL)

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
        rawProgress: Float = travel, returning: Boolean = false): StoryPanelFrame {
        val geometryTravel = geometryPhase(style, travel, returning)
        val arc = if (geometryTravel > 0f && geometryTravel < 1f) sin(PI * geometryTravel).toFloat() else 0f
        val lift = viewport.height * .045f
        var widthProgress = geometryTravel
        var heightProgress = geometryTravel
        var dx = 0f
        var dy = 0f
        var rx = 0f
        var ry = 0f
        var rz = 0f
        var pivotX = .5f
        var pivotY = .5f
        val press = (256f / 27f) * geometryTravel * (1f - geometryTravel) * (1f - geometryTravel) * (1f - geometryTravel)
        when (style) {
            MasterLaunchAnimation.LIFT -> {
                // A pressed tile lifts and opens into a broad landing.
                dy = lift * (.32f * press - arc)
                rx = -9f * arc
            }
            MasterLaunchAnimation.DOOR -> {
                widthProgress = shape(geometryTravel, -.16f)
                heightProgress = shape(geometryTravel, -.24f)
                ry = -64f * arc
                pivotX = 0f
                dx = -viewport.width * .055f * arc
            }
            MasterLaunchAnimation.RIPPLE -> {
                dy = lift * .12f * press
            }
            MasterLaunchAnimation.PAGE_TURN -> {
                widthProgress = shape(geometryTravel, -.32f)
                heightProgress = shape(geometryTravel, .12f)
                rx = 58f * arc
                pivotY = 0f
                dy = -lift * 1.25f * arc
            }
            MasterLaunchAnimation.GLIDE -> {
                widthProgress = shape(geometryTravel, .12f)
                heightProgress = shape(geometryTravel, -.16f)
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
                widthProgress = shape(geometryTravel, .65f)
                heightProgress = shape(geometryTravel, -.55f)
                rx = -10f * arc
                dy = -lift * .40f * arc
            }
            MasterLaunchAnimation.PASSAGE -> {
                // A narrow doorway opens vertically, then admits the full
                // app. Both axes overlap on one clock: no pause or second card.
                widthProgress = shape(geometryTravel, -.70f)
                heightProgress = shape(geometryTravel, .80f)
            }
            MasterLaunchAnimation.CASCADE -> {
                // The upper edge leads the lower edge like a sheet unrolling.
                // A shallow roll supplies depth; its squared envelope reaches
                // zero with zero velocity at both the tile and app endpoints.
                widthProgress = shape(geometryTravel, .28f)
                val roll = 16f * geometryTravel * geometryTravel *
                    (1f - geometryTravel) * (1f - geometryTravel)
                rx = -12f * roll
                pivotY = 0f
            }
            else -> error("Not a story animation: $style")
        }
        // Keep the pressed-in flourish on OPEN, but not on Back. On a wide
        // All Apps row, source.width can be almost the viewport width, so the
        // old press term actually made Ripple/Door narrower than the original
        // tile near geometryTravel=0. The final frames then expanded BACK to 100%:
        // a mathematically real, visible rebound. Reversal stays monotonic.
        val compression = if (returning ||
            style == MasterLaunchAnimation.FOCUS ||
            style == MasterLaunchAnimation.ARC || style == MasterLaunchAnimation.UNFOLD ||
            isFlowStory(style)) 1f
            else 1f - .035f * press
        val width = mix(source.width * compression, viewport.width, widthProgress)
        val height = mix(source.height * compression, viewport.height, heightProgress)
        val cx = mix(source.center.x, viewport.center.x, geometryTravel) + dx
        val cy = mix(source.center.y, viewport.center.y, geometryTravel) + dy
        val bounds = if (isFlowStory(style)) {
            // Interpolate the edges themselves. A wide row and a corner tile
            // then remain anchored inside the window as their aspect changes;
            // center/size interpolation on different phases can overshoot.
            val topProgress = if (style == MasterLaunchAnimation.CASCADE)
                shape(geometryTravel, .35f) else heightProgress
            val bottomProgress = if (style == MasterLaunchAnimation.CASCADE)
                shape(geometryTravel, -.35f) else heightProgress
            Rect(mix(source.left, viewport.left, widthProgress),
                mix(source.top, viewport.top, topProgress),
                mix(source.right, viewport.right, widthProgress),
                mix(source.bottom, viewport.bottom, bottomProgress))
        } else Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
        // The spatial motion is strongly front-loaded: at half of the clock
        // it has almost finished expanding. Revealing the app using traveled
        // distance made the original tile vanish mid-launch and exposed a
        // second, unrelated full-window panel. Keep the entire photographed
        // tile face until near the end of the ACTUAL duration; only its contents
        // crossfade, while the one physical rectangle keeps expanding.
        val reveal = ease(.68f, .96f, progress(rawProgress))
        // A full-width app row must not grow its leading glyph offscreen.
        val artworkScale = minOf(1.44f, viewport.width / source.width,
            viewport.height / source.height).coerceAtLeast(1f)
        return StoryPanelFrame(
            bounds = bounds,
            rotationX = rx, rotationY = ry, pivotX = pivotX, pivotY = pivotY,
            sourceAlpha = 1f - reveal,
            surfaceAlpha = ease(0f, .30f, geometryTravel),
            contentAlpha = reveal,
            sourceScale = compression * mix(1f, artworkScale, geometryTravel),
            backdropAlpha = .50f * ease(0f, 1f, geometryTravel),
            rotationZ = rz,
        )
    }

    fun neighbor(style: MasterLaunchAnimation, raw: Float, bounds: Rect,
        source: Rect, viewport: Rect): StoryNeighborFrame =
        neighborAtTravel(style, settle(0f, 1f, raw), bounds, source, viewport)

    fun neighborAtTravel(style: MasterLaunchAnimation, travel: Float, bounds: Rect,
        source: Rect, viewport: Rect, returning: Boolean = false): StoryNeighborFrame {
        val w = viewport.width.coerceAtLeast(1f)
        val h = viewport.height.coerceAtLeast(1f)
        val dx = (bounds.center.x - source.center.x) / w
        val dy = (bounds.center.y - source.center.y) / h
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceIn(0f, 1f)
        val delay = when (style) {
            MasterLaunchAnimation.RIPPLE -> .035f + distance * .23f
            MasterLaunchAnimation.PAGE_TURN -> .03f +
                ((bounds.center.y - viewport.top) / h).coerceIn(0f, 1f) * .15f
            MasterLaunchAnimation.PASSAGE -> .02f + abs(dx) * .18f + abs(dy) * .05f
            MasterLaunchAnimation.CASCADE -> .02f +
                ((bounds.center.y - viewport.top) / h).coerceIn(0f, 1f) * .28f
            else -> .025f + distance * .10f
        }
        // Propagation is still distance ordered, but each neighbor accelerates
        // smoothly from rest rather than starting a second fast-out clock.
        val phase = if (isFlowStory(style)) geometryPhase(style, travel, returning) else travel
        val move = ease(delay, 1f, phase)
        val fade = 1f - ease(delay + .10f, 1f, phase)
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
                // Ripple's opening wave is intentional. Replaying that pulse
                // in reverse makes neighboring tiles wobble a SECOND time
                // just before landing, even when their paths are monotonic.
                // Keep their return scale/rotation quiet and connected.
                val wave = if (returning) 0f else sin(PI * move).toFloat()
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
            MasterLaunchAnimation.PASSAGE -> {
                // A continuous horizontal weight keeps a tile on the center
                // line still instead of arbitrarily choosing a left/right side.
                val horizontal = (dx * 5f).coerceIn(-1f, 1f)
                StoryNeighborFrame(fade, horizontal * w * .32f * move,
                    dy * h * .04f * move, scale = 1f - .07f * move,
                    rotationY = -horizontal * 8f * move)
            }
            MasterLaunchAnimation.CASCADE -> {
                // Top-to-bottom propagation makes the mosaic yield in rows.
                // Every channel is monotone, including Back: no second pulse.
                val row = ((bounds.center.y - viewport.top) / h).coerceIn(0f, 1f)
                StoryNeighborFrame(fade, dx * w * .04f * move,
                    h * (.24f + .08f * row) * move, scale = 1f - .12f * move,
                    rotationX = -12f * move, pivotY = 0f)
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
