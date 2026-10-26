package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneFolderMotionTest {
    @Test fun closedAndOpenEndpointsAreStable() {
        val start = PhoneFolderMotion.frame(0f)
        val end = PhoneFolderMotion.frame(1f)
        assertEquals(0f, start.headerOpacity, 0f)
        assertEquals(0f, start.childrenOpacity, 0f)
        assertEquals(1f, end.headerOpacity, 0f)
        assertEquals(1f, end.childrenOpacity, 0f)
        assertEquals(1f, end.childrenScale, 0f)
        assertEquals(0f, end.childrenOffsetDp, 0f)
    }

    @Test fun revealIsMonotonicAndClampedDuringReversal() {
        val frames = (0..100).map { PhoneFolderMotion.frame(it / 100f) }
        frames.zipWithNext().forEach { (a, b) ->
            assertTrue(b.headerOpacity >= a.headerOpacity)
            assertTrue(b.childrenOpacity >= a.childrenOpacity)
            assertTrue(b.childrenScale >= a.childrenScale)
            assertTrue(b.childrenOffsetDp <= a.childrenOffsetDp)
        }
        assertEquals(PhoneFolderMotion.frame(0f), PhoneFolderMotion.frame(-.2f))
        assertEquals(PhoneFolderMotion.frame(1f), PhoneFolderMotion.frame(1.2f))
    }
}
