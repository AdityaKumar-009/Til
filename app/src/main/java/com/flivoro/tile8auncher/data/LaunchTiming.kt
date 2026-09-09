package com.flivoro.tile8auncher.data

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Time mappings that can be used for the launcher transition.
 *
 * The values are deliberately named for people choosing a motion style. The
 * implementation details stay behind [LaunchTiming.transform].
 */
enum class TimeCurve(
    val displayName: String,
    val group: String,
    val description: String,
    val strengthLabel: String? = null,
    val hasStepCount: Boolean = false,
) {
    REFERENCE(
        displayName = "Windows fitted",
        group = "Recommended",
        description = "Keeps the fitted Windows launch rhythm.",
    ),
    LINEAR(
        displayName = "Linear",
        group = "Basics",
        description = "Moves at the same pace from start to finish.",
    ),

    CSS_EASE(
        displayName = "Web: Ease",
        group = "Web styles",
        description = "A familiar quick start with a soft finish.",
    ),
    CSS_EASE_IN(
        displayName = "Web: Ease in",
        group = "Web styles",
        description = "Starts gently and gathers speed.",
    ),
    CSS_EASE_OUT(
        displayName = "Web: Ease out",
        group = "Web styles",
        description = "Starts quickly and settles gently.",
    ),
    CSS_EASE_IN_OUT(
        displayName = "Web: Ease in and out",
        group = "Web styles",
        description = "Starts and ends gently.",
    ),

    ANDROID_FAST_OUT_SLOW_IN(
        displayName = "Android: Fast, then smooth",
        group = "Android styles",
        description = "A quick launch that eases into place.",
    ),
    ANDROID_FAST_OUT_LINEAR_IN(
        displayName = "Android: Fast, then direct",
        group = "Android styles",
        description = "Moves quickly and keeps its pace into the end.",
    ),
    ANDROID_LINEAR_OUT_SLOW_IN(
        displayName = "Android: Direct, then smooth",
        group = "Android styles",
        description = "Starts directly and eases into place.",
    ),

    MATERIAL_STANDARD(
        displayName = "Material: Standard",
        group = "Material styles",
        description = "A balanced start and finish for everyday motion.",
    ),
    MATERIAL_STANDARD_ACCELERATE(
        displayName = "Material: Standard accelerate",
        group = "Material styles",
        description = "Builds speed toward the end.",
    ),
    MATERIAL_STANDARD_DECELERATE(
        displayName = "Material: Standard decelerate",
        group = "Material styles",
        description = "Slows into the end.",
    ),
    MATERIAL_EMPHASIZED(
        displayName = "Material: Emphasized",
        group = "Material styles",
        description = "A stronger launch with a calm finish.",
    ),
    MATERIAL_EMPHASIZED_ACCELERATE(
        displayName = "Material: Emphasized accelerate",
        group = "Material styles",
        description = "A stronger push toward the end.",
    ),
    MATERIAL_EMPHASIZED_DECELERATE(
        displayName = "Material: Emphasized decelerate",
        group = "Material styles",
        description = "A stronger slowdown into the end.",
    ),

    EASE_IN_SINE(
        displayName = "Sine: Ease in",
        group = "Sine",
        description = "A gentle start with a smooth build.",
    ),
    EASE_OUT_SINE(
        displayName = "Sine: Ease out",
        group = "Sine",
        description = "A smooth slowdown into the end.",
    ),
    EASE_IN_OUT_SINE(
        displayName = "Sine: Ease in and out",
        group = "Sine",
        description = "A soft start and soft finish.",
    ),

    EASE_IN_QUAD(
        displayName = "Quadratic: Ease in",
        group = "Quadratic",
        description = "A gentle start that gathers speed.",
    ),
    EASE_OUT_QUAD(
        displayName = "Quadratic: Ease out",
        group = "Quadratic",
        description = "A quick start that settles smoothly.",
    ),
    EASE_IN_OUT_QUAD(
        displayName = "Quadratic: Ease in and out",
        group = "Quadratic",
        description = "A balanced gentle start and finish.",
    ),

    EASE_IN_CUBIC(
        displayName = "Cubic: Ease in",
        group = "Cubic",
        description = "A stronger build toward the middle.",
    ),
    EASE_OUT_CUBIC(
        displayName = "Cubic: Ease out",
        group = "Cubic",
        description = "A strong start that settles into place.",
    ),
    EASE_IN_OUT_CUBIC(
        displayName = "Cubic: Ease in and out",
        group = "Cubic",
        description = "A stronger ease at both ends.",
    ),

    EASE_IN_QUART(
        displayName = "Quartic: Ease in",
        group = "Quartic",
        description = "A very gentle start with a strong build.",
    ),
    EASE_OUT_QUART(
        displayName = "Quartic: Ease out",
        group = "Quartic",
        description = "A strong start with a very soft finish.",
    ),
    EASE_IN_OUT_QUART(
        displayName = "Quartic: Ease in and out",
        group = "Quartic",
        description = "A pronounced ease at both ends.",
    ),

    EASE_IN_QUINT(
        displayName = "Quintic: Ease in",
        group = "Quintic",
        description = "A slow start with a dramatic build.",
    ),
    EASE_OUT_QUINT(
        displayName = "Quintic: Ease out",
        group = "Quintic",
        description = "A dramatic start with a long settle.",
    ),
    EASE_IN_OUT_QUINT(
        displayName = "Quintic: Ease in and out",
        group = "Quintic",
        description = "A dramatic ease at both ends.",
    ),

    EASE_IN_EXPO(
        displayName = "Exponential: Ease in",
        group = "Exponential",
        description = "Stays quiet, then accelerates sharply.",
    ),
    EASE_OUT_EXPO(
        displayName = "Exponential: Ease out",
        group = "Exponential",
        description = "Starts sharply, then nearly rests at the end.",
    ),
    EASE_IN_OUT_EXPO(
        displayName = "Exponential: Ease in and out",
        group = "Exponential",
        description = "A sharp launch and a sharp settle.",
    ),

    EASE_IN_CIRC(
        displayName = "Circular: Ease in",
        group = "Circular",
        description = "A round, gentle start with a quick finish.",
    ),
    EASE_OUT_CIRC(
        displayName = "Circular: Ease out",
        group = "Circular",
        description = "A quick start with a round, gentle finish.",
    ),
    EASE_IN_OUT_CIRC(
        displayName = "Circular: Ease in and out",
        group = "Circular",
        description = "A round ease at both ends.",
    ),

    BACK_IN(
        displayName = "Back: Pull in",
        group = "Bouncy and reversing",
        description = "Pulls back briefly before moving forward.",
        strengthLabel = "Back strength",
    ),
    BACK_OUT(
        displayName = "Back: Overshoot",
        group = "Bouncy and reversing",
        description = "Moves past the end, then comes back.",
        strengthLabel = "Back strength",
    ),
    BACK_IN_OUT(
        displayName = "Back: Pull in and overshoot",
        group = "Bouncy and reversing",
        description = "Pulls back first and overshoots near the end.",
        strengthLabel = "Back strength",
    ),
    ELASTIC_IN(
        displayName = "Elastic: Pull in",
        group = "Bouncy and reversing",
        description = "Builds from a springy start.",
        strengthLabel = "Elastic strength",
    ),
    ELASTIC_OUT(
        displayName = "Elastic: Spring into place",
        group = "Bouncy and reversing",
        description = "Springs past the end and settles back.",
        strengthLabel = "Elastic strength",
    ),
    ELASTIC_IN_OUT(
        displayName = "Elastic: Spring in and out",
        group = "Bouncy and reversing",
        description = "Uses a spring at both ends.",
        strengthLabel = "Elastic strength",
    ),
    BOUNCE_IN(
        displayName = "Bounce: Drop in",
        group = "Bouncy and reversing",
        description = "Arrives with a bouncing start.",
        strengthLabel = "Bounce strength",
    ),
    BOUNCE_OUT(
        displayName = "Bounce: Settle in",
        group = "Bouncy and reversing",
        description = "Bounces as it settles into place.",
        strengthLabel = "Bounce strength",
    ),
    BOUNCE_IN_OUT(
        displayName = "Bounce: Bounce both ends",
        group = "Bouncy and reversing",
        description = "Bounces at the start and the finish.",
        strengthLabel = "Bounce strength",
    ),

    STEPS_START(
        displayName = "Steps: Change at the start",
        group = "Stepped motion",
        description = "Moves in visible stops, changing early in each stop.",
        hasStepCount = true,
    ),
    STEPS_END(
        displayName = "Steps: Change at the end",
        group = "Stepped motion",
        description = "Moves in visible stops, changing late in each stop.",
        hasStepCount = true,
    ),
    CUSTOM_CUBIC_BEZIER(
        displayName = "Custom curve",
        group = "Your curve",
        description = "Set the four points and shape the motion yourself.",
    );

    companion object {
        val selectable: List<TimeCurve>
            get() = values().toList()

    }
}

