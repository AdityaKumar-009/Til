package com.flivoro.tile8auncher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * The WP8.1 tile renderer uses an artwork-based rather than bitmap-based
 * visual size. Android adaptive icons commonly place small glyphs inside
 * 108dp canvases, while legacy PNGs are tightly cropped and circular.
 * Both should occupy the same visible fraction of a Start tile.
 */
internal object Wp81IconArtwork {
    fun normalize(bitmap: Bitmap, outputSize: Int = 144): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (Color.alpha(pixels[y * w + x]) >= 45) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        if (maxX < minX || maxY < minY) return bitmap
        val side = outputSize.coerceIn(64, 256)
        val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val sourceW = (maxX - minX + 1).toFloat()
        val sourceH = (maxY - minY + 1).toFloat()
        // An 84% canvas target gives comparable apparent mass for tiny
        // pin glyphs, colored app marks, and fully painted round icons.
        val target = side * .84f
        val ratio = target / max(sourceW, sourceH)
        val drawW = sourceW * ratio
        val drawH = sourceH * ratio
        val dest = RectF(
            (side - drawW) / 2f, (side - drawH) / 2f,
            (side + drawW) / 2f, (side + drawH) / 2f,
        )
        Canvas(result).drawBitmap(bitmap,
            Rect(minX, minY, maxX + 1, maxY + 1), dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return result
    }

    /** Distinguish a meaningful brand gradient from a flat adaptive plate.
     * Require both spatial color variation and nontrivial visible chroma.
     */
    fun isRichBackground(bitmap: Bitmap): Boolean {
        if (bitmap.width < 8 || bitmap.height < 8) return false
        var minR = 255; var minG = 255; var minB = 255
        var maxR = 0; var maxG = 0; var maxB = 0
        var chromatic = 0
        var valid = 0
        val hsv = FloatArray(3)
        for (gy in 0 until 10) for (gx in 0 until 10) {
            val x = (gx * (bitmap.width - 1) / 9).coerceIn(0, bitmap.width - 1)
            val y = (gy * (bitmap.height - 1) / 9).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) < 150) continue
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            minR = min(minR, r); minG = min(minG, g); minB = min(minB, b)
            maxR = max(maxR, r); maxG = max(maxG, g); maxB = max(maxB, b)
            Color.colorToHSV(pixel, hsv)
            if (hsv[1] > .18f) chromatic++
            valid++
        }
        if (valid < 65) return false
        val spread = maxOf(maxR - minR, maxG - minG, maxB - minB)
        return spread >= 36 && chromatic >= valid / 4
    }
}
