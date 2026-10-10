package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Transform state for one tile in the Start surface's shared animation clock. */
internal data class PhoneStartMotionFrame(
    val alpha: Float,
    val rotationY: Float,
    val translationXPx: Float,
    val translationYPx: Float,
    val translationZPx: Float,
    val scale: Float,
    val pivotX: Float,
)

/** Transform state for the icon/text layer inside a classic Start tile. */
internal data class PhoneStartInnerFrame(
    val rotationY: Float,
    val translationXPx: Float,
)

/** The second slide page's entry while DiscoLauncher resumes on Start. */
internal data class PhoneAppsPageEntryFrame(
    val alpha: Float,
    val rotationY: Float,
    val translationXPx: Float,
    val pivotX: Float,
)

/** A point projected through DiscoLauncher’s CSS perspective for the second slide page. */
internal data class PhoneAppsPageProjectedPoint(
    val xCssPx: Float,
    val yCssPx: Float,
)

/**
 * Windows Phone 8.1 Start tile choreography follows DiscoLauncher’s
 * `src/styles/appTransition.scss` and `src/scripts/appTransition.js` timing and
 * transform sequence. CSS-pixel inputs are supplied in dp and converted to
 * physical pixels at the Compose graphics layer. W10M stays on MobileStartMotion.
 */
internal object PhoneStartChoreography {
    private const val CLASSIC_EXIT_MS = 175f
    private const val CLASSIC_SELECTED_EXIT_MS = 300f
    private const val CLASSIC_ENTRY_MS = 500f
    private const val CLASSIC_INNER_ENTRY_MS = 350f
    private const val CLASSIC_STAGGER_MS = 200f
    private const val CLASSIC_SELECTED_DELAY_MS = 200f
    private const val CLASSIC_APPS_SELECTED_DELAY_MS = 300f
    private const val CLASSIC_APPS_EXIT_MS = 200f
    private const val CLASSIC_APPS_PAGE_ENTRY_DELAY_MS = 100f
    private const val CLASSIC_APPS_PAGE_ENTRY_MS = 750f
    private const val FLOW_PERSPECTIVE_CSS_PX = 1000f
    const val CLASSIC_TILE_PERSPECTIVE_CSS_PX = 2000f
    const val CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX = 1000f
    private const val CLASSIC_EXIT_HOLD_MS = 200f
    private const val REFERENCE_VIEWPORT_HEIGHT = 850f
    private const val MOBILE_EXIT_TILE_MS = MobileStartMotion.EXIT_TILE_MS
    private const val MOBILE_ENTRY_TILE_MS = MobileStartMotion.ENTRY_TILE_MS

    /** DiscoLauncher’s `baseScale = innerHeight / 850 / 2 + .5`. */
    fun appTransitionScale(viewportHeightCssPx: Float): Float =
        (viewportHeightCssPx / REFERENCE_VIEWPORT_HEIGHT / 2f + .5f).coerceAtLeast(.5f)

    /** Projects a CSS Z offset into Compose's 2D layer scale at the reference camera. */
    fun projectedDepthScale(depthCssPx: Float, cameraDistancePx: Float, density: Float): Float {
        val denominator = (cameraDistancePx - depthCssPx * density)
            .coerceAtLeast(cameraDistancePx * .1f)
        return (cameraDistancePx / denominator).coerceIn(.25f, 4f)
    }

    /** DiscoLauncher reverses visible tiles and normalizes their indices to 0..1. */
    fun visibleTileAnimationIndex(reverseRank: Int, visibleCount: Int): Float {
        if (visibleCount <= 0) return 0f
        // DiscoLauncher appends its page icon banner before reversing the visible
        // nodes. The banner receives index 0; tiles therefore occupy 1/N..1.
        return (((reverseRank.coerceIn(0, visibleCount - 1) + 1).toFloat() / visibleCount) * 100f)
            .roundToInt() / 100f
    }

