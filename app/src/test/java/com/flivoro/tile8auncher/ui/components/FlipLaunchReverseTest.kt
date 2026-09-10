package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FlipLaunchReverseTest {
    @Test
    fun reverseDurationTracksCurrentForwardProgress() {
        assertEquals(1, reverseLaunchDurationMillis(650, 0f))
        assertEquals(163, reverseLaunchDurationMillis(650, .25f))
        assertEquals(325, reverseLaunchDurationMillis(650, .50f))
        assertEquals(488, reverseLaunchDurationMillis(650, .75f))
        assertEquals(650, reverseLaunchDurationMillis(650, 1f))
    }

    @Test
    fun tinyVisibleReverseStillGetsOneDisplayFrame() {
        assertEquals(16, reverseLaunchDurationMillis(650, .01f))
    }

    @Test
    fun progressIsClamped() {
        assertEquals(1, reverseLaunchDurationMillis(650, -1f))
        assertEquals(650, reverseLaunchDurationMillis(650, 2f))
    }
}
