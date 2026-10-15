package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherUiMode
import java.text.Normalizer

/** Shared by startup preparation and the rendered Start surface. */
internal fun phoneStartSeed(
    tiles: List<TileModel>,
    catalog: List<AppInfo>,
    mode: LauncherUiMode,
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
        it.label.equals("Phone", true) || it.label.equals("Dialer", true)
    }
    val messages = catalog.firstOrNull {
        it.label.equals("Messages", true) || it.label.equals("Messaging", true)
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

internal fun phoneStartupPackages(
    context: Context,
    mode: LauncherUiMode,
    tiles: List<TileModel>,
    catalog: List<AppInfo>,
): List<String> {
    val seed = phoneStartSeed(tiles, catalog, mode)
    val order = PhoneLayoutStore.order(context, mode, seed)
    val folders = PhoneFolderStore.read(context, mode).associateBy(PhoneFolder::id)
    val packagesById = tiles.associate { it.id to it.packageName }
    val installed = catalog.mapTo(HashSet(), AppInfo::packageName)
    // Include folder previews and saved app pins beyond the first alphabet page.
    return order.flatMap { id -> listOf(id) + folders[id]?.children.orEmpty() }
        .mapNotNull { id ->
            packagesById[id] ?: id.takeIf { it.startsWith("app:") }
                ?.removePrefix("app:")?.takeIf { it in installed }
        }.distinct()
}

/** Same #, A..Z, international order as the phone drawer, including folded accented labels. */
internal fun phoneAlphabetGroup(label: String): String {
    val initial = label.firstOrNull()
    val folded = Normalizer.normalize(label, Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar()
    return when {
        initial == null -> "&"
        initial.isDigit() -> "#"
        folded != null && folded in 'A'..'Z' -> folded.toString()
        else -> "&"
    }
}

internal fun phoneAppsInDisplayOrder(apps: List<AppInfo>): List<AppInfo> = apps.sortedWith(
    compareBy<AppInfo> {
        when (val group = phoneAlphabetGroup(it.label)) {
            "#" -> 0
            "&" -> 27
            else -> group[0] - 'A' + 1
        }
    }.thenComparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label) },
)
