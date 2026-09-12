package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Windows 8.1 Motion Accent families represented by the supplied Start backgrounds. */
internal enum class WindowsMotionAccentKind {
    NONE,
    ROBOTS,
    CITY,
    BUBBLES,
    DRAGON,
    GEARS,
}

/**
 * Stable draw-time state. The public-looking properties are snapshot-backed so a draw block that
 * reads them is invalidated without recomposing/rebuilding the wallpaper hierarchy every frame.
 */
@Stable
internal class WindowsMotionAccentFrame(
    val kind: WindowsMotionAccentKind = WindowsMotionAccentKind.NONE,
    phaseSeconds: Float = 0f,
    activity: Float = 0f,
    scrollVelocity: Float = 0f,
    scrollPosition: Float = 0f,
) {
    internal val activityAnimatable = Animatable(activity.coerceIn(0f, 1f))
    internal val phaseState = mutableFloatStateOf(phaseSeconds)
    internal val velocityState = mutableFloatStateOf(scrollVelocity.coerceIn(-1f, 1f))
    internal val positionState = mutableFloatStateOf(scrollPosition)

    val phaseSeconds: Float get() = phaseState.floatValue
    val activity: Float get() = activityAnimatable.value.coerceIn(0f, 1f)
    val scrollVelocity: Float get() = velocityState.floatValue.coerceIn(-1f, 1f)
    val scrollPosition: Float get() = positionState.floatValue.takeIf(Float::isFinite) ?: 0f
}

internal object WindowsMotionAccent {
    // Contemporary Windows 8.1 reports describe roughly 6-8 seconds of post-interaction motion.
    const val ACTIVE_DURATION_MILLIS = 7_000L
    internal const val HOLD_DURATION_MILLIS = 6_250L
    internal const val FADE_DURATION_MILLIS = 750

    fun kindForStyle(style: Int): WindowsMotionAccentKind = when (style) {
        1 -> WindowsMotionAccentKind.ROBOTS
        2 -> WindowsMotionAccentKind.CITY
        3 -> WindowsMotionAccentKind.BUBBLES
        8 -> WindowsMotionAccentKind.DRAGON
        9 -> WindowsMotionAccentKind.GEARS
        else -> WindowsMotionAccentKind.NONE
    }

    fun normalizedVelocity(deltaPx: Float, viewportWidthPx: Float): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val delta = deltaPx.takeIf(Float::isFinite) ?: 0f
        return (delta / width * 12f).coerceIn(-1f, 1f)
    }

    fun artworkOffsetX(frame: WindowsMotionAccentFrame, viewportWidthPx: Float): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        val activity = frame.activity
        if (activity <= 0f) return 0f
        val phase = frame.phaseSeconds
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> sin(phase * 1.55f) * 0.0045f
            WindowsMotionAccentKind.CITY -> 0f
            WindowsMotionAccentKind.BUBBLES -> sin(phase * 0.62f) * 0.0025f
            WindowsMotionAccentKind.DRAGON ->
                (-frame.scrollVelocity * 0.016f) + sin(phase * 1.18f) * 0.0035f
            WindowsMotionAccentKind.GEARS -> 0f
            WindowsMotionAccentKind.NONE -> 0f
        }
        return (fraction * width * activity).takeIf(Float::isFinite) ?: 0f
    }

    fun artworkOffsetY(frame: WindowsMotionAccentFrame, viewportHeightPx: Float): Float {
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        val activity = frame.activity
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> cos(frame.phaseSeconds * 1.25f) * 0.0025f
            WindowsMotionAccentKind.BUBBLES -> sin(frame.phaseSeconds * 0.55f) * 0.0018f
            WindowsMotionAccentKind.DRAGON -> sin(frame.phaseSeconds * 1.62f) * 0.0022f
            else -> 0f
        }
        return (fraction * height * activity).takeIf(Float::isFinite) ?: 0f
    }

    fun robotGearDegrees(phaseSeconds: Float, gearIndex: Int): Float {
        val direction = if (gearIndex % 2 == 0) 1f else -1f
        val speed = 42f + gearIndex.coerceAtLeast(0) * 13f
        return phaseSeconds * speed * direction
    }

    fun gearDegrees(phaseSeconds: Float, gearIndex: Int): Float {
        val direction = if (gearIndex % 2 == 0) 1f else -1f
        val speed = 20f + gearIndex.coerceAtLeast(0) * 8f
        return phaseSeconds * speed * direction
    }

    fun cityLightAlpha(phaseSeconds: Float, lightIndex: Int, activity: Float): Float {
        val pulse = (sin(phaseSeconds * 1.7f + lightIndex * 1.91f) + 1f) * 0.5f
        return (0.06f + pulse * 0.24f) * activity.coerceIn(0f, 1f)
    }

    fun bubbleTravel(phaseSeconds: Float, bubbleIndex: Int): Float {
        val speed = 0.055f + (bubbleIndex % 5) * 0.012f
        val initial = (bubbleIndex * 0.137f) % 1f
        val raw = initial + phaseSeconds * speed
        return raw - kotlin.math.floor(raw)
    }
}

