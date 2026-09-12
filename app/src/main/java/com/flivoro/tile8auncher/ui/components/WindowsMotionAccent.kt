package com.flivoro.tile8auncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Windows 8.1-style Motion Accent families represented by the reconstructed vector themes. */
internal enum class WindowsMotionAccentKind {
    NONE,
    ROBOTS,
    CITY,
    BUBBLES,
    BIRD,
    DRAGON,
    GEARS,
}

/**
 * Draw-time state only. Snapshot state is read directly by Canvas, so animation invalidates drawing
 * without rebuilding the launcher hierarchy.
 */
@Stable
internal class WindowsMotionAccentFrame(
    val kind: WindowsMotionAccentKind = WindowsMotionAccentKind.NONE,
    phaseSeconds: Float = 0f,
    activity: Float = 0f,
    scrollVelocity: Float = 0f,
) {
    internal val phaseState = mutableFloatStateOf(phaseSeconds)
    internal val activityState = mutableFloatStateOf(activity.coerceIn(0f, 1f))
    internal val velocityState = mutableFloatStateOf(scrollVelocity.coerceIn(-1f, 1f))
    internal val enabledState = mutableStateOf(true)

    @Volatile
    internal var lastInteractionNanos: Long = 0L

    val phaseSeconds: Float get() = phaseState.floatValue
    val activity: Float
        get() = if (!enabledState.value) 0f else if (WindowsMotionAccent.isAmbient(kind)) {
            1f
        } else {
            activityState.floatValue.coerceIn(0f, 1f)
        }
    val scrollVelocity: Float get() = velocityState.floatValue.coerceIn(-1f, 1f)
}

internal object WindowsMotionAccent {
    // Historical reports for the Robots theme describe about 6-8 seconds after interaction.
    const val ACTIVE_DURATION_MILLIS = 7_000L
    internal const val HOLD_DURATION_MILLIS = 6_250L
    internal const val FADE_DURATION_MILLIS = 750L

    fun kindForStyle(style: Int): WindowsMotionAccentKind = when (style) {
        1 -> WindowsMotionAccentKind.ROBOTS
        2 -> WindowsMotionAccentKind.CITY
        3 -> WindowsMotionAccentKind.BUBBLES
        5 -> WindowsMotionAccentKind.BIRD
        8 -> WindowsMotionAccentKind.DRAGON
        9 -> WindowsMotionAccentKind.GEARS
        else -> WindowsMotionAccentKind.NONE
    }

    fun isAmbient(kind: WindowsMotionAccentKind): Boolean =
        kind == WindowsMotionAccentKind.CITY || kind == WindowsMotionAccentKind.BUBBLES

