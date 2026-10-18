package com.flivoro.tile8auncher

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.LocalStoryLaunchScene
import com.flivoro.tile8auncher.ui.components.StoryLaunchOverlay
import com.flivoro.tile8auncher.ui.components.StoryLaunchScene
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.start.StartScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Pixel checks on the actual six public surfaces, for all five new stories. */
@RunWith(AndroidJUnit4::class)
class StoryLaunchRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun desktopStart() = verify(LauncherUiMode.DESKTOP, LaunchOrigin.START)
    @Test fun desktopApps() = verify(LauncherUiMode.DESKTOP, LaunchOrigin.ALL_APPS)
    @Test fun wp81Start() = verify(LauncherUiMode.PHONE_8, LaunchOrigin.START)
    @Test fun wp81Apps() = verify(LauncherUiMode.PHONE_8, LaunchOrigin.ALL_APPS)
    @Test fun w10mStart() = verify(LauncherUiMode.MOBILE_10, LaunchOrigin.START)
    @Test fun w10mApps() = verify(LauncherUiMode.MOBILE_10, LaunchOrigin.ALL_APPS)

    private fun verify(mode: LauncherUiMode, origin: LaunchOrigin) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setShowRecentlyInstalledPhoneApps(context, true)
        val repository = AppsRepository(context)
        val colors = listOf(0xFFE3008C, 0xFF008A00, 0xFF00A4EF, 0xFFF09609)
        val tiles = List(12) { n -> TileModel(id = "story_$n", title = "Story $n",
            iconGlyph = "app", size = TileSize.MEDIUM, colorValue = colors[n % colors.size]) }
        val apps = listOf(AppSection("A", listOf(
            AppInfo("Alpha", "com.example.alpha", "com.example.alpha.Main"),
            AppInfo("Alpine", "com.example.alpine", "com.example.alpine.Main"),
            AppInfo("Another", "com.example.another", "com.example.another.Main"),
        )), AppSection("B", listOf(
            AppInfo("Beta", "com.example.beta", "com.example.beta.Main"),
            AppInfo("Browser", "com.example.browser", "com.example.browser.Main"),
        )))
        val selected = if (origin == LaunchOrigin.START) tiles.first() else TileModel(
            id = if (mode == LauncherUiMode.DESKTOP) "app_com.example.alpha" else "phone_app_com.example.alpha",
            title = "Alpha", packageName = "com.example.alpha", colorValue = colors.first())
        var clickedTile = selected
        var clickedBounds = Rect.Zero
        val sample = mutableFloatStateOf(0f)
        val active = mutableStateOf<StoryLaunchScene?>(null)
        val overlay = mutableStateOf(true)
        var rootBounds = Rect.Zero
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scene = active.value
            CompositionLocalProvider(LocalStoryLaunchScene provides scene) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)
                        .onGloballyPositioned { rootBounds = it.boundsInWindow() }) {
                        when {
                            mode != LauncherUiMode.DESKTOP -> PhoneLauncherSurface(
                                mode = mode, tiles = tiles, sections = apps, appsRepository = repository,
                                homeRequest = 0, entranceRequest = 0, launchingTileId = null,
                                preparedRecentApps = if (mode == LauncherUiMode.MOBILE_10) apps.first().apps.take(1) else emptyList(),
                                isLaunching = false, interactionEnabled = scene == null, wallpaperStyle = 0,
                                onLaunch = { tile, bounds, _ -> clickedTile = tile; clickedBounds = bounds },
                                onExitFinished = {}, onOpenSettings = {}, onOpenAppInfo = {})
                            origin == LaunchOrigin.START -> StartScreen(
                                tiles = tiles, appsRepository = repository,
                                onTileClick = { tile, bounds -> clickedTile = tile; clickedBounds = bounds },
                                onTileLongClick = {}, onPowerClick = {}, onSearchClick = {}, onAddAppsClick = {},
                                onNavigateToAllApps = {})
                            else -> AllAppsScreen(sections = apps, appsRepository = repository,
                                onAppClick = { _, bounds -> clickedBounds = bounds },
                                isAppPinned = { false }, onPinApp = {}, onUnpinApp = {},
                                onOpenAppInfo = {}, onUninstallApp = {}, onNavigateToStart = {})
                        }
                        if (scene != null && overlay.value) {
                            StoryLaunchOverlay(scene.state, remember(scene) { Animatable(0f) }, {}, {})
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1900)
        if (origin == LaunchOrigin.ALL_APPS && mode != LauncherUiMode.DESKTOP) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(600)
        }
        if (origin == LaunchOrigin.ALL_APPS) compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
        }
        // Exercise the real click callback: Start supplies its full tile;
        // Desktop Apps supplies the icon plate; phone Apps supplies its row.
        // Text bounds alone describe none of those launch surfaces correctly.
        compose.onAllNodesWithText(if (origin == LaunchOrigin.START) "Story 0" else "Alpha",
            useUnmergedTree = true)[0].performTouchInput { click() }
        compose.mainClock.advanceTimeBy(700)
        val baseline = shot()
        assertTrue("$mode/$origin did not deliver its real launch bounds", clickedBounds.width > 1f)
        for (style in MasterLaunchAnimation.entries.filter { it.isStory }) {
            val state = FlipAnimationState(isRunning = true, sourceTile = clickedTile,
                sourceBounds = clickedBounds,
                masterLaunchAnimation = style, origin = origin,
                accentColor = Color(clickedTile.colorValue))
            val scene = StoryLaunchScene(state) { sample.floatValue }
            compose.runOnIdle {
                scene.viewport = rootBounds
                sample.floatValue = 0f
                overlay.value = true
                active.value = scene
            }
            compose.mainClock.advanceTimeBy(48)
            val first = shot()
            compose.runOnIdle {
                assertNotNull("$mode/$origin/$style did not capture its REAL source cell", scene.sourceLayer)
                assertTrue(scene.capturedSourceBounds.width > 0f)
            }
            val initialDifference = difference(baseline, first)
            if (initialDifference >= .04) {
                save(baseline, "cross-story-failure-${mode.name}-${origin.name}-${style.name}-baseline")
                save(first, "cross-story-failure-${mode.name}-${origin.name}-${style.name}-first")
            }
            assertTrue("$mode/$origin/$style changed the source artwork at time zero",
                initialDifference < .04)

            // Inspect the physical scene WITHOUT its foreground launch plane.
            // This catches a stationary duplicated source or static neighbors.
            compose.runOnIdle { overlay.value = false }
            compose.mainClock.advanceTimeByFrame()
            val hidden = shot()
            assertTrue("$mode/$origin/$style left a duplicate source at its original location",
                difference(first, hidden, scene.capturedSourceBounds, rootBounds) > .06)
            compose.runOnIdle { sample.floatValue = .52f }
            compose.mainClock.advanceTimeByFrame()
            val neighbors = shot()
            assertTrue("$mode/$origin/$style did not move its REAL neighboring actors",
                difference(hidden, neighbors) > .005)
            compose.runOnIdle { overlay.value = true }
            compose.mainClock.advanceTimeBy(32)
            val middle = shot()
            save(middle, "cross-story-${mode.name}-${origin.name}-${style.name}")
            compose.runOnIdle { sample.floatValue = 1f }
            compose.mainClock.advanceTimeByFrame()
            val end = shot()
            assertTrue("$mode/$origin/$style never reached its destination", difference(first, end) > .05)
            compose.runOnIdle { sample.floatValue = .52f }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("$mode/$origin/$style returned through different geometry",
                difference(middle, shot()) < .006)
            compose.runOnIdle { sample.floatValue = 0f }
            compose.mainClock.advanceTimeByFrame()
            val returned = shot()
            val returnDifference = difference(first, returned)
            if (returnDifference >= .006) {
                save(first, "cross-story-failure-${mode.name}-${origin.name}-${style.name}-first")
                save(returned, "cross-story-failure-${mode.name}-${origin.name}-${style.name}-return")
            }
            assertTrue("$mode/$origin/$style snapped on return ($returnDifference)", returnDifference < .006)
            compose.runOnIdle { active.value = null }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun shot(): Bitmap {
        // A virtual clock advance alone doesn't perform Android drawing. Source
        // recording publishes its layer/bounds during that draw, so interleave
        // completed draws and Compose frames before sampling the new pose.
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    private fun difference(a: Bitmap, b: Bitmap, region: Rect? = null, viewport: Rect? = null): Double {
        val offset = viewport?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero
        val rect = region?.translate(-offset) ?: Rect(0f, 0f, a.width.toFloat(), a.height.toFloat())
        var changed = 0
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(a.height) step 5) {
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(a.width) step 5) {
                val first = a.getPixel(x, y)
                val second = b.getPixel(x, y)
                val delta = abs((first shr 16 and 255) - (second shr 16 and 255)) +
                    abs((first shr 8 and 255) - (second shr 8 and 255)) +
                    abs((first and 255) - (second and 255))
                if (delta > 30) changed++
                count++
            }
        }
        return changed.toDouble() / count.coerceAtLeast(1)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Til-motion-frames")
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        val scaled = Bitmap.createScaledBitmap(bitmap, 360, (bitmap.height * 360f / bitmap.width).toInt(), true)
        checkNotNull(context.contentResolver.openOutputStream(uri)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        scaled.recycle()
    }
}