/**
 * Tracks horizontal Start/Apps interaction. Animated profiles wake for about seven seconds and then
 * settle. Static profiles do not run a frame loop at all.
 */
@Composable
internal fun rememberWindowsMotionAccentFrame(
    wallpaperStyle: Int,
    enabled: Boolean,
    viewportWidthPx: Float,
    scrollOffsetPx: () -> Float,
): WindowsMotionAccentFrame {
    val kind = WindowsMotionAccent.kindForStyle(wallpaperStyle)
    val frame = remember(wallpaperStyle) { WindowsMotionAccentFrame(kind = kind) }
    val latestScrollOffset = rememberUpdatedState(scrollOffsetPx)
    val interactionSerial = remember(wallpaperStyle) { mutableIntStateOf(0) }

    LaunchedEffect(enabled, kind, viewportWidthPx, frame) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE) {
            frame.activityAnimatable.snapTo(0f)
            frame.velocityState.floatValue = 0f
            return@LaunchedEffect
        }

        var previous: Float? = null
        snapshotFlow {
            latestScrollOffset.value()
                .takeIf(Float::isFinite)
                ?: 0f
        }
            .distinctUntilChanged()
            .collect { current ->
                val before = previous
                previous = current
                frame.positionState.floatValue = current / max(viewportWidthPx, 1f)
                if (before != null) {
                    val delta = current - before
                    if (abs(delta) >= 0.35f) {
                        val sample = WindowsMotionAccent.normalizedVelocity(delta, viewportWidthPx)
                        frame.velocityState.floatValue =
                            frame.velocityState.floatValue * 0.58f + sample * 0.42f
                        interactionSerial.intValue++
                    }
                }
            }
    }

    LaunchedEffect(interactionSerial.intValue, enabled, kind, frame) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE || interactionSerial.intValue == 0) {
            if (!enabled || kind == WindowsMotionAccentKind.NONE) frame.activityAnimatable.snapTo(0f)
            return@LaunchedEffect
        }
        frame.activityAnimatable.snapTo(1f)
        delay(WindowsMotionAccent.HOLD_DURATION_MILLIS)
        frame.activityAnimatable.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = WindowsMotionAccent.FADE_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        )
        frame.velocityState.floatValue = 0f
    }

    LaunchedEffect(enabled, kind, frame) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE) return@LaunchedEffect
        var previousFrameNanos = 0L
        while (isActive) {
            if (frame.activityAnimatable.value <= 0.001f) {
                previousFrameNanos = 0L
                delay(96L)
            } else {
                withFrameNanos { now ->
                    if (previousFrameNanos != 0L) {
                        val deltaSeconds = ((now - previousFrameNanos) / 1_000_000_000f)
                            .coerceIn(0f, 0.050f)
                        frame.phaseState.floatValue += deltaSeconds
                        if (frame.phaseState.floatValue > 3_600f) frame.phaseState.floatValue %= 60f
                    }
                    previousFrameNanos = now
                }
            }
        }
    }

    return frame
}

