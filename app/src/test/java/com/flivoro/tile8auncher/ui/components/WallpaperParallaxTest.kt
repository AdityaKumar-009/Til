package com.flivoro.tile8auncher.ui.components

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperParallaxTest {
    @Test
    fun translationIsLinearForTheWholeScrollRange() {
        val first = WallpaperParallax.translationX(
            scrollOffsetPx = 1_000f,
            rate = WallpaperParallax.RIBBON_RATE,
            viewportWidthPx = 1_000f,
            maxTravelFraction = WallpaperParallax.RIBBON_RATE,
        )
        val second = WallpaperParallax.translationX(
            scrollOffsetPx = 2_000f,
            rate = WallpaperParallax.RIBBON_RATE,
            viewportWidthPx = 1_000f,
            maxTravelFraction = WallpaperParallax.RIBBON_RATE,
        )

        assertEquals(-65f, first, 0f)
        assertEquals(-130f, second, 0f)
        assertEquals(first, second - first, 0f)
    }

    @Test
    fun layersMoveInTheSameDirectionAtDifferentRates() {
        val scroll = 1_000f

        val glow = WallpaperParallax.translationX(scroll, WallpaperParallax.GLOW_RATE)
        val ribbon = WallpaperParallax.translationX(scroll, WallpaperParallax.RIBBON_RATE)
        val highlight = WallpaperParallax.translationX(scroll, WallpaperParallax.HIGHLIGHT_RATE)

        assertTrue(glow < 0f)
        assertTrue(abs(glow) < abs(ribbon))
        assertTrue(abs(ribbon) < abs(highlight))
    }

    @Test
    fun nonFiniteInputDoesNotPoisonGraphicsLayer() {
        assertEquals(0f, WallpaperParallax.translationX(Float.NaN, 0.1f), 0f)
        assertEquals(0f, WallpaperParallax.translationX(Float.POSITIVE_INFINITY, 0.1f), 0f)
        assertEquals(0f, WallpaperParallax.translationX(100f, Float.NaN), 0f)
        assertEquals(0f, WallpaperParallax.wrapTranslationX(Float.NaN, 100f), 0f)
    }

    @Test
    fun farScrollDoesNotSlowOrStop() {
        val far = WallpaperParallax.translationX(
            scrollOffsetPx = 100_000f,
            rate = WallpaperParallax.HIGHLIGHT_RATE,
        )

        assertEquals(-11_000f, far, 0.01f)
        assertTrue(abs(far) > 1_000f)
    }

    @Test
    fun repeatedPhaseWrapsAtTheArtworkPeriod() {
        val period = 2_000f

        assertEquals(
            WallpaperParallax.wrapTranslationX(0f, period),
            WallpaperParallax.wrapTranslationX(period, period),
            0f,
        )
        assertEquals(
            WallpaperParallax.wrapTranslationX(0f, period),
            WallpaperParallax.wrapTranslationX(-period, period),
            0f,
        )
    }
}
