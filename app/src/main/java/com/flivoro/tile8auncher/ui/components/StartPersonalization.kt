package com.flivoro.tile8auncher.ui.components

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Small persistent Windows 8.1 Start personalization store.
 *
 * Kept independent from tile/app preferences so changing a color can immediately invalidate only
 * the wallpaper/Personalize surfaces. The defaults exactly match the launcher's existing purple
 * palette, so upgrading does not visually change anyone who has not personalized Start.
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
    private val backgroundState = mutableStateOf(Color(DEFAULT_BACKGROUND_ARGB.toULong()))
    private val accentState = mutableStateOf(Color(DEFAULT_ACCENT_ARGB.toULong()))

    val backgroundColor: Color get() = backgroundState.value
    val accentColor: Color get() = accentState.value

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val background = prefs.getLong(KEY_BACKGROUND, DEFAULT_BACKGROUND_ARGB)
            val accent = prefs.getLong(KEY_ACCENT, DEFAULT_ACCENT_ARGB)
            backgroundState.value = Color(background.toULong())
            accentState.value = Color(accent.toULong())
            initialized = true
        }
    }

    fun setBackgroundColor(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        backgroundState.value = Color(argb.toULong())
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BACKGROUND, argb)
            .apply()
    }

    fun setAccentColor(context: Context, color: Color) {
        ensureLoaded(context)
        val argb = color.toArgbLong()
        accentState.value = Color(argb.toULong())
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACCENT, argb)
            .apply()
    }

    fun highlightFor(accent: Color): Color {
        if (accent.toArgbLong() == DEFAULT_ACCENT_ARGB) {
            return Color(DEFAULT_HIGHLIGHT_ARGB.toULong())
        }
        return Color(
            red = (accent.red * 0.72f + 0.28f).coerceIn(0f, 1f),
            green = (accent.green * 0.72f + 0.28f).coerceIn(0f, 1f),
            blue = (accent.blue * 0.72f + 0.28f).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    val backgroundChoices: List<Color> = listOf(
        0xFF180052, 0xFF23053D, 0xFF3B0B59, 0xFF5133AB, 0xFF6A00FF,
        0xFF001E4E, 0xFF004050, 0xFF006A6A, 0xFF008272, 0xFF007233,
        0xFF0A5A20, 0xFF4C5F00, 0xFF7A5C00, 0xFF9A4600, 0xFF9A1B00,
        0xFF8E1730, 0xFF7A174A, 0xFF5E2750, 0xFF3A3A3A, 0xFF111111,
    ).map { Color(it) }

    val accentChoices: List<Color> = listOf(
        0xFF5133AB, 0xFF6A00FF, 0xFF8C0095, 0xFFAC193D, 0xFFD13438,
        0xFFE81123, 0xFFE66C00, 0xFFF0A30A, 0xFF60A917, 0xFF008A00,
        0xFF00A300, 0xFF00ABA9, 0xFF1BA1E2, 0xFF0078D7, 0xFF0050EF,
        0xFF2D89EF, 0xFF6B69D6, 0xFFAA00FF, 0xFFC239B3, 0xFF767676,
    ).map { Color(it) }
}

private fun Color.toArgbLong(): Long {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val r = (red.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val g = (green.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    val b = (blue.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
