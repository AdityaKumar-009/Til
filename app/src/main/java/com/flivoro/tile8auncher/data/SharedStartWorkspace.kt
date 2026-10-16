package com.flivoro.tile8auncher.data

import android.content.Context
import androidx.core.content.edit
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.phone.PhoneFolder
import com.flivoro.tile8auncher.ui.phone.PhoneFolderStore
import com.flivoro.tile8auncher.ui.phone.phoneStartSeed
import org.json.JSONArray
import org.json.JSONObject

/** One canonical pin list. Phone folders are a presentation of those pins, not another workspace. */
internal object SharedStartWorkspace {
    private const val MIGRATED = "shared_start_workspace_v1"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun migrate(context: Context, repository: AppsRepository, desktop: List<TileModel>,
                apps: List<AppInfo>): List<TileModel> {
        val preferences = prefs(context)
        if (preferences.getBoolean(MIGRATED, false)) return desktop
        val keys = listOf("phone_shared_order", "phone_PHONE_8_order", "phone_MOBILE_10_order")
        val selected = if (preferences.contains(keys[0])) keys.take(1) else keys.drop(1)
        val savedOrders = selected.mapNotNull { preferences.getString(it, null) }
        val mode = LauncherFeatureStore.launcherUiMode(context)
            .takeIf { it != LauncherUiMode.DESKTOP } ?: LauncherUiMode.PHONE_8
        val phoneOrder = if (savedOrders.isEmpty()) {
            if (desktop.isEmpty()) emptyList() else phoneStartSeed(desktop, apps, mode).map(TileModel::id)
        }
            else savedOrders.flatMap(::readOrder).distinct()
        // Keep the exact old data as a one-time recovery backup, then merge without dropping pins.
        val backup = JSONObject()
        (keys + listOf("pinned_tiles_json", "phone_shared_live_folders",
            "phone_PHONE_8_live_folders", "phone_MOBILE_10_live_folders")).forEach { key ->
            preferences.getString(key, null)?.let { backup.put(key, it) }
        }
        preferences.edit { putString("shared_start_workspace_backup_v1", backup.toString()) }
        // Very old installs may still have separate phone folders. Preserve children from both
        // modes before PhoneFolderStore's single-mode migration would choose just one of them.
        if (!preferences.contains("phone_shared_live_folders")) {
            val folders = listOf("phone_PHONE_8_live_folders", "phone_MOBILE_10_live_folders")
                .mapNotNull { preferences.getString(it, null) }.flatMap { raw ->
                    runCatching {
                        val array = JSONArray(raw)
                        (0 until array.length()).map { array.getJSONObject(it) }
                    }.getOrDefault(emptyList())
                }.groupBy { it.optString("id") }.mapNotNull { (id, entries) ->
                    if (!id.startsWith("phone_folder_")) return@mapNotNull null
                    PhoneFolder(id, entries.first().optString("name", "Folder"),
                        entries.flatMap { readOrder(it.optJSONArray("children")?.toString() ?: "[]") }.distinct())
                }
            PhoneFolderStore.save(context, mode, folders)
        }
        val phonePins = tilesForPhoneOrder(context, mode, phoneOrder, desktop, apps)
        val merged = desktop + phonePins.filter { incoming -> desktop.none { it.id == incoming.id } }
        repository.savePinnedTiles(merged)
        preferences.edit {
            putString("phone_shared_order", JSONArray(phoneOrder).toString())
            putBoolean(MIGRATED, true)
        }
        return merged
    }

    private fun readOrder(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    /** Normalize old phone app IDs to existing Desktop pins, preserving stock tile identities. */
    fun canonicalId(id: String, tiles: List<TileModel>): String? {
        tiles.firstOrNull { it.id == id }?.let { return it.id }
        val pkg = id.takeIf { it.startsWith("app:") }?.removePrefix("app:") ?: return null
        if (pkg == "mosaic.launcher.settings") return tiles.firstOrNull { it.id == "tile_settings" }?.id
        return tiles.firstOrNull { it.packageName == pkg && !it.id.startsWith("tile_") }?.id
    }

    fun projectPhoneOrder(tiles: List<TileModel>, saved: List<String>, folders: List<PhoneFolder>): List<String> {
        val byId = tiles.associateBy(TileModel::id)
        val byFolder = folders.associateBy(PhoneFolder::id)
        val used = HashSet<String>()
        val result = mutableListOf<String>()
        for (id in saved) {
            val folder = byFolder[id]
            if (folder != null) {
                val children = folder.children.mapNotNull { canonicalId(it, tiles) }
                if (children.any { it !in used }) {
                    result += id
                    used += children
                }
            } else canonicalId(id, tiles)?.let { if (used.add(it)) result += phoneId(byId.getValue(it)) }
        }
        tiles.forEach { if (used.add(it.id)) result += phoneId(it) }
        return result
    }

    private fun phoneId(tile: TileModel): String =
        if (!tile.id.startsWith("tile_") && tile.packageName != null) "app:${tile.packageName}" else tile.id

    fun tilesForPhoneOrder(context: Context, mode: LauncherUiMode, order: List<String>,
                          current: List<TileModel>, apps: List<AppInfo>): List<TileModel> {
        val folders = PhoneFolderStore.read(context, mode).associateBy(PhoneFolder::id)
        val byId = current.associateBy(TileModel::id)
        val byPackage = apps.associateBy(AppInfo::packageName)
        return order.flatMap { id -> folders[id]?.children ?: listOf(id) }.mapNotNull { id ->
            canonicalId(id, current)?.let(byId::get) ?: if (id.startsWith("app:")) {
                val pkg = id.removePrefix("app:")
                if (pkg == "mosaic.launcher.settings") TileModel("tile_settings", "Mosaic Settings",
                    tileType = TileType.SETTINGS, iconGlyph = "settings")
                else byPackage[pkg]?.let { app -> TileModel(id, app.label, app.packageName,
                    app.activityName, usesAppAccent = true) }
            } else null
        }.distinctBy(TileModel::id).mapIndexed { index, tile -> tile.copy(order = index) }
    }

    fun reconcileFolders(context: Context, tiles: List<TileModel>) {
        if (!prefs(context).getBoolean(MIGRATED, false)) return
        val mode = LauncherUiMode.PHONE_8
        val byId = tiles.associateBy(TileModel::id)
        val old = PhoneFolderStore.read(context, mode)
        val updated = old.mapNotNull { folder ->
            folder.copy(children = folder.children.mapNotNull { canonicalId(it, tiles)?.let(byId::get)?.let(::phoneId) }.distinct())
                .takeIf { it.children.isNotEmpty() }
        }
        if (old != updated) PhoneFolderStore.save(context, mode, updated)
    }

    /** All Apps unpin matches Desktop: remove this package, including pins inside live folders. */
    fun orderWithoutPackage(context: Context, mode: LauncherUiMode, order: List<String>,
                            tiles: List<TileModel>, packageName: String): List<String> {
        val remaining = tiles.filterNot {
            it.packageName == packageName || (packageName == "mosaic.launcher.settings" && it.id == "tile_settings")
        }
        reconcileFolders(context, remaining)
        return projectPhoneOrder(remaining, order, PhoneFolderStore.read(context, mode))
    }
}
