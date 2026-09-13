package com.flivoro.tile8auncher.ui.components

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Persistent Windows 8.1 Start personalization store.
 *
 * Background and Accent remain independent user choices, but selecting a stock wallpaper can apply
 * that wallpaper's own default pair atomically via [setColors].
 */
@Stable
internal object StartPersonalization {
    const val DEFAULT_BACKGROUND_ARGB: Long = 0xFF23053D
    const val DEFAULT_ACCENT_ARGB: Long = 0xFF5A148C
    const val DEFAULT_HIGHLIGHT_ARGB: Long = 0xFF8824B8

    private const val PREFS_NAME = "tile8_start_personalization"
    private const val KEY_BACKGROUND = "background_argb"
    private const val KEY_ACCENT = "accent_argb"

    private var initialized = false
    private val backgroundState = mutableStateOf(Color(DEFAULT_BACKGROUND_ARGB))
    private val accentState = mutableStateOf(Color(DEFAULT_ACCENT_ARGB))

    val backgroundColor: Color get() = backgroundState.value
    val accentColor: Color get() = accentState.value

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val background = prefs.getLong(KEY_BACKGROUND, DEFAULT_BACKGROUND_ARGB)
            val accent = prefs.getLong(KEY_ACCENT, DEFAULT_ACCENT_ARGB)
            backgroundState.value = Color(background)
            accentState.value = Color(accent)
            initialized = true
        }
    }

    fun setBackgroundColor(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        backgroundState.value = Color(argb)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BACKGROUND, argb)
            .apply()
    }

    fun setAccentColor(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        accentState.value = Color(argb)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACCENT, argb)
            .apply()
    }

    fun setColors(context: Context, backgroundArgb: Long, accentArgb: Long) {
        ensureLoaded(context)
        val background = backgroundArgb and 0xFFFFFFFFL
        val accent = accentArgb and 0xFFFFFFFFL
        backgroundState.value = Color(background)
        accentState.value = Color(accent)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BACKGROUND, background)
            .putLong(KEY_ACCENT, accent)
            .apply()
    }

    fun highlightFor(accent: Color): Color {
        if (accent.toArgbLong() == DEFAULT_ACCENT_ARGB) {
            return Color(DEFAULT_HIGHLIGHT_ARGB)
        }
        return Color(
            red = (accent.red * 0.72f + 0.28f).coerceIn(0f, 1f),
            green = (accent.green * 0.72f + 0.28f).coerceIn(0f, 1f),
            blue = (accent.blue * 0.72f + 0.28f).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    val backgroundChoices: List<Color> = listOf(
        0xFF180052L, 0xFF23053DL, 0xFF3B0B59L, 0xFF5133ABL, 0xFF6A00FFL,
        0xFF001E4EL, 0xFF004050L, 0xFF006A6AL, 0xFF008272L, 0xFF007233L,
        0xFF0A5A20L, 0xFF4C5F00L, 0xFF7A5C00L, 0xFF9A4600L, 0xFF9A1B00L,
        0xFF8E1730L, 0xFF7A174AL, 0xFF5E2750L, 0xFF3A3A3AL, 0xFF111111L,
    ).map { argb -> Color(argb) }

    val accentChoices: List<Color> = listOf(
        0xFF5133ABL, 0xFF6A00FFL, 0xFF8C0095L, 0xFFAC193DL, 0xFFD13438L,
        0xFFE81123L, 0xFFE66C00L, 0xFFF0A30AL, 0xFF60A917L, 0xFF008A00L,
        0xFF00A300L, 0xFF00ABA9L, 0xFF1BA1E2L, 0xFF0078D7L, 0xFF0050EFL,
        0xFF2D89EFL, 0xFF6B69D6L, 0xFFAA00FFL, 0xFFC239B3L, 0xFF767676L,
    ).map { argb -> Color(argb) }
}

private fun Color.toArgbLong(): Long {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val r = (red.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val g = (green.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val b = (blue.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
