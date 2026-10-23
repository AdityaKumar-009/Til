package com.flivoro.tile8auncher

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.*
import com.flivoro.tile8auncher.features.*
import com.flivoro.tile8auncher.ui.phone.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlinePhoneFolderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun expansionMovesNeighborsAndChildKeepsItsOwnLaunchOrigin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val mode = LauncherUiMode.MOBILE_10
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 4)
        val folder = checkNotNull(PhoneFolderStore.createOrAdd(context, mode, "a", "b"))
        PhoneLayoutStore.saveOrder(context, mode, listOf(folder, "c", "d"))
        val tiles = listOf("a", "b", "c", "d").map {
            TileModel(id = it, title = "Tile $it", iconGlyph = "app", size = TileSize.MEDIUM)
        }
        var launched: TileModel? = null
        var bounds = Rect.Zero
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                    appsRepository = AppsRepository(context), homeRequest = 0, entranceRequest = 0,
                    launchingTileId = null, isLaunching = false, interactionEnabled = true, wallpaperStyle = 0,
                    onLaunch = { tile, rect, _ -> launched = tile; bounds = rect },
                    onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val before = compose.onNodeWithText("Tile d").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Folder").performTouchInput { click() }
        // Expansion adds measured content before its height animation starts.
        // Interleave Android layout with virtual frames instead of sampling
        // the first (zero-height) layout after one uninterrupted clock jump.
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("phone-expanded-folder").isDisplayed()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-expanded-folder").assertIsDisplayed()
        compose.captureFidelity("mobile-inline-folder")
        compose.onNodeWithTag("phone-folder-title").performTextReplacement("Travel")
        compose.runOnIdle { assertEquals("Travel", PhoneFolderStore.read(context, mode).single().name) }
        val after = compose.onNodeWithText("Tile d").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Following tiles must make space inline", after > before)
        compose.onNodeWithText("Tile b").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("b", launched?.id)
            assertTrue("Child source must be inside the expanded folder", bounds.top > 0 && bounds.height > 1f)
            assertEquals(listOf("b", "a"), PhoneFolderStore.read(context, mode).single().children)
        }
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(100)
            compose.onAllNodesWithTag("phone-expanded-folder").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("phone-expanded-folder").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText("Tile d").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
