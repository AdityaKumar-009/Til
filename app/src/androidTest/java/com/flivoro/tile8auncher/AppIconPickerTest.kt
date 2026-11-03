package com.flivoro.tile8auncher

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.features.*
import com.flivoro.tile8auncher.ui.components.*
import com.flivoro.tile8auncher.ui.phone.PhoneAppContextMenu
import com.flivoro.tile8auncher.ui.phone.rememberWp81TileVisual
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppIconPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val target get() = context.packageName
    private val color = android.graphics.Color.rgb(18, 145, 73)
    private fun artwork() = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap()
    private fun refresh() = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        IconPackManager.clearCaches()
        LauncherFeatureRuntime.notifyIconsChanged()
        // Bitmap-only tests have no mounted composition to drain global snapshot changes.
        // Flush them during setup/cleanup instead of waiting for a nonexistent UI frame.
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    @Before fun prepare() {
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        refresh()
    }

    @After fun cleanup() {
        AppIconArtwork.apply(context, target, AppIconArtwork.DEFAULT, null)
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        refresh()
    }

    @Test fun savedIconSurvivesCacheLossAndWinsAcrossEveryGlobalStyle() = runBlocking {
        AppIconArtwork.apply(context, target, "original", artwork())
        refresh()
        val repo = AppsRepository(context)
        for (style in AppIconStyle.entries) {
            compose.runOnIdle { LauncherFeatureStore.setAppIconStyle(context, style) }
            val revision = LauncherFeatureRuntime.iconsRevision
            preloadStartIcons(context, repo, listOf(target), style, revision)
            preloadAllAppsIcons(context, repo, listOf(target), style, revision)
            assertEquals(color, cachedAppIconForCurrentStyle(context, repo, target)!!.asAndroidBitmap().getPixel(10, 10))
            assertEquals(color, cachedAllAppsIconForCurrentStyle(context, repo, target)!!.asAndroidBitmap().getPixel(10, 10))
        }
        IconPackManager.clearCaches()
        assertEquals(color, IconPackManager.loadCustom(context, target)!!.asAndroidBitmap().getPixel(10, 10))
        assertNull(IconPackManager.loadCustom(context, "$target.other"))
    }

    @Test fun phoneNormalizationDoesNotReplaceExplicitArtwork() {
        AppIconArtwork.apply(context, target, "glyph:mail", artwork())
        refresh()
        var actual: androidx.compose.ui.graphics.ImageBitmap? = null
        val repo = AppsRepository(context)
        compose.setContent { actual = rememberWp81TileVisual(repo, target, enabled = true).icon }
        compose.waitUntil(10_000) { actual != null }
        compose.runOnIdle { assertEquals(color, actual!!.asAndroidBitmap().getPixel(10, 10)) }
    }

    @Test fun replacingAndResettingDeletesOnlyManagedArtwork() {
        val source = File(context.cacheDir, "external-icon-test.png")
        source.outputStream().use { artwork().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        LauncherFeatureStore.setCustomIconUri(context, target, Uri.fromFile(source).toString())
        AppIconArtwork.apply(context, target, "original", artwork())
        assertTrue(source.exists())
        val first = File(Uri.parse(LauncherFeatureStore.customIconUri(context, target)).path!!)
        AppIconArtwork.apply(context, target, "mono", artwork())
        assertFalse(first.exists())
        val second = File(Uri.parse(LauncherFeatureStore.customIconUri(context, target)).path!!)
        AppIconArtwork.apply(context, target, AppIconArtwork.DEFAULT, null)
        assertFalse(second.exists())
        assertNull(LauncherFeatureStore.customIconUri(context, target))
        assertEquals(AppIconArtwork.DEFAULT, AppIconArtwork.selected(context, target))
        source.delete()
    }

    @Test fun invalidImageAndFailedApplyRetainExistingChoice() {
        AppIconArtwork.apply(context, target, "original", artwork())
        val before = LauncherFeatureStore.customIconUri(context, target)
        val invalid = File(context.cacheDir, "invalid-icon-test.txt").apply { writeText("not an image") }
        assertNull(IconPackManager.decodeUri(context, Uri.fromFile(invalid), 256))
        assertTrue(runCatching { AppIconArtwork.apply(context, target, "mono", null) }.isFailure)
        assertEquals(before, LauncherFeatureStore.customIconUri(context, target))
        invalid.delete()
    }

    @Test fun importedImageIsBoundedAndKeepsTransparency() {
        val source = File(context.cacheDir, "large-icon-test.png")
        val input = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        input.setPixel(0, 0, color)
        source.outputStream().use { input.compress(Bitmap.CompressFormat.PNG, 100, it) }
        input.recycle()
        val output = IconPackManager.decodeUri(context, Uri.fromFile(source), 256)!!
        assertEquals(256, output.width)
        assertEquals(128, output.height)
        assertEquals(0, output.asAndroidBitmap().getPixel(200, 100))
        source.delete()
    }

    @Test fun previewCancelDoesNotWriteButApplyPersistsSelection() {
        var closed = false
        compose.setContent { MaterialTheme { AppIconPickerSheet(target, "Mosaic", onClose = { closed = true }) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("app-icon-choice-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-choice-original").performClick()
        compose.captureFidelity("app-icon-picker", "app-icon-bottom-sheet")
        assertNull(LauncherFeatureStore.customIconUri(context, target))
        compose.onNodeWithTag("app-icon-apply").performClick()
        compose.waitUntil(10_000) { closed }
        assertEquals("original", AppIconArtwork.selected(context, target))
        assertNotNull(LauncherFeatureStore.customIconUri(context, target))
    }

    @Test fun metroSymbolsContainArtworkAndPersistAsVisibleIcons() {
        for (glyph in listOf("wp81_phone", "wp81_messages", "mail", "camera", "photos", "music",
            "video", "ie", "maps", "games", "folder", "settings")) {
            val image = metroIconBitmap(context, glyph)
            val bitmap = image.asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("$glyph must not be transparent", pixels.count { android.graphics.Color.alpha(it) > 128 } > 100)
            AppIconArtwork.apply(context, target, "glyph:$glyph", image)
            IconPackManager.clearCaches()
            val restored = IconPackManager.loadCustom(context, target)!!.asAndroidBitmap()
            val restoredPixels = IntArray(restored.width * restored.height)
            restored.getPixels(restoredPixels, 0, restored.width, 0, 0, restored.width, restored.height)
            assertTrue("$glyph must remain visible after saving", restoredPixels.count { android.graphics.Color.alpha(it) > 128 } > 100)
        }
    }

    @Test fun pickerActuallyDrawsVectorAndMetroSymbolPreviews() {
        compose.setContent { MaterialTheme { AppIconPickerSheet(target, "Mosaic", onClose = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("app-icon-choice-original").fetchSemanticsNodes().isNotEmpty() }
        for (glyph in listOf("wp81_phone", "camera")) {
            compose.onNodeWithTag("app-icon-choices").performScrollToNode(hasTestTag("app-icon-choice-glyph:$glyph"))
            val bitmap = compose.onNodeWithTag("app-icon-art-glyph:$glyph", useUnmergedTree = true)
                .captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("$glyph should be visible on screen", pixels.count {
                android.graphics.Color.red(it) > 220 && android.graphics.Color.green(it) > 220 &&
                    android.graphics.Color.blue(it) > 220
            } > 50)
        }
    }

    @Test fun cancelLeavesStoredIconUntouched() {
        AppIconArtwork.apply(context, target, "original", artwork())
        refresh()
        val before = LauncherFeatureStore.customIconUri(context, target)
        var closed = false
        compose.setContent { MaterialTheme { AppIconPickerSheet(target, "Mosaic", onClose = { closed = true }) } }
        compose.onNodeWithTag("app-icon-reset").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { closed }
        assertEquals(before, LauncherFeatureStore.customIconUri(context, target))
    }

    @Test fun bothPhoneMenusOfferChangeIconAndDismissBeforeOpening() {
        var mode by androidx.compose.runtime.mutableStateOf(LauncherUiMode.PHONE_8)
        var dismissed = false
        var picked = false
        compose.setContent { MaterialTheme { PhoneAppContextMenu(mode, target, false,
            xPx = 50, yPx = 50,
            rowBoundsInWindow = androidx.compose.ui.geometry.Rect(0f, 50f, 300f, 110f),
            viewportBoundsInWindow = androidx.compose.ui.geometry.Rect(0f, 0f, 400f, 1200f),
            onPinChanged = {}, onAddWidgets = {}, onChangeIcon = { assertTrue(dismissed); picked = true },
            onAppInfo = {}, onDismiss = { dismissed = true }) } }
        for (next in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            compose.runOnIdle { mode = next; dismissed = false; picked = false }
            compose.onNodeWithTag("phone-app-command-change-icon").performClick()
            compose.runOnIdle { assertTrue(picked) }
        }
    }
}
