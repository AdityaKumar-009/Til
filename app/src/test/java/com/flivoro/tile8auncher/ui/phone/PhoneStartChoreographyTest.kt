package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneStartChoreographyTest {
    private val eight = LauncherUiMode.PHONE_8
    private val ten = LauncherUiMode.MOBILE_10

    @Test fun calibratedEndTimesFollowRecordedThirtyFpsWindows() {
        // Reference: versus MP4 144.75s-145.18s, 29.975fps.
        assertEquals(270, PhoneStartChoreography.totalMillis(eight, true))
        assertEquals(534, PhoneStartChoreography.totalMillis(ten, true))
    }

    @Test fun phoneEightExitsLowerRightBeforeUpperLeft() {
        val lowerRight = PhoneStartChoreography.delayMillis(eight, true, 5, 3, 4)
        val upperLeft = PhoneStartChoreography.delayMillis(eight, true, 0, 0, 4)
        assertEquals(0, lowerRight)
        assertEquals(180, upperLeft)
        assertEquals(0f, PhoneStartChoreography.sample(eight, true, 95, 5, 3, 4).alpha, 0f)
        assertEquals(1f, PhoneStartChoreography.sample(eight, true, 95, 0, 0, 4).alpha, 0f)
        assertEquals(0f, PhoneStartChoreography.sample(eight, true, 270, 0, 0, 4).alpha, 0f)
    }

    @Test fun classicSixtyFpsEntranceAlsoRevealsLowerTilesBeforeUpperTiles() {
        // 1000197575.mp4, 47.80-48.30s: lower tiles have turned face-forward
        // before the larger upper-left phone tile rotates fully into view.
        val lower = PhoneStartChoreography.delayMillis(eight, false, 5, 3, 4)
        val upper = PhoneStartChoreography.delayMillis(eight, false, 0, 0, 4)
        assertEquals(0, lower)
        assertEquals(180, upper)
        assertEquals(1f, PhoneStartChoreography.sample(eight, false, 90, 5, 3, 4).alpha, 0f)
        assertEquals(0f, PhoneStartChoreography.sample(eight, false, 90, 0, 0, 4).alpha, 0f)
    }

    @Test fun selectedClassicTileIsRetainedUntilTheLastUpperTileLeaves() {
        val selected = PhoneStartChoreography.sample(eight, true, 95, 5, 3, 4, true)
        assertEquals(1f, selected.alpha, 0f)
        assertEquals(0f, selected.rotationY, 0f)
        assertEquals(1f, selected.pivotX, 0f)
        assertEquals(0f, PhoneStartChoreography.sample(eight, true, 270, 5, 3, 4, true).alpha, 0f)
    }

    @Test fun frameSamplesEveryTenMillisecondsAreFiniteAndReachRestStates() {
        for (mode in listOf(eight, ten)) {
            for (exiting in listOf(true, false)) {
                val total = PhoneStartChoreography.totalMillis(mode, exiting)
                for (ms in 0..(total + 20) step 10) {
                    for (row in 0..5) for (column in 0..3) {
                        val f = PhoneStartChoreography.sample(mode, exiting, ms, row, column, 4)
                        assertTrue(f.alpha in 0f..1f)
                        assertTrue(f.rotationY.isFinite())
                        assertTrue(f.translationXPx.isFinite())
                        assertTrue(f.translationYPx.isFinite())
                        assertTrue(f.scale.isFinite())
                    }
                }
            }
        }
    }

    @Test fun selectedMobileTileDelaysTheSameZoomInsteadOfUsingAnotherScale() {
        val selected = PhoneStartChoreography.sample(ten, true, 167, 0, 0, 4, true)
        val other = PhoneStartChoreography.sample(ten, true, 100, 0, 0, 4, false)
        assertEquals(other.scale, selected.scale, .00001f)
        assertEquals(other.alpha, selected.alpha, .00001f)
    }
}