    /**
     * DiscoLauncher forward resume also turns the second (All Apps) page behind
     * Start: 100 ms delay, then 750 ms from 45°/transparent to face-on/opaque.
     * The CSS declaration spells the delay as `var(.1s)`, which is invalid CSS;
     * this ports the evident intended 0.1s value from the same declaration.
     */
    fun sampleAppsPageEntry(
        elapsedMillis: Int,
        viewportWidthCssPx: Float,
    ): PhoneAppsPageEntryFrame {
        val raw = ((elapsedMillis - CLASSIC_APPS_PAGE_ENTRY_DELAY_MS) /
            CLASSIC_APPS_PAGE_ENTRY_MS).coerceIn(0f, 1f)
        val progress = cubicBezier(raw, .05f, 1f, .1f, 1f)
        return PhoneAppsPageEntryFrame(
            alpha = progress,
            rotationY = 45f * (1f - progress),
            translationXPx = viewportWidthCssPx,
            pivotX = -1f,
        )
    }

    /**
     * Projects the All Apps page as DiscoLauncher lays it out: the page starts one
     * viewport to the right, and both `perspective-origin` and `transform-origin`
     * are at the Start page's left edge (`-100% 50%` on the Apps page).
     */
    fun projectAppsPagePoint(
        pageLocalXPx: Float,
        pageLocalYPx: Float,
        viewportWidthCssPx: Float,
        viewportHeightCssPx: Float,
        rotationYDegrees: Float,
        cameraDistanceCssPx: Float = CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX,
    ): PhoneAppsPageProjectedPoint {
        val angle = rotationYDegrees * DEG_TO_RAD
        val worldX = viewportWidthCssPx + pageLocalXPx
        val rotatedX = worldX * cos(angle)
        val rotatedZ = -worldX * sin(angle)
        val perspectiveScale = cameraDistanceCssPx /
            (cameraDistanceCssPx - rotatedZ).coerceAtLeast(cameraDistanceCssPx * .1f)
        return PhoneAppsPageProjectedPoint(
            xCssPx = rotatedX * perspectiveScale,
            yCssPx = viewportHeightCssPx * .5f +
                (pageLocalYPx - viewportHeightCssPx * .5f) * perspectiveScale,
        )
    }

    private fun delayMillis(index: Float, viewportHeightCssPx: Float): Int =
        (index.coerceIn(0f, 1f) * CLASSIC_STAGGER_MS * appTransitionScale(viewportHeightCssPx))
            .roundToInt()

    fun selectedExitDelayMillis(viewportHeightCssPx: Float): Int =
        (CLASSIC_SELECTED_DELAY_MS * appTransitionScale(viewportHeightCssPx)).roundToInt()

    /** `launchHide()` hides Start 200ms after the selected tile's 300ms exit ends. */
    fun totalMillis(
        mode: LauncherUiMode,
        exiting: Boolean,
        viewportHeightCssPx: Float = REFERENCE_VIEWPORT_HEIGHT,
    ): Int = when (mode) {
        LauncherUiMode.PHONE_8 -> if (exiting) {
            (selectedExitDelayMillis(viewportHeightCssPx) + CLASSIC_SELECTED_EXIT_MS +
                CLASSIC_EXIT_HOLD_MS).roundToInt()
        } else {
            maxOf(
                (CLASSIC_ENTRY_MS + CLASSIC_STAGGER_MS * appTransitionScale(viewportHeightCssPx))
                    .roundToInt(),
                (CLASSIC_APPS_PAGE_ENTRY_DELAY_MS + CLASSIC_APPS_PAGE_ENTRY_MS).roundToInt(),
            )
        }
        LauncherUiMode.MOBILE_10 -> if (exiting) {
            MobileStartMotion.EXIT_TOTAL_MS
        } else {
            MobileStartMotion.ENTRY_TOTAL_MS
        }
        else -> error("Start choreography requires a phone mode")
    }

    fun delayMillis(
        mode: LauncherUiMode,
        exiting: Boolean,
        animationIndex: Float,
        viewportHeightCssPx: Float = REFERENCE_VIEWPORT_HEIGHT,
        selected: Boolean = false,
    ): Int = when (mode) {
        LauncherUiMode.PHONE_8 -> if (exiting && selected) {
            selectedExitDelayMillis(viewportHeightCssPx)
        } else {
            delayMillis(animationIndex, viewportHeightCssPx)
        }
        LauncherUiMode.MOBILE_10 -> 0
        else -> error("Start choreography requires a phone mode")
    }

