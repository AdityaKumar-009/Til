package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherUiMode
import java.text.Normalizer
import java.text.Collator
import java.util.Locale

/** Shared by startup preparation and the rendered Start surface. */
internal fun phoneStartSeed(
    tiles: List<TileModel>,
    catalog: List<AppInfo>,
    mode: LauncherUiMode,
    roles: PhoneAppRoles = PhoneAppRoles(),
): List<TileModel> {
    val byPackage = catalog.associateBy(AppInfo::packageName)
    val desktopById = tiles.associateBy(TileModel::id).mapValues { (id, tile) ->
        when (id) {
            "tile_settings" -> tile.copy(title = "Mosaic Settings", iconGlyph = "settings")
            "tile_mail", "tile_calendar" -> tile.copy(size = TileSize.MEDIUM)
            else -> tile
        }
    }
    val dialer = catalog.firstOrNull {
        roles.glyph(it.packageName, it.label) == "wp81_phone"
    }
    val messages = catalog.firstOrNull {
        roles.glyph(it.packageName, it.label) == "wp81_messages"
    }
    val topAppIds = listOfNotNull(dialer, messages).map { "app:" + it.packageName }
    val stockIds = if (mode == LauncherUiMode.MOBILE_10) listOf(
        "tile_mail", "tile_people", "tile_calendar", "tile_photos",
        "tile_camera", "tile_skype", "tile_weather", "tile_settings",
        "tile_store", "tile_clock", "tile_onedrive", "tile_maps",
    ) else listOf(
        "tile_people", "tile_mail", "tile_calendar", "tile_skype",
        "tile_photos", "tile_camera", "tile_ie", "tile_weather",
        "tile_settings", "tile_store", "tile_clock", "tile_maps",
    )
    return (topAppIds + stockIds).distinct().mapNotNull { id ->
        desktopById[id] ?: if (id.startsWith("app:")) {
            byPackage[id.removePrefix("app:")]?.let { app ->
                TileModel(id = id, title = app.label, packageName = app.packageName)
            }
        } else null
    }.ifEmpty { tiles }
}

/** Same #, A..Z, international order as the phone drawer, including folded accented labels. */
internal fun phoneAlphabetGroup(label: String): String {
    val clean = label.trimStart()
    val initial = clean.firstOrNull()
    val folded = Normalizer.normalize(clean, Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar()
    return when {
        initial == null -> "&"
        initial.isDigit() -> "#"
        folded != null && folded in 'A'..'Z' -> folded.toString()
        else -> "&"
    }
}

internal fun phoneAppsInDisplayOrder(apps: List<AppInfo>, locale: Locale = Locale.getDefault()): List<AppInfo> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return apps.sortedWith(compareBy<AppInfo> {
        when (val group = phoneAlphabetGroup(it.label)) {
            "#" -> 0
            "&" -> 27
            else -> group[0] - 'A' + 1
        }
    }.thenComparator { a, b -> collator.compare(a.label, b.label) }
        .thenBy { it.packageName }.thenBy { it.activityName })
}
