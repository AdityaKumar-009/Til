package com.flivoro.tile8auncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import com.flivoro.tile8auncher.ui.components.WindowsMotionAccent.ACTIVE_DURATION_MILLIS
import com.flivoro.tile8auncher.ui.components.WindowsMotionAccent.FADE_DURATION_MILLIS
import com.flivoro.tile8auncher.ui.components.WindowsMotionAccent.HOLD_DURATION_MILLIS
import com.flivoro.tile8auncher.ui.components.WindowsMotionAccent.isAmbient
import com.flivoro.tile8auncher.ui.components.WindowsMotionAccent.normalizedVelocity

/** Windows 8.1 Motion Accent families represented by the recovered stock-art masks. */
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
 * Draw-time state only. Snapshot values invalidate the wallpaper draw layer without recomposing the
 * launcher hierarchy. Actual artwork geometry is static/cached; motion changes only shader matrices.
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
    // Contemporary reports of the Robots background consistently describe about 6-8 seconds.
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

    /** Whole-art follow is intentionally tiny; the exact recovered source geometry stays intact. */
    fun artworkOffsetX(frame: WindowsMotionAccentFrame, viewportWidthPx: Float): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (frame.activity <= 0f) return 0f
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> sin(frame.phaseSeconds * 1.55f) * 0.0018f
            WindowsMotionAccentKind.BIRD -> -frame.scrollVelocity * 0.006f
            WindowsMotionAccentKind.DRAGON ->
                (-frame.scrollVelocity * 0.010f) + sin(frame.phaseSeconds * 1.18f) * 0.0018f
            else -> 0f
        }
        return (fraction * width * frame.activity).takeIf(Float::isFinite) ?: 0f
    }

    fun artworkOffsetY(frame: WindowsMotionAccentFrame, viewportHeightPx: Float): Float {
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (frame.activity <= 0f) return 0f
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS -> cos(frame.phaseSeconds * 1.25f) * 0.0012f
            WindowsMotionAccentKind.BIRD -> sin(frame.phaseSeconds * 4.0f) * 0.0012f
            WindowsMotionAccentKind.DRAGON -> sin(frame.phaseSeconds * 1.62f) * 0.0014f
            WindowsMotionAccentKind.BUBBLES -> sin(frame.phaseSeconds * 0.42f) * 0.0010f
            else -> 0f
        }
        return (fraction * height * frame.activity).takeIf(Float::isFinite) ?: 0f
    }

    /**
     * Separating tone-mask phase very slightly creates the original subtle "alive" response without
     * drawing fake gears/curves over the recovered stock pixels. Offsets are deliberately sub-1%.
     */
    fun maskLayerOffsetX(
        frame: WindowsMotionAccentFrame,
        viewportWidthPx: Float,
        layerIndex: Int,
    ): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        val activity = frame.activity
        if (activity <= 0f) return 0f
        val layer = layerIndex.coerceIn(0, 2) - 1
        val phase = frame.phaseSeconds
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.ROBOTS ->
                sin(phase * (1.9f + layerIndex * 0.14f)) * layer * 0.0012f
            WindowsMotionAccentKind.GEARS ->
                sin(phase * (1.5f + layerIndex * 0.22f)) * layer * 0.0019f
            WindowsMotionAccentKind.DRAGON ->
                (-frame.scrollVelocity * layer * 0.0028f) + sin(phase * 1.7f) * layer * 0.0008f
            WindowsMotionAccentKind.BIRD ->
                sin(phase * 5.5f) * layer * 0.0010f
            WindowsMotionAccentKind.BUBBLES ->
                sin(phase * 0.55f + layerIndex) * layer * 0.0007f
            else -> 0f
        }
        return width * fraction * activity
    }

    fun maskLayerOffsetY(frame: WindowsMotionAccentFrame, viewportHeightPx: Float): Float {
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (frame.activity <= 0f) return 0f
        val fraction = when (frame.kind) {
            WindowsMotionAccentKind.BUBBLES -> -sin(frame.phaseSeconds * 0.40f) * 0.0018f
            WindowsMotionAccentKind.BIRD -> sin(frame.phaseSeconds * 4.8f) * 0.0008f
            WindowsMotionAccentKind.DRAGON -> sin(frame.phaseSeconds * 1.55f) * 0.0007f
            else -> 0f
        }
        return height * fraction * frame.activity
    }

    /** City windows appear to breathe by changing only the bright recovered source layer. */
    fun highlightLayerAlpha(frame: WindowsMotionAccentFrame): Int {
        if (!frame.enabledState.value) return 255
        val factor = when (frame.kind) {
            WindowsMotionAccentKind.CITY -> 0.80f +
                ((sin(frame.phaseSeconds * 1.35f) + 1f) * 0.5f) * 0.20f
            WindowsMotionAccentKind.BUBBLES -> 0.88f +
                ((sin(frame.phaseSeconds * 0.55f) + 1f) * 0.5f) * 0.12f
            else -> 1f
        }
        return (factor * 255f).toInt().coerceIn(0, 255)
    }

    // Kept as pure helpers for regression compatibility and future extracted moving-part masks.
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
 * Motion clock with no geometry work:
 * - static themes sleep completely;
 * - city/bubbles update at intentionally modest ambient rates;
 * - interaction themes run temporarily then sleep after ~7 seconds;
 * - low-RAM devices use a lower redraw cadence while elapsed-time motion stays the same speed.
 */
