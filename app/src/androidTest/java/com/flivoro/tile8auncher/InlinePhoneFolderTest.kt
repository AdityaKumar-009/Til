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

    @Test fun recordsFolderOpenCloseAndInterruptedReversal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val mode = LauncherUiMode.MOBILE_10
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 4)
        val folder = checkNotNull(PhoneFolderStore.createOrAdd(context, mode, "a", "b"))
        PhoneLayoutStore.saveOrder(context, mode, listOf(folder, "c", "d"))
        val tiles = listOf("a", "b", "c", "d").map {
            TileModel(id = it, title = "Tile $it", iconGlyph = "app", size = TileSize.MEDIUM)
        }
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                    appsRepository = repository, homeRequest = 0, entranceRequest = 0,
                    launchingTileId = null, isLaunching = false, interactionEnabled = true, wallpaperStyle = 0,
                    onLaunch = { _, _, _ -> }, onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val neighborBefore = compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        compose.captureFidelity("folder-rest")
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true).performTouchInput { click() }
        captureFolderFrames("folder-open")
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        captureFolderFrames("folder-close")
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(neighborBefore, compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top, 1f)

        // Real event reversal, not a second evaluation of the motion equation.
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true).performTouchInput { click() }
        captureFolderFrames("folder-reopen")
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(neighborBefore, compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun captureFolderFrames(phase: String) {
        for (millis in 0..480 step 16) {
            if (millis > 0) compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
            compose.captureFidelity("$phase-${millis.toString().padStart(3, '0')}")
        }
    }

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
        val before = compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Folder", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput { click() }
        // Expansion adds measured content before its height animation starts.
        // Interleave Android layout with virtual frames instead of sampling
        // the first (zero-height) layout after one uninterrupted clock jump.
        try {
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeBy(100)
                compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).isDisplayed()
            }
        } catch (failure: Throwable) {
            compose.captureFidelity("cross-mobile-inline-folder-failure")
            throw AssertionError("Folder after tap:\n" +
                compose.onRoot(useUnmergedTree = true).printToString(), failure)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertIsDisplayed()
        compose.captureFidelity("cross-mobile-inline-folder")
        // Windows 10 Mobile renames through a long press; the collapsed
        // preview is not permanently an editable input field.
        compose.onNodeWithTag("phone-folder-title").performTouchInput { longClick() }
        compose.onNodeWithTag("phone-folder-title").performTextReplacement("Travel")
        val after = compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Following tiles must make space inline", after > before)
        compose.onNodeWithText("Tile b", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("b", launched?.id)
            assertTrue("Child source must be inside the expanded folder", bounds.top > 0 && bounds.height > 1f)
            assertEquals(listOf("b", "a"), PhoneFolderStore.read(context, mode).single().children)
        }
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        compose.runOnIdle {
            assertEquals("Travel", PhoneFolderStore.read(context, mode).single().name)
        }
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(100)
            compose.onAllNodesWithTag("phone-expanded-folder", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
