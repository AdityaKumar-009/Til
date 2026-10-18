package com.flivoro.tile8auncher

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.start.StartScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Desktop must animate its REAL visible neighbors, never a cloned source overlay. */
@RunWith(AndroidJUnit4::class)
class DesktopBorrowedSceneRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopStartBorrowsTheCompleteWP81TileScene() =
        checkStart(MasterLaunchAnimation.PHONE_8)

    @Test fun desktopStartBorrowsTheCompleteW10MTileScene() =
        checkStart(MasterLaunchAnimation.MOBILE_10)

    @Test fun desktopAllAppsBorrowsTheCompleteWP81Rows() =
        checkApps(MasterLaunchAnimation.PHONE_8)

    @Test fun desktopAllAppsBorrowsTheCompleteW10MRows() =
        checkApps(MasterLaunchAnimation.MOBILE_10)

    private fun checkStart(profile: MasterLaunchAnimation) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AppsRepository(context)
        val progress = mutableFloatStateOf(0f)
        val chosen = mutableStateOf<MasterLaunchAnimation?>(null)
        val colors = listOf(0xFF00A4EF, 0xFFE3008C, 0xFF008A00, 0xFFF09609)
        val tiles = List(16) { n -> TileModel(
            id = "desktop_$n", title = "Tile $n", iconGlyph = ('A' + n).toString(),
            size = TileSize.MEDIUM, colorValue = colors[n % colors.size],
        ) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                StartScreen(
                    tiles = tiles,
                    appsRepository = repository,
                    onTileClick = { _, _ -> },
                    onTileLongClick = {},
                    onPowerClick = {},
                    onSearchClick = {},
                    onAddAppsClick = {},
                    onNavigateToAllApps = {},
                    borrowedLaunchStyle = chosen.value,
                    borrowedLaunchSelectedId = tiles.first().id,
                    borrowedLaunchProgress = { progress.floatValue },
                )
            }
        }
        compose.mainClock.advanceTimeBy(1900)
        compose.runOnIdle { chosen.value = profile; progress.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        val before = image("cross-desktop-${profile.name}-start-000")
        compose.runOnIdle { progress.floatValue = .82f }
        compose.mainClock.advanceTimeByFrame()
        val after = image("cross-desktop-${profile.name}-start-082")
        assertTrue("$profile failed to animate the Desktop Start neighboring tiles",
            changed(before, after, .40f, .16f, .94f, .86f) > .002)
    }

    private fun checkApps(profile: MasterLaunchAnimation) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AppsRepository(context)
        val chosen = mutableStateOf<MasterLaunchAnimation?>(null)
        val progress = mutableFloatStateOf(0f)
        val sections = listOf(
            AppSection("A", listOf(
                AppInfo("Alpha", "com.example.alpha", "com.example.alpha.Main"),
                AppInfo("Alpine", "com.example.alpine", "com.example.alpine.Main"),
                AppInfo("Another", "com.example.another", "com.example.another.Main"),
            )),
            AppSection("B", listOf(
                AppInfo("Beta", "com.example.beta", "com.example.beta.Main"),
                AppInfo("Browser", "com.example.browser", "com.example.browser.Main"),
                AppInfo("Bing", "com.example.bing", "com.example.bing.Main"),
            )),
            AppSection("C", listOf(
                AppInfo("Camera", "com.example.camera", "com.example.camera.Main"),
                AppInfo("Calculator", "com.example.calc", "com.example.calc.Main"),
            )),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                AllAppsScreen(
                    sections = sections, appsRepository = repository,
                    onAppClick = { _, _ -> }, isAppPinned = { false },
                    onPinApp = {}, onUnpinApp = {}, onOpenAppInfo = {},
                    onUninstallApp = {}, onNavigateToStart = {},
                    borrowedLaunchStyle = chosen.value,
                    borrowedLaunchPackage = "com.example.alpha",
                    borrowedLaunchProgress = { progress.floatValue },
                )
            }
        }
        compose.mainClock.advanceTimeBy(1800)
        // All Apps first gathers Android metadata on Dispatchers.IO. Advancing
        // Compose's virtual clock does not await that independent coroutine:
        // until rows actually exist, animation comparisons only see wallpaper.
        compose.waitUntil(15_000) {
            // IO can finish after the initial virtual-time advance. Pump a
            // Compose frame so its published catalog can actually recompose.
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { chosen.value = profile; progress.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        val before = image("cross-desktop-${profile.name}-apps-000")
        compose.runOnIdle { progress.floatValue = .91f }
        compose.mainClock.advanceTimeByFrame()
        val after = image("cross-desktop-${profile.name}-apps-091")
        assertTrue("$profile failed to animate the Desktop All Apps neighboring rows",
            changed(before, after, .12f, .22f, .94f, .86f) > .002)
    }

    private fun image(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        }
        return bitmap
    }

    private fun changed(
        first: Bitmap, second: Bitmap,
        left: Float, top: Float, right: Float, bottom: Float,
    ): Double {
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        var changed = 0
        var count = 0
        for (y in (height * top).toInt() until (height * bottom).toInt() step 8) {
            for (x in (width * left).toInt() until (width * right).toInt() step 8) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                val delta = abs((a shr 16 and 255) - (b shr 16 and 255)) +
                    abs((a shr 8 and 255) - (b shr 8 and 255)) +
                    abs((a and 255) - (b and 255))
                if (delta > 24) changed++
                count++
            }
        }
        return changed.toDouble() / count.coerceAtLeast(1)
    }
}
