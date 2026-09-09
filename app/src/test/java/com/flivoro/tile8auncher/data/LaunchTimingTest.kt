package com.flivoro.tile8auncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTimingTest {

    @Test
    fun defaultTimingKeepsTheFittedWindowsMapping() {
        val timing = LaunchTiming()

        assertEquals(LaunchTiming.DEFAULT_DURATION_MILLIS, timing.durationMillis)
        assertEquals(TimeCurve.REFERENCE, timing.curve)
        assertEquals(0f, timing.transform(0f), 0f)
        assertEquals(.25f, timing.transform(.25f), 0f)
        assertEquals(.5f, timing.transform(.5f), 0f)
        assertEquals(1f, timing.transform(1f), 0f)
    }

    @Test
    fun everyCurveHasFiniteBoundedEndpointsAndSamples() {
        val inputs = listOf(
            Float.NEGATIVE_INFINITY,
            -1f,
            0f,
            .01f,
            .25f,
            .5f,
            .75f,
            .99f,
            1f,
            2f,
            Float.POSITIVE_INFINITY,
            Float.NaN,
        )

        for (curve in TimeCurve.values()) {
            val timing = LaunchTiming(curve = curve)
            assertEquals("$curve at start", 0f, timing.transform(0f), 0f)
            assertEquals("$curve at end", 1f, timing.transform(1f), 0f)
            for (input in inputs) {
                val output = timing.transform(input)
                assertTrue("$curve produced a non-finite value for $input", output.isFinite())
                assertTrue("$curve went below zero for $input", output >= 0f)
                assertTrue("$curve went above one for $input", output <= 1f)
            }
        }
    }

    @Test
    fun standardEaseCurvesHaveTheExpectedDirection() {
        val easeIn = LaunchTiming(curve = TimeCurve.EASE_IN_CUBIC)
        val easeOut = LaunchTiming(curve = TimeCurve.EASE_OUT_CUBIC)
        val easeInOut = LaunchTiming(curve = TimeCurve.EASE_IN_OUT_CUBIC)

        assertTrue(easeIn.transform(.25f) < .25f)
        assertTrue(easeOut.transform(.25f) > .25f)
        assertEquals(.5f, easeInOut.transform(.5f), .0001f)
    }

    @Test
    fun emphasizedMaterialCurveUsesItsOwnTwoPartShape() {
        val emphasized = LaunchTiming(curve = TimeCurve.MATERIAL_EMPHASIZED)
        val standard = LaunchTiming(curve = TimeCurve.MATERIAL_STANDARD)

        assertTrue(kotlin.math.abs(emphasized.transform(.25f) - standard.transform(.25f)) > .01f)
        assertEquals(.4f, emphasized.transform(.166666f), .0001f)
        assertTrue(emphasized.transform(.75f) > .95f)
    }

    @Test
    fun customCubicBezierCanRepresentLinearAndWebEase() {
        val linear = LaunchTiming(
            curve = TimeCurve.CUSTOM_CUBIC_BEZIER,
            customX1 = 0f,
            customY1 = 0f,
            customX2 = 1f,
            customY2 = 1f,
        )
        val webEase = LaunchTiming(
            curve = TimeCurve.CUSTOM_CUBIC_BEZIER,
            customX1 = .25f,
            customY1 = .1f,
            customX2 = .25f,
            customY2 = 1f,
        )

        assertEquals(.25f, linear.transform(.25f), .0001f)
        assertEquals(.5f, linear.transform(.5f), .0001f)
        assertEquals(1f, linear.transform(1f), 0f)
        assertTrue(webEase.transform(.5f) > .75f)
    }

    @Test
    fun stepsRespectTheRequestedNumberOfStops() {
        val early = LaunchTiming(curve = TimeCurve.STEPS_START, steps = 4)
        val late = LaunchTiming(curve = TimeCurve.STEPS_END, steps = 4)

        assertEquals(.5f, early.transform(.26f), .0001f)
        assertEquals(.25f, late.transform(.26f), .0001f)
    }

    @Test
    fun invalidValuesAreClampedToSafeRanges() {
        val safe = LaunchTiming(
            durationMillis = 1,
            customX1 = Float.NaN,
            customY1 = -10f,
            customX2 = 10f,
            customY2 = 10f,
            strength = Float.POSITIVE_INFINITY,
            steps = 100,
        ).sanitized()

        assertEquals(LaunchTiming.MIN_DURATION_MILLIS, safe.durationMillis)
        assertEquals(LaunchTiming.DEFAULT_CUSTOM_X1, safe.customX1, 0f)
        assertEquals(LaunchTiming.MIN_CUSTOM_Y, safe.customY1, 0f)
        assertEquals(1f, safe.customX2, 0f)
        assertEquals(LaunchTiming.MAX_CUSTOM_Y, safe.customY2, 0f)
        assertEquals(LaunchTiming.DEFAULT_STRENGTH, safe.strength, 0f)
        assertEquals(LaunchTiming.MAX_STRENGTH,
            LaunchTiming(strength = 100f).sanitized().strength, 0f)
        assertEquals(LaunchTiming.MAX_STEPS, safe.steps)
    }
}