data class LaunchTiming(
    val durationMillis: Int = DEFAULT_DURATION_MILLIS,
    val curve: TimeCurve = TimeCurve.REFERENCE,
    val customX1: Float = DEFAULT_CUSTOM_X1,
    val customY1: Float = DEFAULT_CUSTOM_Y1,
    val customX2: Float = DEFAULT_CUSTOM_X2,
    val customY2: Float = DEFAULT_CUSTOM_Y2,
    val strength: Float = DEFAULT_STRENGTH,
    val steps: Int = DEFAULT_STEPS,
) {
    /** Maps a progress value to the selected motion curve. */
    fun transform(fraction: Float): Float {
        val input = normalizedFraction(fraction)
        if (input == 0f) return 0f
        if (input == 1f) return 1f

        val strengthAmount = finiteOrDefault(strength, DEFAULT_STRENGTH).coerceIn(0f, MAX_STRENGTH)
        val stepCount = steps.coerceIn(MIN_STEPS, MAX_STEPS)
        val result = when (curve) {
            TimeCurve.REFERENCE,
            TimeCurve.LINEAR -> input

            TimeCurve.CSS_EASE -> cubicBezier(input, .25f, .10f, .25f, 1f)
            TimeCurve.CSS_EASE_IN -> cubicBezier(input, .42f, 0f, 1f, 1f)
            TimeCurve.CSS_EASE_OUT -> cubicBezier(input, 0f, 0f, .58f, 1f)
            TimeCurve.CSS_EASE_IN_OUT -> cubicBezier(input, .42f, 0f, .58f, 1f)

            TimeCurve.ANDROID_FAST_OUT_SLOW_IN -> cubicBezier(input, .40f, 0f, .20f, 1f)
            TimeCurve.ANDROID_FAST_OUT_LINEAR_IN -> cubicBezier(input, .40f, 0f, 1f, 1f)
            TimeCurve.ANDROID_LINEAR_OUT_SLOW_IN -> cubicBezier(input, 0f, 0f, .20f, 1f)

            TimeCurve.MATERIAL_STANDARD -> cubicBezier(input, .20f, 0f, 0f, 1f)
            TimeCurve.MATERIAL_STANDARD_ACCELERATE -> cubicBezier(input, .30f, 0f, 1f, 1f)
            TimeCurve.MATERIAL_STANDARD_DECELERATE -> cubicBezier(input, 0f, 0f, .30f, 1f)
            TimeCurve.MATERIAL_EMPHASIZED -> materialEmphasized(input)
            TimeCurve.MATERIAL_EMPHASIZED_ACCELERATE -> cubicBezier(input, .30f, 0f, .80f, .15f)
            TimeCurve.MATERIAL_EMPHASIZED_DECELERATE -> cubicBezier(input, .05f, .70f, .10f, 1f)

            TimeCurve.EASE_IN_SINE -> 1f - cos(input * HALF_PI)
            TimeCurve.EASE_OUT_SINE -> sin(input * HALF_PI)
            TimeCurve.EASE_IN_OUT_SINE -> -(cos(PI.toFloat() * input) - 1f) / 2f

            TimeCurve.EASE_IN_QUAD -> powerIn(input, 2)
            TimeCurve.EASE_OUT_QUAD -> powerOut(input, 2)
            TimeCurve.EASE_IN_OUT_QUAD -> powerInOut(input, 2)
            TimeCurve.EASE_IN_CUBIC -> powerIn(input, 3)
            TimeCurve.EASE_OUT_CUBIC -> powerOut(input, 3)
            TimeCurve.EASE_IN_OUT_CUBIC -> powerInOut(input, 3)
            TimeCurve.EASE_IN_QUART -> powerIn(input, 4)
            TimeCurve.EASE_OUT_QUART -> powerOut(input, 4)
            TimeCurve.EASE_IN_OUT_QUART -> powerInOut(input, 4)
            TimeCurve.EASE_IN_QUINT -> powerIn(input, 5)
            TimeCurve.EASE_OUT_QUINT -> powerOut(input, 5)
            TimeCurve.EASE_IN_OUT_QUINT -> powerInOut(input, 5)

            TimeCurve.EASE_IN_EXPO -> expoIn(input)
            TimeCurve.EASE_OUT_EXPO -> expoOut(input)
            TimeCurve.EASE_IN_OUT_EXPO -> expoInOut(input)
            TimeCurve.EASE_IN_CIRC -> 1f - sqrt(1f - input * input)
            TimeCurve.EASE_OUT_CIRC -> sqrt(1f - (input - 1f) * (input - 1f))
            TimeCurve.EASE_IN_OUT_CIRC -> circInOut(input)

            TimeCurve.BACK_IN -> blendWithLinear(backIn(input, BACK_OVERSHOOT), input, strengthAmount)
            TimeCurve.BACK_OUT -> blendWithLinear(backOut(input, BACK_OVERSHOOT), input, strengthAmount)
            TimeCurve.BACK_IN_OUT -> blendWithLinear(backInOut(input, BACK_OVERSHOOT), input, strengthAmount)
            TimeCurve.ELASTIC_IN -> blendWithLinear(elasticIn(input), input, strengthAmount)
            TimeCurve.ELASTIC_OUT -> blendWithLinear(elasticOut(input), input, strengthAmount)
            TimeCurve.ELASTIC_IN_OUT -> blendWithLinear(elasticInOut(input), input, strengthAmount)
            TimeCurve.BOUNCE_IN -> blendWithLinear(bounceIn(input), input, strengthAmount)
            TimeCurve.BOUNCE_OUT -> blendWithLinear(bounceOut(input), input, strengthAmount)
            TimeCurve.BOUNCE_IN_OUT -> blendWithLinear(bounceInOut(input), input, strengthAmount)

            TimeCurve.STEPS_START -> stepsAt(input, stepCount, startsEarly = true)
            TimeCurve.STEPS_END -> stepsAt(input, stepCount, startsEarly = false)

            TimeCurve.CUSTOM_CUBIC_BEZIER -> cubicBezier(
                input,
                finiteOrDefault(customX1, DEFAULT_CUSTOM_X1).coerceIn(0f, 1f),
                finiteOrDefault(customY1, DEFAULT_CUSTOM_Y1).coerceIn(MIN_CUSTOM_Y, MAX_CUSTOM_Y),
                finiteOrDefault(customX2, DEFAULT_CUSTOM_X2).coerceIn(0f, 1f),
                finiteOrDefault(customY2, DEFAULT_CUSTOM_Y2).coerceIn(MIN_CUSTOM_Y, MAX_CUSTOM_Y),
            )
        }

        return finiteOrDefault(result, input).coerceIn(0f, 1f)
    }

    /** Returns a copy with every persisted or user-entered value in range. */
    fun sanitized(): LaunchTiming = copy(
        durationMillis = durationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS),
        customX1 = finiteOrDefault(customX1, DEFAULT_CUSTOM_X1).coerceIn(0f, 1f),
        customY1 = finiteOrDefault(customY1, DEFAULT_CUSTOM_Y1).coerceIn(MIN_CUSTOM_Y, MAX_CUSTOM_Y),
        customX2 = finiteOrDefault(customX2, DEFAULT_CUSTOM_X2).coerceIn(0f, 1f),
        customY2 = finiteOrDefault(customY2, DEFAULT_CUSTOM_Y2).coerceIn(MIN_CUSTOM_Y, MAX_CUSTOM_Y),
        strength = finiteOrDefault(strength, DEFAULT_STRENGTH).coerceIn(0f, MAX_STRENGTH),
        steps = steps.coerceIn(MIN_STEPS, MAX_STEPS),
    )

    companion object {
        const val MIN_DURATION_MILLIS = 100
        const val MAX_DURATION_MILLIS = 2000
        const val DEFAULT_DURATION_MILLIS = 650
        const val DEFAULT_CUSTOM_X1 = .25f
        const val DEFAULT_CUSTOM_Y1 = .10f
        const val DEFAULT_CUSTOM_X2 = .25f
        const val DEFAULT_CUSTOM_Y2 = 1f
        const val DEFAULT_STRENGTH = 1f
        const val MAX_STRENGTH = 2f
        const val MIN_STEPS = 2
        const val MAX_STEPS = 24
        const val DEFAULT_STEPS = 6
        const val MIN_CUSTOM_Y = -2f
        const val MAX_CUSTOM_Y = 2f

        private const val BACK_OVERSHOOT = 1.70158f
        private val HALF_PI = (PI / 2.0).toFloat()
    }
}