    fun normalizedVelocity(deltaPx: Float, viewportWidthPx: Float): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val delta = deltaPx.takeIf(Float::isFinite) ?: 0f
        return (delta / width * 12f).coerceIn(-1f, 1f)
    }

    /** Subtle whole-object follow used by the reconstructed interactive layers. */
    fun artworkOffsetX(frame: WindowsMotionAccentFrame, viewportWidthPx: Float): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (frame.activity <= 0f) return 0f
        val phase = frame.phaseSeconds
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> sin(phase * 1.55f) * 0.0035f
            WindowsMotionAccentKind.BIRD -> -frame.scrollVelocity * 0.012f
            WindowsMotionAccentKind.DRAGON ->
                (-frame.scrollVelocity * 0.016f) + sin(phase * 1.18f) * 0.0035f
            else -> 0f
        }
        return (fraction * width * frame.activity).takeIf(Float::isFinite) ?: 0f
    }

    fun artworkOffsetY(frame: WindowsMotionAccentFrame, viewportHeightPx: Float): Float {
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (frame.activity <= 0f) return 0f
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> cos(frame.phaseSeconds * 1.25f) * 0.0025f
            WindowsMotionAccentKind.BIRD -> sin(frame.phaseSeconds * 4.0f) * 0.002f
            WindowsMotionAccentKind.DRAGON -> sin(frame.phaseSeconds * 1.62f) * 0.0022f
            else -> 0f
        }
        return (fraction * height * frame.activity).takeIf(Float::isFinite) ?: 0f
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

    fun birdWingDegrees(phaseSeconds: Float, velocity: Float, activity: Float): Float {
        if (activity <= 0f) return 0f
        val flutter = sin(phaseSeconds * 9.0f) * 16f
        return (flutter + velocity.coerceIn(-1f, 1f) * 10f) * activity
    }

    fun dragonTailDegrees(phaseSeconds: Float, velocity: Float, activity: Float): Float {
        if (activity <= 0f) return 0f
        return (
            sin(phaseSeconds * 2.2f) * 5f -
                velocity.coerceIn(-1f, 1f) * 12f
            ) * activity
    }

    fun cityLightAlpha(phaseSeconds: Float, lightIndex: Int, activity: Float = 1f): Float {
        val pulse = (sin(phaseSeconds * 1.7f + lightIndex * 1.91f) + 1f) * 0.5f
        return ((0.07f + pulse * 0.28f) * activity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    fun bubbleTravel(phaseSeconds: Float, bubbleIndex: Int): Float {
        val speed = 0.050f + (bubbleIndex % 5) * 0.010f
        val initial = (bubbleIndex * 0.137f) % 1f
        val raw = initial + phaseSeconds * speed
        return raw - floor(raw)
    }
}

/**
 * Efficient Motion Accent clock:
 * - static themes sleep completely;
 * - city updates at ~8 fps when idle because only lights change;
 * - bubbles update at ~25 fps because their movement is slow;
 * - interaction-driven themes temporarily update at display-friendly cadence, then sleep after the
 *   documented ~7 second activity window.
 *
 * Mechanical/creature themes advance their phase with the last horizontal scroll direction. A
 * direction reversal therefore reverses cogs/body motion continuously instead of flipping an
 * already accumulated absolute angle.
 */
@Composable
internal fun rememberWindowsMotionAccentFrame(
    wallpaperStyle: Int,
    enabled: Boolean,
    viewportWidthPx: Float,
    sceneState: StartBackgroundSceneState,
): WindowsMotionAccentFrame {
    val kind = WindowsMotionAccent.kindForStyle(wallpaperStyle)
    val frame = remember(wallpaperStyle) { WindowsMotionAccentFrame(kind = kind) }

    LaunchedEffect(enabled, frame) {
        frame.enabledState.value = enabled
        if (!enabled) {
            frame.activityState.floatValue = 0f
            frame.velocityState.floatValue = 0f
        }
    }

    LaunchedEffect(frame, viewportWidthPx, enabled, sceneState) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE) return@LaunchedEffect
        var lastSerial = sceneState.interactionSerial
        snapshotFlow { sceneState.interactionSerial }.collect { serial ->
            if (serial == lastSerial) return@collect
            lastSerial = serial
            val delta = sceneState.lastScrollDeltaPx
            val sample = WindowsMotionAccent.normalizedVelocity(delta, viewportWidthPx)
            frame.velocityState.floatValue =
                frame.velocityState.floatValue * 0.48f + sample * 0.52f
            frame.lastInteractionNanos = System.nanoTime()
            frame.activityState.floatValue = 1f
        }
    }

    LaunchedEffect(enabled, kind, frame) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE) return@LaunchedEffect

        var previousNanos = System.nanoTime()
        while (isActive) {
            val now = System.nanoTime()
            val sinceInteractionMillis = if (frame.lastInteractionNanos == 0L) {
                Long.MAX_VALUE
            } else {
                ((now - frame.lastInteractionNanos) / 1_000_000L).coerceAtLeast(0L)
            }
            val interactionActive = sinceInteractionMillis < WindowsMotionAccent.ACTIVE_DURATION_MILLIS
            val ambient = WindowsMotionAccent.isAmbient(kind)

            if (!interactionActive && !ambient) {
                frame.activityState.floatValue = 0f
                frame.velocityState.floatValue *= 0.65f
                if (abs(frame.velocityState.floatValue) < 0.002f) {
                    frame.velocityState.floatValue = 0f
                }
                previousNanos = now
                delay(120L)
                continue
            }

            val intervalMillis = when {
                interactionActive -> 16L
                kind == WindowsMotionAccentKind.BUBBLES -> 40L
                kind == WindowsMotionAccentKind.CITY -> 120L
                else -> 80L
            }
            delay(intervalMillis)

            val tick = System.nanoTime()
            val deltaSeconds = ((tick - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.15f)
            previousNanos = tick

            // City/bubbles are independent ambient clocks. Interactive artwork instead advances in
            // the user's current horizontal direction, preserving continuity when direction flips.
            val phaseDirection = if (ambient || frame.velocityState.floatValue >= 0f) 1f else -1f
            frame.phaseState.floatValue += deltaSeconds * phaseDirection
            if (abs(frame.phaseState.floatValue) > 3_600f) {
                frame.phaseState.floatValue %= 60f
            }

            if (interactionActive) {
                val elapsed = if (frame.lastInteractionNanos == 0L) 0L else
                    ((tick - frame.lastInteractionNanos) / 1_000_000L).coerceAtLeast(0L)
                frame.activityState.floatValue = when {
                    elapsed <= WindowsMotionAccent.HOLD_DURATION_MILLIS -> 1f
                    elapsed >= WindowsMotionAccent.ACTIVE_DURATION_MILLIS -> 0f
                    else -> 1f - (
                        (elapsed - WindowsMotionAccent.HOLD_DURATION_MILLIS).toFloat() /
                            WindowsMotionAccent.FADE_DURATION_MILLIS.toFloat()
                        ).coerceIn(0f, 1f)
                }
                frame.velocityState.floatValue *= 0.965f
            } else {
                frame.activityState.floatValue = 0f
                frame.velocityState.floatValue = 0f
            }
        }
    }

    return frame
}
