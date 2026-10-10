package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import kotlin.math.cos
import kotlin.math.exp
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
internal data class PhoneProjectedPoint(
    val xCssPx: Float,
    val yCssPx: Float,
)

/**
 * Windows Phone 8.1 motion: native-video forward entry with the established
 * DiscoLauncher-derived exit/Back tracks. CSS-pixel
 * inputs are supplied in dp and converted to physical pixels at the Compose
 * graphics layer. W10M stays on MobileStartMotion.
 */
internal object PhoneStartChoreography {
    private const val CLASSIC_EXIT_MS = 175f
    private const val CLASSIC_SELECTED_EXIT_MS = 300f
    private const val CLASSIC_ENTRY_MS = 225f
    private const val CLASSIC_BACK_ENTRY_MS = 350f
    private const val CLASSIC_STAGGER_MS = 200f
    // 1000197575 contains long repeated-frame holds: its 47s return must not
    // set real-time duration. Normal-speed 1000197576, 112.70–113.20s, supplies
    // the clock; the slowed clip supplies intermediate geometry. See the audit.
    const val NATIVE_FORWARD_STAGGER_MULTIPLIER = 1.0f
    private const val CLASSIC_SELECTED_DELAY_MS = 200f
    private const val CLASSIC_APPS_SELECTED_DELAY_MS = 300f
    private const val CLASSIC_APPS_EXIT_MS = 200f
    private const val CLASSIC_APPS_PAGE_ENTRY_DELAY_MS = 27f
    private const val CLASSIC_APPS_PAGE_ENTRY_MS = 500f
    // DiscoLauncher src/script.js sets --app-transition-scale from
    // appTransitionScale(window.innerHeight) at startup. The root CSS default
    // of 1 is only a fallback before that JS initialization.
    private const val FLOW_PERSPECTIVE_CSS_PX = 1000f
    const val CLASSIC_TILE_PERSPECTIVE_CSS_PX = 2000f
    const val CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX = 1000f
    private const val REFERENCE_VIEWPORT_HEIGHT = 850f
    private const val MOBILE_EXIT_TILE_MS = MobileStartMotion.EXIT_TILE_MS
    private const val MOBILE_ENTRY_TILE_MS = MobileStartMotion.ENTRY_TILE_MS

    /** Fitted from the four face corners, not from total colored-screen area. */
    fun nativeCameraDistance(viewportWidthCssPx: Float): Float = viewportWidthCssPx * 3.26f

    /** Native's diagonal wave follows position, including mixed-size tile centres. */
    fun nativeForwardWavePosition(centerX: Float, centerY: Float, viewportWidth: Float): Float =
        (122f * centerX + 82f * centerY) / viewportWidth.coerceAtLeast(1f)

    fun nativeForwardAnimationIndex(wavePosition: Float, firstWavePosition: Float): Float =
        (1f - (wavePosition - firstWavePosition) / CLASSIC_STAGGER_MS).coerceIn(0f, 1f)

    /** DiscoLauncher’s `baseScale = innerHeight / 850 / 2 + .5`. */
    fun appTransitionScale(viewportHeightCssPx: Float): Float =
        (viewportHeightCssPx / REFERENCE_VIEWPORT_HEIGHT / 2f + .5f).coerceAtLeast(.5f)

    /** DiscoLauncher reverses visible tiles and normalizes their indices to 0..1. */
    fun visibleTileAnimationIndex(reverseRank: Int, visibleCount: Int): Float {
        if (visibleCount <= 0) return 0f
        // DiscoLauncher appends its page icon banner before reversing the visible
        // nodes. The banner receives index 0; tiles therefore occupy 1/N..1.
        return (((reverseRank.coerceIn(0, visibleCount - 1) + 1).toFloat() / visibleCount) * 100f)
            .roundToInt() / 100f
    }

