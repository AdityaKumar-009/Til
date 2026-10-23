package com.flivoro.tile8auncher

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.phone.LocalPhoneMotionSampleMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exercises the real Compose layers and lifecycle, not just the motion equations. */
@RunWith(AndroidJUnit4::class)
class PhoneMotionRenderTest {
    @get:Rule val compose = createComposeRule()
    private var capturePrefix = ""

    @Test fun mobileExitHoldsItsFinalFrameAndReturnRestoresTheSurface() {
        captureMode(LauncherUiMode.MOBILE_10, "", 16, 608, 640, 384)
    }

    @Test fun classicNativeExitAndReturnAreCapturedAtEveryFrame() {
        captureMode(LauncherUiMode.PHONE_8, "wp81-", 16, 640, 800, 400)
    }

    private fun captureMode(
        mode: LauncherUiMode,
        prefix: String,
        frameStep: Int,
        exitEnd: Int,
        entryEnd: Int,
        beforeCompletion: Int,
    ) {
        capturePrefix = prefix
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, if (mode == LauncherUiMode.PHONE_8) 4 else 6)
        LauncherFeatureStore.setPhoneTileOpacity(context, 100)
        LauncherFeatureStore.setBackgroundStyle(context, mode,
            com.flivoro.tile8auncher.features.PhoneBackgroundStyle.FULL_SCREEN)
        StartPersonalization.ensureLoaded(context)
        StartPersonalization.setCustomWallpaperUri(context, null)
        val colors = listOf(0xFF00A4EF, 0xFFE3008C, 0xFF008A00, 0xFFF09609, 0xFF603CBA)
        val classicSizes = listOf(TileSize.MEDIUM, TileSize.SMALL, TileSize.SMALL,
            TileSize.SMALL, TileSize.SMALL, TileSize.MEDIUM, TileSize.MEDIUM,
            TileSize.WIDE, TileSize.MEDIUM, TileSize.MEDIUM)
        val tiles = List(if (mode == LauncherUiMode.PHONE_8) classicSizes.size else 15) { index -> TileModel(
            id = "motion_$index", title = "Tile ${index + 1}",
            iconGlyph = ('A' + index).toString(), colorValue = colors[index % colors.size],
            size = if (mode == LauncherUiMode.PHONE_8) classicSizes[index] else TileSize.MEDIUM,
        ) }
        val sections = listOf(
            AppSection("A", listOf(
                AppInfo("Alpha", "com.example.alpha", "com.example.alpha.Main"),
                AppInfo("Alpine", "com.example.alpine", "com.example.alpine.Main"),
                AppInfo("App Social", "com.example.social", "com.example.social.Main"),
            )),
            AppSection("B", listOf(
                AppInfo("Beta", "com.example.beta", "com.example.beta.Main"),
                AppInfo("Budíky", "com.example.clocks", "com.example.clocks.Main"),
            )),
            AppSection("C", listOf(
                AppInfo("Cestování", "com.example.travel", "com.example.travel.Main"),
                AppInfo("Camera", "com.example.camera", "com.example.camera.Main"),
            )),
            AppSection("F", listOf(
                AppInfo("Finance", "com.example.finance", "com.example.finance.Main"),
                AppInfo("Fotky", "com.example.photos", "com.example.photos.Main"),
            )),
            AppSection("H", listOf(
                AppInfo("HERE Maps", "com.example.maps", "com.example.maps.Main"),
            )),
            AppSection("O", listOf(
                AppInfo("Office", "com.example.office", "com.example.office.Main"),
            )),
            AppSection("T", listOf(
                AppInfo("Telefon", "com.example.phone", "com.example.phone.Main"),
            )),
        )
        val launching = mutableStateOf(false)
        val selected = mutableStateOf<String?>(null)
        val lastLaunchedTileId = mutableStateOf<String?>(null)
        val home = mutableIntStateOf(0)
        val entrance = mutableIntStateOf(0)
        val resumeUsesBackMotion = mutableStateOf(false)
        val sampledMotionMillis = mutableStateOf<Int?>(null)
        var completions = 0
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalPhoneMotionSampleMillis provides { sampledMotionMillis.value }) {
                MaterialTheme {
                    PhoneLauncherSurface(
                        mode = mode, tiles = tiles, sections = sections,
                        appsRepository = repository, homeRequest = home.intValue,
                        entranceRequest = entrance.intValue, launchingTileId = selected.value,
                        isLaunching = launching.value, interactionEnabled = !launching.value,
                        wallpaperStyle = 0, resumeUsesBackMotion = resumeUsesBackMotion.value,
                        returningTileId = lastLaunchedTileId.value,
                        onLaunch = { tile, _, _ ->
                            selected.value = tile.id
                            lastLaunchedTileId.value = tile.id
                            launching.value = true
                        },
                        onExitFinished = { completions++ }, onOpenSettings = {}, onOpenAppInfo = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(if (mode == LauncherUiMode.PHONE_8) 2000 else 1000)
        val resting = capture("00-rest")
        assertTrue("Fixture must draw actual tiles", coloredFraction(resting) > .35)

        // Both WP8.1 and W10M must advance with the finger *before* pointer-up.
        // Check two distinct held positions: a gesture-only transition would
        // leave both frames unchanged until the release.
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-width * .30f, 0f), 100)
        }
        compose.mainClock.advanceTimeByFrame()
        val heldThirtyPercent = capture("panorama-finger-30-percent")
        assertTrue("${mode.name} panorama did not move with the held finger",
            difference(resting, heldThirtyPercent) > .03)
        compose.onRoot().performTouchInput {
            moveBy(Offset(-width * .30f, 0f), 100)
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("${mode.name} panorama stopped following the finger during drag",
            difference(heldThirtyPercent, capture("panorama-finger-60-percent")) > .03)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Alpha").assertIsDisplayed()

        if (mode == LauncherUiMode.MOBILE_10) {
            // Verify that swiping backward follows the finger too, not merely
            // Start -> All Apps. Finish on Start for the remaining motion tests.
            val appsRestAfterDrag = capture("w10-apps-after-finger-swipe")
            compose.onRoot().performTouchInput {
                down(center)
                moveBy(Offset(width * .30f, 0f), 100)
            }
            compose.mainClock.advanceTimeByFrame()
            val reverseHeld = capture("w10-reverse-finger-30-percent")
            assertTrue("W10M All Apps -> Start remained still during held drag",
                difference(appsRestAfterDrag, reverseHeld) > .03)
            compose.onRoot().performTouchInput {
                moveBy(Offset(width * .30f, 0f), 100)
            }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("W10M reverse panorama stopped following the finger",
                difference(reverseHeld, capture("w10-reverse-finger-60-percent")) > .03)
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(400)
        } else {
            compose.onRoot().performTouchInput { swipeRight() }
            compose.mainClock.advanceTimeBy(400)

            // Home on an already-settled Start page must NOT replay the entry.
            // The previous test expected exactly the unwanted regression reported
            // on device. Keep the return-from-All-Apps animation checked below.
            val rootBeforeRepeatHome = capture("start-before-repeated-home")
            // The real Android Home key, not an in-app control, now owns root Home.
            // A settled Start without a new external Home request remains stable.
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("wp81-home-return-app-page").assertDoesNotExist()
            val rootAfterRepeatHome = capture("start-after-repeated-home")
            assertTrue("Repeated Home unexpectedly restarted the Start animation",
                difference(rootBeforeRepeatHome, rootAfterRepeatHome) < .01)
            compose.mainClock.advanceTimeBy(1664)
            assertTrue("Repeated Home changed the settled Start screen",
                difference(rootBeforeRepeatHome, capture("start-home-idle")) < .01)
        }

        // A panorama round trip must reveal real All Apps pixels, not merely
        // keep offscreen/semantics nodes alive (the previous regression).
        compose.onRoot().performTouchInput { swipeLeft() }
        if (mode == LauncherUiMode.MOBILE_10) {
            // A horizontal swipe must not replay row-entrance choreography.
            // Once the panorama reaches All Apps, its contents stay frame-stable.
            compose.mainClock.advanceTimeBy(280)
            val appsEntered = capture("w10-all-apps-entered")
            compose.mainClock.advanceTimeBy(400)
            val appsSettled = capture("w10-all-apps-settled")
            assertTrue("W10M All Apps row labels are missing",
                brightPixelsInMobileAppLabelRegion(appsSettled) > 25)
            assertTrue("W10M All Apps rows animated after the page finished sliding",
                difference(appsEntered, appsSettled) < .01)
        } else {
            compose.mainClock.advanceTimeBy(400)
        }
        if (mode == LauncherUiMode.PHONE_8) {
            compose.onNodeWithText("Alpha").assertIsDisplayed()
            val appsAfterSwipe = capture("apps-after-manual-swipe")
            assertTrue("All Apps page is black or offscreen after navigation",
                brightPixelsInAppLabelRegion(appsAfterSwipe) > 100)
            assertTrue("All Apps must differ from the Start page",
                difference(resting, appsAfterSwipe) > .01)
        }
        val appsAtTop = capture("apps-at-top")
        compose.onRoot().performTouchInput {
            down(Offset(width * .75f, height * .62f))
            moveBy(Offset(0f, height * .20f), 100)
        }
        compose.mainClock.advanceTimeByFrame()
        val appsPulledPastTop = capture("apps-elastic-pull")
        assertTrue("${mode.name} All Apps did not stretch at its top edge",
            difference(appsAtTop, appsPulledPastTop) > .001)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        assertTrue("${mode.name} All Apps did not spring back from its top edge",
            difference(appsAtTop, capture("apps-elastic-return")) < .01)
        compose.onRoot().performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(320)
        assertTrue("Start flickered or replayed entry after Apps", difference(resting,
            capture("01-panorama-return")) < .01)

        // External state mirrors the launch coordinator. The outgoing pose must
        // survive selected-ID clearing before the external window is attached.
        compose.runOnIdle { selected.value = tiles.first().id; launching.value = true }
        compose.mainClock.advanceTimeByFrame() // recompose the state change
        compose.mainClock.advanceTimeByFrame() // animation's first frame is t=0
        capture("exit-000")
        repeat(exitEnd / frameStep) { frame ->
            compose.mainClock.advanceTimeBy(frameStep.toLong())
            val elapsed = (frame + 1) * frameStep
            capture("exit-${elapsed.toString().padStart(3, '0')}")
            if (elapsed == beforeCompletion) compose.runOnIdle {
                assertEquals("App opened before its exit animation finished", 0, completions)
            }
        }
        val finalExit = capture("exit-$exitEnd-handoff")
        compose.runOnIdle { assertEquals("Expected one completed handoff", 1, completions) }
        assertTrue("Start content must be black at handoff", centralBrightness(finalExit) < .01)
        compose.runOnIdle { selected.value = null }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("Outgoing tiles reappeared during Android handoff",
            difference(finalExit, capture("exit-held")) < .001)
        compose.runOnIdle { assertEquals(1, completions) }

        compose.runOnIdle {
            launching.value = false
            // Explicit Home uses the forward/native entry. Back is captured separately.
            resumeUsesBackMotion.value = false
            entrance.intValue++
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        capture("entry-000")
        repeat(entryEnd / frameStep) { frame ->
            compose.mainClock.advanceTimeBy(frameStep.toLong())
            capture("entry-${((frame + 1) * frameStep).toString().padStart(3, '0')}")
        }
        assertTrue("Return failed to restore the original tile geometry",
            difference(resting, capture("02-return-settled")) < .01)

        // Capture the actual Back lifecycle too. The earlier test exercised only
        // explicit Home; the two navigation phases must remain distinct.
        compose.runOnIdle {
            resumeUsesBackMotion.value = true
            entrance.intValue++
            sampledMotionMillis.value = 0
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val startBackFirst = capture("start-back-return-000")
        assertTrue("Start Back must begin with hidden tiles", centralBrightness(startBackFirst) < .01)
        for (elapsed in 10..entryEnd step 10) {
            compose.runOnIdle { sampledMotionMillis.value = elapsed }
            compose.mainClock.advanceTimeByFrame()
            capture("start-back-return-${elapsed.toString().padStart(3, '0')}")
        }
        assertTrue("Start Back did not restore its original geometry",
            difference(resting, capture("start-back-return-settled")) < .01)
        compose.runOnIdle { sampledMotionMillis.value = null }

        if (mode == LauncherUiMode.PHONE_8) {
            // DiscoLauncher snaps the panorama to Start and runs the second
            // Apps page's own forward turn behind the entering Start tiles.
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("Alpha").assertIsDisplayed()
            val appsBeforeHome = capture("apps-home-return-before")
            assertTrue("App-list label fixture must be visible before Home",
                brightPixelsInAppLabelRegion(appsBeforeHome) > 100)
            compose.runOnIdle {
                resumeUsesBackMotion.value = false
                home.intValue++
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val homeReturnStart = capture("home-return-000")
            compose.onNodeWithTag("wp81-home-return-app-page").assertExists()
            compose.onNodeWithText("Alpha").assertExists()
            assertTrue("Apps page begins transparent before its delayed turn",
                brightPixelsInAppsSearchRegion(homeReturnStart) < 5)
            var homeReturnMidpoint: Bitmap? = null
            var appsPageRowsFrame: Bitmap? = null
            var appsPageVisibleFrame: Bitmap? = null
            for (elapsed in frameStep..entryEnd step frameStep) {
                compose.mainClock.advanceTimeBy(frameStep.toLong())
                val frame = capture("home-return-${elapsed.toString().padStart(3, '0')}")
                if (elapsed == 128) {
                    assertTrue("First Start tiles should appear during native-stagger entry",
                        chromaticFraction(frame) > .0005)
                }
                // Sample the native second-pane turn during the tile cascade.
                if (elapsed == 112) appsPageRowsFrame = frame
                if (elapsed == 160) appsPageVisibleFrame = frame
                if (elapsed == 416) homeReturnMidpoint = frame
            }
            val visibleAppLabelPixels = brightPixelsInAppLabelRegion(
                checkNotNull(appsPageRowsFrame), minimumChannelValue = 40,
            )
            assertTrue(
                "All Apps row labels must turn into view behind the entering Start tiles " +
                    "(found $visibleAppLabelPixels projected text pixels)",
                visibleAppLabelPixels > 5,
            )
            assertTrue("All Apps search control must be visibly projected behind Start",
                brightPixelsInAppsSearchRegion(checkNotNull(appsPageVisibleFrame)) > 5)
            assertTrue("WP8.1 Start tiles must enter during the Home return",
                difference(homeReturnStart, checkNotNull(homeReturnMidpoint)) > .001)
            val homeReturnSettled = capture("home-return-settled")
            compose.onNodeWithTag("wp81-home-return-app-page").assertDoesNotExist()
            assertTrue("Home return must settle on the original Start layout",
                difference(resting, homeReturnSettled) < .01)

            // Exercise the separate app-list row/letter turn on the real Compose layers.
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(400)
            capture("apps-rest")
            val firstIconY = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center.y.toInt()
            val restingIconWidth = purpleIconWidthAt(appsBeforeHome, firstIconY)
            assertTrue("Expected a visible reference app icon", restingIconWidth > 30)
            compose.runOnIdle { completions = 0 }
            compose.onNodeWithText("Alpha").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            capture("apps-exit-000")
            repeat(exitEnd / frameStep) { frame ->
                compose.mainClock.advanceTimeBy(frameStep.toLong())
                val elapsed = (frame + 1) * frameStep
                capture("apps-exit-${elapsed.toString().padStart(3, '0')}")
                if (elapsed == beforeCompletion) compose.runOnIdle {
                    assertEquals("App-list launch completed before its exit envelope", 0, completions)
                }
            }
            val appListExit = capture("apps-exit-$exitEnd-handoff")
            compose.runOnIdle { assertEquals("Expected app-list launch completion", 1, completions) }
            assertTrue("App-list content must be black at handoff", centralBrightness(appListExit) < .01)

            // Native All Apps Back has its own bottom-to-top reveal and turn.
            // It must stay on the same panorama from the first returning frame.
            compose.runOnIdle {
                selected.value = null
                launching.value = false
                resumeUsesBackMotion.value = true
                entrance.intValue++
                sampledMotionMillis.value = 0
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val backReturnStart = capture("apps-back-return-000")
            assertTrue("WP8.1 Back revealed the list before its return turn",
                brightPixelsInAppLabelRegion(backReturnStart) < 5)
            assertTrue("WP8.1 Back showed Start tiles instead of the hidden Apps pose",
                centralBrightness(backReturnStart) < .01)
            var previousIconWidth = 0
            for (elapsed in 10..entryEnd step 10) {
                compose.runOnIdle { sampledMotionMillis.value = elapsed }
                compose.mainClock.advanceTimeByFrame()
                val frame = capture("apps-back-return-${elapsed.toString().padStart(3, '0')}")
                if (elapsed in 300..390) {
                    val width = purpleIconWidthAt(frame, firstIconY)
                    assertTrue("Fading WP8.1 icon was cropped at ${elapsed}ms: $width/$restingIconWidth",
                        width >= restingIconWidth * .88f)
                    if (previousIconWidth > 0) assertTrue("WP8.1 icon edge popped at ${elapsed}ms",
                        abs(width - previousIconWidth) <= restingIconWidth * .04f)
                    previousIconWidth = width
                }
            }
            val appListBackSettled = capture("apps-back-return-settled")
            assertTrue("WP8.1 Back never revealed the All Apps rows",
                difference(backReturnStart, appListBackSettled) > .01)
            assertTrue("WP8.1 Back should finish on All Apps, without a panorama slide",
                difference(appsBeforeHome, appListBackSettled) < .02)
        } else if (mode == LauncherUiMode.MOBILE_10) {
            // W10M All Apps uses the same centered row zoom/fade as Start when
            // an app opens, with the page held until its wallpaper fade ends.
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(700)
            val appsRest = capture("w10-apps-launch-rest")
            assertTrue("W10M All Apps app row fixture is not visible",
                brightPixelsInMobileAppLabelRegion(appsRest) > 25)
            compose.runOnIdle { completions = 0 }
            compose.onNodeWithText("Alpha").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val appsExitStart = capture("w10-apps-launch-000")
            for (elapsed in frameStep..exitEnd step frameStep) {
                compose.mainClock.advanceTimeBy(frameStep.toLong())
                val frame = capture("w10-apps-launch-${elapsed.toString().padStart(3, '0')}")
                if (elapsed == 192) assertTrue("W10M All Apps rows did not animate out",
                    difference(appsExitStart, frame) > .001)
                if (elapsed == beforeCompletion) compose.runOnIdle {
                    assertEquals("W10M app-list launch completed before its exit envelope", 0, completions)
                }
            }
            val appListExit = capture("w10-apps-launch-$exitEnd-handoff")
            compose.runOnIdle { assertEquals("Expected W10M app-list launch completion", 1, completions) }
            assertTrue("W10M All Apps page must be black at handoff", centralBrightness(appListExit) < .01)

            compose.runOnIdle {
                selected.value = null
                launching.value = false
                resumeUsesBackMotion.value = true
                entrance.intValue++
                sampledMotionMillis.value = 0
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val appsBackFirst = capture("w10-apps-back-return-000")
            assertTrue("W10M All Apps Back must start hidden", centralBrightness(appsBackFirst) < .01)
            assertNoStartTilesInAppsGutter(appsBackFirst, 0)
            for (elapsed in 10..entryEnd step 10) {
                compose.runOnIdle { sampledMotionMillis.value = elapsed }
                compose.mainClock.advanceTimeByFrame()
                val frame = capture("w10-apps-back-return-${elapsed.toString().padStart(3, '0')}")
                assertNoStartTilesInAppsGutter(frame, elapsed)
            }
            compose.onNodeWithText("Alpha").assertIsDisplayed()
            assertTrue("W10M Back failed to restore the originating app list",
                difference(appsRest, capture("w10-apps-back-return-settled")) < .01)
        }
    }

    private fun capture(name: String): Bitmap {
        compose.waitForIdle() // Android draw is separate from the manual Compose clock.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // AGP uninstalls the test target after connected tests and removes its
            // private external files. Export evidence through MediaStore so it
            // survives that cleanup; this requires no broad storage permission.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$capturePrefix$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Til-motion-frames")
            }
            val uri = checkNotNull(context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } else {
            val directory = File(context.getExternalFilesDir(null), "motion-frames").apply { mkdirs() }
            File(directory, "$capturePrefix$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        return bitmap
    }

    private fun coloredFraction(bitmap: Bitmap): Double {
        var colored = 0
        var count = 0
        for (y in bitmap.height / 10 until bitmap.height * 8 / 10 step 4) {
            for (x in bitmap.width / 10 until bitmap.width * 9 / 10 step 4) {
                val pixel = bitmap.getPixel(x, y)
                if (maxOf((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255) > 120) colored++
                count++
            }
        }
        return colored.toDouble() / count
    }

    private fun chromaticFraction(bitmap: Bitmap): Double {
        var colored = 0
        var count = 0
        for (y in bitmap.height / 10 until bitmap.height * 8 / 10 step 4) {
            for (x in bitmap.width / 10 until bitmap.width * 9 / 10 step 4) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                val maximum = maxOf(red, green, blue)
                if (maximum > 120 && maximum - minOf(red, green, blue) > 30) colored++
                count++
            }
        }
        return colored.toDouble() / count
    }

    private fun centralBrightness(bitmap: Bitmap): Double {
        var sum = 0L
        var count = 0
        for (y in bitmap.height / 10 until bitmap.height * 8 / 10 step 4) {
            for (x in bitmap.width / 10 until bitmap.width * 9 / 10 step 4) {
                val pixel = bitmap.getPixel(x, y)
                sum += ((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255)
                count++
            }
        }
        return sum.toDouble() / (count * 3.0 * 255.0)
    }

    private fun assertNoStartTilesInAppsGutter(bitmap: Bitmap, elapsed: Int) {
        // This fixture's wallpaper and app icons are purple. Cyan, green,
        // orange or pink in the empty left gutter can only come from Start.
        // The central black-frame assertion missed these narrow tile strips.
        var leaked = 0
        var count = 0
        for (y in bitmap.height / 10 until bitmap.height * 8 / 10 step 4) {
            for (x in 0 until bitmap.width * 4 / 100 step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                if (green - red > 30 || red - blue > 30) leaked++
                count++
            }
        }
        assertTrue("Start tiles leaked into All Apps at ${elapsed}ms ($leaked/$count pixels)",
            leaked.toDouble() / count < .001)
    }

    private fun purpleIconWidthAt(bitmap: Bitmap, y: Int): Int {
        var left = bitmap.width
        var right = -1
        for (x in 0 until bitmap.width / 2) {
            val pixel = bitmap.getPixel(x, y.coerceIn(0, bitmap.height - 1))
            val red = (pixel shr 16) and 255
            val green = (pixel shr 8) and 255
            val blue = pixel and 255
            if (blue - red > 20 && red - green > 10) {
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        return (right - left + 1).coerceAtLeast(0)
    }

    private fun brightPixelsInAppLabelRegion(
        bitmap: Bitmap,
        minimumChannelValue: Int = 220,
    ): Int {
        // DiscoLauncher places app rows at x=81px; their 52px icon and 12px
        // title gap put the label around x=145px on a 360px CSS viewport.
        val left = (bitmap.width * .37f).toInt()
        val right = (bitmap.width * .95f).toInt()
        val top = (bitmap.height * .14f).toInt()
        val bottom = (bitmap.height * .40f).toInt()
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (((pixel shr 16) and 255) > minimumChannelValue &&
                    ((pixel shr 8) and 255) > minimumChannelValue &&
                    (pixel and 255) > minimumChannelValue
                ) count++
            }
        }
        return count
    }

    private fun brightPixelsInMobileAppLabelRegion(bitmap: Bitmap): Int {
        // W10M list rows start near x=78px on a 360px CSS viewport, unlike
        // WP8.1's narrower 62px icon and x=145px label column.
        val left = (bitmap.width * .18f).toInt()
        val right = (bitmap.width * .90f).toInt()
        val top = (bitmap.height * .20f).toInt()
        val bottom = (bitmap.height * .80f).toInt()
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (((pixel shr 16) and 255) > 220 &&
                    ((pixel shr 8) and 255) > 220 &&
                    (pixel and 255) > 220
                ) count++
            }
        }
        return count
    }

    private fun brightPixelsInAppsSearchRegion(bitmap: Bitmap): Int {
        val left = (bitmap.width * .55f).toInt()
        val right = (bitmap.width * .99f).toInt()
        val top = (bitmap.height * .08f).toInt()
        val bottom = (bitmap.height * .18f).toInt()
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 255
                val green = (pixel shr 8) and 255
                val blue = pixel and 255
                if (minOf(red, green, blue) > 60 &&
                    maxOf(red, green, blue) - minOf(red, green, blue) < 50
                ) count++
            }
        }
        return count
    }

    private fun difference(first: Bitmap, second: Bitmap): Double {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var sum = 0L
        var count = 0
        for (y in 0 until first.height step 4) {
            for (x in 0 until first.width step 4) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                for (shift in listOf(0, 8, 16)) sum += abs(((a shr shift) and 255) - ((b shr shift) and 255))
                count++
            }
        }
        return sum.toDouble() / (count * 3.0 * 255.0)
    }
}
