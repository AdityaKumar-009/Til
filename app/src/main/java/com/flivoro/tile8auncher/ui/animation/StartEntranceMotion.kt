package com.flivoro.tile8auncher.ui.animation

enum class StartEntranceKind { RETURN, STARTUP }

/**
 * Windows 8.1 Start entrance fit.
 *
 * Startup is fitted to the repository's 1920x1080/60-fps Windows 8.1 comparison capture. The
 * measured frames are interpolated continuously; a nominal 10 ms sampling grid therefore follows
 * the same fitted path without inventing unrecorded source frames. Motion is stored as viewport
 * fractions rather than pixels so portrait and landscape preserve the same geometry.
 *
 * Home/Back return keeps the separate short fit from test.mp4.
 */
internal object StartEntranceMotion {
    const val DurationMillis = 2_800
    const val BandStaggerMillis = 120f

    fun durationMillis(kind: StartEntranceKind): Int =
        if (kind == StartEntranceKind.STARTUP) DurationMillis else 680

    private val returnTime = floatArrayOf(0f, 34f, 67f, 134f, 167f, 200f, 234f, 267f, 334f, 400f, 500f, 600f)
    private val returnTravel = MotionCurve(returnTime,
        floatArrayOf(.32f, .18f, .10f, .060f, .043f, .031f, .023f, .017f, .014f, .006f, .003f, 0f))
    private val returnGrowth = MotionCurve(returnTime,
        floatArrayOf(.78f, .86f, .92f, .95f, .972f, .98f, .992f, .994f, .997f, 1f, 1f, 1f))
    private val returnOpacity = MotionCurve(returnTime,
        floatArrayOf(0f, .04f, .28f, .72f, .90f, 1f, 1f, 1f, 1f, 1f, 1f, 1f))

    private const val TileMotionDurationMillis = 2_500f
    private const val MaximumBandDelayMillis = 240f
    private const val HeaderFadeStartMillis = 800f
    private const val HeaderFadeEndMillis = 1_400f

    // Center-displacement samples from the Windows 8.1 reference. The later points preserve the
    // characteristic long deceleration tail; scale and opacity finish much earlier than travel.
    private val time = floatArrayOf(0.000000f, 16.667000f, 33.333000f, 50.000000f, 83.333000f, 100.000000f, 133.333000f, 166.667000f, 200.000000f, 233.333000f, 266.667000f, 300.000000f, 333.333000f, 400.000000f, 500.000000f, 600.000000f, 700.000000f, 800.000000f, 900.000000f, 1000.000000f, 1100.000000f, 1200.000000f, 1300.000000f, 1500.000000f, 1800.000000f, 2000.000000f, 2200.000000f, 2400.000000f, 2500.000000f)
    private val travel = MotionCurve(time, floatArrayOf(0.380000f, 0.370000f, 0.339583f, 0.329167f, 0.303125f, 0.292708f, 0.270833f, 0.249479f, 0.230208f, 0.212500f, 0.196875f, 0.175000f, 0.162500f, 0.139583f, 0.111979f, 0.087500f, 0.070833f, 0.056250f, 0.043750f, 0.035417f, 0.027083f, 0.020833f, 0.016667f, 0.010417f, 0.004167f, 0.002083f, 0.000521f, 0.000000f, 0.000000f))
    private val growth = MotionCurve(time, floatArrayOf(0.600000f, 0.610000f, 0.629032f, 0.677419f, 0.766129f, 0.798387f, 0.854839f, 0.883065f, 0.911290f, 0.935484f, 0.943548f, 0.967742f, 0.967742f, 0.983871f, 0.995968f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f))
    private val opacity = MotionCurve(time, floatArrayOf(0.000000f, 0.050000f, 0.596600f, 0.753100f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f))

    private val settled = EntranceFrame(offsetFraction = 0f, scale = 1f, alpha = 1f)

