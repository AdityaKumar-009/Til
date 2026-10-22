package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import org.junit.Assert.*
import org.junit.Test

class FlowStoryMotionTest {
    private val source = Rect(32f, 145f, 180f, 293f)
    private val portrait = Rect(0f, 0f, 400f, 850f)
    private val styles = listOf(MasterLaunchAnimation.PASSAGE, MasterLaunchAnimation.CASCADE)

    @Test fun edgesExpandContinuouslyForTilesAndWideRowsInBothOrientations() {
        for (viewport in listOf(portrait, Rect(0f, 0f, 850f, 400f))) {
            val sources = listOf(source, Rect(16f, 145f, viewport.width - 16f, 209f),
                Rect(0f, 0f, 72f, 72f),
                Rect(viewport.width - 72f, viewport.height - 72f, viewport.width, viewport.height))
            for (style in styles) for (anchor in sources) {
                var previous = StoryLaunchMotion.panelAtTravel(style, 0f, anchor, viewport)
                assertEquals(anchor, previous.bounds)
                for (step in 1..1000) {
                    val t = step / 1000f
                    val next = StoryLaunchMotion.panelAtTravel(style, t, anchor, viewport)
                    assertTrue(next.bounds.left <= previous.bounds.left + .001f)
                    assertTrue(next.bounds.top <= previous.bounds.top + .001f)
                    assertTrue(next.bounds.right >= previous.bounds.right - .001f)
                    assertTrue(next.bounds.bottom >= previous.bounds.bottom - .001f)
                    assertTrue(next.bounds.left >= viewport.left - .001f && next.bounds.right <= viewport.right + .001f)
                    assertTrue(next.bounds.top >= viewport.top - .001f && next.bounds.bottom <= viewport.bottom + .001f)
                    assertTrue(kotlin.math.abs(next.rotationX) <= 12.001f)
                    previous = next
                }
                assertEquals(viewport, previous.bounds)
                assertEquals(0f, previous.rotationX, 0f)
            }
        }
    }

    @Test fun backAtAnyLaunchFrameRetracesTheSamePanelAndNeighborPose() {
        val neighbor = Rect(210f, 350f, 358f, 498f)
        for (style in styles) for (step in 0..1000) {
            val raw = step / 1000f
            val travel = StoryLaunchMotion.settle(0f, 1f, raw)
            assertEquals(StoryLaunchMotion.panelAtTravel(style, travel, source, portrait, raw),
                StoryLaunchMotion.panelAtTravel(style, travel, source, portrait, raw, returning = true))
            assertEquals(StoryLaunchMotion.neighborAtTravel(style, travel, neighbor, source, portrait),
                StoryLaunchMotion.neighborAtTravel(style, travel, neighbor, source, portrait, returning = true))
        }
    }

    @Test fun passageOpensTallAndCascadeReleasesUpperRowsFirstWithSingleTileHandoff() {
        val passage = StoryLaunchMotion.panelAtTravel(MasterLaunchAnimation.PASSAGE, .5f, source, portrait)
        val widthFraction = (passage.bounds.width - source.width) / (portrait.width - source.width)
        val heightFraction = (passage.bounds.height - source.height) / (portrait.height - source.height)
        assertTrue(heightFraction - widthFraction > .3f)
        val cascade = StoryLaunchMotion.panelAtTravel(MasterLaunchAnimation.CASCADE, .5f, source, portrait)
        assertEquals(-12f, cascade.rotationX, .0001f)
        assertTrue(kotlin.math.abs(cascade.bounds.width - passage.bounds.width) > 50f)
        val top = Rect(210f, 30f, 358f, 178f)
        val bottom = Rect(210f, 650f, 358f, 798f)
        assertTrue(StoryLaunchMotion.neighborAtTravel(MasterLaunchAnimation.CASCADE,
            .25f, top, source, portrait).y > .01f)
        assertEquals(0f, StoryLaunchMotion.neighborAtTravel(MasterLaunchAnimation.CASCADE,
            .25f, bottom, source, portrait).y, 0f)
        for (style in styles) for (step in 0..1000) {
            val t = step / 1000f
            assertFalse(StoryLaunchMotion.shouldDrawOriginalCell(style, true, false, t))
            val cell = StoryLaunchMotion.shouldDrawOriginalCell(style, true, true, t)
            assertEquals(t == 0f, cell)
            assertEquals(if (cell) 1f else 0f, StoryLaunchMotion.returnToSourceOpacity(style, t), 0f)
        }
    }
}
