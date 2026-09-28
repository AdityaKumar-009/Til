package com.flivoro.tile8auncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.flivoro.tile8auncher.R
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import kotlin.math.max
import kotlin.math.min

/** Persistent accent color used by the All Apps icon containers. */
@Stable
internal object StartPersonalization {
    const val DEFAULT_ACCENT_ARGB: Long = 0xFF603CBA

    private const val PREFS_NAME = "tile8_start_personalization"
    private const val KEY_ACCENT = "accent_argb"
    private const val KEY_WALLPAPER_ACCENT = "wallpaper_accent_argb"
    private const val KEY_CUSTOM_WALLPAPER_URI = "custom_wallpaper_uri"
    private const val KEY_CUSTOM_WALLPAPER_OVERLAY = "custom_wallpaper_overlay"

    private var initialized = false
    private val accentState = mutableStateOf(Color(DEFAULT_ACCENT_ARGB))
    private val wallpaperAccentState = mutableStateOf(Color(DEFAULT_ACCENT_ARGB))
    private val customWallpaperUriState = mutableStateOf<String?>(null)
    private val customWallpaperOverlayState = mutableStateOf(0.24f)

    val accentColor: Color get() = accentState.value
    val accentArgb: Long get() = accentState.value.toArgbLong()
    val wallpaperAccentColor: Color get() = wallpaperAccentState.value
    val wallpaperAccentArgb: Long get() = wallpaperAccentState.value.toArgbLong()
    val customWallpaperUri: String? get() = customWallpaperUriState.value
    val customWallpaperOverlay: Float get() = customWallpaperOverlayState.value

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val accent = prefs.getLong(KEY_ACCENT, DEFAULT_ACCENT_ARGB) and 0xFFFFFFFFL
            accentState.value = Color(accent)
            val wallpaperAccent =
                prefs.getLong(KEY_WALLPAPER_ACCENT, accent) and 0xFFFFFFFFL
            wallpaperAccentState.value = Color(wallpaperAccent)
            customWallpaperUriState.value = prefs.getString(KEY_CUSTOM_WALLPAPER_URI, null)
                ?.takeIf(String::isNotBlank)
            LauncherImageCache.preload(context, customWallpaperUriState.value)
            customWallpaperOverlayState.value =
                prefs.getFloat(KEY_CUSTOM_WALLPAPER_OVERLAY, 0.24f).coerceIn(0f, 0.72f)
            WindowsColors.Purple = accent
            initialized = true
        }
    }

    fun setAccentColor(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        accentState.value = Color(argb)
        WindowsColors.Purple = argb
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACCENT, argb)
            .apply()
    }


    fun setCustomWallpaperUri(context: Context, uri: String?) {
        ensureLoaded(context)
        val normalized = uri?.takeIf(String::isNotBlank)
        val previous = customWallpaperUriState.value
        customWallpaperUriState.value = normalized
        if (normalized != null) LauncherImageCache.preload(context, normalized)
        if (previous != null && previous != normalized) LauncherImageCache.forget(previous)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (normalized == null) remove(KEY_CUSTOM_WALLPAPER_URI)
                else putString(KEY_CUSTOM_WALLPAPER_URI, normalized)
            }
            .apply()
    }

    fun setWallpaperDerivedAccent(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        if (wallpaperAccentState.value.toArgbLong() == argb) return
        wallpaperAccentState.value = Color(argb)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_WALLPAPER_ACCENT, argb)
            .apply()
    }

    /**
     * Calculates a readable Metro accent from the wallpaper that is actually being shown.
     * Generated style 0 follows the user's manual accent; bitmap/custom wallpapers are sampled
     * for their strongest saturated hue so All Apps visually belongs to that wallpaper.
     * Call this from a background dispatcher.
     */
    fun calculateWallpaperAccent(
        context: Context,
        wallpaperStyle: Int,
        customUri: String?,
        fallbackAccent: Color,
    ): Color {
        val bitmap = when {
            !customUri.isNullOrBlank() ->
                LauncherImageCache.getOrDecode(context.applicationContext, customUri, maxSide = 720)
            wallpaperStyle <= 0 -> null
            else -> BitmapFactory.decodeResource(
                context.resources,
                wallpaperResourceId(wallpaperStyle),
                BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                },
            )
        }
        return if (bitmap == null) fallbackAccent else Color(extractWallpaperAccent(bitmap))
    }

    fun setCustomWallpaperOverlay(context: Context, alpha: Float) {
        ensureLoaded(context)
        val safe = alpha.coerceIn(0f, 0.72f)
        customWallpaperOverlayState.value = safe
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CUSTOM_WALLPAPER_OVERLAY, safe)
            .apply()
    }

    private fun wallpaperResourceId(style: Int): Int = when (style.coerceIn(1, 9)) {
        1 -> R.drawable.start_wallpaper_1
        2 -> R.drawable.start_wallpaper_2
        3 -> R.drawable.start_wallpaper_3
        4 -> R.drawable.start_wallpaper_4
        5 -> R.drawable.start_wallpaper_5
        6 -> R.drawable.start_wallpaper_6
        7 -> R.drawable.start_wallpaper_7
        8 -> R.drawable.start_wallpaper_8
        else -> R.drawable.start_wallpaper_9
    }

    private fun extractWallpaperAccent(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return DEFAULT_ACCENT_ARGB

        val bins = 24
        val weights = DoubleArray(bins)
        val rs = DoubleArray(bins)
        val gs = DoubleArray(bins)
        val bs = DoubleArray(bins)
        var neutralR = 0.0
        var neutralG = 0.0
        var neutralB = 0.0
        var neutralWeight = 0.0
        val hsv = FloatArray(3)
        val step = max(1, min(bitmap.width, bitmap.height) / 72)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = AndroidColor.alpha(pixel)
                if (alpha >= 96) {
                    val r = AndroidColor.red(pixel)
                    val g = AndroidColor.green(pixel)
                    val b = AndroidColor.blue(pixel)
                    AndroidColor.RGBToHSV(r, g, b, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    val alphaWeight = alpha / 255.0

                    neutralR += r * alphaWeight
                    neutralG += g * alphaWeight
                    neutralB += b * alphaWeight
                    neutralWeight += alphaWeight

                    if (saturation >= 0.18f && value >= 0.20f) {
                        val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
                        val weight = alphaWeight *
                            (0.25 + saturation * saturation * 1.75) *
                            (0.35 + value * 0.65)
                        weights[bin] += weight
                        rs[bin] += r * weight
                        gs[bin] += g * weight
                        bs[bin] += b * weight
                    }
                }
                x += step
            }
            y += step
        }

        val best = weights.indices.maxByOrNull { weights[it] } ?: 0
        val chosenWeight = weights[best]
        val baseR: Int
        val baseG: Int
        val baseB: Int
        if (chosenWeight > 0.001) {
            baseR = (rs[best] / chosenWeight).toInt().coerceIn(0, 255)
            baseG = (gs[best] / chosenWeight).toInt().coerceIn(0, 255)
            baseB = (bs[best] / chosenWeight).toInt().coerceIn(0, 255)
        } else if (neutralWeight > 0.001) {
            baseR = (neutralR / neutralWeight).toInt().coerceIn(0, 255)
            baseG = (neutralG / neutralWeight).toInt().coerceIn(0, 255)
            baseB = (neutralB / neutralWeight).toInt().coerceIn(0, 255)
        } else {
            return DEFAULT_ACCENT_ARGB
        }

        AndroidColor.RGBToHSV(baseR, baseG, baseB, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.52f)
        hsv[2] = hsv[2].coerceIn(0.48f, 0.78f)
        return AndroidColor.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
    }

    val accentChoices: List<Color> = listOf(
        0xFF5133ABL, 0xFF603CBAL, 0xFF6A00FFL, 0xFF8C0095L, 0xFFAC193DL,
        0xFFD13438L, 0xFFE51400L, 0xFFE66C00L, 0xFFF0A30AL, 0xFF60A917L,
        0xFF008A00L, 0xFF00A300L, 0xFF00ABA9L, 0xFF008299L, 0xFF1BA1E2L,
        0xFF0078D7L, 0xFF0050EFL, 0xFF2D89EFL, 0xFFC239B3L, 0xFF767676L,
    ).map { argb -> Color(argb) }
}

private fun Color.toArgbLong(): Long {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val r = (red.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val g = (green.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val b = (blue.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