private fun normalizedFraction(value: Float): Float = when {
    value.isNaN() -> 0f
    value == Float.POSITIVE_INFINITY -> 1f
    value == Float.NEGATIVE_INFINITY -> 0f
    else -> value.coerceIn(0f, 1f)
}

private fun finiteOrDefault(value: Float, fallback: Float): Float =
    if (value.isFinite()) value else fallback

private fun cubicBezier(time: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    var low = 0f
    var high = 1f
    repeat(20) {
        val middle = (low + high) / 2f
        val x = cubicCoordinate(middle, x1, x2)
        if (x < time) {
            low = middle
        } else {
            high = middle
        }
    }
    val parameter = (low + high) / 2f
    return cubicCoordinate(parameter, y1, y2)
}

private fun cubicCoordinate(parameter: Float, firstControl: Float, secondControl: Float): Float {
    val inverse = 1f - parameter
    return 3f * inverse * inverse * parameter * firstControl +
        3f * inverse * parameter * parameter * secondControl +
        parameter * parameter * parameter
}

private fun powerIn(value: Float, exponent: Int): Float = power(value, exponent)

private fun powerOut(value: Float, exponent: Int): Float = 1f - power(1f - value, exponent)

private fun powerInOut(value: Float, exponent: Int): Float = if (value < .5f) {
    power(value * 2f, exponent) / 2f
} else {
    1f - power((1f - value) * 2f, exponent) / 2f
}

