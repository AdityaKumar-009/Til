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
            assertEquals(0f, first.rotationZ, .0001f)
            assertEquals(0f, last.rotationZ, .0001f)
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
        val a = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .10f, near, source, viewport)
        val b = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .10f, far, source, viewport)
        assertTrue(abs(a.x) + abs(a.y) > .01f)
        assertEquals(0f, abs(b.x) + abs(b.y), .00001f)
    }

    @Test fun travelKeepsOriginalDurationWithMeasuredReleaseAndCalmFinish() {
        val quarter = StoryLaunchMotion.settle(0f, 1f, .25f)
        val threeQuarters = StoryLaunchMotion.settle(0f, 1f, .75f)
        assertTrue("Opening should accelerate promptly without a kick", quarter > .45f && quarter < .56f)
        assertTrue("The last quarter should gently settle", threeQuarters > .96f)
        var previous = 0f
        for (sample in 0..100) {
            val distance = StoryLaunchMotion.settle(0f, 1f, sample / 100f)
            assertTrue(distance in previous..1f)
            previous = distance
        }
        val originalDurations = mapOf(MasterLaunchAnimation.LIFT to 520,
            MasterLaunchAnimation.DOOR to 560, MasterLaunchAnimation.RIPPLE to 560,
            MasterLaunchAnimation.PAGE_TURN to 600, MasterLaunchAnimation.GLIDE to 520)
        originalDurations.forEach { (style, duration) ->
            assertEquals(duration, StoryLaunchMotion.duration(style))
        }
        styles.forEach { assertTrue(StoryLaunchMotion.duration(it) in 520..600) }
    }

    @Test fun expansionHasNoVelocityStepsBetweenTheOldChoreographyStages() {
        val step = .002f
        for (style in styles) {
            fun channels(time: Float): List<Float> {
                val f = StoryLaunchMotion.panel(style, time, source, viewport)
                return listOf(f.bounds.left / viewport.width, f.bounds.right / viewport.width,
                    f.bounds.top / viewport.height, f.bounds.bottom / viewport.height,
                    f.rotationX / 90f, f.rotationY / 90f, f.rotationZ / 90f)
            }
            val rest = channels(0f)
            val first = channels(step)
            first.indices.forEach { channel ->
                assertTrue("$style kicks directly out of rest",
                    abs(first[channel] - rest[channel]) / step < .12f)
            }
            for (join in listOf(.08f, .10f, .13f, .20f, .23f, .82f, .88f, .90f, .92f)) {
                val before = channels(join - step)
                val at = channels(join)
                val after = channels(join + step)
                at.indices.forEach { channel ->
                    val incoming = (at[channel] - before[channel]) / step
                    val outgoing = (after[channel] - at[channel]) / step
                    assertTrue("$style changes velocity abruptly at $join, channel $channel",
                        abs(outgoing - incoming) < .25f)
                }
            }
        }
    }

    @Test fun artworkAndAppOverlapWithoutABlankBeatOrFirstReturnFrameCut() {
        for (style in styles) {
            for (sample in 0..StoryLaunchMotion.duration(style)) {
                val f = StoryLaunchMotion.panel(style,
                    sample.toFloat() / StoryLaunchMotion.duration(style), source, viewport)
                assertEquals("$style loses both identities during the handoff",
                    1f, f.sourceAlpha + f.contentAlpha, .00001f)
            }
            val easing = StoryLaunchMotion.returnEasing(1f)
            val firstFrameTime = 16f / StoryLaunchMotion.duration(style)
            val firstReturn = StoryLaunchMotion.panel(style,
                1f - easing.transform(firstFrameTime), source, viewport)
            assertTrue("$style cuts away the app on the first Back frame", firstReturn.contentAlpha > .99f)
        }
    }

    @Test fun wideRowArtworkKeepsItsLeadingGlyphInsideTheWindow() {
        val row = Rect(16f, 145f, 384f, 209f)
        for (style in styles) for (sample in 0..100) {
            val f = StoryLaunchMotion.panel(style, sample / 100f, row, viewport)
            assertTrue("$style enlarges the row beyond the viewport",
                row.width * f.sourceScale <= viewport.width + .001f)
        }
    }

    @Test fun fullAndInterruptedReturnsKeepThePathButDecelerateIntoTheSource() {
        for (start in listOf(1f, .413f, .08f)) {
            val easing = StoryLaunchMotion.returnEasing(start)
            val startingDistance = StoryLaunchMotion.settle(0f, 1f, start)
            assertEquals(0f, easing.transform(0f), 0f)
            assertEquals(1f, easing.transform(1f), 0f)
            var previous = startingDistance
            for (sample in 0..100) {
                val time = sample / 100f
                val raw = start * (1f - easing.transform(time))
                val distance = StoryLaunchMotion.settle(0f, 1f, raw)
                assertTrue("Return must not reverse direction", distance <= previous + .0001f)
                assertEquals("Return must retrace the lead curve with a soft landing",
                    startingDistance * (1f - StoryLaunchMotion.settle(0f, 1f, time)), distance, .0001f)
                previous = distance
            }
        }
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
                        assertTrue(f.rotationX.isFinite() && f.rotationY.isFinite() && f.rotationZ.isFinite())
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
            // window or another preset when the return clock changes its pacing.
            val returning = StoryLaunchMotion.panel(style, interruption, source, viewport)
            assertEquals(outgoing, returning)
            val preceding = StoryLaunchMotion.panel(style, interruption - .001f, source, viewport)
            assertTrue(abs(outgoing.bounds.center.x - preceding.bounds.center.x) < 2f)
            assertTrue(abs(outgoing.bounds.center.y - preceding.bounds.center.y) < 3f)
        }
    }

    @Test fun naturalOptionsExpandWithoutBounceOrDeepRotations() {
        val natural = listOf(MasterLaunchAnimation.FOCUS, MasterLaunchAnimation.ARC,
            MasterLaunchAnimation.UNFOLD)
        for (style in natural) for (anchor in listOf(source, Rect(16f, 145f, 384f, 209f))) {
            var previous = StoryLaunchMotion.panel(style, 0f, anchor, viewport)
            for (sample in 1..200) {
                val f = StoryLaunchMotion.panel(style, sample / 200f, anchor, viewport)
                assertTrue("$style reverses width mid-expansion", f.bounds.width >= previous.bounds.width - .001f)
                assertTrue("$style reverses height mid-expansion", f.bounds.height >= previous.bounds.height - .001f)
                assertTrue(abs(f.rotationX) <= 10f && abs(f.rotationY) <= 10f && abs(f.rotationZ) <= 6f)
                if (style == MasterLaunchAnimation.FOCUS) {
                    assertEquals(0f, abs(f.rotationX) + abs(f.rotationY) + abs(f.rotationZ), 0f)
                }
                previous = f
            }
        }
    }

    @Test fun arcMirrorsTheSourceAndHasNoDirectionSnapAtScreenCenter() {
        fun mirrored(rect: Rect) = Rect(viewport.width - rect.right, rect.top,
            viewport.width - rect.left, rect.bottom)
        val centered = Rect(126f, 145f, 274f, 293f)
        for (sample in 0..100) {
            val t = sample / 100f
            val left = StoryLaunchMotion.panel(MasterLaunchAnimation.ARC, t, source, viewport)
            val right = StoryLaunchMotion.panel(MasterLaunchAnimation.ARC, t, mirrored(source), viewport)
            assertEquals(viewport.width, left.bounds.center.x + right.bounds.center.x, .001f)
            assertEquals(left.bounds.center.y, right.bounds.center.y, .001f)
            assertEquals(left.rotationZ, -right.rotationZ, .001f)
            val a = StoryLaunchMotion.panel(MasterLaunchAnimation.ARC, t,
                Rect(125.9f, 145f, 273.9f, 293f), viewport)
            val b = StoryLaunchMotion.panel(MasterLaunchAnimation.ARC, t, centered, viewport)
            assertTrue(abs(a.bounds.center.x - b.bounds.center.x) < .11f)
            assertTrue(abs(a.rotationZ - b.rotationZ) < .01f)
        }
    }
}
