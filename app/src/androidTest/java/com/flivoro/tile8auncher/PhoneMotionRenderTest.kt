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

    @Test fun mobileExitHoldsItsFinalFrameAndReturnRestoresTheSurface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.MOBILE_10, 6)
        LauncherFeatureStore.setPhoneTileOpacity(context, 100)
        StartPersonalization.ensureLoaded(context)
        StartPersonalization.setCustomWallpaperUri(context, null)
        val colors = listOf(0xFF00A4EF, 0xFFE3008C, 0xFF008A00, 0xFFF09609, 0xFF603CBA)
        val tiles = List(15) { index -> TileModel(
            id = "motion_$index", title = "Tile ${index + 1}",
            iconGlyph = ('A' + index).toString(), colorValue = colors[index % colors.size],
        ) }
        val launching = mutableStateOf(false)
        val selected = mutableStateOf<String?>(null)
        val entrance = mutableIntStateOf(0)
        var completions = 0
        val repository = AppsRepository(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PhoneLauncherSurface(
                    mode = LauncherUiMode.MOBILE_10, tiles = tiles, sections = emptyList(),
                    appsRepository = repository, homeRequest = 0,
                    entranceRequest = entrance.intValue, launchingTileId = selected.value,
                    isLaunching = launching.value, interactionEnabled = !launching.value,
                    wallpaperStyle = 0,
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
        repeat(12) { frame ->
            compose.mainClock.advanceTimeBy(32)
            capture("exit-${((frame + 1) * 32).toString().padStart(3, '0')}")
        }
        compose.runOnIdle { assertEquals("App opened before wallpaper had faded", 0, completions) }
        compose.mainClock.advanceTimeBy(224)
        val finalExit = capture("exit-608-handoff")
        compose.runOnIdle { assertEquals("Expected one completed handoff", 1, completions) }
        assertTrue("Start content must be black at handoff", centralBrightness(finalExit) < .01)
        compose.runOnIdle { selected.value = null }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("Outgoing tiles reappeared during Android handoff",
            difference(finalExit, capture("exit-held")) < .001)
        compose.runOnIdle { assertEquals(1, completions) }

        compose.runOnIdle { launching.value = false; entrance.intValue++ }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        capture("entry-000")
        repeat(20) { frame ->
            compose.mainClock.advanceTimeBy(32)
            capture("entry-${((frame + 1) * 32).toString().padStart(3, '0')}")
        }
        assertTrue("Return failed to restore the original tile geometry",
            difference(resting, capture("02-return-settled")) < .01)
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
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
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
            File(directory, "$name.png").outputStream().use {
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
