package com.flivoro.tile8auncher.ui.components

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.flivoro.tile8auncher.ui.theme.WindowsColors

/** Persistent accent color used by the All Apps icon containers. */
@Stable
internal object StartPersonalization {
    const val DEFAULT_ACCENT_ARGB: Long = 0xFF603CBA

    private const val PREFS_NAME = "tile8_start_personalization"
    private const val KEY_ACCENT = "accent_argb"
    private const val KEY_CUSTOM_WALLPAPER_URI = "custom_wallpaper_uri"
    private const val KEY_CUSTOM_WALLPAPER_OVERLAY = "custom_wallpaper_overlay"

    private var initialized = false
    private val accentState = mutableStateOf(Color(DEFAULT_ACCENT_ARGB))
    private val customWallpaperUriState = mutableStateOf<String?>(null)
    private val customWallpaperOverlayState = mutableStateOf(0.24f)

    val accentColor: Color get() = accentState.value
    val customWallpaperUri: String? get() = customWallpaperUriState.value
    val customWallpaperOverlay: Float get() = customWallpaperOverlayState.value

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val accent = prefs.getLong(KEY_ACCENT, DEFAULT_ACCENT_ARGB) and 0xFFFFFFFFL
            accentState.value = Color(accent)
            customWallpaperUriState.value = prefs.getString(KEY_CUSTOM_WALLPAPER_URI, null)
                ?.takeIf(String::isNotBlank)
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
        customWallpaperUriState.value = normalized
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (normalized == null) remove(KEY_CUSTOM_WALLPAPER_URI)
                else putString(KEY_CUSTOM_WALLPAPER_URI, normalized)
            }
            .apply()
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
