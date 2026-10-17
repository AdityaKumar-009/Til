package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.*
import org.junit.Test

class MobileAppsMotionTest {
    private fun exit(ms: Int, row: Float, selected: Boolean = false) =
        MobileAppsMotion.sample(true, ms, row, 40f, 360f, 180f, 360f, selected)

    private fun back(ms: Int, row: Float, selected: Boolean = false) =
        MobileAppsMotion.sample(false, ms, row, 40f, 360f, 180f, 360f,
            selected, returningFromApp = true)

    @Test fun nativeLaunchKeepsSelectedContinuumAfterOrdinaryRowsAndSearchAfterSelection() {
        // Native origin ~1023.24s, source steps 40ms. At 1023.40 top rows are
        // gone; at 1023.48-52 Continuum remains; at 1023.56 only search remains.
        assertEquals(0f, exit(160, 0f).alpha, 0f)
        assertTrue(exit(160, .7f, true).alpha > .9f)
        assertTrue(exit(240, .7f, true).alpha > exit(240, .7f).alpha)
        assertEquals(0f, exit(320, .7f, true).alpha, 0f)
        assertEquals(1f, MobileAppsMotion.chromeAlpha(true, 320), 0f)
        assertEquals(0f, MobileAppsMotion.chromeAlpha(true, 400), 0f)
        // Selection remains an additive hold even for the bottom visible row.
        assertTrue(exit(240, 1f, true).alpha > exit(240, 1f).alpha)
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
                for (ms in -10..410 step 10) {
                    val frame = exit(ms, row, selected)
                    assertTrue(frame.alpha in 0f..1f && frame.scale in 1f..2f)
                    assertTrue(frame.translationXPx.isFinite() && frame.translationYPx.isFinite())
                }
                assertEquals(0f, exit(400, row, selected).alpha, 0f)
            }
        }
    }
}
