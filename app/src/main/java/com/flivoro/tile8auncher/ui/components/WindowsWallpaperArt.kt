package com.flivoro.tile8auncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import com.flivoro.tile8auncher.R
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/** Default Start colors recovered from the supplied Windows artwork itself. */
internal data class WallpaperDefaultColors(
    val backgroundArgb: Long,
    val accentArgb: Long,
)

/**
 * Three compact alpha-only textures preserving the exact pixels of the supplied Windows artwork.
 *
 * At runtime these masks are tinted with the selected Accent palette. The original JPEG's flat
 * background never reaches the renderer, so Background color remains a genuinely independent
 * solid plane. ALPHA_8 keeps each cached texture to one byte per pixel.
 */
internal data class WallpaperArtMasks(
    val shadowMask: Bitmap,
    val primaryMask: Bitmap,
    val highlightMask: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val defaults: WallpaperDefaultColors,
) {
    val allocationBytes: Int
        get() = shadowMask.allocationByteCount + primaryMask.allocationByteCount +
            highlightMask.allocationByteCount
}

/** Pure mask classification helpers kept separate for deterministic regression tests. */
internal object WallpaperMaskMath {
    const val JPEG_NOISE_FLOOR = 7

    fun weightedDistance(dr: Int, dg: Int, db: Int): Int =
        (abs(dr) * 77 + abs(dg) * 150 + abs(db) * 29) shr 8

    fun signedLuma(dr: Int, dg: Int, db: Int): Int =
        (dr * 54 + dg * 183 + db * 19) shr 8

    fun alphaForDistance(distance: Int): Int =
        ((distance - JPEG_NOISE_FLOOR) * 10).coerceIn(0, 255)

    /** 0 = shadow, 1 = primary/chroma, 2 = highlight. */
    fun toneBucket(dr: Int, dg: Int, db: Int): Int {
        val luma = signedLuma(dr, dg, db)
        return when {
            luma < -10 -> 0
            luma > 10 -> 2
            else -> 1
        }
    }
}

/**
 * Lazy artwork cache. No bitmap decoding or per-pixel work happens while the user is scrolling.
 * Only the currently selected full-size theme plus tiny Personalize previews are retained.
 */
internal object WindowsWallpaperArtCache {
    private const val FULL_CACHE_KB = 14 * 1024
    private const val PREVIEW_SAMPLE = 4
    private val defaultsCache = ConcurrentHashMap<Int, WallpaperDefaultColors>()

    private val masks = object : LruCache<Int, WallpaperArtMasks>(FULL_CACHE_KB) {
        override fun sizeOf(key: Int, value: WallpaperArtMasks): Int =
            max(1, value.allocationBytes / 1024)

        override fun entryRemoved(
            evicted: Boolean,
            key: Int,
            oldValue: WallpaperArtMasks,
            newValue: WallpaperArtMasks?,
        ) {
            if (newValue === oldValue) return
            oldValue.shadowMask.recycle()
            oldValue.primaryMask.recycle()
            oldValue.highlightMask.recycle()
        }
    }

    fun peek(style: Int, preview: Boolean, lowRam: Boolean): WallpaperArtMasks? =
        masks.get(cacheKey(style, preview, lowRam))

    fun load(
        context: Context,
        style: Int,
        preview: Boolean,
        lowRam: Boolean,
    ): WallpaperArtMasks? {
        if (style !in 1..9) return null
        val key = cacheKey(style, preview, lowRam)
        masks.get(key)?.let { return it }
        synchronized(this) {
            masks.get(key)?.let { return it }
            val sample = when {
                preview -> PREVIEW_SAMPLE
                lowRam -> 2
                else -> 1
            }
            val source = decode(context, style, sample) ?: return null
            return try {
                buildMasks(source, style).also { masks.put(key, it) }
            } finally {
                source.recycle()
            }
        }
    }

