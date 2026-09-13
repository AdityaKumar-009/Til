package com.flivoro.tile8auncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object WindowsColors {
    // Exact 32-bit ARGB Hex values from Windows 8.1 Start Screen (100% opaque)
    const val MailBlue = 0xFF0072C6L
    const val InternetExplorerBlue = 0xFF00A4EFL
    const val StoreGreen = 0xFF008A00L
    const val WeatherCyan = 0xFF0097A7L
    const val CalendarPurple = 0xFF5133ABL
    const val PeopleOrange = 0xFFE05206L
    const val SkypeCyan = 0xFF00AFF0L
    const val MusicOrange = 0xFFE06000L
    const val VideoCrimson = 0xFFB91D47L
    const val ReadingListCrimson = 0xFFA20025L
    const val HelpOrange = 0xFFD24726L
    const val MoneyGreen = 0xFF008A00L
    const val SportsPurple = 0xFF603CBAL
    const val NewsRed = 0xFFB91D47L
    const val PhotosCyan = 0xFF008299L
    const val OneDriveBlue = 0xFF094AB2L
    const val OneNotePurple = 0xFF7719AAL
    const val DesktopBlue = 0xFF1C355EL
    const val SettingsPurple = 0xFF5C2D91L
    const val CameraPink = 0xFFD80073L
    const val Teal = 0xFF00ABA9L
    const val Lime = 0xFF60A917L
    const val Amber = 0xFFF0A30AL
    const val Red = 0xFFE51400L
    const val Magenta = 0xFFD80073L

    // Windows 8.1 All Apps icon-container accent. It stays purple by default but is intentionally
    // snapshot-backed so Personalize can change the All Apps accent without touching wallpaper art.
    var Purple by mutableLongStateOf(0xFF603CBAL)

    const val DarkCyan = 0xFF008299L
    const val DarkViolet = 0xFF2E0854L
    const val Gray = 0xFF555555L

    // Background Wallpapers
    val DefaultWallpaperStart = Color(0xFF23053D)
    val DefaultWallpaperEnd = Color(0xFF0C0117)
    val SwirlHighlight1 = Color(0xFF5A148C)
    val SwirlHighlight2 = Color(0xFF8824B8)

    // Text & Chrome
    val TextWhite = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xCCFFFFFF)
    val TextMuted = Color(0x88FFFFFF)

    val ColorOptions = listOf(
        MailBlue,
        InternetExplorerBlue,
        Teal,
        StoreGreen,
        Lime,
        HelpOrange,
        Amber,
        Red,
        ReadingListCrimson,
        Magenta,
        0xFF603CBAL,
        DarkCyan,
        OneDriveBlue,
        OneNotePurple,
        Gray
    )
}

/**
 * Converts a 32-bit ARGB Long (e.g. 0xFF0072C6L) into a 100% solid Compose Color.
 */
fun Long.toTileColor(): Color {
    val argb = (this and 0xFFFFFFFFL).toInt()
    return Color(argb)
}
