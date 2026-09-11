package com.flivoro.tile8auncher.ui.animation

import kotlin.math.min
import kotlin.math.pow

enum class StartEntranceKind { RETURN, STARTUP }

/**
 * Narrow runtime override used only for a real screen-off -> unlock cycle.
 *
 * MainActivity keeps requesting STARTUP for its existing startup/unlock gate so none of its
 * lifecycle, rendering or pre-hide behavior changes. Tile8Application arms this flag only when the
 * display actually goes to sleep. While armed, STARTUP resolves to the short RETURN motion.
 * Cold launcher creation and configuration/orientation recreation remain genuine STARTUP motion.
 */
internal object UnlockEntranceMotionOverride {
    @Volatile
    private var useReturnMotionForStartup = false

    fun arm() {
        useReturnMotionForStartup = true
    }

    fun cancel() {
        useReturnMotionForStartup = false
    }

    fun resolve(kind: StartEntranceKind): StartEntranceKind =
        if (useReturnMotionForStartup && kind == StartEntranceKind.STARTUP) {
            StartEntranceKind.RETURN
        } else {
            kind
        }
}

/**
 * Windows 8.1 Start entrance fit.
 *
 * STARTUP remains fitted to the repository's separate 1920x1080/60-fps comparison capture.
 * RETURN is fitted independently to every encoded frame of test.mp4 around 56.033-56.633 s.
 * test.mp4 is 30 fps, so actual observations are ~33.333 ms apart; values between those samples
 * are continuous interpolation rather than invented 10 ms source frames.
 *
 * STARTUP remains viewport-normalized. RETURN is normalized to the measured 251 px Start band,
 * because the desktop reference moves the tile group relative to its own width, not relative to the
 * full 1920 px monitor. That preserves the same perceived sweep/zoom when a phone portrait viewport
 * contains roughly one Start band, while landscape keeps the same band-relative movement.
 * Real phone unlock deliberately reuses RETURN; first launch/configuration recreation remain STARTUP.
 */
internal object StartEntranceMotion {
    const val DurationMillis = 2_800
    const val BandStaggerMillis = 120f

    private const val ReturnDurationMillis = 600
    private const val ReturnBandStaggerMillis = 28f
    private const val ReturnMaximumBandDelayMillis = 84f
    private const val ReturnBandTravelRatio = .85f

