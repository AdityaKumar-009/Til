package com.flivoro.tile8auncher.data

import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.phoneAppsInDisplayOrder
import com.flivoro.tile8auncher.ui.phone.phoneStartSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupIconPlanTest {
    private fun app(id: String, label: String = id) = AppInfo(label, id, "$id.Main")

    @Test
    fun installingHundredsOfAppsDoesNotExpandBlockingArtwork() {
        val apps = (0 until 500).map { app("app$it") }
        val plan = startupIconPlan(listOf("app499"), apps, emptySet(), viewportIconCount = 24)
        assertEquals(listOf("app499"), plan.startPackages)
        assertEquals(apps.take(24).map(AppInfo::packageName), plan.allAppsPackages)
        assertEquals(500, apps.size) // No truncation of the catalog handed to the launcher.
    }

    @Test
    fun recentAppsAreReadyFirstAndHiddenAppsDoNotSpendTheViewportBudget() {
        val apps = listOf(app("hidden"), app("private"), app("a"), app("b"), app("new"))
        val plan = startupIconPlan(listOf("private", "private"), apps,
            setOf("hidden", "private"), listOf(app("new"), app("hidden"), app("removed")), 3)
        assertEquals(listOf("new", "a", "b"), plan.allAppsPackages)
        assertEquals(listOf("private"), plan.startPackages)
    }

    @Test
    fun duplicateActivitiesConsumeOnlyOneIconSlot() {
        val plan = startupIconPlan(emptyList(), listOf(app("a"), app("a"), app("b")),
            emptySet(), viewportIconCount = 2)
        assertEquals(listOf("a", "b"), plan.allAppsPackages)
    }

    @Test
    fun anEmptyCatalogStillAllowsSavedStartPins() {
        val plan = startupIconPlan(listOf("saved", ""), emptyList(), emptySet(), viewportIconCount = 20)
        assertEquals(listOf("saved"), plan.startPackages)
        assertTrue(plan.allAppsPackages.isEmpty())
    }

    @Test
    fun phonePreloadOrderIncludesNumbersAndAccentedLabelsBeforeLaterLetters() {
        val ordered = phoneAppsInDisplayOrder(listOf(
            app("z", "Zebra"), app("e", "Éclair"), app("a", "Alpha"),
            app("symbol", "!tool"), app("num", "1Password"), app("cn", "相机"),
        ))
        assertEquals(listOf("num", "a", "e", "z", "symbol", "cn"),
            ordered.map(AppInfo::packageName))
    }

    @Test
    fun phoneDefaultSeedMatchesTheRenderedLayoutAndKeepsFixtures() {
        val tiles = listOf(TileModel("tile_mail", "Mail"), TileModel("tile_settings", "Settings"),
            TileModel("tile_desktop", "Desktop"))
        val apps = listOf(app("phone", "Phone"), app("sms", "Messages"))
        for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            val seed = phoneStartSeed(tiles, apps, mode)
            assertEquals(listOf("app:phone", "app:sms", "tile_mail", "tile_settings"),
                seed.map(TileModel::id))
            assertEquals("Mosaic Settings", seed.last().title)
        }
        val fixtures = listOf(TileModel("test-one", "Test"))
        assertEquals(fixtures, phoneStartSeed(fixtures, emptyList(), LauncherUiMode.PHONE_8))
    }
}
