package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.*
import org.junit.Test

class MobileStartMotionTest {
    private fun frame(out: Boolean, ms: Int, row: Float, x: Float, y: Float) =
        MobileStartMotion.sample(out, ms, row, x, y, 254f, 456f)

    @Test fun exitTracksMeasuredGlyphLocationsAcrossDifferentRows() {
        // Native PTS from 1000197577.mp4 (right phone), crop (1026,60,508,840).
        // Fitted motion origin 7.665s; tolerances include one 33.4ms source interval.
        val gear = frame(true, 109, 0f, 254f, 121f) // f233, 7.774433s
        assertEquals(254f, 254f + gear.translationXPx, 2f)
        assertEquals(103f, 121f + gear.translationYPx, 8f)
        val edge = frame(true, 276, .5f, 254f, 456f) // f238, 7.941267s
        assertEquals(456f, 456f + edge.translationYPx, 2f)
        assertEquals(1.38f, edge.scale, .07f)
        val mix = frame(true, 276, .75f, 419f, 621f) // same presentation frame
        assertEquals(450.5f, 419f + mix.translationXPx, 8f)
        assertEquals(652.5f, 621f + mix.translationYPx, 8f)
    }

    @Test fun returnTracksMeasuredScaleAndLocationInsteadOfAnUpwardSlide() {
        // Fitted return origin 18.780s. f566 and f570 are independently tracked glyphs.
        val mix = frame(false, 105, .75f, 419f, 621f)
        assertEquals(394.5f, 419f + mix.translationXPx, 6f)
        assertEquals(595.5f, 621f + mix.translationYPx, 6f)
        assertEquals(.86f, mix.scale, .04f)
        val store = frame(false, 239, 0f, 84.5f, 119f)
        assertEquals(111.5f, 84.5f + store.translationXPx, 6f)
        assertEquals(173f, 119f + store.translationYPx, 6f)
        assertEquals(.84f, store.scale, .04f)
    }

    @Test fun lowerRowsLeaveDownwardAndUpperRowsLeaveUpward() {
        assertTrue(frame(true, 200, 0f, 80f, 120f).translationYPx < 0f)
        assertTrue(frame(true, 330, 1f, 420f, 788f).translationYPx > 0f)
        assertTrue(frame(true, 330, 1f, 420f, 788f).translationXPx > 0f)
        assertEquals(0f, frame(true, 200, .5f, 254f, 456f).rotationY, 0f)
    }

    @Test fun wallpaperStaysVisibleAfterTilesThenReachesBlackBeforeHandoff() {
        val last = frame(true, 383, 1f, 420f, 788f)
        assertEquals(0f, last.alpha, 0f)
        assertEquals(1f, MobileStartMotion.wallpaperAlpha(true, 383), 0f)
        assertTrue(MobileStartMotion.wallpaperAlpha(true, 467) in .45f.. .55f)
        assertEquals(0f, MobileStartMotion.wallpaperAlpha(true, MobileStartMotion.EXIT_TOTAL_MS), 0f)
    }

    @Test fun scrolledAndDenseRowsKeepDistinctTimingAndACommonAnchor() {
        val rows = listOf(-40f, 44f, 128f, 212f, 296f, 380f, 464f, 548f)
        val delays = rows.map { MobileStartMotion.delayMillis(true,
            MobileStartMotion.rowFraction(it, rows.first(), rows.last())) }
        assertEquals(delays.size, delays.distinct().size)
        for (density in listOf(.75f, 1f, 2f, 3f)) {
            val a = frame(true, 220, .5f, 419f, 621f)
            val b = MobileStartMotion.sample(true, 220, .5f, 419f * density,
                621f * density, 254f * density, 456f * density)
            assertEquals(a.translationXPx * density, b.translationXPx, .001f)
            assertEquals(a.translationYPx * density, b.translationYPx, .001f)
        }
    }

    @Test fun allVisibleRowsReachExactEndpointsWithoutNaNOrOvershoot() {
        for (row in listOf(0f, .25f, .5f, .75f, 1f)) {
            for (selected in listOf(false, true)) {
                for (ms in -10..660 step 10) {
                    val f = MobileStartMotion.sample(true, ms, row, 0f, 0f, 254f, 456f, selected)
                    assertTrue(f.alpha in 0f..1f)
                    assertTrue(f.scale in 1f..2f)
                    assertTrue(f.translationXPx.isFinite() && f.translationYPx.isFinite())
                }
                assertEquals(0f, frame(true, 534, row, 80f, 120f).alpha, 0f)
                val settled = frame(false, 630, row, 80f, 120f)
                assertEquals(1f, settled.alpha, 0f)
                assertEquals(1f, settled.scale, 0f)
                assertEquals(0f, settled.translationXPx, 0f)
                assertEquals(0f, settled.translationYPx, 0f)
            }
        }
    }
}
