package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StoryLaunchMotionTest {
    private val styles = MasterLaunchAnimation.entries.filter { it.isStory }
    private val viewport = Rect(0f, 0f, 400f, 850f)
    private val source = Rect(32f, 145f, 180f, 293f)

    @Test fun sourceIdentityAndFullWindowAreExactInEveryStory() {
        for (style in styles) {
            val first = StoryLaunchMotion.panel(style, 0f, source, viewport)
            val last = StoryLaunchMotion.panel(style, 1f, source, viewport)
            assertEquals(style.name, source, first.bounds)
            assertEquals(style.name, viewport, last.bounds)
            assertEquals(1f, first.sourceAlpha, .00001f)
            assertEquals(0f, first.surfaceAlpha, .00001f)
            assertEquals(0f, first.contentAlpha, .00001f)
            assertEquals(0f, last.sourceAlpha, .00001f)
            assertEquals(1f, last.surfaceAlpha, .00001f)
            assertEquals(1f, last.contentAlpha, .00001f)
            assertEquals(0f, last.rotationX, .0001f)
            assertEquals(0f, last.rotationY, .0001f)
        }
    }

    @Test fun everyVisibleNeighborParticipatesAndEndsHidden() {
        val neighbors = listOf(Rect(210f, 145f, 350f, 293f), Rect(32f, 350f, 180f, 500f),
            Rect(210f, 550f, 350f, 700f))
        for (style in styles) for (bounds in neighbors) {
            val first = StoryLaunchMotion.neighbor(style, 0f, bounds, source, viewport)
            val middle = StoryLaunchMotion.neighbor(style, .56f, bounds, source, viewport)
            val last = StoryLaunchMotion.neighbor(style, 1f, bounds, source, viewport)
            assertEquals(1f, first.alpha, .00001f)
            assertEquals(0f, first.x, .00001f)
            assertEquals(0f, first.y, .00001f)
            assertEquals(1f, first.scale, .00001f)
            assertTrue("$style leaves a neighboring tile stationary", abs(middle.x) + abs(middle.y) > 1f)
            assertEquals(0f, last.alpha, .00001f)
        }
    }

    @Test fun rippleReachesNearTilesBeforeDistantTiles() {
        val near = Rect(185f, 145f, 250f, 210f)
        val far = Rect(300f, 700f, 390f, 790f)
        val a = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .16f, near, source, viewport)
        val b = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .16f, far, source, viewport)
        assertTrue(abs(a.x) + abs(a.y) > .01f)
        assertEquals(0f, abs(b.x) + abs(b.y), .00001f)
    }

    @Test fun allSamplesStayFiniteAndContinuousIncludingWideRowsAndLandscape() {
        for (vp in listOf(viewport, Rect(0f, 0f, 850f, 400f))) {
            for (bounds in listOf(source, Rect(-40f, -20f, 350f, 40f), Rect.Zero)) {
                val anchor = StoryLaunchMotion.source(bounds, vp)
                for (style in styles) {
                    var previous = StoryLaunchMotion.panel(style, 0f, anchor, vp)
                    val duration = StoryLaunchMotion.duration(style)
                    for (ms in 1..duration) {
                        val f = StoryLaunchMotion.panel(style, ms.toFloat() / duration, anchor, vp)
                        assertTrue(f.bounds.width > 0f && f.bounds.height > 0f)
                        assertTrue(f.bounds.left.isFinite() && f.bounds.top.isFinite())
                        assertTrue(f.rotationX.isFinite() && f.rotationY.isFinite())
                        assertTrue(f.sourceAlpha in 0f..1f && f.surfaceAlpha in 0f..1f)
                        assertTrue(f.contentAlpha in 0f..1f)
                        assertTrue("$style jumps between adjacent samples",
                            abs(f.bounds.left - previous.bounds.left) < vp.width * .025f &&
                                abs(f.bounds.top - previous.bounds.top) < vp.height * .025f)
                        previous = f
                    }
                }
            }
        }
        assertEquals(0f, StoryLaunchMotion.progress(Float.NaN), 0f)
        assertEquals(0f, StoryLaunchMotion.progress(-1f), 0f)
        assertEquals(1f, StoryLaunchMotion.progress(2f), 0f)
    }

    @Test fun interruptedReturnHasNoGeometryDiscontinuity() {
        for (style in styles) {
            val interruption = .413f
            val outgoing = StoryLaunchMotion.panel(style, interruption, source, viewport)
            // Reversal starts at the exact current time; it never jumps to a full
            // window, another preset, or an independently restarted easing curve.
            val returning = StoryLaunchMotion.panel(style, interruption, source, viewport)
            assertEquals(outgoing, returning)
            val preceding = StoryLaunchMotion.panel(style, interruption - .001f, source, viewport)
            assertTrue(abs(outgoing.bounds.center.x - preceding.bounds.center.x) < 2f)
            assertTrue(abs(outgoing.bounds.center.y - preceding.bounds.center.y) < 3f)
        }
    }
}
