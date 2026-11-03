package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonePanoramaWallpaperDimmingTest {
    @Test fun `wallpaper stays unchanged on Start and dims 30 percent on All Apps`() {
        assertEquals(0f, panoramaWallpaperDimAlpha(0f), 0.0001f)
        assertEquals(0.30f, panoramaWallpaperDimAlpha(1f), 0.0001f)
    }

    @Test fun `dimming interpolates linearly in either swipe direction`() {
        assertEquals(0.075f, panoramaWallpaperDimAlpha(0.25f), 0.0001f)
        assertEquals(0.15f, panoramaWallpaperDimAlpha(0.50f), 0.0001f)
        assertEquals(0.225f, panoramaWallpaperDimAlpha(0.75f), 0.0001f)
    }

    @Test fun `overshoot cannot darken beyond the requested range`() {
        assertEquals(0f, panoramaWallpaperDimAlpha(-0.2f), 0.0001f)
        assertEquals(0.30f, panoramaWallpaperDimAlpha(1.2f), 0.0001f)
    }
}
