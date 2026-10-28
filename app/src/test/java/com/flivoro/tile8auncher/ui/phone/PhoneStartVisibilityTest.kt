package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rendering may omit distant cells, never cells at or near the viewport. */
class PhoneStartVisibilityTest {
    @Test fun includesVisibleTilesAndOneViewportOfOverscan() {
        val viewport = 1000f
        assertTrue(shouldComposePhoneStartTile(0f, 200f, viewport))
        assertTrue(shouldComposePhoneStartTile(900f, 1100f, viewport))
        assertTrue(shouldComposePhoneStartTile(-1050f, -950f, viewport))
        assertTrue(shouldComposePhoneStartTile(1950f, 2050f, viewport))
        assertTrue(shouldComposePhoneStartTile(-1000f, -900f, viewport))
        assertTrue(shouldComposePhoneStartTile(2000f, 2100f, viewport))
    }

    @Test fun skipsOnlyDistantOffscreenTiles() {
        val viewport = 1000f
        assertFalse(shouldComposePhoneStartTile(-1500f, -1100f, viewport))
        assertFalse(shouldComposePhoneStartTile(2100f, 2200f, viewport))
    }

    @Test fun retainsAllTilesUntilViewportHasBeenMeasured() {
        assertTrue(shouldComposePhoneStartTile(-20000f, -18000f, 0f))
        assertTrue(shouldComposePhoneStartTile(30000f, 32000f, 0f))
    }
}
