package com.flivoro.tile8auncher

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
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
        val expandedY = compose.onNodeWithTag("phone-expanded-folder",
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val sourceBottom = compose.onNodeWithTag("phone-start-tile-$folder",
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Folder content must open beneath its Start tile",
            expandedY >= sourceBottom - 2f)
        val expandedBounds = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val divider = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val upperDivider = compose.onNodeWithTag("phone-folder-top-divider", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val lastChild = compose.onNodeWithTag("phone-start-tile-b", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val followingTile = compose.onNodeWithTag("phone-start-tile-d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The bottom rule must be beneath the expanded tiles", divider.top >= lastChild.bottom - 1f)
        assertTrue("The bottom rule must be above the following tile", divider.bottom <= followingTile.top + 1f)
        assertEquals("Folder rules must have the same thickness", upperDivider.height, divider.height, 1f)
        assertEquals("Folder rules must share the same trailing inset", upperDivider.right, divider.right, 1f)
        val dividerBottomGap = expandedBounds.bottom - divider.bottom
        assertTrue("The bottom rule must lie inside the expanded folder", dividerBottomGap >= 0f)
        // The folder tile itself is the primary open/close toggle on Lumia.
        compose.onNodeWithTag("phone-start-tile-$folder",
            useUnmergedTree = true).performTouchInput { click() }
        captureFolderFrames("folder-close")
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(neighborBefore, compose.onNodeWithText("Tile d", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top, 1f)

        // Real event reversal, not a second evaluation of the motion equation.
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-start-tile-$folder",
            useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true).performTouchInput { click() }
        captureFolderFrames("folder-reopen")
        compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse folder").assertIsDisplayed()
        val childBefore = compose.onNodeWithText("Tile b", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        val closingBounds = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val closingDivider = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("The bottom rule must follow the moving folder edge", dividerBottomGap,
            closingBounds.bottom - closingDivider.bottom, 1f)
        val childClosing = compose.onNodeWithText("Tile b", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Closing child must move back toward the source tile",
            childClosing.top < childBefore.top)
        assertTrue("Closing child must shrink toward its miniature size",
            childClosing.width < childBefore.width)
        assertTrue("Closing child must not move above its miniature source",
            childClosing.top >= source.top - 2f)
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

    @Test fun openingAndClosingDoNotPaintOverAnAdjacentStartTile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val mode = LauncherUiMode.MOBILE_10
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 4)
        val folder = checkNotNull(PhoneFolderStore.createOrAdd(context, mode, "a", "b"))
        PhoneLayoutStore.saveOrder(context, mode, listOf(folder, "c", "d"))
        val tiles = listOf(
            TileModel(id = "a", title = "Tile a", colorValue = 0xffe03020, iconGlyph = "app"),
            TileModel(id = "b", title = "Tile b", colorValue = 0xffe03020, iconGlyph = "app"),
            TileModel(id = "c", title = "Tile c", colorValue = 0xff209040, iconGlyph = "app"),
            TileModel(id = "d", title = "Tile d", colorValue = 0xff2080d0, iconGlyph = "app"),
        )
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                    appsRepository = repository, homeRequest = 0, entranceRequest = 0,
                    launchingTileId = null, isLaunching = false, interactionEnabled = true,
                    wallpaperStyle = 0, onLaunch = { _, _, _ -> }, onExitFinished = {},
                    onOpenSettings = {}, onOpenAppInfo = {})
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val neighbor = compose.onNodeWithTag("phone-start-tile-c", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val points = listOf(.06f, .16f, .26f).flatMap { x ->
            listOf(.60f, .75f, .88f).map { y ->
                (neighbor.left + neighbor.width * x).toInt() to
                    (neighbor.top + neighbor.height * y).toInt()
            }
        }
        fun sample(): List<Int> {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            return points.map { (x, y) -> image.getPixel(x, y) }
        }
        val expected = sample()
        fun verifyFrames(phase: String) {
            repeat(30) { frame ->
                compose.mainClock.advanceTimeBy(16)
                compose.waitForIdle()
                val actual = sample()
                if (actual != expected) compose.captureFidelity("folder-overdraw-$phase-$frame")
                assertEquals("Neighbor was overdrawn during $phase frame $frame", expected, actual)
            }
        }
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
            .performTouchInput { click() }
        verifyFrames("open")
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
            .performTouchInput { click() }
        verifyFrames("close")
    }

    @Test fun smallFolderClearsTallNeighborInSixColumnLayout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val mode = LauncherUiMode.MOBILE_10
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 6)
        val folder = checkNotNull(PhoneFolderStore.createOrAdd(context, mode, "a", "b"))
        PhoneLayoutStore.saveOrder(context, mode, listOf(folder, "c", "d"))
        PhoneLayoutStore.saveSize(context, mode, folder, TileSize.SMALL)
        val tiles = listOf(
            TileModel(id = "a", title = "Tile a", iconGlyph = "app", size = TileSize.WIDE),
            TileModel(id = "b", title = "Tile b", iconGlyph = "app", size = TileSize.MEDIUM),
            TileModel(id = "c", title = "Tall neighbor", iconGlyph = "app", size = TileSize.LARGE),
            TileModel(id = "d", title = "Following tile", iconGlyph = "app", size = TileSize.MEDIUM),
        )
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                    appsRepository = repository, homeRequest = 0, entranceRequest = 0,
                    launchingTileId = null, isLaunching = false, interactionEnabled = true,
                    wallpaperStyle = 0, onLaunch = { _, _, _ -> }, onExitFinished = {},
                    onOpenSettings = {}, onOpenAppInfo = {})
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val before = compose.onNodeWithTag("phone-start-tile-c", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
            .performTouchInput { click() }
        repeat(10) { compose.mainClock.advanceTimeBy(64); compose.waitForIdle() }
        val folderBounds = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val neighborBounds = compose.onNodeWithTag("phone-start-tile-c", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Spanning neighbor must clear the entire expanded folder",
            neighborBounds.top >= folderBounds.bottom - 2f)
        compose.captureFidelity("folder-small-six-columns-tall-neighbor")
        compose.onNodeWithContentDescription("Collapse folder").performClick()
        repeat(8) { compose.mainClock.advanceTimeBy(64); compose.waitForIdle() }
        assertEquals(before, compose.onNodeWithTag("phone-start-tile-c", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top, 1f)
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
