package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsMotionAccentTest {
    @Test
    fun animatedStylesMapToTheirOwnMotionFamilies() {
        assertEquals(WindowsMotionAccentKind.ROBOTS, WindowsMotionAccent.kindForStyle(1))
        assertEquals(WindowsMotionAccentKind.CITY, WindowsMotionAccent.kindForStyle(2))
        assertEquals(WindowsMotionAccentKind.BUBBLES, WindowsMotionAccent.kindForStyle(3))
        assertEquals(WindowsMotionAccentKind.BIRD, WindowsMotionAccent.kindForStyle(5))
        assertEquals(WindowsMotionAccentKind.DRAGON, WindowsMotionAccent.kindForStyle(8))
        assertEquals(WindowsMotionAccentKind.GEARS, WindowsMotionAccent.kindForStyle(9))

        listOf(0, 4, 6, 7).forEach { style ->
            assertEquals(WindowsMotionAccentKind.NONE, WindowsMotionAccent.kindForStyle(style))
        }
    }

    @Test
    fun robotsPostInteractionWindowStaysWithinHistoricalSixToEightSeconds() {
        assertTrue(WindowsMotionAccent.ACTIVE_DURATION_MILLIS in 6_000L..8_000L)
        assertEquals(
            WindowsMotionAccent.ACTIVE_DURATION_MILLIS,
            WindowsMotionAccent.HOLD_DURATION_MILLIS + WindowsMotionAccent.FADE_DURATION_MILLIS,
        )
    }

    @Test
    fun normalizedVelocityIsFiniteBoundedAndDirectionPreserving() {
        assertEquals(0.6f, WindowsMotionAccent.normalizedVelocity(50f, 1_000f), 0.0001f)
        assertEquals(-0.6f, WindowsMotionAccent.normalizedVelocity(-50f, 1_000f), 0.0001f)
        assertEquals(1f, WindowsMotionAccent.normalizedVelocity(10_000f, 1_000f), 0f)
        assertEquals(-1f, WindowsMotionAccent.normalizedVelocity(-10_000f, 1_000f), 0f)
        assertEquals(0f, WindowsMotionAccent.normalizedVelocity(Float.NaN, 1_000f), 0f)
    }

    @Test
    fun cityLightsAndBubbleTravelStayInDrawableRanges() {
        repeat(64) { index ->
            val alpha = WindowsMotionAccent.cityLightAlpha(
                phaseSeconds = index * 0.37f,
                lightIndex = index,
            )
            assertTrue(alpha in 0f..1f)

            val travel = WindowsMotionAccent.bubbleTravel(
                phaseSeconds = index * 1.73f,
                bubbleIndex = index,
            )
            assertTrue(travel >= 0f && travel < 1f)
        }
    }

    @Test
    fun dragonFollowRespondsToScrollDirectionWithoutUnboundedTravel() {
        val right = WindowsMotionAccent.artworkOffsetX(
            WindowsMotionAccentFrame(
                kind = WindowsMotionAccentKind.DRAGON,
                phaseSeconds = 0f,
                activity = 1f,
                scrollVelocity = 1f,
            ),
            viewportWidthPx = 1_000f,
        )
        val left = WindowsMotionAccent.artworkOffsetX(
            WindowsMotionAccentFrame(
                kind = WindowsMotionAccentKind.DRAGON,
                phaseSeconds = 0f,
                activity = 1f,
                scrollVelocity = -1f,
            ),
            viewportWidthPx = 1_000f,
        )

        assertTrue(right < 0f)
        assertTrue(left > 0f)
        assertTrue(kotlin.math.abs(right) <= 20f)
        assertTrue(kotlin.math.abs(left) <= 20f)
    }

    @Test
    fun ambientFamiliesRemainLimitedToCityAndBubbles() {
        assertTrue(WindowsMotionAccent.isAmbient(WindowsMotionAccentKind.CITY))
        assertTrue(WindowsMotionAccent.isAmbient(WindowsMotionAccentKind.BUBBLES))
        assertTrue(!WindowsMotionAccent.isAmbient(WindowsMotionAccentKind.ROBOTS))
        assertTrue(!WindowsMotionAccent.isAmbient(WindowsMotionAccentKind.DRAGON))
    }
}
