package com.flivoro.tile8auncher.ui.components

import kotlin.math.max

/**
 * Scroll-to-translation and crop math for Windows-style Start wallpaper layers.
 *
 * Windows' depth effect is a continuous, slower horizontal track behind the Start content. Keep
 * the source position linear and never modulo-wrap the moving layer itself: wrapping a live layer
 * creates a visible phase jump when the viewport crosses the artificial period.
 */
internal object WallpaperParallax {
    const val GLOW_RATE = 0.025f
    const val RIBBON_RATE = 0.065f
    const val HIGHLIGHT_RATE = 0.11f
    const val IMAGE_RATE = 0.08f

    private const val FALLBACK_VIEWPORT_WIDTH_PX = 1080f
    private const val FALLBACK_MAX_TRAVEL = 0.20f

    data class CoverTransform(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val renderedWidth: Float,
        val renderedHeight: Float,
    )

    /** Returns the unbounded, constant-rate translation for the one shared wallpaper world. */
    @Suppress("UNUSED_PARAMETER")
    fun translationX(
        scrollOffsetPx: Float,
        rate: Float,
        viewportWidthPx: Float = FALLBACK_VIEWPORT_WIDTH_PX,
        maxTravelFraction: Float = FALLBACK_MAX_TRAVEL,
    ): Float {
        val legacyOffset = scrollOffsetPx.takeIf { it.isFinite() } ?: 0f
        val safeOffset = SharedWallpaperScroll.effectiveOffset(legacyOffset)
        val safeRate = rate.takeIf { it.isFinite() } ?: 0f
        val rawTranslation = -safeOffset * safeRate
        return rawTranslation.takeIf { it.isFinite() } ?: 0f
    }

    /**
     * Legacy phase helper kept for compatibility/tests. Rendering no longer uses this value for a
     * moving layer; it is useful only for artwork that is intrinsically periodic.
     */
    fun repeatingTranslationX(
        scrollOffsetPx: Float,
        rate: Float,
        repeatPeriodPx: Float,
    ): Float = wrapTranslationX(
        linearTranslationPx = translationX(scrollOffsetPx, rate),
        repeatPeriodPx = repeatPeriodPx,
    )

    fun wrapTranslationX(
        linearTranslationPx: Float,
        repeatPeriodPx: Float,
    ): Float {
        val safeTranslation = linearTranslationPx.takeIf { it.isFinite() } ?: 0f
        val safePeriod = repeatPeriodPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
        val halfPeriod = safePeriod.toDouble() / 2.0
        val period = safePeriod.toDouble()
        val wrapped = ((safeTranslation.toDouble() + halfPeriod) % period + period) % period -
            halfPeriod
        return wrapped.toFloat().takeIf { it.isFinite() } ?: 0f
    }

    /**
     * Aspect-preserving center-crop transform for one viewport.
     *
     * The returned X center can be moved into the middle panel of a wider backing layer. This is
     * what lets portrait phones show the same artwork geometry as landscape instead of stretching
     * a 4:3 Windows asset independently on X and Y.
     */
    fun coverTransform(
        bitmapWidthPx: Float,
        bitmapHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        viewportCenterXPx: Float = viewportWidthPx / 2f,
    ): CoverTransform {
        val bitmapWidth = bitmapWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val bitmapHeight = bitmapHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val viewportWidth = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val viewportHeight = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val centerX = viewportCenterXPx.takeIf { it.isFinite() } ?: viewportWidth / 2f

        val scale = max(viewportWidth / bitmapWidth, viewportHeight / bitmapHeight)
        val renderedWidth = bitmapWidth * scale
        val renderedHeight = bitmapHeight * scale
        return CoverTransform(
            scale = scale,
            offsetX = centerX - renderedWidth / 2f,
            offsetY = (viewportHeight - renderedHeight) / 2f,
            renderedWidth = renderedWidth,
            renderedHeight = renderedHeight,
        )
    }
}