    /** Native Home return exposes the second page behind the staggered Start faces. */
    fun sampleAppsPageEntry(
        elapsedMillis: Int,
        viewportWidthCssPx: Float,
    ): PhoneAppsPageEntryFrame {
        val raw = ((elapsedMillis - CLASSIC_APPS_PAGE_ENTRY_DELAY_MS) /
            CLASSIC_APPS_PAGE_ENTRY_MS).coerceIn(0f, 1f)
        val remaining = exponentialRemaining(raw, 2.84)
        return PhoneAppsPageEntryFrame(
            alpha = ((elapsedMillis - CLASSIC_APPS_PAGE_ENTRY_DELAY_MS) / 16f).coerceIn(0f, 1f),
            rotationY = 90f * remaining,
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
    ): PhoneProjectedPoint {
        val angle = rotationYDegrees * DEG_TO_RAD
        val worldX = viewportWidthCssPx + pageLocalXPx
        val rotatedX = worldX * cos(angle)
        val rotatedZ = -worldX * sin(angle)
        val camera = cameraDistanceCssPx * viewportWidthCssPx / 360f
        val perspectiveScale = camera /
            (camera - rotatedZ).coerceAtLeast(camera * .1f)
        return PhoneProjectedPoint(
            xCssPx = rotatedX * perspectiveScale,
            yCssPx = viewportHeightCssPx * .5f +
                (pageLocalYPx - viewportHeightCssPx * .5f) * perspectiveScale,
        )
    }

    /**
     * Projects one point through the Start/App-list page camera after the CSS
     * transform around an element's origin. Unlike a per-tile Android camera,
     * DiscoLauncher places perspective on the parent page, centered on the page.
     */
    fun projectPlanePoint(
        localXPx: Float,
        localYPx: Float,
        elementLeftCssPx: Float,
        elementTopCssPx: Float,
        elementWidthCssPx: Float,
        elementHeightCssPx: Float,
        viewportWidthCssPx: Float,
        viewportHeightCssPx: Float,
        motion: PhoneStartMotionFrame,
        inner: PhoneStartInnerFrame? = null,
        cameraDistanceCssPx: Float = CLASSIC_TILE_PERSPECTIVE_CSS_PX,
    ): PhoneProjectedPoint {
        val innerAngle = (inner?.rotationY ?: 0f) * DEG_TO_RAD
        val innerDx = localXPx - elementWidthCssPx * .5f
        val innerX = elementWidthCssPx * .5f + innerDx * cos(innerAngle) +
            (inner?.translationXPx ?: 0f)
        val innerY = localYPx
        val innerZ = -innerDx * sin(innerAngle)

        val pivotX = motion.pivotX * elementWidthCssPx
        val outerDx = (innerX - pivotX) * motion.scale
        val outerDy = (innerY - elementHeightCssPx * .5f) * motion.scale
        val outerDz = innerZ * motion.scale
        val outerAngle = motion.rotationY * DEG_TO_RAD
        val rotatedX = outerDx * cos(outerAngle) + outerDz * sin(outerAngle)
        val rotatedZ = -outerDx * sin(outerAngle) + outerDz * cos(outerAngle)
        val worldX = elementLeftCssPx + pivotX + rotatedX + motion.translationXPx
        val worldY = elementTopCssPx + elementHeightCssPx * .5f + outerDy + motion.translationYPx
        val worldZ = rotatedZ + motion.translationZPx
        val perspectiveScale = cameraDistanceCssPx /
            (cameraDistanceCssPx - worldZ).coerceAtLeast(cameraDistanceCssPx * .1f)
        val perspectiveOriginX = viewportWidthCssPx * .5f
        val perspectiveOriginY = viewportHeightCssPx * .5f
        return PhoneProjectedPoint(
            xCssPx = perspectiveOriginX + (worldX - perspectiveOriginX) * perspectiveScale,
            yCssPx = perspectiveOriginY + (worldY - perspectiveOriginY) * perspectiveScale,
        )
    }

    private fun delayMillis(index: Float, viewportHeightCssPx: Float): Int =
        (index.coerceIn(0f, 1f) * CLASSIC_STAGGER_MS *
            appTransitionScale(viewportHeightCssPx)).roundToInt()

    fun selectedExitDelayMillis(viewportHeightCssPx: Float): Int =
        (CLASSIC_SELECTED_DELAY_MS * appTransitionScale(viewportHeightCssPx)).roundToInt()

    /** Last visible Start/Apps track; no additional 200ms blank launch padding. */
    private fun launchHideMillis(viewportHeightCssPx: Float): Int {
        return maxOf(
            selectedExitDelayMillis(viewportHeightCssPx) + CLASSIC_SELECTED_EXIT_MS.roundToInt(),
            (CLASSIC_APPS_SELECTED_DELAY_MS + CLASSIC_APPS_EXIT_MS).roundToInt(),
        )
    }

    /** Entry/exit clock endpoints; the retained exit pose still prevents launch flashes. */
    fun totalMillis(
        mode: LauncherUiMode,
        exiting: Boolean,
        viewportHeightCssPx: Float = REFERENCE_VIEWPORT_HEIGHT,
    ): Int = when (mode) {
        LauncherUiMode.PHONE_8 -> if (exiting) launchHideMillis(viewportHeightCssPx) else {
            maxOf(
                (CLASSIC_BACK_ENTRY_MS +
                    CLASSIC_STAGGER_MS * appTransitionScale(viewportHeightCssPx)).roundToInt(),
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

    fun classicNativeForwardTotalMillis(viewportHeightCssPx: Float): Int =
        maxOf(
            (CLASSIC_ENTRY_MS + CLASSIC_STAGGER_MS).roundToInt(),
            (CLASSIC_APPS_PAGE_ENTRY_DELAY_MS + CLASSIC_APPS_PAGE_ENTRY_MS).roundToInt(),
        )

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
        entryStaggerMultiplier: Float = 1f,
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
        val baseDelay = if (!exiting && !resumeUsesBackMotion) {
            (animationIndex.coerceIn(0f, 1f) * CLASSIC_STAGGER_MS).roundToInt()
        } else delayMillis(mode, exiting, animationIndex, viewportHeightCssPx, selected)
        val delay = if (!exiting && !resumeUsesBackMotion) {
            (baseDelay * entryStaggerMultiplier).roundToInt()
        } else baseDelay
        val duration = when {
            exiting && selected -> CLASSIC_SELECTED_EXIT_MS
            exiting -> CLASSIC_EXIT_MS
            resumeUsesBackMotion -> CLASSIC_BACK_ENTRY_MS
            else -> CLASSIC_ENTRY_MS
        }
        val raw = ((elapsedMillis - delay) / duration).coerceIn(0f, 1f)

        if (exiting) {
            // CSS converts the mismatched `rotateY(0)` and compound end transform
            // to matrices, then interpolates the decomposed translation and rotation.
            // The endpoint is the -40° compound turn around the tile's left edge.
            val progress = cubicBezier(raw, .75f, 0f, 1f, 0f)
            val endTranslationX = -viewportWidthCssPx * .25f +
                tileLeftCssPx * (cos(30f * DEG_TO_RAD) - 1f)
            return PhoneStartMotionFrame(
                alpha = if (raw >= 1f) 0f else 1f,
                rotationY = -40f * progress,
                translationXPx = endTranslationX * progress,
                translationYPx = 0f,
                translationZPx = tileLeftCssPx * .5f * progress,
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

        // All faces share the page axis behind the screen. A local hinge with
        // the same positive offset for every tile fits the left phone tile but
        // incorrectly sends the right-hand tiles outside the screen.
        val progress = nativeEntryRemaining(raw)
        val angle = 80f * progress * DEG_TO_RAD
        return PhoneStartMotionFrame(
            alpha = if (elapsedMillis < delay) 0f else 1f,
            rotationY = 80f * progress,
            translationXPx = tileLeftCssPx * (cos(angle) - 1f) +
                viewportWidthCssPx * .203f * sin(angle),
            translationYPx = 0f,
            translationZPx = -tileLeftCssPx * sin(angle) +
                viewportWidthCssPx * .218f * (cos(angle) - 1f),
            scale = 1f,
            pivotX = 0f,
        )
    }

    private fun nativeEntryRemaining(progress: Float): Float =
        exponentialRemaining(progress, 2.55)

    private fun exponentialRemaining(progress: Float, exponent: Double): Float =
        ((exp(exponent * (1f - progress.coerceIn(0f, 1f))) - 1.0) /
            (exp(exponent) - 1.0)).toFloat()

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
