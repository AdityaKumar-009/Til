package com.flivoro.tile8auncher

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry

/** Compact, persistent render evidence; exported even when a later assertion fails. */
internal fun ComposeContentTestRule.captureFidelity(name: String, tag: String? = null) {
    val bitmap = (if (tag == null) onRoot() else onNodeWithTag(tag, useUnmergedTree = true))
        .captureToImage().asAndroidBitmap()
    val scaled = Bitmap.createScaledBitmap(bitmap, 360,
        (bitmap.height * 360f / bitmap.width).toInt(), true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "cross-fidelity-$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Til-motion-frames")
    }
    val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    checkNotNull(context.contentResolver.openOutputStream(uri)).use {
        scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    if (scaled !== bitmap) scaled.recycle()
}
