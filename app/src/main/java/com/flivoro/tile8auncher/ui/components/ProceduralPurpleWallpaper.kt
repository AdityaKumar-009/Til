package com.flivoro.tile8auncher.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * One shared raster of the original procedural purple wallpaper for picture-filled phone tiles.
 * Its shapes, shader colors, and gradients reproduce WindowsWallpaper's non-parallax style 0.
 * The raster is shared across moving tile faces rather than replaying vectors for every tile.
 */
internal object ProceduralPurpleWallpaper {
    private var dimensions: Pair<Int, Int>? = null
    private var cached: Bitmap? = null

    @Synchronized
    fun bitmap(gridWidthPx: Float, gridHeightPx: Float): Bitmap {
        // PhoneTilePicture center-crops its source using a uniform scale. Reduce *both*
        // dimensions by the same factor or the taller generated grid would crop off ribbons.
        // Keep a bounded texture even for very long vertically scrolling Start lists.
        val logicalWidth = gridWidthPx.coerceAtLeast(1f)
        val logicalHeight = gridHeightPx.coerceAtLeast(1f)
        val reduction = minOf(1f, 960f / logicalWidth, 1920f / logicalHeight)
        val w = (logicalWidth * reduction).roundToInt().coerceIn(1, 960)
        val h = (logicalHeight * reduction).roundToInt().coerceIn(1, 1920)
        val key = w to h
        cached?.takeIf { dimensions == key && !it.isRecycled }?.let { return it }
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val width = w.toFloat()
        val height = h.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        paint.shader = LinearGradient(0f, 0f, width, height,
            intArrayOf(0xFF23053D.toInt(), 0xFF140224.toInt(), 0xFF0C0117.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)

        // The original three-viewport vector layer is positioned one viewport left.
        val origins = floatArrayOf(-2f * width, -width, 0f, width)
        fun x(origin: Float, fraction: Float, mirrored: Boolean): Float =
            origin + (if (mirrored) 1f - fraction else fraction) * width
        fun alpha(rgb: Int, opacity: Float): Int =
            (rgb and 0x00FFFFFF) or ((opacity * 255).roundToInt().coerceIn(0, 255) shl 24)

        for (i in origins.indices) {
            val origin = origins[i]
            val mirrored = i % 2 == 1
            val centerX = x(origin, .15f, mirrored)
            paint.shader = RadialGradient(centerX, height * .20f, width * .60f,
                intArrayOf(alpha(0xFF4A0E72.toInt(), .35f), android.graphics.Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(centerX, height * .20f, width * .60f, paint)
        }
        for (i in origins.indices) {
            val origin = origins[i]
            val mirrored = i % 2 == 1
            val path = Path().apply {
                moveTo(x(origin, .35f, mirrored), 0f)
                cubicTo(x(origin, .45f, mirrored), height * .30f,
                    x(origin, .75f, mirrored), height * .50f,
                    x(origin, .60f, mirrored), height)
                lineTo(x(origin, .90f, mirrored), height)
                cubicTo(x(origin, .95f, mirrored), height * .45f,
                    x(origin, .65f, mirrored), height * .20f,
                    x(origin, .55f, mirrored), 0f)
                close()
            }
            paint.shader = LinearGradient(
                origin + width * (if (mirrored) .65f else .35f), 0f,
                origin + width * (if (mirrored) .25f else .75f), height,
                intArrayOf(alpha(0xFF5A148C.toInt(), .45f),
                    alpha(0xFF8824B8.toInt(), .35f),
                    alpha(0xFF32094D.toInt(), .20f)),
                null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        for (i in origins.indices) {
            val origin = origins[i]
            val mirrored = i % 2 == 1
            val path = Path().apply {
                moveTo(x(origin, .48f, mirrored), 0f)
                cubicTo(x(origin, .58f, mirrored), height * .25f,
                    x(origin, .82f, mirrored), height * .45f,
                    x(origin, .72f, mirrored), height)
                lineTo(x(origin, 1f, mirrored), height)
                lineTo(x(origin, 1f, mirrored), 0f)
                close()
            }
            paint.shader = LinearGradient(
                origin + width * .50f, 0f,
                origin + width * (if (mirrored) 0f else 1f), height,
                intArrayOf(alpha(0xFF8824B8.toInt(), .25f),
                    alpha(0xFF5A148C.toInt(), .15f),
                    android.graphics.Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        paint.shader = null
        dimensions = key
        cached = image
        return image
    }
}
