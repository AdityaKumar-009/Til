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