    /**
     * Lightweight color analysis used when the user taps a stock wallpaper in Personalize.
     * Selecting the stock art therefore restores that art's own captured Windows color pair.
     */
    fun defaultColors(context: Context, style: Int): WallpaperDefaultColors {
        if (style == 0) {
            return WallpaperDefaultColors(
                StartPersonalization.DEFAULT_BACKGROUND_ARGB,
                StartPersonalization.DEFAULT_ACCENT_ARGB,
            )
        }
        defaultsCache[style]?.let { return it }
        synchronized(this) {
            defaultsCache[style]?.let { return it }
            val bitmap = decode(context, style, 8)
                ?: return WallpaperDefaultColors(
                    StartPersonalization.DEFAULT_BACKGROUND_ARGB,
                    StartPersonalization.DEFAULT_ACCENT_ARGB,
                )
            return try {
                analyzeDefaults(bitmap).also { defaultsCache[style] = it }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @DrawableRes
    private fun resourceForStyle(style: Int): Int = when (style) {
        1 -> R.drawable.start_wallpaper_1
        2 -> R.drawable.start_wallpaper_2
        3 -> R.drawable.start_wallpaper_3
        4 -> R.drawable.start_wallpaper_4
        5 -> R.drawable.start_wallpaper_5
        6 -> R.drawable.start_wallpaper_6
        7 -> R.drawable.start_wallpaper_7
        8 -> R.drawable.start_wallpaper_8
        9 -> R.drawable.start_wallpaper_9
        else -> 0
    }

    private fun decode(context: Context, style: Int, sample: Int): Bitmap? {
        val resource = resourceForStyle(style)
        if (resource == 0) return null
        return BitmapFactory.decodeResource(
            context.resources,
            resource,
            BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun cacheKey(style: Int, preview: Boolean, lowRam: Boolean): Int =
        style * 10 + when {
            preview -> 1
            lowRam -> 2
            else -> 0
        }

    private fun buildMasks(source: Bitmap, style: Int): WallpaperArtMasks {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val defaults = analyzeDefaults(pixels, width, height)
        defaultsCache[style] = defaults

        val background = defaults.backgroundArgb
        val baseR = ((background shr 16) and 0xFF).toInt()
        val baseG = ((background shr 8) and 0xFF).toInt()
        val baseB = (background and 0xFF).toInt()

        val shadow = ByteArray(pixels.size)
        val primary = ByteArray(pixels.size)
        val highlight = ByteArray(pixels.size)

        pixels.forEachIndexed { index, pixel ->
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            val dr = r - baseR
            val dg = g - baseG
            val db = b - baseB
            val distance = WallpaperMaskMath.weightedDistance(dr, dg, db)
            val alpha = WallpaperMaskMath.alphaForDistance(distance)
            if (alpha == 0) return@forEachIndexed
            val value = alpha.toByte()
            when (WallpaperMaskMath.toneBucket(dr, dg, db)) {
                0 -> shadow[index] = value
                2 -> highlight[index] = value
                else -> primary[index] = value
            }
        }

        return WallpaperArtMasks(
            shadowMask = alphaBitmap(width, height, shadow),
            primaryMask = alphaBitmap(width, height, primary),
            highlightMask = alphaBitmap(width, height, highlight),
            sourceWidth = width,
            sourceHeight = height,
            defaults = defaults,
        )
    }

    private fun alphaBitmap(width: Int, height: Int, alpha: ByteArray): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val rowBytes = bitmap.rowBytes
        val buffer = ByteBuffer.allocateDirect(rowBytes * height)
        var sourceOffset = 0
        repeat(height) {
            buffer.put(alpha, sourceOffset, width)
            repeat(rowBytes - width) { buffer.put(0) }
            sourceOffset += width
        }
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun analyzeDefaults(bitmap: Bitmap): WallpaperDefaultColors {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return analyzeDefaults(pixels, bitmap.width, bitmap.height)
    }

    private fun analyzeDefaults(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): WallpaperDefaultColors {
        val backgroundHistogram = IntArray(32 * 32 * 32)
        val step = max(1, minOf(width, height) / 192)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = pixels[y * width + x]
                val bin = quantizedBin(pixel)
                val border = x < width / 10 || x >= width * 9 / 10 ||
                    y < height / 10 || y >= height * 9 / 10
                backgroundHistogram[bin] += if (border) 4 else 1
                x += step
            }
            y += step
        }
        val backgroundBin = maxIndex(backgroundHistogram)
        val backgroundRgb = averageBin(pixels, backgroundBin) ?: binCenter(backgroundBin)
        val baseR = backgroundRgb.first
        val baseG = backgroundRgb.second
        val baseB = backgroundRgb.third

        val accentHistogram = IntArray(32 * 32 * 32)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            val distance = WallpaperMaskMath.weightedDistance(r - baseR, g - baseG, b - baseB)
            if (distance > 16) {
                accentHistogram[quantizedBin(pixel)] += 1 + (distance / 28).coerceAtMost(6)
            }
            index += max(1, step)
        }
        val accentBin = maxIndex(accentHistogram)
        val accentRgb = if (accentHistogram[accentBin] > 0) {
            averageBin(pixels, accentBin) ?: binCenter(accentBin)
        } else {
            Triple(
                (baseR + (255 - baseR) * 3 / 10).coerceIn(0, 255),
                (baseG + (255 - baseG) * 3 / 10).coerceIn(0, 255),
                (baseB + (255 - baseB) * 3 / 10).coerceIn(0, 255),
            )
        }

        return WallpaperDefaultColors(
            backgroundArgb = argbLong(baseR, baseG, baseB),
            accentArgb = argbLong(accentRgb.first, accentRgb.second, accentRgb.third),
        )
    }

    private fun quantizedBin(pixel: Int): Int {
        val r = (pixel shr 16 and 0xFF) shr 3
        val g = (pixel shr 8 and 0xFF) shr 3
        val b = (pixel and 0xFF) shr 3
        return (r shl 10) or (g shl 5) or b
    }

    private fun binCenter(bin: Int): Triple<Int, Int, Int> {
        val r = ((bin shr 10) and 31) * 8 + 4
        val g = ((bin shr 5) and 31) * 8 + 4
        val b = (bin and 31) * 8 + 4
        return Triple(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
    }

    private fun averageBin(pixels: IntArray, wantedBin: Int): Triple<Int, Int, Int>? {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val stride = max(1, pixels.size / 250_000)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            if (quantizedBin(pixel) == wantedBin) {
                red += pixel shr 16 and 0xFF
                green += pixel shr 8 and 0xFF
                blue += pixel and 0xFF
                count++
            }
            index += stride
        }
        if (count == 0L) return null
        return Triple((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun maxIndex(values: IntArray): Int {
        var winner = 0
        var winnerValue = Int.MIN_VALUE
        values.forEachIndexed { index, value ->
            if (value > winnerValue) {
                winner = index
                winnerValue = value
            }
        }
        return winner
    }

    private fun argbLong(r: Int, g: Int, b: Int): Long =
        0xFF000000L or ((r and 0xFF).toLong() shl 16) or
            ((g and 0xFF).toLong() shl 8) or (b and 0xFF).toLong()
}
