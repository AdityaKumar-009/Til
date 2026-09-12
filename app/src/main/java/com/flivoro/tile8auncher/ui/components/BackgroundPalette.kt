package com.flivoro.tile8auncher.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Semantic artwork colors. Every Start pattern is transparent and uses these roles, so Background
 * color and Accent color remain genuinely independent like Windows 8.1 Personalize.
 */
internal data class BackgroundPalette(
    val primary: Color,
    val secondary: Color,
    val shadow: Color,
    val highlight: Color,
    val faint: Color,
) {
    companion object {
        fun fromAccent(accent: Color): BackgroundPalette = BackgroundPalette(
            primary = accent,
            secondary = accent.scaleRgb(0.72f),
            shadow = accent.scaleRgb(0.42f),
            highlight = accent.mixWhite(0.32f),
            faint = accent.mixWhite(0.14f),
        )
    }
}

private fun Color.scaleRgb(scale: Float): Color = Color(
    red = (red * scale).coerceIn(0f, 1f),
    green = (green * scale).coerceIn(0f, 1f),
    blue = (blue * scale).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.mixWhite(amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = (red + (1f - red) * t).coerceIn(0f, 1f),
        green = (green + (1f - green) * t).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * t).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
