package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import com.flivoro.tile8auncher.ui.components.connectedStoryBackgroundAlpha
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.data.LaunchTiming
import com.flivoro.tile8auncher.data.TimeCurve
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StoryLaunchMotionTest {
    private val styles = MasterLaunchAnimation.entries.filter { it.isStory }
    private val viewport = Rect(0f, 0f, 400f, 850f)
    private val source = Rect(32f, 145f, 180f, 293f)

    @Test fun solidTileGrowsWithoutASecondSurfaceFade() {
        // The expanding colored plane must exist at exactly the tapped tile's
        // first frame; a separate 0..30% alpha ramp made a large tile pop in.
        for (style in styles) {
            val start = StoryLaunchMotion.panel(style, 0f, source, viewport)
            assertEquals(source, start.bounds)
            for (time in listOf(0f, .01f, .05f, .25f, .50f, 1f)) {
                assertEquals("$style lost its continuous opaque surface at $time",
                    1f, connectedStoryBackgroundAlpha(1f, time), .00001f)
            }
        }
        // Wallpaper/translucent tiles keep their original 50%-duration
        // arrival at solid accent instead of snapping to an opaque card.
        assertEquals(0f, connectedStoryBackgroundAlpha(0f, 0f), 0f)
        assertEquals(.5f, connectedStoryBackgroundAlpha(0f, .25f), .00001f)
        assertEquals(1f, connectedStoryBackgroundAlpha(0f, .5f), .00001f)
        // Photo tiles are visually fully painted. They must NOT reveal
        // wallpaper/neighbor tiles for half the animation just because their
        // underlying solid accent initially has opacity zero.
        for (time in listOf(0f, .01f, .08f, .20f, .25f, .49f, .50f, 1f)) {
            assertEquals("A photo tile became see-through at $time",
                1f, connectedStoryBackgroundAlpha(0f, time,
                    sourcePictureMode = true), .00001f)
        }
        // Explicit semi-transparent tiles should preserve their original
        // opacity at tap rather than suddenly becoming fully transparent.
        assertEquals(.75f, connectedStoryBackgroundAlpha(.75f, 0f), .00001f)
        assertEquals(.875f, connectedStoryBackgroundAlpha(.75f, .25f), .00001f)
        assertEquals(1f, connectedStoryBackgroundAlpha(.75f, .5f), .00001f)
    }

    @Test fun allOptionalPresetsUseCustomDurationAndSpatialCurve() {
        val normal = LaunchTiming()
        val custom = LaunchTiming(durationMillis = 800,
            durationOverridden = true, curve = TimeCurve.EASE_IN_CUBIC)
        for (style in styles) {
            assertEquals(StoryLaunchMotion.duration(style),
                StoryLaunchMotion.effectiveDuration(style, normal))
            assertEquals(800, StoryLaunchMotion.effectiveDuration(style, custom))
            assertEquals(.125f, StoryLaunchMotion.traveledPhase(.5f, custom), .0001f)
            val ref = StoryLaunchMotion.traveledPhase(.5f, normal)
            assertTrue("Reference should preserve the original emphatic curve", ref > .5f)
            val frame = StoryLaunchMotion.panelAtTravel(style,
                StoryLaunchMotion.traveledPhase(.5f, custom), source, viewport, .5f)
            assertTrue("$style failed to respect custom slower spatial progress",
                frame.bounds.width < viewport.width)
        }
    }

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
        val a = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .015f, near, source, viewport)
        val b = StoryLaunchMotion.neighbor(MasterLaunchAnimation.RIPPLE, .015f, far, source, viewport)
        assertTrue(abs(a.x) + abs(a.y) > .01f)
        assertEquals(0f, abs(b.x) + abs(b.y), .00001f)
    }

    @Test fun travelKeepsOriginalDurationWithMeasuredReleaseAndCalmFinish() {
        val quarter = StoryLaunchMotion.settle(0f, 1f, .25f)
        val threeQuarters = StoryLaunchMotion.settle(0f, 1f, .75f)
        assertTrue("Emphasized decelerate must cover most of the distance early", quarter > .80f && quarter < .86f)
        assertTrue("The last quarter should settle quietly", threeQuarters > .985f)
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

    @Test fun expansionIsContinuousAcrossEveryFormerChoreographySeam() {
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
                assertTrue("$style makes an excessive first-frame leap",
                    abs(first[channel] - rest[channel]) < .14f)
            }
            for (join in listOf(.08f, .10f, .13f, .20f, .23f, .82f, .88f, .90f, .92f)) {
                val before = channels(join - step)
                val at = channels(join)
                val after = channels(join + step)
                at.indices.forEach { channel ->
                    // Keep the original emphasized fast-out curve; it has
                    // changing acceleration by design. Only a discontinuity in
                    // actual rendered position/rotation is a morph regression.
                    assertTrue("$style teleports at $join, channel $channel",
                        abs(at[channel] - before[channel]) < .06f &&
                            abs(after[channel] - at[channel]) < .06f)
                }
            }
        }
    }

    @Test fun foregroundIconKeepsItsOriginalSmallToSlightlyLargerScale() {
        // The surface should fill the viewport, but the icon must NOT fill it.
        // This protects against drawing the entire tile bitmap with
        // ContentScale.FillBounds (giant glyph -> tiny glyph on every tap).
        for (style in styles) {
            var previous = 0f
            for (step in 0..200) {
                val raw = step / 200f
                val frame = StoryLaunchMotion.panel(style, raw, source, viewport)
                assertTrue("$style magnifies the logo beyond its old 1.3x cap",
                    frame.sourceScale <= 1.45f)
                assertTrue("$style shrinks the logo while launching",
                    frame.sourceScale + .04f >= previous)
                previous = frame.sourceScale
            }
            val completed = StoryLaunchMotion.panel(style, 1f, source, viewport)
            assertEquals("$style does not fill the destination panel",
                viewport, completed.bounds)
        }
    }

    @Test fun fourVisibleStoriesNeverReboundDuringFinalReturnFrames() {
        // Ripple/Door once compressed a nearly full-width row BELOW its
        // original size before expanding again over the last few Back frames.
        // Also test Focus/Uncover because every preset shares the final
        // compositor-to-real-cell ownership transfer.
        val shown = listOf(
            MasterLaunchAnimation.RIPPLE,
            MasterLaunchAnimation.FOCUS,
            MasterLaunchAnimation.DOOR,
            MasterLaunchAnimation.UNFOLD,
        )
        val anchors = listOf(
            source,
            Rect(16f, 145f, 384f, 209f), // nearly full-width All Apps row
            Rect(16f, 145f, 384f, 293f), // wide tile
        )
        for (style in shown) for (anchor in anchors) {
            var previous = StoryLaunchMotion.panelAtTravel(
                style, 0f, anchor, viewport, 0f, returning = true)
            assertEquals("$style reverse must end at the untouched tile",
                anchor, previous.bounds)
            for (step in 1..1000) {
                val travel = step / 1000f
                val next = StoryLaunchMotion.panelAtTravel(
                    style, travel, anchor, viewport, travel, returning = true)
                assertTrue("$style briefly becomes narrower than its source at $travel",
                    next.bounds.width >= anchor.width - .001f)
                assertTrue("$style width rebounds near source at $travel",
                    next.bounds.width >= previous.bounds.width - .001f)
                assertTrue("$style height rebounds near source at $travel",
                    next.bounds.height >= previous.bounds.height - .001f)
                previous = next
            }
        }
    }

    @Test fun rippleReturnNeighborsDoNotPlayASecondWaveOrScaleBounce() {
        val other = Rect(210f, 145f, 350f, 293f)
        var previous = StoryLaunchMotion.neighborAtTravel(
            MasterLaunchAnimation.RIPPLE, 0f, other, source, viewport,
            returning = true)
        assertEquals(1f, previous.scale, .00001f)
        assertEquals(0f, previous.rotationX, .00001f)
        assertEquals(0f, previous.rotationY, .00001f)
        for (tick in 1..1000) {
            val move = tick / 1000f
            val next = StoryLaunchMotion.neighborAtTravel(
                MasterLaunchAnimation.RIPPLE, move, other, source, viewport,
                returning = true)
            assertTrue("Ripple neighbor rebounds while reversing", next.scale <= previous.scale + .0001f)
            assertEquals("Ripple reintroduces a rotation on return", 0f, next.rotationX, .0001f)
            assertEquals("Ripple reintroduces a rotation on return", 0f, next.rotationY, .0001f)
            previous = next
        }
        // The opening wave is unchanged; only the backward pulse is removed.
        val opening = StoryLaunchMotion.neighborAtTravel(
            MasterLaunchAnimation.RIPPLE, .55f, other, source, viewport)
        assertTrue(kotlin.math.abs(opening.rotationX) + kotlin.math.abs(opening.rotationY) > .01f)
    }

    @Test fun finalCellHandoffOnlyHappensAtSubpixelGeometry() {
        val handoff = StoryLaunchMotion.RETURN_HANDOFF_TRAVEL
        assertEquals(1f, StoryLaunchMotion.returnToSourceOpacity(0f), 0f)
        assertEquals(0f, StoryLaunchMotion.returnToSourceOpacity(handoff), .00001f)
        for (style in listOf(MasterLaunchAnimation.RIPPLE, MasterLaunchAnimation.FOCUS,
                MasterLaunchAnimation.DOOR, MasterLaunchAnimation.UNFOLD)) {
            val frame = StoryLaunchMotion.panelAtTravel(
                style, handoff, source, viewport, handoff, returning = true)
            assertTrue("$style transfers with a visibly oversized panel",
                kotlin.math.abs(frame.bounds.width - source.width) < 1f)
            assertTrue("$style transfers with a visibly tall panel",
                kotlin.math.abs(frame.bounds.height - source.height) < 1f)
        }
    }

    @Test fun returningSourceCellBlendsWithMovingTileWithoutBlink() {
        // The physical Start/All Apps cell owns the final pose before the
        // overlay is unmounted. Their weights are complementary in both
        // directions, including the exact handoff at the source position.
        for (step in 0..300) {
            val travel = step / 1000f
            val restored = StoryLaunchMotion.returnToSourceOpacity(travel)
            val animated = 1f - restored
            assertTrue(restored in 0f..1f)
            assertEquals("Return leaves a missing tile at $travel",
                1f, restored + animated, .000001f)
        }
        assertEquals(1f, StoryLaunchMotion.returnToSourceOpacity(0f), .00001f)
        assertEquals(0f, StoryLaunchMotion.returnToSourceOpacity(.30f), .00001f)
    }

    @Test fun onlyTheCenteredIconRemainsAtExactClockMidpointAndReturnIsSymmetric() {
        for (style in styles) {
            val halfway = StoryLaunchMotion.panel(style, .50f, source, viewport)
            assertTrue("$style does not grow the connected tile", halfway.bounds.width > source.width)
            assertEquals("$style retains extra tile content at the midpoint",
                1f, StoryLaunchMotion.iconOnlyOpacity(.50f), .00001f)
            assertEquals(0f, StoryLaunchMotion.titleOpacity(.50f), .00001f)
            assertEquals(0f, StoryLaunchMotion.iconOnlyOpacity(0f), .00001f)
            for (tick in 0..100) {
                val p = tick / 100f
                val focus = StoryLaunchMotion.iconOnlyOpacity(p)
                assertTrue(focus in 0f..1f)
                assertEquals("$style leaves a hole during the title-to-icon handoff",
                    1f, focus + (1f - focus), .00001f)
                // Both directions use this exact clock channel, so a Back
                // pass from 1 -> 0 restores all tile details by the endpoint.
                assertEquals(focus, StoryLaunchMotion.iconOnlyOpacity(p), .00001f)
            }
        }
    }

    @Test fun tileTextDisappearsOnOpeningAndReappearsOnBack() {
        val times = listOf(0f, .12f, .20f, .28f, .36f, .42f, .6f, 1f)
        val values = times.map(StoryLaunchMotion::titleOpacity)
        assertEquals(1f, values.first(), .00001f)
        assertEquals(0f, values.last(), .00001f)
        values.zipWithNext().forEach { (before, after) ->
            assertTrue("Source title jumped instead of fading", after <= before)
        }
        val returned = times.asReversed().map(StoryLaunchMotion::titleOpacity)
        returned.zipWithNext().forEach { (before, after) ->
            assertTrue("Back did not fade the source title back in", after >= before)
        }
    }

    @Test fun sourceFaceStaysAttachedAcrossTheRealLaunchMidpoint() {
        for (style in styles) {
            val start = StoryLaunchMotion.panel(style, 0f, source, viewport)
            val middle = StoryLaunchMotion.panel(style, .5f, source, viewport)
            val handoff = StoryLaunchMotion.panel(style, .82f, source, viewport)
            val finished = StoryLaunchMotion.panel(style, 1f, source, viewport)
            assertEquals("$style must preserve the tapped tile at the beginning",
                1f, start.sourceAlpha, .00001f)
            assertEquals("$style must keep the source visible at 50% clock time",
                1f, middle.sourceAlpha, .00001f)
            assertTrue("$style has not expanded its source by the midpoint",
                middle.bounds.width > source.width && middle.bounds.height > source.height)
            assertTrue("$style needs an overlap of source and app identities",
                handoff.sourceAlpha > 0f && handoff.contentAlpha > 0f)
            assertEquals("$style did not finish its destination handoff",
                1f, finished.contentAlpha, .00001f)
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
                    startingDistance * (1f - StoryLaunchMotion.ease(0f, 1f, time)), distance, .0001f)
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
                            abs(f.bounds.left - previous.bounds.left) < vp.width * .06f &&
                                abs(f.bounds.top - previous.bounds.top) < vp.height * .06f)
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
    @Test fun sharedEasingPathMatchesOriginalPanelAndNeighbors() {
        val neighborBounds = Rect(210f, 145f, 350f, 293f)
        for (style in styles) for (tick in 0..100) {
            val t = tick / 100f
            val traveled = StoryLaunchMotion.settle(0f, 1f, t)
            assertEquals(
                "Pre-eased panel changed the story geometry",
                StoryLaunchMotion.panel(style, t, source, viewport),
                StoryLaunchMotion.panelAtTravel(style, traveled, source, viewport, t),
            )
            assertEquals(
                "Pre-eased neighbors diverged from the selected source",
                StoryLaunchMotion.neighbor(style, t, neighborBounds, source, viewport),
                StoryLaunchMotion.neighborAtTravel(style, traveled, neighborBounds, source, viewport),
            )
        }
    }

}
