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

    @Test fun classicTracksFinishWithoutDeadLaunchPadding() {
        assertEquals(500, PhoneStartChoreography.totalMillis(eight, true, height))
        assertEquals(550, PhoneStartChoreography.totalMillis(eight, false, height))
        assertEquals(500, PhoneStartChoreography.totalMillis(eight, true, 637.5f))
        assertEquals(527, PhoneStartChoreography.totalMillis(eight, false, 637.5f))
        assertEquals(534, PhoneStartChoreography.totalMillis(ten, true))
    }

    @Test fun classicCascadeReservesIndexZeroForTheIconBanner() {
        assertEquals(.07f, PhoneStartChoreography.visibleTileAnimationIndex(0, 15), 0f)
        assertEquals(.53f, PhoneStartChoreography.visibleTileAnimationIndex(7, 15), 0f)
        assertEquals(1f, PhoneStartChoreography.visibleTileAnimationIndex(14, 15), 0f)
        assertEquals(1f, PhoneStartChoreography.visibleTileAnimationIndex(0, 1), 0f)
        assertEquals(2000f, PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX, 0f)
        assertEquals(1000f, PhoneStartChoreography.CLASSIC_APPS_PAGE_PERSPECTIVE_CSS_PX, 0f)
    }

    @Test fun nativeForwardTimingIsIndependentOfDeviceHeightAndSlowedReference() {
        assertEquals(527, PhoneStartChoreography.classicNativeForwardTotalMillis(850f))
        assertEquals(527, PhoneStartChoreography.classicNativeForwardTotalMillis(648f))
        assertEquals(0f, classicFrame(false, 199, 1f).alpha, 0f)
        assertEquals(80f, classicFrame(false, 200, 1f).rotationY, .001f)
        assertEquals(0f, classicFrame(false, 450, 1f).rotationY, .001f)

    }

    @Test fun nativeForwardCornersTrackMeasuredPhoneFaceRatherThanAFixedHinge() {
        // Independent silhouette observations: 1000197576, 112.895–113.095s.
        // Crop is 508px wide. Register animation onset at 112.6467s.
        val samples = listOf(
            248 to floatArrayOf(96f,69.7f,253f,97.4f,253f,293f,96f,282.3f),
            282 to floatArrayOf(82f,66.6f,264f,89.1f,264f,290f,82f,280.9f),
            315 to floatArrayOf(52f,62.8f,267f,73f,267f,283.7f,52f,279.3f),
            349 to floatArrayOf(46f,62.2f,261f,70f,261f,282.5f,46f,279.3f),
            382 to floatArrayOf(35f,61.5f,253f,64.4f,253f,280.6f,35f,278.7f),
            449 to floatArrayOf(30f,61f,249f,62.5f,249f,279f,30f,279f),
        )
        val corners = listOf(0f to 0f, 218f to 0f, 218f to 218f, 0f to 218f)
        for ((time, measured) in samples) {
            val frame = PhoneStartChoreography.sample(eight, false, time, 0f, 1f,
                846f, 508f, 28f, tileWidthCssPx = 218f)
            corners.forEachIndexed { index, (x, y) ->
                val point = PhoneStartChoreography.projectPlanePoint(x, y,
                    28f, 61f, 218f, 218f, 508f, 846f, frame,
                    cameraDistanceCssPx = PhoneStartChoreography.nativeCameraDistance(508f))
                assertEquals("native x at ${time}ms corner $index", measured[index*2], point.xCssPx, 10f)
                assertEquals("native y at ${time}ms corner $index", measured[index*2+1], point.yCssPx, 10f)
            }
        }
    }

    @Test fun nativeDiagonalWaveTracksLeftRightAndWideFaces() {
        // Independent 30 Hz recording: accept half a source frame (16 ms),
        // but require one common timestamp to fit all four face corners.
        data class Observation(val time: Int, val points: FloatArray)
        data class Face(val left: Float, val top: Float, val width: Float,
            val height: Float, val observations: List<Observation>)
        val faces = listOf(
            Face(28f, 293f, 219f, 218f, listOf(
                Observation(178, floatArrayOf(118.0f, 299.0f, 199.0f, 311.0f, 199.0f, 497.0f, 118.0f, 506.2f)),
                Observation(212, floatArrayOf(92.0f, 295.1f, 258.0f, 304.3f, 258.0f, 502.5f, 92.0f, 508.8f)),
                Observation(245, floatArrayOf(57.0f, 292.8f, 265.0f, 297.7f, 265.0f, 506.9f, 57.0f, 510.5f)),
                Observation(279, floatArrayOf(50.0f, 292.6f, 263.0f, 296.2f, 263.0f, 507.4f, 50.0f, 511.0f)),
            )),
            Face(260f, 293f, 219f, 218f, listOf(
                Observation(112, floatArrayOf(190.0f, 314.2f, 250.0f, 323.6f, 250.0f, 488.0f, 190.0f, 495.0f)),
                Observation(145, floatArrayOf(237.0f, 309.6f, 344.0f, 318.8f, 344.0f, 492.0f, 237.0f, 498.2f)),
                Observation(178, floatArrayOf(274.0f, 302.8f, 438.0f, 309.7f, 438.0f, 498.4f, 274.0f, 503.0f)),
                Observation(212, floatArrayOf(277.0f, 298.6f, 469.0f, 302.7f, 469.0f, 503.2f, 277.0f, 506.3f)),
            )),
            Face(28f, 524f, 451f, 218f, listOf(
                Observation(112, floatArrayOf(105.0f, 518.7f, 293.0f, 498.9f, 293.0f, 666.6f, 105.0f, 724.4f)),
                Observation(145, floatArrayOf(96.0f, 522.7f, 369.0f, 498.4f, 369.0f, 680.0f, 96.0f, 729.9f)),
                Observation(178, floatArrayOf(72.0f, 524.8f, 447.0f, 505.8f, 447.0f, 702.8f, 72.0f, 736.7f)),
                Observation(212, floatArrayOf(55.0f, 525.4f, 475.0f, 512.1f, 475.0f, 722.9f, 55.0f, 737.5f)),
            )),
        )
        val firstWave = PhoneStartChoreography.nativeForwardWavePosition(137.5f, 170f, 508f)
        for (face in faces) {
            val wave = PhoneStartChoreography.nativeForwardWavePosition(
                face.left + face.width / 2f, face.top + face.height / 2f, 508f)
            val index = PhoneStartChoreography.nativeForwardAnimationIndex(wave, firstWave)
            val corners = listOf(0f to 0f, face.width to 0f,
                face.width to face.height, 0f to face.height)
            for (observation in face.observations) {
                val error = (observation.time - 16..observation.time + 16).minOf { time ->
                    val frame = PhoneStartChoreography.sample(eight, false, time, 0f, index,
                        838f, 508f, face.left, tileWidthCssPx = face.width)
                    corners.mapIndexed { i, (x, y) ->
                        val point = PhoneStartChoreography.projectPlanePoint(x, y,
                            face.left, face.top, face.width, face.height, 508f, 838f, frame,
                            cameraDistanceCssPx = PhoneStartChoreography.nativeCameraDistance(508f))
                        maxOf(kotlin.math.abs(point.xCssPx - observation.points[i * 2]),
                            kotlin.math.abs(point.yCssPx - observation.points[i * 2 + 1]))
                    }.maxOrNull()!!
                }
                assertTrue("Native face at ${face.left},${face.top}, ${observation.time}ms: ${error}px", error < 12f)
            }
        }
    }

    @Test fun classicExitUsesReverseVisibleOrderAndSelectedTileDelay() {
        assertEquals(0, PhoneStartChoreography.delayMillis(eight, true, 0f, height))
        assertEquals(200, PhoneStartChoreography.delayMillis(eight, true, 1f, height))
        assertEquals(200, PhoneStartChoreography.delayMillis(
            eight, true, .95f, height, selected = true,
        ))
        assertEquals("Disco's initialized CSS stagger is 0.875 at a 637.5px viewport",
            175, PhoneStartChoreography.delayMillis(eight, true, 1f, 637.5f))
        assertEquals("Selected tile also uses --app-transition-scale",
            175, PhoneStartChoreography.selectedExitDelayMillis(637.5f))
        assertEquals("At the 850px reference viewport the scale is 1",
            200, PhoneStartChoreography.delayMillis(eight, true, 1f, 850f))
        assertEquals(500, PhoneStartChoreography.totalMillis(eight, true, 637.5f))

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
        val moving = classicFrame(true, 87, index = 0f, tileLeft = tileLeft)
        val matrixProgress = -moving.rotationY / 40f
        val endX = -width * .25f + tileLeft * (kotlin.math.cos(Math.toRadians(30.0)).toFloat() - 1f)
        assertTrue("The exit sample must be between its source keyframes", matrixProgress in 0f..1f)
        assertEquals("CSS matrix interpolation lerps the decomposed endpoint X",
            endX * matrixProgress, moving.translationXPx, .001f)
        assertEquals("CSS matrix interpolation lerps the decomposed endpoint Z",
            tileLeft * .5f * matrixProgress, moving.translationZPx, .001f)

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

    @Test fun classicReturnStaggersFacesWithoutMovingTheirContentsOutside() {
        val firstBeforeDelay = classicFrame(exiting = false, elapsed = 13, index = .07f)
        val first = classicFrame(exiting = false, elapsed = 14, index = .07f)
        val delayed = classicFrame(exiting = false, elapsed = 0, index = 1f)
        assertEquals(0f, firstBeforeDelay.alpha, 0f)
        assertEquals(1f, first.alpha, 0f)
        assertEquals(0f, delayed.alpha, 0f)
        assertEquals(80f, first.rotationY, .001f)
        assertEquals(0f, classicFrame(false, 199, 1f).alpha, 0f)
        assertEquals(1f, classicFrame(false, 200, 1f).alpha, 0f)
        assertEquals(0f, classicFrame(false, 700, 1f).rotationY, .001f)

        assertEquals(0f, classicFrame(false, 700, 0f).rotationY, .001f)
    }

    @Test fun nativeHomeKeepsTheSecondPageVisibleThroughTheTileCascade() {
        val hidden = PhoneStartChoreography.sampleAppsPageEntry(26, width)
        assertEquals(0f, hidden.alpha, 0f)
        val moving = PhoneStartChoreography.sampleAppsPageEntry(112, width)
        assertEquals(1f, moving.alpha, 0f)
        assertTrue(moving.rotationY in 40f..60f)
        val corner = PhoneStartChoreography.projectAppsPagePoint(0f, height/2f,
            width, height, moving.rotationY)
        assertTrue("All Apps must still occupy the right of Start", corner.xCssPx in 150f..230f)
        val settled = PhoneStartChoreography.sampleAppsPageEntry(527, width)
        assertEquals(0f, settled.rotationY, 0f)
        assertEquals(1f, settled.alpha, 0f)
    }

    @Test fun homeAppsPageUsesTheSourcePerspectiveOriginAtTheStartPageEdge() {
        val leftTop = PhoneStartChoreography.projectAppsPagePoint(
            pageLocalXPx = 0f,
            pageLocalYPx = 0f,
            viewportWidthCssPx = width,
            viewportHeightCssPx = height,
            rotationYDegrees = 45f,
        )
        val rightCenter = PhoneStartChoreography.projectAppsPagePoint(
            pageLocalXPx = width,
            pageLocalYPx = height / 2f,
            viewportWidthCssPx = width,
            viewportHeightCssPx = height,
            rotationYDegrees = 45f,
        )
        assertEquals(203.2f, leftTop.xCssPx, .5f)
        assertEquals(85.9f, leftTop.yCssPx, .5f)
        assertEquals(337.5f, rightCenter.xCssPx, .5f)
        assertEquals(height / 2f, rightCenter.yCssPx, .001f)

        val settledLeft = PhoneStartChoreography.projectAppsPagePoint(
            pageLocalXPx = 0f,
            pageLocalYPx = height / 2f,
            viewportWidthCssPx = width,
            viewportHeightCssPx = height,
            rotationYDegrees = 0f,
        )
        assertEquals(width, settledLeft.xCssPx, .001f)
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
        val pivotX = -120f
        val pivotToTileX = -pivotX
        val rotatedTileX = pivotX + pivotToTileX * kotlin.math.cos(Math.toRadians(-80.0)).toFloat()
        val rotatedTileZ = -pivotToTileX * kotlin.math.sin(Math.toRadians(-80.0)).toFloat()
        assertEquals("The resulting back pose must equal Disco's composed CSS transform X",
            -223.90818f, rotatedTileX + start.translationXPx, .002f)
        assertEquals("The resulting back pose must equal Disco's composed CSS transform Z",
            65.94005f, rotatedTileZ + start.translationZPx, .002f)

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
        assertTrue("Back reveal must progress between its opacity keyframes",
            firstRevealSegment.alpha > 0f && firstRevealSegment.alpha < 1f)

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

        val letter = PhoneStartChoreography.sampleAppListExit(
            400, 0f, height, width, letter = true, tileLeftCssPx = 81f, tileWidthCssPx = 280f,
        )
        assertEquals(-width - 1000f / 3f, letter.translationXPx, .001f)
        assertEquals(-81f / 280f, letter.pivotX, .001f)
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
