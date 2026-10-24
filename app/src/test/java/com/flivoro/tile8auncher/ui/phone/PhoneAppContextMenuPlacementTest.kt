package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAppContextMenuPlacementTest {
    @Test fun opensBelowTheHeldRowWhenThereIsSpace() {
        val position = placeClassicAppMenu(
            rowTopPx = 180, rowBottomPx = 244,
            viewportTopPx = 24, viewportBottomPx = 776, menuHeightPx = 219,
        )
        assertEquals(244, position.topPx)
        assertFalse(position.opensAbove)
    }

    @Test fun opensAboveTheHeldRowNearBottomLikeDisco() {
        val position = placeClassicAppMenu(
            rowTopPx = 620, rowBottomPx = 684,
            viewportTopPx = 24, viewportBottomPx = 776, menuHeightPx = 219,
        )
        assertEquals(401, position.topPx)
        assertTrue(position.opensAbove)
    }

    @Test fun flipsWhenMenuWouldExactlyTouchTheBottomInset() {
        val position = placeClassicAppMenu(
            rowTopPx = 493, rowBottomPx = 557,
            viewportTopPx = 24, viewportBottomPx = 776, menuHeightPx = 219,
        )
        assertEquals(274, position.topPx)
        assertTrue(position.opensAbove)
    }

    @Test fun staysInsideTopInsetWhenAnAboveMenuDoesNotFit() {
        val position = placeClassicAppMenu(
            rowTopPx = 40, rowBottomPx = 104,
            viewportTopPx = 24, viewportBottomPx = 320, menuHeightPx = 219,
        )
        assertEquals(24, position.topPx)
        assertTrue(position.opensAbove)
    }

    @Test fun respectsNonzeroWindowOriginAndConstrainedHeight() {
        val position = placeClassicAppMenu(
            rowTopPx = 160, rowBottomPx = 224,
            viewportTopPx = 110, viewportBottomPx = 285, menuHeightPx = 219,
        )
        assertEquals(110, position.topPx)
        assertTrue(position.opensAbove)
    }
}
