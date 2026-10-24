package com.flivoro.tile8auncher.ui.components

import com.flivoro.tile8auncher.data.STOCK_DEFAULT_TILE_IDS
import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStockTileColorTest {
    @Test
    fun originalDesktopStockTilesKeepTheirWindows81Palette() {
        for (id in STOCK_DEFAULT_TILE_IDS) {
            assertFalse(id, shouldDeriveAppTileBackground(id, LauncherUiMode.DESKTOP))
        }
    }

    @Test
    fun otherDesktopAppsStillUseWp81AdaptiveBackgroundLogic() {
        assertTrue(shouldDeriveAppTileBackground("app_com.example.camera", LauncherUiMode.DESKTOP))
        assertTrue(shouldDeriveAppTileBackground("app_com.example.browser_123", LauncherUiMode.DESKTOP))
    }

    @Test
    fun phoneModeColorBehaviorDoesNotChange() {
        for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            for (id in STOCK_DEFAULT_TILE_IDS) {
                assertTrue("$mode: $id", shouldDeriveAppTileBackground(id, mode))
            }
        }
    }
}