/** Draw only the accent behavior; the exact supplied artwork stays underneath this layer. */
internal fun DrawScope.drawWindowsMotionAccent(
    frame: WindowsMotionAccentFrame,
    viewportWidthPx: Float,
    accentColor: Color,
    highlightColor: Color,
) {
    if (frame.kind == WindowsMotionAccentKind.NONE || frame.activity <= 0.001f) return
    val viewportWidth = viewportWidthPx.coerceAtLeast(1f)
    val height = size.height.coerceAtLeast(1f)

    repeat(3) { panel ->
        val originX = panel * viewportWidth
        val mirrored = panel % 2 == 1
        when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> drawRobotAccents(
                originX, viewportWidth, height, mirrored, frame, highlightColor,
            )
            WindowsMotionAccentKind.CITY -> drawCityAccents(
                originX, viewportWidth, height, mirrored, frame, highlightColor,
            )
            WindowsMotionAccentKind.BUBBLES -> drawBubbleAccents(
                originX, viewportWidth, height, mirrored, frame, highlightColor,
            )
            WindowsMotionAccentKind.DRAGON -> drawDragonAccent(
                originX, viewportWidth, height, mirrored, frame, accentColor, highlightColor,
            )
            WindowsMotionAccentKind.GEARS -> drawGearAccents(
                originX, viewportWidth, height, mirrored, frame, highlightColor,
            )
            WindowsMotionAccentKind.NONE -> Unit
        }
    }
}

private fun DrawScope.drawRobotAccents(
    originX: Float,
    width: Float,
    height: Float,
    mirrored: Boolean,
    frame: WindowsMotionAccentFrame,
    color: Color,
) {
    val positions = arrayOf(
        Triple(0.18f, 0.28f, 0.055f),
        Triple(0.53f, 0.72f, 0.072f),
        Triple(0.81f, 0.39f, 0.047f),
    )
    positions.forEachIndexed { index, (fx, fy, fr) ->
        val xFraction = if (mirrored) 1f - fx else fx
        val bob = sin(frame.phaseSeconds * (1.1f + index * 0.17f) + index) * height * 0.006f
        drawGear(
            center = Offset(originX + width * xFraction, height * fy + bob),
            radius = width * fr,
            teeth = 9 + index,
            rotationDegrees = WindowsMotionAccent.robotGearDegrees(frame.phaseSeconds, index),
            color = color.copy(alpha = frame.activity * (0.10f + index * 0.018f)),
            strokeWidth = max(1.25f, width * 0.0021f),
        )
    }
}

private fun DrawScope.drawCityAccents(
    originX: Float,
    width: Float,
    height: Float,
    mirrored: Boolean,
    frame: WindowsMotionAccentFrame,
    color: Color,
) {
    repeat(18) { index ->
        val column = index % 9
        val row = index / 9
        val rawX = 0.08f + column * 0.105f
        val xFraction = if (mirrored) 1f - rawX else rawX
        val yFraction = 0.70f + row * 0.085f + (column % 3) * 0.014f
        val lightWidth = max(2f, width * 0.008f)
        val lightHeight = max(2f, height * 0.008f)
        drawRect(
            color = color.copy(
                alpha = WindowsMotionAccent.cityLightAlpha(
                    frame.phaseSeconds,
                    index,
                    frame.activity,
                ),
            ),
            topLeft = Offset(
                originX + width * xFraction - lightWidth / 2f,
                height * yFraction,
            ),
            size = androidx.compose.ui.geometry.Size(lightWidth, lightHeight),
        )
    }
}

