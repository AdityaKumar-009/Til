package com.flivoro.tile8auncher.ui.phone

import androidx.compose.ui.geometry.Rect
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
    @Test fun childTravelsFromItsMiniatureToItsOwnFullSizeRectangle() {
        val origin = Rect(72f, -160f, 108f, -124f)
        val target = Rect(182f, 0f, 350f, 168f)
        fun bounds(p: Float): Rect {
            val f = PhoneFolderMotion.childTransform(p, 2, origin, target)
            val x = target.left + f.translationX
            val y = target.top + f.translationY
            return Rect(x, y, x + target.width * f.scaleX, y + target.height * f.scaleY)
        }
        val start = bounds(0f)
        assertEquals(origin.left, start.left, .001f)
        assertEquals(origin.top, start.top, .001f)
        assertEquals(origin.width, start.width, .001f)
        assertEquals(origin.height, start.height, .001f)
        assertEquals(target, bounds(1f))
        val frames = (0..100).map { bounds(it / 100f) }
        frames.zipWithNext().forEach { (a, b) ->
            assertTrue(b.left >= a.left)
            assertTrue(b.top >= a.top)
            assertTrue(b.width >= a.width - .001f)
            assertTrue(b.height >= a.height - .001f)
        }
        val last = PhoneFolderMotion.childTransform(1f, 40, origin, target)
        assertEquals(1f, last.alpha, 0f)
        assertEquals(1f, last.scaleX, 0f)
        assertEquals(1f, last.scaleY, 0f)
    }

    @Test fun remainingTimeTracksTheInterruptedDistance() {
        assertEquals(380, PhoneFolderMotion.remainingDuration(0f, true))
        assertEquals(420, PhoneFolderMotion.remainingDuration(1f, false))
        assertEquals(105, PhoneFolderMotion.remainingDuration(.25f, false))
        assertEquals(95, PhoneFolderMotion.remainingDuration(.75f, true))
        assertEquals(0, PhoneFolderMotion.remainingDuration(0f, false))
        assertEquals(0, PhoneFolderMotion.remainingDuration(1f, true))
    }

    @Test fun bothDirectionsEaseIntoAndOutOfTheTransition() {
        val positions = (0..100).map {
            PhoneFolderMotion.spaceProgress(PhoneFolderMotion.clockEasing.transform(it / 100f))
        }
        val steps = positions.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it >= 0f })
        assertTrue("Opening and closing must start gently", steps.first() < steps[50] * .2f)
        assertTrue("Opening and closing must settle gently", steps.last() < steps[50] * .2f)
        assertTrue("Collapse must not be shorter than expansion",
            PhoneFolderMotion.CLOSE_MS >= PhoneFolderMotion.OPEN_MS)
    }

    @Test fun miniatureHandoffNeverLeavesBothRepresentationsInvisible() {
        val rect = Rect(0f, 0f, 100f, 100f)
        (0..100).forEach { i ->
            val p = i / 100f
            val child = PhoneFolderMotion.childTransform(p, 7, rect, rect)
            assertEquals(1f, PhoneFolderMotion.previewOpacity(p) + child.alpha, .0001f)
        }
        assertEquals(1f, PhoneFolderMotion.collapseOpacity(1f), 0f)
    }

    @Test fun miniatureBoundsFitSmallMediumAndWideFolderFaces() {
        val slots = listOf(PhoneTileSlot(0, 0, 2, 2), PhoneTileSlot(2, 0, 2, 2),
            PhoneTileSlot(0, 2, 4, 2))
        for ((width, height) in listOf(82f to 82f, 168f to 168f, 340f to 168f)) {
            val rects = PhoneFolderMotion.previewBounds(slots, width, height)
            rects.forEach {
                assertTrue(it.left >= 0f && it.top >= 0f)
                assertTrue(it.right <= width && it.bottom <= height)
            }
            assertTrue(rects[0].right < rects[1].left)
            assertTrue(rects[0].bottom < rects[2].top)
        }
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
    @Test fun sourcePlateSettlesToAnUprightCollapseTarget() {
        val closed = PhoneFolderMotion.sourceFrame(0f)
        val open = PhoneFolderMotion.sourceFrame(1f)
        val press = PhoneFolderMotion.sourceFrame(.12f)
        assertEquals(closed, open)
        assertEquals(1f, open.alpha, 0f)
        assertEquals(1f, open.scale, 0f)
        assertTrue(press.scale < closed.scale)
        (0..100).forEach { i ->
            val frame = PhoneFolderMotion.sourceFrame(i / 100f)
            assertEquals(0f, frame.rotationX, 0f)
            assertEquals(0f, frame.offsetDp, 0f)
            assertTrue(frame.scale in .96f..1f)
        }
    }

    @Test fun spanningNeighborClearsTheEntireFolderAndKeepsItsRelativeSpacing() {
        val slots = listOf(PhoneTileSlot(0, 0, 2, 2), PhoneTileSlot(2, 0, 2, 4),
            PhoneTileSlot(0, 2, 2, 2), PhoneTileSlot(0, 4, 4, 2))
        val boundary = 2
        val clearance = PhoneFolderMotion.overlappingRows(slots, 0, boundary)
        assertEquals(2, clearance)
        val step = 86f
        val folderHeight = 230f
        slots.forEachIndexed { i, slot ->
            if (PhoneFolderMotion.needsInlineDisplacement(slot.row, slot.rows, boundary, i == 0)) {
                val top = slot.row * step + folderHeight + clearance * step
                assertTrue(top >= boundary * step + folderHeight)
            }
        }
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
