package com.flivoro.tile8auncher.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharmsMotionTest {
    @Test
    fun deceleration_has_finite_clamped_endpoints_and_monotone_progress() {
        assertEquals(0f, CharmsMotion.decelerate(0f), 0f)
        assertEquals(1f, CharmsMotion.decelerate(1f), 0f)
        assertEquals(0f, CharmsMotion.decelerate(-4f), 0f)
        assertEquals(1f, CharmsMotion.decelerate(4f), 0f)
        assertEquals(0f, CharmsMotion.decelerate(Float.NaN), 0f)

        var previous = 0f
        for (step in 1..100) {
            val current = CharmsMotion.decelerate(step / 100f)
            assertTrue("deceleration moved backwards at $step", current >= previous)
            previous = current
        }
        assertTrue(CharmsMotion.decelerate(.5f) > .5f)
    }

    @Test
    fun edge_predicate_requires_right_strip_and_inward_horizontal_slop() {
        assertTrue(CharmsMotion.isWithinRightEdge(985f, 1_000f, 28f))
        assertTrue(CharmsMotion.isWithinRightEdge(999f, 1_000f, 28f))
        assertFalse(CharmsMotion.isWithinRightEdge(970f, 1_000f, 28f))
        assertFalse(CharmsMotion.isWithinRightEdge(1_001f, 1_000f, 28f))

        assertTrue(CharmsMotion.isInwardHorizontalSwipe(-18f, 4f, 12f))
        assertFalse(CharmsMotion.isInwardHorizontalSwipe(18f, 4f, 12f))
        assertFalse(CharmsMotion.isInwardHorizontalSwipe(-18f, 20f, 12f))
        assertFalse(CharmsMotion.isInwardHorizontalSwipe(-8f, 1f, 12f))
    }

    @Test
    fun edge_ui_commits_only_after_deliberate_inward_pull() {
        assertFalse(CharmsMotion.shouldCommitEdgeSwipe(-20f, 2f, 12f, 34f))
        assertTrue(CharmsMotion.shouldCommitEdgeSwipe(-40f, 4f, 12f, 34f))
        assertFalse(CharmsMotion.shouldCommitEdgeSwipe(-40f, 38f, 12f, 34f))
        assertFalse(CharmsMotion.shouldCommitEdgeSwipe(40f, 1f, 12f, 34f))
    }

    @Test
    fun vertical_or_reversed_edge_motion_is_cancelled() {
        assertTrue(CharmsMotion.shouldCancelEdgeSwipe(-10f, 22f, 12f))
        assertTrue(CharmsMotion.shouldCancelEdgeSwipe(14f, 2f, 12f))
        assertFalse(CharmsMotion.shouldCancelEdgeSwipe(-22f, 8f, 12f))
    }

    @Test
    fun vertical_movement_crosses_slop_without_becoming_an_open_gesture() {
        assertTrue(CharmsMotion.hasCrossedTouchSlop(4f, 20f, 12f))
        assertFalse(CharmsMotion.isInwardHorizontalSwipe(4f, 20f, 12f))
    }
}
