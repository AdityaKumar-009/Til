package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import kotlin.math.exp

/**
 * Timeline model expressed in milliseconds; Compose only provides the vsync presentation clock.
 * PHONE_8 uses the actual Microsoft.Phone.Controls.Toolkit TurnstileFeatherEffect 2013
 * parameters (Microsoft Public License): 350/250 ms, 40/50 ms feather, ±80/50 degrees,
 * ExponentialEase exponent 6, CenterOfRotationX = -0.2.
 *
 * MOBILE_10 is a separately tuned UWP-inspired entrance/drill-in approximation. Microsoft
 * never published the Windows 10 Mobile shell's private launch-compositor timeline.
 */
internal enum class PhoneMotionPhase {
    FORWARD_IN, FORWARD_OUT, BACKWARD_IN, BACKWARD_OUT,
}

internal data class PhoneMotionFrame(
    val alpha: Float,
    val rotationY: Float,
    val offsetYPx: Float,
    val scale: Float,
    val pivotX: Float,
)

internal object PhoneMotionTimeline {
    private const val CLASSIC_IN_MS = 350
    private const val CLASSIC_OUT_MS = 250
    private const val MAX_CASCADE_ORDINAL = 6
    const val CLASSIC_PIVOT_X = -0.2f

    fun delayMillis(mode: LauncherUiMode, phase: PhoneMotionPhase, ordinal: Int): Int {
        val position = ordinal.coerceIn(0, MAX_CASCADE_ORDINAL)
        return if (mode == LauncherUiMode.PHONE_8) {
            position * when (phase) {
                PhoneMotionPhase.FORWARD_IN, PhoneMotionPhase.BACKWARD_OUT -> 40
                PhoneMotionPhase.FORWARD_OUT, PhoneMotionPhase.BACKWARD_IN -> 50
            }
        } else position * 18
    }

    fun durationMillis(mode: LauncherUiMode, phase: PhoneMotionPhase): Int =
        if (mode == LauncherUiMode.PHONE_8) {
            when (phase) {
                PhoneMotionPhase.FORWARD_IN, PhoneMotionPhase.BACKWARD_IN -> CLASSIC_IN_MS
                PhoneMotionPhase.FORWARD_OUT, PhoneMotionPhase.BACKWARD_OUT -> CLASSIC_OUT_MS
            }
        } else {
            when (phase) {
                PhoneMotionPhase.FORWARD_IN, PhoneMotionPhase.BACKWARD_IN -> 300
                PhoneMotionPhase.FORWARD_OUT, PhoneMotionPhase.BACKWARD_OUT -> 200
            }
        }

    fun totalMillis(mode: LauncherUiMode, phase: PhoneMotionPhase, ordinal: Int): Int =
        delayMillis(mode, phase, ordinal) + durationMillis(mode, phase)

    /**
     * Microsoft's source-defined ExponentialEase exponent 6, using natural e, not base 2.
     * Do not substitute Compose's FastOutSlowInEasing (the old implementation did).
     */
    internal fun exponentialEaseIn6(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return ((exp(6.0 * p) - 1.0) / (exp(6.0) - 1.0)).toFloat()
    }

    internal fun exponentialEaseOut6(progress: Float): Float =
        1f - exponentialEaseIn6(1f - progress.coerceIn(0f, 1f))

    fun sample(
        mode: LauncherUiMode,
        phase: PhoneMotionPhase,
        elapsedMillis: Int,
        ordinal: Int,
        selectedTile: Boolean = false,
    ): PhoneMotionFrame {
        val delay = delayMillis(mode, phase, ordinal)
        val duration = durationMillis(mode, phase)
        val t = ((elapsedMillis - delay).toFloat() / duration).coerceIn(0f, 1f)
        val entering = phase == PhoneMotionPhase.FORWARD_IN || phase == PhoneMotionPhase.BACKWARD_IN

        if (mode == LauncherUiMode.PHONE_8) {
            val eased = if (entering) exponentialEaseOut6(t) else exponentialEaseIn6(t)
            val (from, to) = when (phase) {
                PhoneMotionPhase.FORWARD_IN -> -80f to 0f
                PhoneMotionPhase.FORWARD_OUT -> 0f to 50f
                PhoneMotionPhase.BACKWARD_IN -> 50f to 0f
                PhoneMotionPhase.BACKWARD_OUT -> 0f to -80f
            }
            // The historical feather effect snapped opacity to 1 at each item's begin time,
            // then kept it opaque until the exit turnstile had finished (no generic fade).
            val visible = if (entering) {
                elapsedMillis >= delay
            } else {
                elapsedMillis < delay + duration
            }
            return PhoneMotionFrame(
                alpha = if (visible) 1f else 0f,
                rotationY = from + (to - from) * eased,
                offsetYPx = 0f,
                scale = 1f,
                pivotX = CLASSIC_PIVOT_X,
            )
        }

        // Windows 10 Mobile switched to Windows Runtime navigation styles; do not
        // incorrectly apply the older toolkit's page rotation to the newer OS.
        val eased = if (entering) exponentialEaseOut6(t) else exponentialEaseIn6(t)
        val scale = if (entering) {
            0.94f + 0.06f * eased
        } else if (selectedTile) {
            1f + 0.16f * eased
        } else {
            1f - 0.055f * eased
        }
        return PhoneMotionFrame(
            alpha = if (entering) {
                if (elapsedMillis < delay) 0f else (t * 5f).coerceIn(0f, 1f)
            } else {
                1f - eased
            },
            rotationY = 0f,
            offsetYPx = if (entering) 90f * (1f - eased) else -40f * eased,
            scale = scale,
            pivotX = 0.5f,
        )
    }
}
