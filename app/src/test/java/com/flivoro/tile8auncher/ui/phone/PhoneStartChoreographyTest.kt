package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneStartChoreographyTest {
    private val eight = LauncherUiMode.PHONE_8
    private val ten = LauncherUiMode.MOBILE_10
    private val height = 850f
    private val width = 360f

    @Test fun classicTimingMatchesDiscoLauncherAtReferenceViewport() {
        assertEquals(700, PhoneStartChoreography.totalMillis(eight, true, height))
        assertEquals(700, PhoneStartChoreography.totalMillis(eight, false, height))
        assertEquals(675, PhoneStartChoreography.totalMillis(eight, true, 637.5f))
        assertEquals(534, PhoneStartChoreography.totalMillis(ten, true))
    }

    @Test fun classicCascadeUsesDiscosZeroToOneVisibleIndexRange() {
        assertEquals(0f, PhoneStartChoreography.visibleTileAnimationIndex(0, 15), 0f)
        assertEquals(.5f, PhoneStartChoreography.visibleTileAnimationIndex(7, 15), 0f)
        assertEquals(1f, PhoneStartChoreography.visibleTileAnimationIndex(14, 15), 0f)
        assertEquals(0f, PhoneStartChoreography.visibleTileAnimationIndex(0, 1), 0f)
        assertEquals(2000f, PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX, 0f)
    }

    @Test fun classicExitUsesReverseVisibleOrderAndSelectedTileDelay() {
        assertEquals(0, PhoneStartChoreography.delayMillis(eight, true, 0f, height))
        assertEquals(200, PhoneStartChoreography.delayMillis(eight, true, 1f, height))
        assertEquals(200, PhoneStartChoreography.delayMillis(
            eight, true, .95f, height, selected = true,
        ))

        val first = classicFrame(exiting = true, elapsed = 0, index = 0f)
        val last = classicFrame(exiting = true, elapsed = 0, index = 1f)
        assertEquals(1f, first.alpha, 0f)
        assertEquals(1f, last.alpha, 0f)
        assertEquals(0f, first.rotationY, 0f)
        assertEquals(0f, last.rotationY, 0f)
        assertEquals(0f, classicFrame(true, 100, 0f, selected = true).rotationY, 0f)
        assertEquals(0f, classicFrame(true, 500, 0f, selected = true).alpha, 0f)
        assertEquals(0f, classicFrame(true, 375, 1f).alpha, 0f)
    }

    @Test fun classicExitUsesFortyDegreeLeftEdgeTurnAndDepthTranslation() {
        val tileLeft = 120f
        val final = classicFrame(true, 400, index = 0f, tileLeft = tileLeft)
        assertEquals(-40f, final.rotationY, .001f)
        assertEquals(-90f + tileLeft * (kotlin.math.cos(Math.PI / 6).toFloat() - 1f),
            final.translationXPx, .001f)
        assertEquals(tileLeft * .5f, final.translationZPx, .001f)
        assertEquals(0f, final.pivotX, 0f)
        assertEquals(0f, final.alpha, 0f)

        val selectedBeforeDelay = classicFrame(true, 199, 0f, selected = true)
        assertEquals(0f, selectedBeforeDelay.rotationY, 0f)
        assertEquals(1f, selectedBeforeDelay.alpha, 0f)
    }

    @Test fun classicReturnTurnsOuterFaceAndInnerContentOnSeparateTracks() {
        val first = classicFrame(exiting = false, elapsed = 0, index = 0f)
        val delayed = classicFrame(exiting = false, elapsed = 0, index = 1f)
        assertEquals(1f, first.alpha, 0f)
        assertEquals(0f, delayed.alpha, 0f)
        assertEquals(70f, first.rotationY, .001f)
        assertEquals(0f, classicFrame(false, 199, 1f).alpha, 0f)
        assertEquals(1f, classicFrame(false, 200, 1f).alpha, 0f)
        assertEquals(0f, classicFrame(false, 700, 1f).rotationY, .001f)

        val content = PhoneStartChoreography.sampleInnerEntry(0, 0f, height, 1f)
        assertEquals(45f, content.rotationY, .001f)
        assertEquals(60f, content.translationXPx, .001f)
        val settledContent = PhoneStartChoreography.sampleInnerEntry(350, 0f, height, 1f)
        assertEquals(0f, settledContent.rotationY, .001f)
        assertEquals(0f, settledContent.translationXPx, .001f)
        assertEquals(0f, classicFrame(false, 700, 0f).rotationY, .001f)
    }

    @Test fun classicBackReturnMatchesDiscoBackKeyframeMatrixAndReveal() {
        val start = PhoneStartChoreography.sample(
            mode = eight,
            exiting = false,
            elapsedMillis = 100,
            mobileRowFraction = 0f,
            animationIndex = .5f,
            viewportHeightCssPx = height,
            viewportWidthCssPx = width,
            tileLeftCssPx = 120f,
            tileWidthCssPx = 80f,
            resumeUsesBackMotion = true,
        )
        assertEquals(0f, start.alpha, 0f)
        assertEquals(-80f, start.rotationY, .001f)
        assertEquals(-124.74597f, start.translationXPx, .001f)
        assertEquals(-52.23689f, start.translationZPx, .001f)
        assertEquals(-1.5f, start.pivotX, .001f)

        val firstRevealSegment = PhoneStartChoreography.sample(
            mode = eight,
            exiting = false,
            elapsedMillis = 101,
            mobileRowFraction = 0f,
            animationIndex = .5f,
            viewportHeightCssPx = height,
            viewportWidthCssPx = width,
            tileLeftCssPx = 120f,
            tileWidthCssPx = 80f,
            resumeUsesBackMotion = true,
        )
        assertEquals(.89143f, firstRevealSegment.alpha, .001f)

        val revealed = PhoneStartChoreography.sample(
            mode = eight,
            exiting = false,
            elapsedMillis = 110,
            mobileRowFraction = 0f,
            animationIndex = .5f,
            viewportHeightCssPx = height,
            viewportWidthCssPx = width,
            tileLeftCssPx = 120f,
            tileWidthCssPx = 80f,
            resumeUsesBackMotion = true,
        )
        assertEquals(1f, revealed.alpha, .001f)
        assertTrue("Back return must already be turning toward the front",
            revealed.rotationY > start.rotationY && revealed.rotationY < 0f)

        val settled = PhoneStartChoreography.sample(
            mode = eight,
            exiting = false,
            elapsedMillis = 600,
            mobileRowFraction = 0f,
            animationIndex = .5f,
            viewportHeightCssPx = height,
            viewportWidthCssPx = width,
            tileLeftCssPx = 120f,
            tileWidthCssPx = 80f,
            resumeUsesBackMotion = true,
        )
        assertEquals(1f, settled.alpha, 0f)
        assertEquals(0f, settled.rotationY, .001f)
        assertEquals(0f, settled.translationXPx, .001f)
        assertEquals(0f, settled.translationZPx, .001f)
    }

    @Test fun classicAppsListExitUsesItsSeparateSelectedDelayAndPerspectiveShift() {
        assertEquals(.5f, PhoneStartChoreography.appListAnimationIndex(0, 0, 2), .001f)
        assertEquals(.25f, PhoneStartChoreography.appListAnimationIndex(1, 0, 2), .001f)
        assertEquals(0f, PhoneStartChoreography.appListAnimationIndex(2, 0, 2), .001f)

        val selectedBefore = PhoneStartChoreography.sampleAppListExit(299, 0f, height, width, selected = true)
        assertEquals(1f, selectedBefore.alpha, 0f)
        assertEquals(0f, selectedBefore.rotationY, 0f)
        val selectedFinished = PhoneStartChoreography.sampleAppListExit(500, 0f, height, width, selected = true)
        assertEquals(-90f, selectedFinished.rotationY, .001f)
        assertEquals(-1000f / 3f, selectedFinished.translationXPx, .001f)
        assertEquals(0f, selectedFinished.alpha, 0f)

        val letter = PhoneStartChoreography.sampleAppListExit(400, 0f, height, width, letter = true)
        assertEquals(-width - 1000f / 3f, letter.translationXPx, .001f)
        assertEquals(0f, letter.pivotX, 0f)
    }

    @Test fun classicSamplesStayFiniteAndReachRestStates() {
        for (exiting in listOf(true, false)) {
            val total = PhoneStartChoreography.totalMillis(eight, exiting, height)
            for (ms in 0..(total + 20) step 10) {
                for (index in listOf(0f, .25f, .5f, .75f, 1f)) {
                    val frame = classicFrame(exiting, ms, index)
                    assertTrue(frame.alpha in 0f..1f)
                    assertTrue(frame.rotationY.isFinite())
                    assertTrue(frame.translationXPx.isFinite())
                    assertTrue(frame.translationZPx.isFinite())
                    assertTrue(frame.scale.isFinite())
                }
            }
        }
    }

    @Test fun selectedMobileTileKeepsTheSameZoomAtTheSameClockTime() {
        val selected = PhoneStartChoreography.sample(
            ten, true, 167, .4f, 0f, height, width, 0f, selected = true,
        )
        val other = PhoneStartChoreography.sample(
            ten, true, 100, .4f, .4f, height, width, 120f, selected = false,
        )
        assertEquals(other.scale, selected.scale, .00001f)
        assertEquals(other.alpha, selected.alpha, .00001f)
    }

    private fun classicFrame(
        exiting: Boolean,
        elapsed: Int,
        index: Float,
        selected: Boolean = false,
        tileLeft: Float = 120f,
    ) = PhoneStartChoreography.sample(
        mode = eight,
        exiting = exiting,
        elapsedMillis = elapsed,
        mobileRowFraction = .5f,
        animationIndex = index,
        viewportHeightCssPx = height,
        viewportWidthCssPx = width,
        tileLeftCssPx = tileLeft,
        selected = selected,
    )
}
