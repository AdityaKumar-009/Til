package com.flivoro.tile8auncher.ui.phone

/** Shared appearance policy for Windows Phone 8.1 and Windows 10 Mobile. */
internal fun isPhoneOrMessagesApp(title: String): Boolean =
    title.equals("Phone", ignoreCase = true) ||
        title.equals("Dialer", ignoreCase = true) ||
        title.equals("Messages", ignoreCase = true) ||
        title.equals("Messaging", ignoreCase = true)

/** Stock Metro tiles and the Phone/Messages shortcuts follow the selected accent. */
internal fun usesPhoneThemeAccent(
    tileId: String,
    title: String,
    hasManualColor: Boolean,
): Boolean = !hasManualColor && (tileId.startsWith("tile_") ||
    isPhoneOrMessagesApp(title))

/** Brand-colored tiles must never replace Metro stock or manual colors. */
internal fun canUsePhoneBrandColor(
    tileId: String,
    title: String,
    hasManualColor: Boolean,
    isDiscoLayered: Boolean,
): Boolean = isDiscoLayered && !usesPhoneThemeAccent(tileId, title, false) &&
    !hasManualColor

/** Match WP8.1's 30-unit glyph within a 52-unit app-list icon plate. */
internal fun phoneAllAppsGlyphSizeDp(plateSideDp: Float): Float =
    plateSideDp * (30f / 52f)

internal fun phoneAllAppsSettingsGlyphSizeDp(plateSideDp: Float): Float =
    plateSideDp * (27f / 52f)

internal fun phoneAllAppsFallbackSizeSp(plateSideDp: Float): Float =
    plateSideDp * (22f / 52f)
