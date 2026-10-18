package com.flivoro.tile8auncher

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.LocalPhoneMotionSampleMillis
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises native cross-mode choices on real Compose TILE AND NEIGHBOR layers.
 * Saves device-rendered evidence. A single selected-tile overlay with static
 * neighbors fails this test even if its shape/curve is otherwise correct.
 */
@RunWith(AndroidJUnit4::class)
class CrossModeLaunchRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wp81LayoutUsesFullW10MStartScene() =
        captureScene(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10, false)

    @Test fun w10mLayoutUsesFullWP81StartScene() =
        captureScene(LauncherUiMode.MOBILE_10, LauncherUiMode.PHONE_8, false)

    @Test fun wp81LayoutUsesFullW10MAppsScene() =
        captureScene(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10, true)

    @Test fun w10mLayoutUsesFullWP81AppsScene() =
        captureScene(LauncherUiMode.MOBILE_10, LauncherUiMode.PHONE_8, true)

    private fun captureScene(
        layoutMode: LauncherUiMode,
        animationMode: LauncherUiMode,
        allApps: Boolean,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AppsRepository(context)
        val shades = listOf(0xFF00A4EF, 0xFFE3008C, 0xFF008A00, 0xFFF09609, 0xFF603CBA)
        val tiles = List(16) { n -> TileModel(
            id = "cross_$n", title = "Tile $n",
            iconGlyph = ('A' + n).toString(), colorValue = shades[n % shades.size],
            size = TileSize.MEDIUM,
        ) }
        val apps = listOf(
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
            )),
        )
        val launching = mutableStateOf(false)
        val selected = mutableStateOf<String?>(null)
        val sampleMillis = mutableIntStateOf(0)
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalPhoneMotionSampleMillis provides { sampleMillis.intValue },
            ) {
                MaterialTheme {
                    PhoneLauncherSurface(
                        mode = layoutMode,
                        launchMotionMode = animationMode,
                        tiles = tiles,
                        sections = apps,
                        appsRepository = repository,
                        homeRequest = 0,
                        entranceRequest = 0,
                        launchingTileId = selected.value,
                        isLaunching = launching.value,
                        interactionEnabled = !launching.value,
                        wallpaperStyle = 0,
                        onLaunch = { _, _, _ -> },
                        onExitFinished = { finished++ },
                        onOpenSettings = {},
                        onOpenAppInfo = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1600)
        if (allApps) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(420)
        }
        compose.runOnIdle {
            selected.value = if (allApps) "phone_app_com.example.alpha" else "cross_0"
            sampleMillis.intValue = 0
            launching.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        val first = save("${layoutMode.name}-${animationMode.name}-${if (allApps) "apps" else "start"}-000")
        compose.runOnIdle { sampleMillis.intValue = 240 }
        compose.mainClock.advanceTimeByFrame()
        val middle = save("${layoutMode.name}-${animationMode.name}-${if (allApps) "apps" else "start"}-240")
        compose.runOnIdle { sampleMillis.intValue = 440 }
        compose.mainClock.advanceTimeByFrame()
        val late = save("${layoutMode.name}-${animationMode.name}-${if (allApps) "apps" else "start"}-440")

        // The selected top tile and several OTHER visible tiles/letters must change.
        assertTrue("Selected physical tile did not animate in $layoutMode borrowing $animationMode",
            changed(first, late, .1f, .08f, .80f, .38f) > .003)
        assertTrue("Neighboring tiles or app rows stayed fixed in $layoutMode borrowing $animationMode",
            changed(first, middle, .1f, .39f, .88f, .88f) > .004 || 
                changed(first, late, .1f, .39f, .88f, .88f) > .004)
    }

    private fun save(name: String): Bitmap {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "cross-$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Til-motion-frames")
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val uri = checkNotNull(context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        return image
    }

    private fun changed(
        a: Bitmap, b: Bitmap,
        x0: Float, y0: Float, x1: Float, y1: Float,
    ): Double {
        var different = 0
        var count = 0
        val right = minOf(a.width, b.width)
        val bottom = minOf(a.height, b.height)
        for (y in (bottom * y0).toInt() until (bottom * y1).toInt() step 6) {
            for (x in (right * x0).toInt() until (right * x1).toInt() step 6) {
                val first = a.getPixel(x, y)
                val second = b.getPixel(x, y)
                val delta = kotlin.math.abs((first shr 16 and 255) - (second shr 16 and 255)) +
                    kotlin.math.abs((first shr 8 and 255) - (second shr 8 and 255)) +
                    kotlin.math.abs((first and 255) - (second and 255))
                if (delta > 24) different++
                count++
            }
        }
        return different.toDouble() / count.coerceAtLeast(1)
    }
}
