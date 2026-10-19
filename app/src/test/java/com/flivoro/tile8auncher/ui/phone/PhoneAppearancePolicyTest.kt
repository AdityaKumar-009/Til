package com.flivoro.tile8auncher.ui.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAppearancePolicyTest {
    @Test fun stockTilesUseTheThemeAccentUnlessCustomColored() {
        assertTrue(usesPhoneThemeAccent("tile_settings", "Mosaic Settings", false))
        assertTrue(usesPhoneThemeAccent("tile_maps", "Maps", false))
        assertFalse(usesPhoneThemeAccent("tile_settings", "Mosaic Settings", true))
        assertFalse(canUsePhoneBrandColor("tile_maps", "Maps", false, true))
    }

    @Test fun phoneAndMessagesRemainAccentColoredInBothModes() {
        for (title in listOf("Phone", "Dialer", "Messages", "Messaging")) {
            assertTrue(usesPhoneThemeAccent("app.package", title, false))
            assertFalse(canUsePhoneBrandColor("phone_app_package", title, false, true))
        }
    }

    @Test fun brandedBackgroundsRequireLayeredModeAndNoManualOverride() {
        assertTrue(canUsePhoneBrandColor("app_installed", "Camera Extra", false, true))
        assertFalse(canUsePhoneBrandColor("app_installed", "Camera Extra", true, true))
        assertFalse(canUsePhoneBrandColor("app_installed", "Camera Extra", false, false))
        assertFalse(usesPhoneThemeAccent("app_installed", "Camera Extra", false))
    }

    @Test fun allAppsAndStartResolveTheSameBrandedTileColor() {
        val blue = 0xFF0078D7L
        val red = 0xFFCC0033L
        assertEquals(red, resolvePhoneTileBackgroundArgb(
            "phone_app_camera", "Camera Extra", blue, red,
            manualColor = false, discoLayered = true))
        assertEquals(blue, resolvePhoneTileBackgroundArgb(
            "phone_app_camera", "Camera Extra", blue, red,
            manualColor = true, discoLayered = true))
        assertEquals(blue, resolvePhoneTileBackgroundArgb(
            "tile_camera", "Camera", blue, red,
            manualColor = false, discoLayered = true))
        assertEquals(blue, resolvePhoneTileBackgroundArgb(
            "phone_app_dialer", "Phone", blue, red,
            manualColor = false, discoLayered = true))
        assertEquals(blue, resolvePhoneTileBackgroundArgb(
            "phone_app_camera", "Camera Extra", blue, red,
            manualColor = false, discoLayered = false))
        assertEquals(blue, resolvePhoneTileBackgroundArgb(
            "phone_app_camera", "Camera Extra", blue, null,
            manualColor = false, discoLayered = true))
    }

    @Test fun mobileAllAppsIconsUseTheExactClassicArtworkRatio() {
        assertEquals(30f, phoneAllAppsGlyphSizeDp(52f), .0001f)
        assertEquals(45f * 30f / 52f, phoneAllAppsGlyphSizeDp(45f), .0001f)
        assertEquals(45f * 27f / 52f, phoneAllAppsSettingsGlyphSizeDp(45f), .0001f)
        assertEquals(45f * 22f / 52f, phoneAllAppsFallbackSizeSp(45f), .0001f)
    }
    @Test fun wallpaperColorWinsForThemeTilesButNotManuallyColoredOrBrandedTiles() {
        val saved = 0xFF5133ABL
        val wallpaper = 0xFF1BA1E2L
        val brand = 0xFFE51400L
        assertEquals(wallpaper, resolvePhoneTileBackgroundArgb(
            "tile_settings", "Settings", saved, null,
            manualColor = false, discoLayered = true, wallpaperAccent = wallpaper))
        assertEquals(wallpaper, resolvePhoneTileBackgroundArgb(
            "phone_app_dialer", "Phone", saved, brand,
            manualColor = false, discoLayered = true, wallpaperAccent = wallpaper))
        assertEquals(saved, resolvePhoneTileBackgroundArgb(
            "tile_settings", "Settings", saved, brand,
            manualColor = true, discoLayered = true, wallpaperAccent = wallpaper))
        assertEquals(brand, resolvePhoneTileBackgroundArgb(
            "phone_app_camera", "Camera", saved, brand,
            manualColor = false, discoLayered = true, wallpaperAccent = wallpaper))
    }

    @Test fun transparentAndPhotoTilesReachSolidHalfwayAndReverseContinuously() {
        assertEquals(0f, sourceColorArrival(0f), 0f)
        assertEquals(1f, sourceColorArrival(.5f), 0f)
        assertEquals(1f, sourceColorArrival(1f), 0f)
        assertEquals(.5f, sourceColorArrival(.25f), .00001f)
        var previous = 0f
        for (i in 0..100) {
            val progress = i / 100f
            val blend = sourceColorArrival(progress)
            assertTrue("Color crossfade reversed at $progress", blend >= previous)
            previous = blend
        }
    }

}
