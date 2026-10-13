package com.flivoro.tile8auncher.data

import android.graphics.Color

/**
 * A flat white, black or neutral Android adaptive-icon plate is not a Metro
 * accent. Preserve the application's existing tile color for such layers.
 * Vivid light colours (including yellow) are deliberately allowed.
 */
fun usableDiscoTileAccent(color: Long?): Long? {
    if (color == null) return null
    val argb = color.toInt()
    val hsv = FloatArray(3)
    Color.colorToHSV(argb, hsv)
    if (Color.alpha(argb) < 180 || hsv[1] < .14f ||
        hsv[2] < .16f) return null
    return color
}

/** True white adaptive backgrounds are valid when a separate colored logo
 * occupies the foreground. For black/white foreground glyphs, a neutral
 * background is a plate and the original Metro tile color must survive. */
fun discoLayeredAccentOrNull(
    context: android.content.Context,
    packageName: String?,
    color: Long?,
): Long? {
    usableDiscoTileAccent(color)?.let { return it }
    if (color == null || packageName.isNullOrBlank()) return null
    val knownColorForeground = context.getSharedPreferences(
        "tile8_app_visual_cache_v1", android.content.Context.MODE_PRIVATE,
    ).getBoolean("disco_layer_color_v3:$packageName", false)
    return color.takeIf { knownColorForeground }
}