private fun DrawScope.drawBubbleAccents(
    originX: Float,
    width: Float,
    height: Float,
    mirrored: Boolean,
    frame: WindowsMotionAccentFrame,
    color: Color,
) {
    repeat(11) { index ->
        val rawX = 0.08f + ((index * 0.173f) % 0.84f)
        val xFraction = if (mirrored) 1f - rawX else rawX
        val travel = WindowsMotionAccent.bubbleTravel(frame.phaseSeconds, index)
        val y = height * (1.08f - travel * 1.22f)
        val sway = sin(frame.phaseSeconds * 0.72f + index * 0.9f) * width * 0.018f
        val radius = width * (0.010f + (index % 4) * 0.005f)
        drawCircle(
            color = color.copy(alpha = frame.activity * (0.055f + (index % 3) * 0.018f)),
            radius = radius,
            center = Offset(originX + width * xFraction + sway, y),
            style = Stroke(width = max(1f, radius * 0.12f)),
        )
    }
}

private fun DrawScope.drawDragonAccent(
    originX: Float,
    width: Float,
    height: Float,
    mirrored: Boolean,
    frame: WindowsMotionAccentFrame,
    accentColor: Color,
    highlightColor: Color,
) {
    val direction = if (mirrored) -1f else 1f
    val velocityPull = frame.scrollVelocity * width * 0.055f * direction
    val wave = sin(frame.phaseSeconds * 2.15f) * height * 0.014f
    val path = Path().apply {
        val startX = originX + width * if (mirrored) 0.84f else 0.16f
        moveTo(startX + velocityPull, height * 0.22f + wave)
        cubicTo(
            originX + width * if (mirrored) 0.70f else 0.30f,
            height * 0.30f - wave,
            originX + width * if (mirrored) 0.52f else 0.48f,
            height * 0.16f + wave,
            originX + width * if (mirrored) 0.35f else 0.65f,
            height * 0.28f - wave,
        )
    }
    drawPath(
        path = path,
        color = highlightColor.copy(alpha = frame.activity * 0.075f),
        style = Stroke(width = max(1.5f, width * 0.0045f)),
    )
    drawCircle(
        color = accentColor.copy(alpha = frame.activity * 0.055f),
        radius = width * 0.075f,
        center = Offset(
            originX + width * if (mirrored) 0.26f else 0.74f,
            height * 0.20f + wave,
        ),
    )
}

private fun DrawScope.drawGearAccents(
    originX: Float,
    width: Float,
    height: Float,
    mirrored: Boolean,
    frame: WindowsMotionAccentFrame,
    color: Color,
) {
    val positions = arrayOf(
        Triple(0.13f, 0.80f, 0.085f),
        Triple(0.35f, 0.70f, 0.054f),
        Triple(0.62f, 0.84f, 0.095f),
        Triple(0.84f, 0.66f, 0.060f),
    )
    positions.forEachIndexed { index, (fx, fy, fr) ->
        val xFraction = if (mirrored) 1f - fx else fx
        drawGear(
            center = Offset(originX + width * xFraction, height * fy),
            radius = width * fr,
            teeth = 10 + (index % 3) * 2,
            rotationDegrees = WindowsMotionAccent.gearDegrees(frame.phaseSeconds, index),
            color = color.copy(alpha = frame.activity * (0.075f + index * 0.012f)),
            strokeWidth = max(1.25f, width * 0.002f),
        )
    }
}

private fun DrawScope.drawGear(
    center: Offset,
    radius: Float,
    teeth: Int,
    rotationDegrees: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (radius <= 0f || teeth < 3) return
    rotate(rotationDegrees, pivot = center) {
        val path = Path()
        val steps = teeth * 2
        repeat(steps + 1) { step ->
            val angle = (step.toFloat() / steps.toFloat()) * PI.toFloat() * 2f
            val r = if (step % 2 == 0) radius else radius * 0.82f
            val point = Offset(
                center.x + cos(angle) * r,
                center.y + sin(angle) * r,
            )
            if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color = color, style = Stroke(width = strokeWidth))
        drawCircle(
            color = color,
            radius = radius * 0.30f,
            center = center,
            style = Stroke(width = strokeWidth),
        )
    }
}
