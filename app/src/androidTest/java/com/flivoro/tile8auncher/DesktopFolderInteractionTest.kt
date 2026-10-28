package com.flivoro.tile8auncher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.ui.start.StartScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopFolderInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedAppsCanCreateAndOpenTheDesktopFolderExtension() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var tiles by mutableStateOf(listOf(
            TileModel("desktop_alpha", "Alpha", "com.example.alpha", "com.example.alpha.Main"),
            TileModel("desktop_beta", "Beta", "com.example.beta", "com.example.beta.Main"),
            TileModel("desktop_gamma", "Gamma", "com.example.gamma", "com.example.gamma.Main"),
        ))
        var createdFolder: String? = null
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                MaterialTheme {
                    StartScreen(tiles = tiles, appsRepository = AppsRepository(context),
                        onTileClick = { _, _ -> }, onTileLongClick = {},
                        onPowerClick = {}, onSearchClick = {}, onAddAppsClick = {},
                        onNavigateToAllApps = {}, onTilesChanged = { tiles = it })
                }
            }
            compose.mainClock.advanceTimeBy(2000)
            compose.waitForIdle()
            compose.onNodeWithTag("desktop-start-tile-desktop_alpha", useUnmergedTree = true)
                .performTouchInput { longClick() }
            compose.mainClock.advanceTimeBy(250)
            compose.onNodeWithTag("desktop-start-tile-desktop_beta", useUnmergedTree = true)
                .performTouchInput { click() }
            compose.mainClock.advanceTimeBy(250)
            compose.onNodeWithText("Create folder").assertIsDisplayed().performClick()
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            compose.runOnIdle {
                val folder = tiles.single { it.id.startsWith("folder_") }
                createdFolder = folder.id
                assertEquals(listOf("com.example.alpha", "com.example.beta"),
                    LauncherFeatureStore.folderPackages(context, folder.id))
                assertEquals(2, tiles.size)
                assertTrue(tiles.any { it.id == "desktop_gamma" })
            }
            compose.onNodeWithTag("desktop-start-tile-${checkNotNull(createdFolder)}", useUnmergedTree = true)
                .performTouchInput { click() }
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithTag("desktop-folder-dialog").assertIsDisplayed()
        } finally {
            createdFolder?.let { LauncherFeatureStore.removeFolder(context, it) }
        }
    }
}
