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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
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
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.LocalStoryLaunchScene
import com.flivoro.tile8auncher.ui.components.StoryLaunchOverlay
import com.flivoro.tile8auncher.ui.components.StoryLaunchScene
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchSourceFace
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchDestinationIcon
import com.flivoro.tile8auncher.ui.start.StartScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Pixel checks on the actual six public surfaces, for every optional story. */
@RunWith(org.junit.runners.Parameterized::class)
class StoryLaunchRenderTest(
    private val mode: LauncherUiMode,
    private val origin: LaunchOrigin,
    private val style: MasterLaunchAnimation,
) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @org.junit.runners.Parameterized.Parameters(name = "{0}/{1}/{2}")
        fun cases(): List<Array<Any>> = LauncherUiMode.entries.flatMap { mode ->
            listOf(LaunchOrigin.START, LaunchOrigin.ALL_APPS).flatMap { origin ->
                MasterLaunchAnimation.entries.filter { it.isStory }.map { style ->
                    arrayOf<Any>(mode, origin, style)
                }
            }
        }
    }
    @Test fun sourceAndReturnRemainContinuous() = verify(mode, origin)

    private fun verify(mode: LauncherUiMode, origin: LaunchOrigin) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        LauncherFeatureStore.setLauncherUiMode(context, mode)
        LauncherFeatureStore.setShowRecentlyInstalledPhoneApps(context, true)
        LauncherFeatureStore.setBackgroundStyle(context, mode,
            com.flivoro.tile8auncher.features.PhoneBackgroundStyle.FULL_SCREEN)
        LauncherFeatureStore.setPhoneTileOpacity(context, 100)
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
                                onAppClick = { app, bounds ->
                                    clickedTile = selected.copy(colorValue =
                                        repository.getDiscoTileAccentColor(app.packageName)
                                            ?: com.flivoro.tile8auncher.ui.components.StartPersonalization.wallpaperAccentArgb)
                                    clickedBounds = bounds
                                },
                                isAppPinned = { false }, onPinApp = {}, onUnpinApp = {},
                                onOpenAppInfo = {}, onUninstallApp = {}, onNavigateToStart = {})
                        }
                        if (scene != null && overlay.value) {
                            val phone = mode != LauncherUiMode.DESKTOP
                            StoryLaunchOverlay(scene.state, remember(scene) { Animatable(0f) }, {}, {},
                                sourceForeground = if (phone) { modifier ->
                                    PhoneLaunchSourceFace(clickedTile, mode, repository, modifier,
                                        sourceOrigin = origin, foregroundOnly = true, detailsOnly = true)
                                } else null,
                                sourceBackground = if (phone) { modifier ->
                                    PhoneLaunchSourceFace(clickedTile, mode, repository, modifier,
                                        sourceBounds = clickedBounds, sourceOrigin = origin, backgroundOnly = true)
                                } else null,
                                destinationIcon = if (phone) { modifier ->
                                    PhoneLaunchDestinationIcon(clickedTile, mode, repository, modifier,
                                        sourceOrigin = origin,
                                        iconSize = if (origin == LaunchOrigin.ALL_APPS) 28.dp else 44.dp)
                                } else null)
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
        run {
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
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                scene.sourcePrepared && scene.sourceOwner != null
            }
            val first = shot()
            compose.runOnIdle {
                assertNotNull("$mode/$origin/$style did not capture its REAL source cell", scene.sourceOwner)
                assertTrue(scene.capturedSourceBounds.width > 0f)
            }
            val initialDifference = difference(baseline, first)
            if (initialDifference >= .04) {
                saveFailure(baseline, first, "initial", initialDifference)
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
            if (mode == LauncherUiMode.MOBILE_10) {
                // Review early acceleration and the connected handoff, rather
                // than judging continuity from a single midpoint screenshot.
                compose.runOnIdle { overlay.value = true }
                save(first, "cross-story-sequence-${origin.name}-${style.name}-000")
                val samples = if (style == MasterLaunchAnimation.PASSAGE || style == MasterLaunchAnimation.CASCADE)
                    (1..36).map { it / 36f } else listOf(.08f, .24f, .40f)
                for (time in samples) {
                    compose.runOnIdle { sample.floatValue = time }
                    save(shot(), "cross-story-sequence-${origin.name}-${style.name}-${(time * 1000).toInt()}")
                }
                compose.runOnIdle { overlay.value = false }
            }
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
            // Render the return, including the point where transparent child
            // layers become visible again, instead of teleporting past it.
            compose.runOnIdle { scene.returning = true }
            for (step in 16 downTo 0) {
                compose.runOnIdle { sample.floatValue = .52f * step / 16f }
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            val returned = shot()
            val returnDifference = difference(baseline, returned)
            if (returnDifference >= .006) {
                saveFailure(baseline, returned, "return", returnDifference)
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

    private fun saveFailure(expected: Bitmap, actual: Bitmap, phase: String, delta: Double) {
        val name = "cross-story-failure-${mode.name}-${origin.name}-${style.name}-$phase"
        save(expected, "$name-expected")
        save(actual, "$name-actual")
        val difference = Bitmap.createBitmap(expected.width, expected.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            val a = expected.getPixel(x, y); val b = actual.getPixel(x, y)
            difference.setPixel(x, y, android.graphics.Color.rgb(
                abs((a shr 16 and 255) - (b shr 16 and 255)),
                abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255))))
        }
        save(difference, "$name-difference")
        difference.recycle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = java.io.File(context.getExternalFilesDir(null), "motion-frames").apply { mkdirs() }
        java.io.File(dir, "$name.json").writeText(
            """{"mode":"$mode","origin":"$origin","style":"$style","phase":"$phase","poseMillis":0,"changedFraction":$delta,"sdk":${android.os.Build.VERSION.SDK_INT},"width":${actual.width},"height":${actual.height}}""")
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