    fun durationMillis(kind: StartEntranceKind): Int =
        if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.STARTUP) {
            DurationMillis
        } else {
            ReturnDurationMillis
        }


    /**
     * Converts the fitted offset into pixels using the same geometry the reference actually moves.
     * RETURN is relative to one Start band; STARTUP intentionally keeps its existing viewport basis.
     */
    fun translationX(
        frame: EntranceFrame,
        kind: StartEntranceKind,
        viewportWidthPx: Float,
        bandWidthPx: Float,
    ): Float {
        val safeViewport = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeBand = bandWidthPx.takeIf { it.isFinite() && it > 0f } ?: safeViewport
        val basis = if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.RETURN) {
            safeBand
        } else {
            safeViewport
        }
        return (frame.offsetFraction * basis).takeIf(Float::isFinite) ?: 0f
    }

    /*
     * Short Start-return reference, measured from every encoded frame of test.mp4 after cropping
     * the desktop sequence to a 350x630 portrait window around the first Start band.
     *
     * Blank background is still present at 56.033 s. At 56.067 s the Mail band is first visible at
     * 193/251 of final size, ~6.5% opacity, and its center is 75 px to the right of the settled
     * center. It then follows the recorded 53.5, 53.5, 34.5, 26, 19, 15, 10, 10, 8.5, 4.5,
     * 2.5, 2.5, 2.5, 1.5, .5, 0 px deceleration tail through 56.600 s.
     *
     * Those displacements are divided by the measured final 251 px band width, NOT the 1920 px
     * desktop width. StartScreen multiplies this ratio by its real band width so portrait and
     * landscape preserve the reference's group-relative geometry.
     */
    private const val ReturnReferenceBandWidthPx = 251f
    private val returnTime = floatArrayOf(
        0f, 33.333f, 66.667f, 100f, 133.333f, 166.667f, 200f, 233.333f, 266.667f,
        300f, 333.333f, 366.667f, 400f, 433.333f, 466.667f, 500f, 533.333f, 566.667f, 600f,
    )
    private val returnTravel = MotionCurve(
        returnTime,
        floatArrayOf(
            // t=0 is invisible; 94.656 px extrapolates the measured first-frame deceleration.
            94.656f / ReturnReferenceBandWidthPx,
            75f / ReturnReferenceBandWidthPx,
            53.5f / ReturnReferenceBandWidthPx,
            53.5f / ReturnReferenceBandWidthPx,
            34.5f / ReturnReferenceBandWidthPx,
            26f / ReturnReferenceBandWidthPx,
            19f / ReturnReferenceBandWidthPx,
            15f / ReturnReferenceBandWidthPx,
            10f / ReturnReferenceBandWidthPx,
            10f / ReturnReferenceBandWidthPx,
            8.5f / ReturnReferenceBandWidthPx,
            4.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            1.5f / ReturnReferenceBandWidthPx,
            .5f / ReturnReferenceBandWidthPx,
            0f,
            0f,
        ),
    )
    private val returnGrowth = MotionCurve(
        returnTime,
        floatArrayOf(
            .680000f,
            193f / ReturnReferenceBandWidthPx,
            226f / ReturnReferenceBandWidthPx,
            226f / ReturnReferenceBandWidthPx,
            238f / ReturnReferenceBandWidthPx,
            243f / ReturnReferenceBandWidthPx,
            247f / ReturnReferenceBandWidthPx,
            249f / ReturnReferenceBandWidthPx,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )
    private val returnOpacity = MotionCurve(
        returnTime,
        floatArrayOf(
            0f,
            .064516f,
            .411290f,
            .411290f,
            .755245f,
            .993007f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )

    // Header is deliberately slower than tile opacity in the source recording.
    private val returnHeaderTime = floatArrayOf(
        0f, 33.333f, 66.667f, 100f, 133.333f, 166.667f, 200f, 233.333f,
        266.667f, 300f, 333.333f, 366.667f, 600f,
    )
    private val returnHeaderOpacity = MotionCurve(
        returnHeaderTime,
        floatArrayOf(
            0f, 0f, .068f, .071f, .258f, .390f, .581f, .711f,
            .906f, .906f, .999f, 1f, 1f,
        ),
    )

    private const val TileMotionDurationMillis = 2_500f
    private const val MaximumBandDelayMillis = 240f
    private const val HeaderFadeStartMillis = 800f
    private const val HeaderFadeEndMillis = 1_400f

    // Long STARTUP reference. Intentionally unchanged by the RETURN fidelity pass.
    private val time = floatArrayOf(0.000000f, 16.667000f, 33.333000f, 50.000000f, 83.333000f, 100.000000f, 133.333000f, 166.667000f, 200.000000f, 233.333000f, 266.667000f, 300.000000f, 333.333000f, 400.000000f, 500.000000f, 600.000000f, 700.000000f, 800.000000f, 900.000000f, 1000.000000f, 1100.000000f, 1200.000000f, 1300.000000f, 1500.000000f, 1800.000000f, 2000.000000f, 2200.000000f, 2400.000000f, 2500.000000f)
    private val travel = MotionCurve(time, floatArrayOf(0.380000f, 0.370000f, 0.339583f, 0.329167f, 0.303125f, 0.292708f, 0.270833f, 0.249479f, 0.230208f, 0.212500f, 0.196875f, 0.175000f, 0.162500f, 0.139583f, 0.111979f, 0.087500f, 0.070833f, 0.056250f, 0.043750f, 0.035417f, 0.027083f, 0.020833f, 0.016667f, 0.010417f, 0.004167f, 0.002083f, 0.000521f, 0.000000f, 0.000000f))
    private val growth = MotionCurve(time, floatArrayOf(0.600000f, 0.610000f, 0.629032f, 0.677419f, 0.766129f, 0.798387f, 0.854839f, 0.883065f, 0.911290f, 0.935484f, 0.943548f, 0.967742f, 0.967742f, 0.983871f, 0.995968f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f))
    private val opacity = MotionCurve(time, floatArrayOf(0.000000f, 0.050000f, 0.596600f, 0.753100f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f, 1.000000f))

    private val settled = EntranceFrame(offsetFraction = 0f, scale = 1f, alpha = 1f)

    fun frame(
        progress: Float,
        viewportBandPosition: Float,
        kind: StartEntranceKind = StartEntranceKind.RETURN,
    ): EntranceFrame {
        val safeProgress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        if (safeProgress >= 1f) return settled

        val safePosition = viewportBandPosition
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
        val resolvedKind = UnlockEntranceMotionOverride.resolve(kind)
        if (resolvedKind == StartEntranceKind.RETURN) {
            val absoluteMilliseconds = (safeProgress * ReturnDurationMillis)
                .coerceIn(0f, ReturnDurationMillis.toFloat())

            // The recording shows ~28 ms of scale/opacity stagger per band. Horizontal movement is
            // not time-delayed: later bands move a slightly smaller distance and all converge on the
            // same deceleration tail. Delaying translation was the primary source of the exaggerated
            // spread in the previous implementation.
            val visualDelay = (safePosition * ReturnBandStaggerMillis)
                .coerceIn(0f, ReturnMaximumBandDelayMillis)
            val visualMilliseconds = (absoluteMilliseconds - visualDelay)
                .coerceIn(0f, ReturnDurationMillis.toFloat())
            val travelRatio = ReturnBandTravelRatio.pow(safePosition).coerceIn(.50f, 1f)

            return EntranceFrame(
                offsetFraction = (returnTravel.at(absoluteMilliseconds) * travelRatio)
                    .coerceIn(0f, 1f),
                scale = returnGrowth.at(visualMilliseconds).coerceIn(0f, 1f),
                alpha = returnOpacity.at(visualMilliseconds).coerceIn(0f, 1f),
            )
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
     * Decorative wallpaper anchor; base color itself remains stationary. RETURN uses the same
     * band-relative fit as the tiles. The caller converts it through backgroundEntranceOffsetPx().
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
        val resolvedKind = UnlockEntranceMotionOverride.resolve(kind)
        return if (resolvedKind == StartEntranceKind.RETURN) {
            val milliseconds = (safeProgress * ReturnDurationMillis)
                .coerceIn(0f, ReturnDurationMillis.toFloat())
            returnTravel.at(milliseconds).coerceIn(0f, 1f)
        } else {
            val milliseconds = (safeProgress * DurationMillis).coerceIn(0f, TileMotionDurationMillis)
            travel.at(milliseconds).coerceIn(0f, 1f)
        }
    }

    /**
     * Synthetic scroll offset used only for entrance parallax. For RETURN the phone's short side is
     * the stable physical motion basis, so rotating the device does not multiply the wallpaper
     * sweep by the landscape width. STARTUP keeps its existing full-width behavior unchanged.
     */
    fun backgroundEntranceOffsetPx(
        progress: Float,
        kind: StartEntranceKind,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: width
        val basis = if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.RETURN) {
            min(width, height)
        } else {
            width
        }
        return (backgroundTravelFraction(progress, kind) * basis)
            .takeIf(Float::isFinite) ?: 0f
    }

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

    fun headerAlpha(progress: Float, kind: StartEntranceKind = StartEntranceKind.RETURN): Float {
        val safeProgress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        val resolvedKind = UnlockEntranceMotionOverride.resolve(kind)
        if (resolvedKind == StartEntranceKind.RETURN) {
            return returnHeaderOpacity.at(safeProgress * ReturnDurationMillis).coerceIn(0f, 1f)
        }
        val milliseconds = safeProgress * DurationMillis
        if (milliseconds <= HeaderFadeStartMillis) return 0f
        if (milliseconds >= HeaderFadeEndMillis) return 1f

        val fraction = (milliseconds - HeaderFadeStartMillis) /
            (HeaderFadeEndMillis - HeaderFadeStartMillis)
        return (fraction * fraction * (3f - 2f * fraction)).coerceIn(0f, 1f)
    }
}

internal data class EntranceFrame(
    val offsetFraction: Float,
    val scale: Float,
    val alpha: Float,
)