    /**
     * Returns a finite frame for a frozen viewport-relative band position.
     * Position zero is the first band at the captured viewport start.
     */
    fun frame(progress: Float, viewportBandPosition: Float, kind: StartEntranceKind = StartEntranceKind.RETURN): EntranceFrame {
        val safeProgress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        if (safeProgress >= 1f) return settled

        val safePosition = viewportBandPosition
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
        if (kind == StartEntranceKind.RETURN) {
            val milliseconds = (safeProgress * durationMillis(kind) -
                (safePosition * 36f).coerceIn(0f, 72f)).coerceIn(0f, 600f)
            return EntranceFrame(returnTravel.at(milliseconds), returnGrowth.at(milliseconds),
                returnOpacity.at(milliseconds))
        }
        val delay = (safePosition * BandStaggerMillis)
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, MaximumBandDelayMillis)
            ?: MaximumBandDelayMillis
        val milliseconds = (safeProgress * DurationMillis - delay)
            .coerceIn(0f, TileMotionDurationMillis)

        return EntranceFrame(
            offsetFraction = travel.at(milliseconds).coerceIn(0f, 1f),
            scale = growth.at(milliseconds).coerceIn(0f, 1f),
            alpha = opacity.at(milliseconds).coerceIn(0f, 1f),
        )
    }

    /**
     * Anchor travel used by the decorative wallpaper layers during a Start entrance.
     *
     * The base background color never moves. Instead, callers feed this synthetic foreground
     * travel into the existing wallpaper-parallax depth rates (2.5-11%, 8% for bitmap artwork).
     * The art therefore starts only a few percent of a viewport to the right and settles with the
     * tile group, reproducing the Windows sense that the wallpaper sits behind the tiles rather
     * than being painted onto them.
     */
    fun backgroundTravelFraction(
        progress: Float,
        kind: StartEntranceKind = StartEntranceKind.RETURN,
    ): Float {
        val safeProgress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        if (safeProgress >= 1f) return 0f
        return if (kind == StartEntranceKind.RETURN) {
            val milliseconds = (safeProgress * durationMillis(kind)).coerceIn(0f, 600f)
            returnTravel.at(milliseconds).coerceIn(0f, 1f)
        } else {
            val milliseconds = (safeProgress * DurationMillis).coerceIn(0f, TileMotionDurationMillis)
            travel.at(milliseconds).coerceIn(0f, 1f)
        }
    }

    /**
     * Converts a captured LazyRow start index and pixel offset into a stable
     * band position. It is evaluated only when an entrance starts, never from
     * the scrolling composition path.
     */
    fun viewportBandPosition(
        bandIndex: Int,
        snapshotStartBand: Int,
        snapshotStartOffsetPx: Int,
        bandExtentPx: Float,
    ): Float {
        val safeExtent = bandExtentPx
            .takeIf { it.isFinite() && it > 0f }
            ?: 1f
        val offsetFraction = (snapshotStartOffsetPx.toFloat() / safeExtent)
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        return (bandIndex.toFloat() - snapshotStartBand.toFloat() - offsetFraction)
            .takeIf(Float::isFinite)
            ?: 0f
    }

    /** Header fades in after the tile group has already appeared. */
    fun headerAlpha(progress: Float, kind: StartEntranceKind = StartEntranceKind.RETURN): Float {
        val safeProgress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        if (kind == StartEntranceKind.RETURN) return returnOpacity.at(safeProgress * durationMillis(kind))
        val milliseconds = safeProgress * DurationMillis
        if (milliseconds <= HeaderFadeStartMillis) return 0f
        if (milliseconds >= HeaderFadeEndMillis) return 1f

        val fraction = (milliseconds - HeaderFadeStartMillis) /
            (HeaderFadeEndMillis - HeaderFadeStartMillis)
        // Smoothstep keeps the measured fade quiet at both ends.
        return (fraction * fraction * (3f - 2f * fraction)).coerceIn(0f, 1f)
    }
}

internal data class EntranceFrame(
    val offsetFraction: Float,
    val scale: Float,
    val alpha: Float,
)