@Composable
internal fun rememberWindowsMotionAccentFrame(
    wallpaperStyle: Int,
    enabled: Boolean,
    viewportWidthPx: Float,
    sceneState: StartBackgroundSceneState,
    lowRamMode: Boolean = false,
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
            val sample = normalizedVelocity(delta, viewportWidthPx)
            frame.velocityState.floatValue =
                frame.velocityState.floatValue * 0.48f + sample * 0.52f
            frame.lastInteractionNanos = System.nanoTime()
            frame.activityState.floatValue = 1f
        }
    }

    LaunchedEffect(enabled, kind, frame, lowRamMode) {
        if (!enabled || kind == WindowsMotionAccentKind.NONE) return@LaunchedEffect

        var previousNanos = System.nanoTime()
        while (isActive) {
            val now = System.nanoTime()
            val sinceInteractionMillis = if (frame.lastInteractionNanos == 0L) {
                Long.MAX_VALUE
            } else {
                ((now - frame.lastInteractionNanos) / 1_000_000L).coerceAtLeast(0L)
            }
            val interactionActive = sinceInteractionMillis < ACTIVE_DURATION_MILLIS
            val ambient = isAmbient(kind)

            if (!interactionActive && !ambient) {
                frame.activityState.floatValue = 0f
                frame.velocityState.floatValue *= 0.65f
                if (abs(frame.velocityState.floatValue) < 0.002f) {
                    frame.velocityState.floatValue = 0f
                }
                previousNanos = now
                delay(if (lowRamMode) 220L else 150L)
                continue
            }

            val intervalMillis = when {
                interactionActive -> if (lowRamMode) 33L else 16L
                kind == WindowsMotionAccentKind.BUBBLES -> if (lowRamMode) 90L else 56L
                kind == WindowsMotionAccentKind.CITY -> if (lowRamMode) 250L else 160L
                else -> if (lowRamMode) 140L else 90L
            }
            delay(intervalMillis)

            val tick = System.nanoTime()
            val deltaSeconds = ((tick - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
            previousNanos = tick

            val phaseDirection = if (ambient || frame.velocityState.floatValue >= 0f) 1f else -1f
            frame.phaseState.floatValue += deltaSeconds * phaseDirection
            if (abs(frame.phaseState.floatValue) > 3_600f) {
                frame.phaseState.floatValue %= 60f
            }

            if (interactionActive) {
                val elapsed = if (frame.lastInteractionNanos == 0L) 0L else
                    ((tick - frame.lastInteractionNanos) / 1_000_000L).coerceAtLeast(0L)
                frame.activityState.floatValue = when {
                    elapsed <= HOLD_DURATION_MILLIS -> 1f
                    elapsed >= ACTIVE_DURATION_MILLIS -> 0f
                    else -> 1f - (
                        (elapsed - HOLD_DURATION_MILLIS).toFloat() /
                            FADE_DURATION_MILLIS.toFloat()
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
