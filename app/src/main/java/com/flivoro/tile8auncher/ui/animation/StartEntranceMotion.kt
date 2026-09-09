package com.flivoro.tile8auncher.ui.animation

/** Fitted to the Start reveal at 56.033–56.633s in the supplied 30fps recording. */
internal object StartEntranceMotion {
    const val DurationMillis = 680
    private val time = floatArrayOf(0f, 34f, 67f, 134f, 167f, 200f, 234f, 267f, 334f, 400f, 500f, 600f)
    private val travel = MotionCurve(time, floatArrayOf(.32f, .18f, .10f, .060f, .043f, .031f, .023f, .017f, .014f, .006f, .003f, 0f))
    private val growth = MotionCurve(time, floatArrayOf(.78f, .86f, .92f, .95f, .972f, .98f, .992f, .994f, .997f, 1f, 1f, 1f))
    private val opacity = MotionCurve(time, floatArrayOf(0f, .04f, .28f, .72f, .90f, 1f, 1f, 1f, 1f, 1f, 1f, 1f))

    fun frame(progress: Float, column: Int): EntranceFrame {
        val milliseconds = (progress.coerceIn(0f, 1f) * DurationMillis - column.coerceIn(0, 3) * 24f).coerceAtLeast(0f)
        return EntranceFrame(travel.at(milliseconds), growth.at(milliseconds), opacity.at(milliseconds))
    }
}

internal data class EntranceFrame(val offsetFraction: Float, val scale: Float, val alpha: Float)