private fun power(value: Float, exponent: Int): Float = when (exponent) {
    2 -> value * value
    3 -> value * value * value
    4 -> value * value * value * value
    else -> value * value * value * value * value
}

private fun expoIn(value: Float): Float = 2.0.pow((10f * (value - 1f)).toDouble()).toFloat()

private fun expoOut(value: Float): Float = 1f - 2.0.pow((-10f * value).toDouble()).toFloat()

private fun expoInOut(value: Float): Float = if (value < .5f) {
    2.0.pow((20f * value - 10f).toDouble()).toFloat() / 2f
} else {
    (2f - 2.0.pow((-20f * value + 10f).toDouble()).toFloat()) / 2f
}

private fun circInOut(value: Float): Float = if (value < .5f) {
    (1f - sqrt(1f - (2f * value) * (2f * value))) / 2f
} else {
    (sqrt(1f - (-2f * value + 2f) * (-2f * value + 2f)) + 1f) / 2f
}

/** The two cubic segments in Material 1.13's m3_sys_motion_easing_emphasized_path_data. */
private fun materialEmphasized(value: Float): Float {
    val splitX = .166666f
    val splitY = .4f
    return if (value < splitX) {
        splitY * cubicBezier(value / splitX, .05f / splitX, 0f,
            .133333f / splitX, .06f / splitY)
    } else {
        splitY + (1f - splitY) * cubicBezier((value - splitX) / (1f - splitX),
            (.208333f - splitX) / (1f - splitX), (.82f - splitY) / (1f - splitY),
            (.25f - splitX) / (1f - splitX), 1f)
    }
}

