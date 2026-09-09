package com.flivoro.tile8auncher.ui.components

/**
 * Scroll-to-translation math for wallpaper layers.
 *
 * The raw translation is deliberately linear. A separate wrapped phase is used only when a
 * finite repeated layer is handed to graphicsLayer; its repeated artwork makes that phase reset
 * invisible while the artwork still advances at the same rate for the whole scroll range.
 */
internal object WallpaperParallax {
    const val GLOW_RATE = 0.025f
    const val RIBBON_RATE = 0.065f
    const val HIGHLIGHT_RATE = 0.11f
    const val IMAGE_RATE = 0.08f

    private const val FALLBACK_VIEWPORT_WIDTH_PX = 1080f
    private const val FALLBACK_MAX_TRAVEL = 0.20f

    /** Returns the unbounded, constant-rate translation for a scroll position. */
    @Suppress("UNUSED_PARAMETER")
    fun translationX(
        scrollOffsetPx: Float,
        rate: Float,
        viewportWidthPx: Float = FALLBACK_VIEWPORT_WIDTH_PX,
        maxTravelFraction: Float = FALLBACK_MAX_TRAVEL,
    ): Float {
        val safeOffset = scrollOffsetPx.takeIf { it.isFinite() } ?: 0f
        val safeRate = rate.takeIf { it.isFinite() } ?: 0f
        val rawTranslation = -safeOffset * safeRate
        return rawTranslation.takeIf { it.isFinite() } ?: 0f
    }

    /**
     * Folds a linear translation into one mirrored/repeated period for a finite graphics layer.
     * The period is an implementation detail of the repeated artwork, not a motion limit.
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
}