    fun sample(
        mode: LauncherUiMode,
        exiting: Boolean,
        elapsedMillis: Int,
        mobileRowFraction: Float,
        animationIndex: Float,
        viewportHeightCssPx: Float,
        viewportWidthCssPx: Float,
        tileLeftCssPx: Float,
        selected: Boolean = false,
        resumeUsesBackMotion: Boolean = false,
        tileWidthCssPx: Float = 1f,
    ): PhoneStartMotionFrame {
        if (mode == LauncherUiMode.MOBILE_10) {
            val mobile = MobileStartMotion.sample(
                exiting, elapsedMillis, mobileRowFraction,
                0f, 0f, 0f, 0f, selected,
            )
            return PhoneStartMotionFrame(
                alpha = mobile.alpha,
                rotationY = mobile.rotationY,
                translationXPx = mobile.translationXPx,
                translationYPx = mobile.translationYPx,
                translationZPx = 0f,
                scale = mobile.scale,
                pivotX = mobile.pivotX,
            )
        }
        check(mode == LauncherUiMode.PHONE_8)
        val delay = delayMillis(mode, exiting, animationIndex, viewportHeightCssPx, selected)
        val duration = when {
            exiting && selected -> CLASSIC_SELECTED_EXIT_MS
            exiting -> CLASSIC_EXIT_MS
            else -> CLASSIC_ENTRY_MS
        }
        val raw = ((elapsedMillis - delay) / duration).coerceIn(0f, 1f)

        if (exiting) {
            // appTransition.scss: translateX(-25vw), then the -30°/-10° compound
            // turn. The returned X/Z offsets and left-edge pivot preserve its 3D matrix.
            val progress = cubicBezier(raw, .75f, 0f, 1f, 0f)
            val angle = 30f * progress * DEG_TO_RAD
            val offset = tileLeftCssPx * progress
            return PhoneStartMotionFrame(
                alpha = if (raw >= 1f) 0f else 1f,
                rotationY = -40f * progress,
                translationXPx = -viewportWidthCssPx * .25f * progress +
                    offset * (cos(angle) - 1f),
                translationYPx = 0f,
                translationZPx = offset * sin(angle),
                scale = 1f,
                pivotX = 0f,
            )
        }

        if (resumeUsesBackMotion) {
            // DiscoLauncher appTransition-back: interpolate its 0% matrix to the
            // face-on matrix. The non-zero origin is -offsetLeft, so the pivot
            // lands on the left edge of the page rather than the tile itself.
            val progress = cubicBezier(raw, .05f, 1f, .1f, 1f)
            val remaining = 1f - progress
            val angle40 = 40f * DEG_TO_RAD
            val cos30 = cos(30f * DEG_TO_RAD)
            val cos40 = cos(angle40)
            val sin40 = sin(angle40)
            val startX = tileLeftCssPx * (1f + cos30 * (cos40 - 2f) - .5f * sin40) -
                cos30 * viewportWidthCssPx * .25f
            val startZ = tileLeftCssPx * (.5f * (cos40 - 2f) + cos30 * sin40) -
                viewportWidthCssPx * .125f
            return PhoneStartMotionFrame(
                alpha = when {
                    elapsedMillis < delay || raw <= 0f -> 0f
                    raw < .01f -> cubicBezier(raw / .01f, .05f, 1f, .1f, 1f)
                    else -> 1f
                },
                rotationY = -80f * remaining,
                translationXPx = startX * remaining,
                translationYPx = 0f,
                translationZPx = startZ * remaining,
                scale = 1f,
                pivotX = if (tileWidthCssPx > 0f) -tileLeftCssPx / tileWidthCssPx else 0f,
            )
        }

        // Home resume: the tile background turns around its left edge for 500ms.
        // CSS also offsets the tile by +left/-left around its two rotations.
        val progress = 1f - cubicBezier(raw, .3f, 1f, .2f, 1f)
        val outerAngle = 70f * progress
        // The forward-resume CSS explicitly sets --app-animation-distance to 0px.
        val offset = 0f
        val firstX = offset * (cos(10f * progress * DEG_TO_RAD) - 1f)
        val firstZ = offset * sin(10f * progress * DEG_TO_RAD)
        val turn = outerAngle * DEG_TO_RAD
        return PhoneStartMotionFrame(
            alpha = if (elapsedMillis < delay) 0f else 1f,
            rotationY = outerAngle,
            translationXPx = firstX * cos(turn) + firstZ * sin(turn),
            translationYPx = 0f,
            translationZPx = -firstX * sin(turn) + firstZ * cos(turn),
            scale = 1f,
            pivotX = 0f,
        )
    }

