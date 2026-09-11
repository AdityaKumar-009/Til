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

    @Test fun returnMatchesTestMp4MailTileAtRecordedFrames() {
        assertEquals(600, StartEntranceMotion.durationMillis(StartEntranceKind.RETURN))
        assertEquals(2800, StartEntranceMotion.durationMillis(StartEntranceKind.STARTUP))

        val at133 = StartEntranceMotion.frame(133.333f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(35.5f / 1920f, at133.offsetFraction, .00005f)
        assertEquals(237f / 248f, at133.scale, .0005f)
        assertEquals(.741f, at133.alpha, .002f)

        val at200 = StartEntranceMotion.frame(200f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(19f / 1920f, at200.offsetFraction, .00005f)
        assertEquals(244f / 248f, at200.scale, .0005f)
        assertEquals(1f, at200.alpha, .0001f)
    }

    @Test fun returnHeaderUsesItsSlowerMeasuredFade() {
        assertEquals(.258f, StartEntranceMotion.headerAlpha(133.333f / 600f), .003f)
        assertEquals(.581f, StartEntranceMotion.headerAlpha(200f / 600f), .003f)
        assertEquals(.906f, StartEntranceMotion.headerAlpha(266.667f / 600f), .003f)
        assertEquals(1f, StartEntranceMotion.headerAlpha(366.667f / 600f), .0001f)
        assertEquals(0f, StartEntranceMotion.headerAlpha(200f / 2800f, StartEntranceKind.STARTUP), .0001f)
    }

    @Test fun returnBandsStaggerScaleAndOpacityWithoutExaggeratingTranslation() {
        val progress = 200f / 600f
        val first = StartEntranceMotion.frame(progress, 0f, StartEntranceKind.RETURN)
        val second = StartEntranceMotion.frame(progress, 1f, StartEntranceKind.RETURN)
        val third = StartEntranceMotion.frame(progress, 2f, StartEntranceKind.RETURN)

        assertTrue(second.scale < first.scale)
        assertTrue(third.scale < second.scale)
        assertTrue(second.alpha <= first.alpha)
        assertTrue(third.alpha <= second.alpha)

        // test.mp4 shows later bands travelling less horizontally, unlike the old delayed-travel
        // implementation which pushed them much farther to the right.
        assertEquals(first.offsetFraction * .85f, second.offsetFraction, .000001f)
        assertEquals(first.offsetFraction * .85f * .85f, third.offsetFraction, .000001f)
    }

    @Test fun returnCurveRemainsSmoothOnTenMillisecondInterpolationGrid() {
        val duration = StartEntranceMotion.durationMillis(StartEntranceKind.RETURN)
        var previous = StartEntranceMotion.frame(0f, 0f, StartEntranceKind.RETURN)
        for (ms in 10..duration step 10) {
            val frame = StartEntranceMotion.frame(ms.toFloat() / duration, 0f, StartEntranceKind.RETURN)
            assertTrue("return travel reversed at $ms ms", frame.offsetFraction <= previous.offsetFraction + .000001f)
            assertTrue("return scale reversed at $ms ms", frame.scale >= previous.scale - .000001f)
            assertTrue("return alpha reversed at $ms ms", frame.alpha >= previous.alpha - .000001f)
            previous = frame
        }
        assertEquals(EntranceFrame(0f, 1f, 1f), previous)
    }

    @Test fun wallpaperAnchorUsesSameMeasuredTravelAndSettlesMonotonically() {
        for (kind in StartEntranceKind.entries) {
            val duration = StartEntranceMotion.durationMillis(kind)
            var previous = StartEntranceMotion.backgroundTravelFraction(0f, kind)
            assertEquals(StartEntranceMotion.frame(0f, 0f, kind).offsetFraction, previous, .000001f)
            for (ms in 10..duration step 10) {
                val progress = ms.toFloat() / duration
                val current = StartEntranceMotion.backgroundTravelFraction(progress, kind)
                assertTrue("wallpaper anchor moved backwards at $ms ms for $kind", current <= previous + .000001f)
                assertTrue(current in 0f..1f)
                previous = current
            }
            assertEquals(0f, StartEntranceMotion.backgroundTravelFraction(1f, kind), 0f)
        }
    }

    @Test fun normalizedTravelProducesSameFractionInPortraitAndLandscape() {
        val p = 100f / StartEntranceMotion.durationMillis(StartEntranceKind.STARTUP)
        val fraction = StartEntranceMotion.backgroundTravelFraction(p, StartEntranceKind.STARTUP)
        val portraitWidth = 1080f
        val landscapeWidth = 2400f
        val portraitNormalized = (portraitWidth * fraction) / portraitWidth
        val landscapeNormalized = (landscapeWidth * fraction) / landscapeWidth
        assertEquals(fraction, portraitNormalized, .000001f)
        assertEquals(fraction, landscapeNormalized, .000001f)
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
            assertTrue(StartEntranceMotion.backgroundTravelFraction(p, kind).isFinite())
        }
    }
}
