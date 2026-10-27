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
    @Test fun childTilesCascadeIndividuallyAndReverseWithoutJumping() {
        val samples = (0..100).map { it / 100f }
        val first = samples.map { PhoneFolderMotion.childFrame(it, 0) }
        val later = samples.map { PhoneFolderMotion.childFrame(it, 6) }
        assertTrue(first[30].childrenOpacity > later[30].childrenOpacity)
        first.zipWithNext().forEach { (a, b) ->
            assertTrue(b.childrenOpacity >= a.childrenOpacity)
            assertTrue(b.childrenOffsetDp <= a.childrenOffsetDp)
            assertTrue(b.childrenScale >= a.childrenScale)
        }
        assertEquals(PhoneFolderMotion.childFrame(.42f, 2),
            PhoneFolderMotion.childFrame(.42f, 2))
        assertEquals(1f, first.last().childrenOpacity, 0f)
        assertEquals(1f, later.last().childrenOpacity, 0f)
    }

    @Test fun folderFocusScrollOnlyMovesWhenChildrenWouldBeClipped() {
        val target = PhoneFolderMotion.focusScrollTarget(
            currentScrollPx = 0, headerTopPx = 650f,
            expandedHeightPx = 480f, viewportHeightPx = 800f,
        )
        assertTrue(target > 0)
        assertTrue(target < 650)
        assertEquals(200, PhoneFolderMotion.focusScrollTarget(
            currentScrollPx = 200, headerTopPx = 260f,
            expandedHeightPx = 220f, viewportHeightPx = 800f,
        ))
        assertEquals(200, PhoneFolderMotion.focusScrollTarget(
            200, 1000f, 0f, 800f,
        ))
    }
    @Test fun physicalFolderTileFoldsAndReversesContinuously() {
        val closed = PhoneFolderMotion.sourceFrame(0f)
        val open = PhoneFolderMotion.sourceFrame(1f)
        assertEquals(1f, closed.alpha, 0f)
        assertEquals(1f, closed.scale, 0f)
        assertEquals(0f, closed.rotationX, 0f)
        assertTrue(open.alpha < closed.alpha)
        assertTrue(open.scale < closed.scale)
        assertTrue(open.rotationX < closed.rotationX)
        val samples = (0..100).map { PhoneFolderMotion.sourceFrame(it / 100f) }
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue(b.alpha <= a.alpha)
            assertTrue(b.scale <= a.scale)
            assertTrue(b.rotationX <= a.rotationX)
            assertTrue(b.offsetDp >= a.offsetDp)
        }
        val halfway = PhoneFolderMotion.sourceFrame(.35f)
        assertEquals(halfway, PhoneFolderMotion.sourceFrame(.35f))
        assertEquals(closed, PhoneFolderMotion.sourceFrame(-1f))
        assertEquals(open, PhoneFolderMotion.sourceFrame(2f))
    }

    @Test fun tallNeighborMovesWhenFolderOpensAtItsOwnBoundary() {
        assertTrue(PhoneFolderMotion.needsInlineDisplacement(
            slotRow = 0, slotRows = 4, boundaryRow = 2, isFolderSource = false))
        assertTrue(PhoneFolderMotion.needsInlineDisplacement(
            slotRow = 2, slotRows = 2, boundaryRow = 2, isFolderSource = false))
        assertTrue(!PhoneFolderMotion.needsInlineDisplacement(
            slotRow = 0, slotRows = 2, boundaryRow = 2, isFolderSource = false))
        assertTrue(!PhoneFolderMotion.needsInlineDisplacement(
            slotRow = 0, slotRows = 2, boundaryRow = 2, isFolderSource = true))
    }

}