    fun sampleInnerEntry(
        elapsedMillis: Int,
        animationIndex: Float,
        viewportHeightCssPx: Float,
        density: Float,
    ): PhoneStartInnerFrame {
        val delay = delayMillis(animationIndex, viewportHeightCssPx)
        val raw = ((elapsedMillis - delay) / CLASSIC_INNER_ENTRY_MS).coerceIn(0f, 1f)
        val remaining = 1f - cubicBezier(raw, .2f, .25f, .25f, 1f)
        return PhoneStartInnerFrame(
            rotationY = 45f * remaining,
            translationXPx = 60f * density * remaining,
        )
    }

    /** CSS exit used by DiscoLauncher for app-list rows, letter rows and search icon. */
    fun sampleAppListExit(
        elapsedMillis: Int,
        animationIndex: Float,
        viewportHeightCssPx: Float,
        viewportWidthCssPx: Float,
        selected: Boolean = false,
        letter: Boolean = false,
        tileLeftCssPx: Float = 0f,
        tileWidthCssPx: Float = 1f,
    ): PhoneStartMotionFrame {
        val delay = if (selected) CLASSIC_APPS_SELECTED_DELAY_MS.roundToInt()
        else delayMillis(animationIndex, viewportHeightCssPx)
        val raw = ((elapsedMillis - delay) / CLASSIC_APPS_EXIT_MS).coerceIn(0f, 1f)
        val progress = cubicBezier(raw, .75f, 0f, 1f, 0f)
        val letterOffset = if (letter) -viewportWidthCssPx else 0f
        return PhoneStartMotionFrame(
            alpha = if (raw >= 1f) 0f else 1f,
            rotationY = -90f * progress,
            translationXPx = (-FLOW_PERSPECTIVE_CSS_PX / 3f + letterOffset) * progress,
            translationYPx = 0f,
            translationZPx = 0f,
            scale = 1f,
            pivotX = if (tileWidthCssPx > 0f) -tileLeftCssPx / tileWidthCssPx else 0f,
        )
    }

    /** App-list DOM order is reversed and reserves two trailing indices for its controls. */
    fun appListAnimationIndex(itemIndex: Int, firstVisible: Int, lastVisible: Int): Float {
        val count = (lastVisible - firstVisible + 1).coerceAtLeast(1)
        return ((lastVisible - itemIndex).coerceAtLeast(0).toFloat() / (count + 1))
            .coerceIn(0f, 1f).let { (it * 100f).roundToInt() / 100f }
    }

    private fun cubicBezier(
        x: Float,
        p1x: Float,
        p1y: Float,
        p2x: Float,
        p2y: Float,
    ): Float {
        val target = x.coerceIn(0f, 1f)
        if (target == 0f || target == 1f) return target
        var low = 0f
        var high = 1f
        repeat(22) {
            val t = (low + high) * .5f
            if (cubic(t, 0f, p1x, p2x, 1f) < target) low = t else high = t
        }
        val t = (low + high) * .5f
        return cubic(t, 0f, p1y, p2y, 1f)
    }

    private fun cubic(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3f * u * u * t * p1 +
            3f * u * t * t * p2 + t * t * t * p3
    }

    private const val DEG_TO_RAD = 0.017453292519943295f
}
