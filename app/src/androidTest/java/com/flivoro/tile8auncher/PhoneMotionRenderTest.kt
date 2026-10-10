package com.flivoro.tile8auncher

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
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
        captureMode(LauncherUiMode.MOBILE_10, "", 32, 608, 640, 384)
    }

    @Test fun classicDiscoLauncherExitAndReturnAreCapturedAtEveryFrame() {
        captureMode(LauncherUiMode.PHONE_8, "wp81-", 16, 800, 800, 560)
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
        LauncherFeatureStore.setPhoneSmallColumns(context, mode, 6)
        LauncherFeatureStore.setPhoneTileOpacity(context, 100)
        StartPersonalization.ensureLoaded(context)
        StartPersonalization.setCustomWallpaperUri(context, null)
        val colors = listOf(0xFF00A4EF, 0xFFE3008C, 0xFF008A00, 0xFFF09609, 0xFF603CBA)
        val tiles = List(15) { index -> TileModel(
            id = "motion_$index", title = "Tile ${index + 1}",
            iconGlyph = ('A' + index).toString(), colorValue = colors[index % colors.size],
        ) }
        val sections = listOf(
            AppSection("A", listOf(
                AppInfo("Alpha", "com.example.alpha", "com.example.alpha.Main"),
                AppInfo("Alpine", "com.example.alpine", "com.example.alpine.Main"),
            )),
            AppSection("B", listOf(
                AppInfo("Beta", "com.example.beta", "com.example.beta.Main"),
            )),
        )
        val launching = mutableStateOf(false)
        val selected = mutableStateOf<String?>(null)
        val home = mutableIntStateOf(0)
        val entrance = mutableIntStateOf(0)
        val resumeUsesBackMotion = mutableStateOf(false)
        var completions = 0
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(
                    mode = mode, tiles = tiles, sections = sections,
                    appsRepository = repository, homeRequest = home.intValue,
                    entranceRequest = entrance.intValue, launchingTileId = selected.value,
                    isLaunching = launching.value, interactionEnabled = !launching.value,
                    wallpaperStyle = 0, resumeUsesBackMotion = resumeUsesBackMotion.value,
                    onLaunch = { tile, _, _ -> selected.value = tile.id; launching.value = true },
                    onExitFinished = { completions++ }, onOpenSettings = {}, onOpenAppInfo = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        val resting = capture("00-rest")
        assertTrue("Fixture must draw actual tiles", coloredFraction(resting) > .35)

        // A panorama round trip must not start a second Start-entry animation.
        compose.onNodeWithText("⌕").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("‹").performClick()
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
            resumeUsesBackMotion.value = mode == LauncherUiMode.PHONE_8
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

        if (mode == LauncherUiMode.PHONE_8) {
            // A system Home return from All Apps must discard that pane immediately;
            // only the WP8.1 Start tiles run their 3D return choreography.
            compose.onNodeWithText("⌕").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("Alpha").assertExists()
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
            assertTrue("All Apps must be invisible in the first Home-return frame",
                brightPixelsInAppLabelRegion(homeReturnStart) < 5)
            compose.mainClock.advanceTimeBy(16)
            val homeReturnMoving = capture("home-return-016")
            compose.onNodeWithText("Alpha").assertDoesNotExist()
            assertTrue("WP8.1 Start tiles must keep their own return animation",
                difference(homeReturnStart, homeReturnMoving) > .001)
            for (elapsed in (frameStep * 2)..entryEnd step frameStep) {
                compose.mainClock.advanceTimeBy(frameStep.toLong())
                capture("home-return-${elapsed.toString().padStart(3, '0')}")
            }
            assertTrue("Home return must settle on the original Start layout",
                difference(resting, capture("home-return-settled")) < .01)

            // Exercise the separate app-list row/letter turn on the real Compose layers.
            compose.onNodeWithText("⌕").performClick()
            compose.mainClock.advanceTimeBy(400)
            capture("apps-rest")
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

    private fun brightPixelsInAppLabelRegion(bitmap: Bitmap): Int {
        val left = (bitmap.width * .16f).toInt()
        val right = (bitmap.width * .41f).toInt()
        val top = (bitmap.height * .205f).toInt()
        val bottom = (bitmap.height * .30f).toInt()
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (((pixel shr 16) and 255) > 220 &&
                    ((pixel shr 8) and 255) > 220 && (pixel and 255) > 220
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
