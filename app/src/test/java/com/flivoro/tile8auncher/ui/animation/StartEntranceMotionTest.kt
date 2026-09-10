package com.flivoro.tile8auncher.ui.animation

import org.junit.Assert.*
import org.junit.Test

class StartEntranceMotionTest {
    @Test fun visibleGroupsSettleWithoutOvershootForBothEntrances() {
        for (kind in StartEntranceKind.entries) for (group in listOf(-.5f, 0f, .5f, 1f, 2f, 20f)) {
            var previous = StartEntranceMotion.frame(0f, group, kind)
            val duration = StartEntranceMotion.durationMillis(kind)
            for (ms in 1..duration) {
                val frame = StartEntranceMotion.frame(ms.toFloat() / duration, group, kind)
                assertTrue(frame.offsetFraction <= previous.offsetFraction + .000001f)
                assertTrue(frame.scale >= previous.scale - .000001f && frame.scale <= 1f)
                assertTrue(frame.alpha >= previous.alpha - .000001f && frame.alpha <= 1f)
                previous = frame
            }
            assertEquals(EntranceFrame(0f, 1f, 1f), previous)
        }
    }

    @Test fun startupMatchesMeasuredDesktopTileAtOneHundredMilliseconds() {
        val frame = StartEntranceMotion.frame(100f / 2800, 0f, StartEntranceKind.STARTUP)
        assertEquals(281f / 960, frame.offsetFraction, .0001f)
        assertEquals(198f / 248, frame.scale, .0001f)
        assertEquals(1f, frame.alpha, .0001f)
    }

    @Test fun homeReturnUsesIndependentShortTrack() {
        assertEquals(680, StartEntranceMotion.durationMillis(StartEntranceKind.RETURN))
        assertEquals(2800, StartEntranceMotion.durationMillis(StartEntranceKind.STARTUP))
        val home = StartEntranceMotion.frame(200f / 680, 0f, StartEntranceKind.RETURN)
        val unlock = StartEntranceMotion.frame(200f / 2800, 0f, StartEntranceKind.STARTUP)
        assertEquals(.98f, home.scale, .0001f)
        assertTrue(unlock.offsetFraction > home.offsetFraction * 4)
        assertEquals(1f, StartEntranceMotion.headerAlpha(200f / 680), .0001f)
        assertEquals(0f, StartEntranceMotion.headerAlpha(200f / 2800, StartEntranceKind.STARTUP), .0001f)
    }

    @Test fun staggerFollowsViewportAfterManyBandsHaveScrolledAway() {
        val first = StartEntranceMotion.viewportBandPosition(20, 20, 100, 400f)
        val neighbor = StartEntranceMotion.viewportBandPosition(21, 20, 100, 400f)
        assertEquals(-.25f, first, 0f)
        assertEquals(.75f, neighbor, 0f)
        val a = StartEntranceMotion.frame(.08f, first, StartEntranceKind.STARTUP)
        val b = StartEntranceMotion.frame(.08f, neighbor, StartEntranceKind.STARTUP)
        assertTrue(a.offsetFraction < b.offsetFraction)
        assertTrue(a.scale > b.scale)
    }

    @Test fun invalidCoordinatesAndProgressRemainFinite() {
        for (kind in StartEntranceKind.entries) for (p in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            val f = StartEntranceMotion.frame(p, Float.NaN, kind)
            assertTrue(f.offsetFraction.isFinite() && f.scale.isFinite() && f.alpha.isFinite())
        }
    }
}
