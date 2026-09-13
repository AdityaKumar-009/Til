package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperMaskMathTest {
    @Test
    fun jpegNoiseFloorStaysTransparent() {
        assertEquals(0, WallpaperMaskMath.alphaForDistance(0))
        assertEquals(0, WallpaperMaskMath.alphaForDistance(WallpaperMaskMath.JPEG_NOISE_FLOOR))
        assertTrue(WallpaperMaskMath.alphaForDistance(20) > 0)
        assertEquals(255, WallpaperMaskMath.alphaForDistance(80))
    }

    @Test
    fun toneBucketsPreserveDarkNeutralAndLightArtworkRoles() {
        assertEquals(0, WallpaperMaskMath.toneBucket(-30, -30, -30))
        assertEquals(2, WallpaperMaskMath.toneBucket(30, 30, 30))
        assertEquals(1, WallpaperMaskMath.toneBucket(30, -15, 5))
    }

    @Test
    fun weightedDistanceIsSymmetricAndGreenWeightedMost() {
        assertEquals(
            WallpaperMaskMath.weightedDistance(20, -30, 10),
            WallpaperMaskMath.weightedDistance(-20, 30, -10),
        )
        assertTrue(
            WallpaperMaskMath.weightedDistance(0, 30, 0) >
                WallpaperMaskMath.weightedDistance(30, 0, 0),
        )
    }
}
