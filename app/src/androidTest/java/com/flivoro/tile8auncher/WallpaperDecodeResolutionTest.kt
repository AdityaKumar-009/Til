package com.flivoro.tile8auncher

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.ui.components.LauncherImageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A small accent-color read must not poison the full-resolution Start wallpaper. */
@RunWith(AndroidJUnit4::class)
class WallpaperDecodeResolutionTest {
    @Test fun fullWallpaperDecodeCannotReuseSmallerAccentBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("wallpaper-resolution-", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = Uri.fromFile(source).toString()
            val accent = LauncherImageCache.getOrDecode(context, uri, maxSide = 720)
            val full = LauncherImageCache.getOrDecode(context, uri, maxSide = 2560)
            assertNotNull(accent)
            assertNotNull(full)
            assertTrue(full!!.width > accent!!.width)
            assertEquals(1600, full.width)
            LauncherImageCache.forget(uri)
        } finally {
            image.recycle()
            source.delete()
        }
    }
}