private fun backIn(value: Float, overshoot: Float): Float =
    value * value * ((overshoot + 1f) * value - overshoot)

private fun backOut(value: Float, overshoot: Float): Float {
    val shifted = value - 1f
    return 1f + shifted * shifted * ((overshoot + 1f) * shifted + overshoot)
}

private fun backInOut(value: Float, overshoot: Float): Float {
    val scaled = value * 2f
    val adjusted = overshoot * 1.525f
    return if (scaled < 1f) {
        scaled * scaled * ((adjusted + 1f) * scaled - adjusted) / 2f
    } else {
        val shifted = scaled - 2f
        (shifted * shifted * ((adjusted + 1f) * shifted + adjusted) + 2f) / 2f
    }
}

private fun elasticIn(value: Float): Float {
    val c4 = (2.0 * PI / 3.0).toFloat()
    return -2.0.pow((10f * value - 10f).toDouble()).toFloat() *
        sin((value * 10f - 10.75f) * c4)
}

private fun elasticOut(value: Float): Float {
    val c4 = (2.0 * PI / 3.0).toFloat()
    return 2.0.pow((-10f * value).toDouble()).toFloat() *
        sin((value * 10f - .75f) * c4) + 1f
}

private fun elasticInOut(value: Float): Float {
    val c5 = (2.0 * PI / 4.5).toFloat()
    return if (value < .5f) {
        -(2.0.pow((20f * value - 10f).toDouble()).toFloat() *
            sin((20f * value - 11.125f) * c5)) / 2f
    } else {
        2.0.pow((-20f * value + 10f).toDouble()).toFloat() *
            sin((20f * value - 11.125f) * c5) / 2f + 1f
    }
}

private fun bounceIn(value: Float): Float = 1f - bounceOut(1f - value)

private fun bounceOut(value: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        value < 1f / d1 -> n1 * value * value
        value < 2f / d1 -> {
            val shifted = value - 1.5f / d1
            n1 * shifted * shifted + .75f
        }
        value < 2.5f / d1 -> {
            val shifted = value - 2.25f / d1
            n1 * shifted * shifted + .9375f
        }
        else -> {
            val shifted = value - 2.625f / d1
            n1 * shifted * shifted + .984375f
        }
    }
}

private fun bounceInOut(value: Float): Float = if (value < .5f) {
    (1f - bounceOut(1f - 2f * value)) / 2f
} else {
    (1f + bounceOut(2f * value - 1f)) / 2f
}

private fun blendWithLinear(curved: Float, linear: Float, strength: Float): Float =
    linear + (curved - linear) * strength

private fun stepsAt(value: Float, count: Int, startsEarly: Boolean): Float {
    val scaled = value * count
    return if (startsEarly) {
        ceil(scaled.toDouble()).toFloat() / count
    } else {
        floor(scaled.toDouble()).toFloat() / count
    }
}
