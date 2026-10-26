package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.*
import org.junit.Test

class MobileAppsMotionTest {
    private fun exit(ms: Int, row: Float, selected: Boolean = false) =
        MobileAppsMotion.sample(true, ms, row, 40f, 360f, 180f, 360f, selected)

    private fun back(ms: Int, row: Float, selected: Boolean = false) =
        MobileAppsMotion.sample(false, ms, row, 40f, 360f, 180f, 360f,
            selected, returningFromApp = true)

    @Test fun allAppsAppLaunchExactlyMatchesStartCurveStaggerAndFadeAtTenMillisecondIntervals() {
        assertEquals(MobileStartMotion.EXIT_TOTAL_MS, MobileAppsMotion.EXIT_TOTAL_MS)
        for (row in listOf(0f, .25f, .5f, .7f, 1f)) {
            for (selected in listOf(false, true)) {
                assertEquals(MobileStartMotion.delayMillis(true, row, selected),
                    MobileAppsMotion.exitDelayMillis(row, selected))
                for (ms in 0..MobileAppsMotion.EXIT_TOTAL_MS step 10) {
                    assertEquals(
                        "All Apps launch diverged from Start at ${ms}ms, row=$row, selected=$selected",
                        MobileStartMotion.sample(true, ms, row, 40f, 360f, 180f, 360f, selected),
                        exit(ms, row, selected),
                    )
                }
            }
        }
        for (ms in 0..MobileAppsMotion.EXIT_TOTAL_MS step 10) {
            assertEquals(MobileStartMotion.wallpaperAlpha(true, ms),
                MobileAppsMotion.chromeAlpha(true, ms), 0f)
        }
        // The old All Apps timeline had already hidden the top row by 160ms;
        // its slower Start-equivalent curve keeps it visible until 250ms.
        assertTrue(exit(160, 0f).alpha > .8f)
        assertEquals(0f, exit(250, 0f).alpha, 0f)
        assertTrue(exit(320, .7f, true).alpha > exit(320, .7f).alpha)
        assertEquals(1f, MobileAppsMotion.chromeAlpha(true, 400), 0f)
        assertEquals(0f, MobileAppsMotion.chromeAlpha(true, 534), 0f)
    }

    @Test fun nativeBackRevealsBottomBeforeTopAndSettlesWithinObservedWindow() {
        // Shell appears ~1072.44s. Lower rows show at 1072.48-52, top rows
        // at ~1072.60, and the layout settles by ~1072.76-80.
        assertTrue(back(80, 1f).alpha > .5f)
        assertEquals(0f, back(80, 0f).alpha, 0f)
        assertTrue(back(160, 0f).alpha > 0f)
        assertTrue(back(160, 0f).scale > 1f)
        assertTrue(back(160, 0f).translationXPx < 0f)
        for (row in listOf(0f, .5f, 1f)) {
            assertEquals(back(160, row), back(160, row, selected = true))
            assertEquals(1f, back(360, row).alpha, 0f)
            assertEquals(1f, back(360, row).scale, 0f)
        }
    }

    @Test fun ordinaryPanoramaNavigationKeepsRowsAndChromeSettled() {
        for (ms in listOf(0, 80, 200, 400)) {
            val frame = MobileAppsMotion.sample(false, ms, .5f, 40f, 300f, 180f, 360f)
            assertEquals(1f, frame.alpha, 0f)
            assertEquals(1f, frame.scale, 0f)
            assertEquals(0f, frame.translationXPx, 0f)
            assertEquals(1f, MobileAppsMotion.chromeAlpha(false, ms), 0f)
        }
    }

    @Test fun denseAndScrolledListsStayFiniteAndReachHandoffBeforeCompletion() {
        for (row in listOf(-.2f, 0f, .25f, .75f, 1f, 1.2f)) {
            for (selected in listOf(false, true)) {
                for (ms in -10..550 step 10) {
                    val frame = exit(ms, row, selected)
                    assertTrue(frame.alpha in 0f..1f && frame.scale in 1f..2f)
                    assertTrue(frame.translationXPx.isFinite() && frame.translationYPx.isFinite())
                }
                assertEquals(0f, exit(MobileAppsMotion.EXIT_TOTAL_MS, row, selected).alpha, 0f)
            }
        }
    }
}
