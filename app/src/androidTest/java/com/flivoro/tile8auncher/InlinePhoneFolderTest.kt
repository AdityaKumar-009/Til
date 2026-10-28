package com.flivoro.tile8auncher

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.*
import com.flivoro.tile8auncher.features.*
import com.flivoro.tile8auncher.ui.phone.*
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

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
        assertEquals("Space above both rules must match", upperDivider.center.y - sourceBottom,
            divider.center.y - lastChild.bottom, 2f)
        assertEquals("Space below both rules must match", lastChild.top - upperDivider.center.y,
            followingTile.top - divider.center.y, 2f)
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

    @Test fun classicFolderHasAccentRulesAndItsOwnReversibleMotion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val mode = LauncherUiMode.PHONE_8
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 4)
        LauncherFeatureStore.setBackgroundStyle(context, mode, PhoneBackgroundStyle.TILE_PICTURE)
        StartPersonalization.ensureLoaded(context)
        val previousAccent = StartPersonalization.wallpaperAccentColor
        StartPersonalization.setWallpaperDerivedAccent(context, Color(0xFF00C040))
        try {
            val folder = checkNotNull(PhoneFolderStore.createOrAdd(context, mode, "a", "b"))
            PhoneLayoutStore.saveOrder(context, mode, listOf(folder, "c", "d"))
            val tiles = listOf("a", "b", "c", "d").map {
                TileModel(id = it, title = "Tile $it", iconGlyph = "app", size = TileSize.MEDIUM)
            }
            val launching = mutableStateOf(false)
            val selected = mutableStateOf<String?>(null)
            val sampledMillis = mutableIntStateOf(2000)
            val entrance = mutableIntStateOf(0)
            val repository = AppsRepository(context)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                CompositionLocalProvider(LocalPhoneMotionSampleMillis provides { sampledMillis.intValue }) {
                    MaterialTheme {
                        PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                            appsRepository = repository, homeRequest = 0, entranceRequest = entrance.intValue,
                            launchingTileId = selected.value, isLaunching = launching.value,
                            interactionEnabled = !launching.value, wallpaperStyle = 0,
                            onLaunch = { tile, _, _ -> selected.value = tile.id; launching.value = true },
                            onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1500)
            compose.waitForIdle()
            val neighborBefore = compose.onNodeWithTag("phone-start-tile-d", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top
            fun tapFolder() = compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
                .performTouchInput { click() }
            tapFolder()
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            val openingHeight = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.height
            compose.captureFidelity("classic-folder-open-096")
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            val fullHeight = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.height
            assertTrue("Classic folder should open progressively", openingHeight > 0f && openingHeight < fullHeight)
            val top = compose.onNodeWithTag("phone-folder-top-divider", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val bottom = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Classic rules use the same thickness", top.height, bottom.height, 1f)
            assertTrue("Classic rules are thicker than W10M's one-dp rule", top.height > 3f)
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            listOf(top, bottom).forEach { line ->
                val color = image.getPixel(line.center.x.toInt(), line.center.y.toInt())
                assertTrue("Classic rule must use the green theme accent",
                    (color shr 8 and 255) > 140 && (color shr 16 and 255) < 40)
            }
            listOf("b", "d").forEach { id ->
                val tile = compose.onNodeWithTag("phone-start-tile-$id", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val color = image.getPixel((tile.right - 20f).toInt(), (tile.bottom - 12f).toInt())
                val brightness = (color shr 16 and 255) + (color shr 8 and 255) + (color and 255)
                assertTrue("Tile-picture wallpaper must extend over the entire displaced tile $id", brightness > 15)
            }
            compose.captureFidelity("classic-folder-open-settled")

            tapFolder()
            compose.mainClock.advanceTimeBy(96)
            compose.waitForIdle()
            val closingHeight = compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.height
            assertTrue("Classic close must reverse through the same geometry", closingHeight in 1f..(fullHeight - 1f))
            compose.captureFidelity("classic-folder-close-096")
            tapFolder()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            assertEquals("Reopening must finish without a jump", fullHeight,
                compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot.height, 2f)

            // The rules belong to the classic Start scene when a child launches.
            compose.onNodeWithTag("phone-start-tile-b", useUnmergedTree = true)
                .performTouchInput { click() }
            compose.runOnIdle { assertTrue(launching.value); sampledMillis.intValue = 1000 }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val afterLaunch = compose.onRoot().captureToImage().asAndroidBitmap()
            listOf(top, bottom).forEach { line ->
                val color = afterLaunch.getPixel(line.center.x.toInt(), line.center.y.toInt())
                assertTrue("Classic rule must leave with Start", (color shr 8 and 255) < 80)
            }
            compose.runOnIdle { launching.value = false; entrance.intValue++; sampledMillis.intValue = 2000 }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onNodeWithTag("phone-folder-title").performClick()
            compose.onNodeWithTag("phone-folder-title").performTextReplacement("Classic folder")
            tapFolder()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            compose.onNodeWithTag("phone-expanded-folder", useUnmergedTree = true).assertDoesNotExist()
            assertEquals("Classic folder", PhoneFolderStore.read(context, mode).single().name)
            assertEquals(neighborBefore,
                compose.onNodeWithTag("phone-start-tile-d", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot.top, 2f)
        } finally {
            StartPersonalization.setWallpaperDerivedAccent(context, previousAccent)
        }
    }

    @Test fun folderRulesFollowLaunchAndBothReturnAnimations() {
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
        val launching = mutableStateOf(false)
        val selected = mutableStateOf<String?>(null)
        val back = mutableStateOf(false)
        val entrance = mutableIntStateOf(0)
        val home = mutableIntStateOf(0)
        val sampledMillis = mutableIntStateOf(2000)
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalPhoneMotionSampleMillis provides { sampledMillis.intValue }) {
                MaterialTheme {
                    PhoneLauncherSurface(mode = mode, tiles = tiles, sections = emptyList(),
                        appsRepository = repository, homeRequest = home.intValue,
                        entranceRequest = entrance.intValue, launchingTileId = selected.value,
                        isLaunching = launching.value, interactionEnabled = !launching.value,
                        wallpaperStyle = 0, resumeUsesBackMotion = back.value,
                        onLaunch = { tile, _, _ -> selected.value = tile.id; launching.value = true },
                        onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onNodeWithTag("phone-start-tile-$folder", useUnmergedTree = true)
            .performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val tags = listOf("phone-folder-top-divider", "phone-folder-bottom-divider")
        val restingBounds = tags.map { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val points = restingBounds.map { it.center.x.toInt() to it.center.y.toInt() }
        fun brightness(): List<Int> {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            return points.map { (x, y) ->
                val color = image.getPixel(x, y)
                (((color shr 16) and 255) + ((color shr 8) and 255) + (color and 255)) / 3
            }
        }
        val restingBrightness = brightness()
        assertTrue("Both rules must be visible before launch", restingBrightness.all { it > 70 })
        compose.onNodeWithTag("phone-start-tile-b", useUnmergedTree = true)
            .performTouchInput { click() }
        compose.runOnIdle { assertTrue(launching.value); sampledMillis.intValue = 250 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val movingBottom = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The lower rule must move with Start during app launch",
            abs(movingBottom.center.y - restingBounds[1].center.y) > 2f ||
                abs(movingBottom.width - restingBounds[1].width) > 2f)
        compose.captureFidelity("folder-rules-launch-mid")
        compose.runOnIdle { sampledMillis.intValue = 534 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("Neither folder rule may remain stationary after launch", brightness().all { it < 55 })
        compose.captureFidelity("folder-rules-launch-end")

        compose.runOnIdle {
            launching.value = false
            back.value = true
            entrance.intValue++
            sampledMillis.intValue = 0
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("Neither rule may flash before Back return", brightness().all { it < 55 })
        compose.runOnIdle { sampledMillis.intValue = 267 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val backBottom = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The lower rule must move with Start during Back return",
            abs(backBottom.center.y - restingBounds[1].center.y) > 2f ||
                abs(backBottom.width - restingBounds[1].width) > 2f)
        compose.captureFidelity("folder-rules-back-mid")
        compose.runOnIdle { sampledMillis.intValue = 534 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("Both rules must return after Back", brightness().all { it > 70 })

        compose.runOnIdle { back.value = false; home.intValue++; sampledMillis.intValue = 0 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("Neither rule may flash before Home entry", brightness().all { it < 55 })
        compose.runOnIdle { sampledMillis.intValue = 315 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val homeBottom = compose.onNodeWithTag("phone-folder-bottom-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The lower rule must move with Start during Home entry",
            abs(homeBottom.center.y - restingBounds[1].center.y) > 2f ||
                abs(homeBottom.width - restingBounds[1].width) > 2f)
        compose.captureFidelity("folder-rules-home-mid")
        compose.runOnIdle { sampledMillis.intValue = 630 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("Both rules must return after Home", brightness().all { it > 70 })
        compose.captureFidelity("folder-rules-return-settled")
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
