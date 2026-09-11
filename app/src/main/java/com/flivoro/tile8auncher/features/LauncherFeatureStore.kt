package com.flivoro.tile8auncher.features

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class StartDoubleTapAction {
    NONE,
    SEARCH,
    ALL_APPS,
    CHARMS,
    LOCK_DEVICE,
}

/** Process-local signal for feature activities that mutate the persisted Start layout. */
object LauncherFeatureRuntime {
    var pinnedTilesRevision by mutableIntStateOf(0)
        private set

    fun notifyPinnedTilesChanged() {
        pinnedTilesRevision++
    }
}

/**
 * Preferences for optional modern launcher capabilities.
 *
 * These intentionally live outside AppsRepository's core tile/layout preferences so adding an
 * Android-only capability cannot change any Windows 8.1 motion state or launch timing values.
 */
object LauncherFeatureStore {
    const val PREFS_NAME = "tile8_launcher_prefs_v2"

    private const val HIDDEN_PACKAGES = "feature_hidden_packages"
    private const val PRIVATE_PACKAGES = "feature_private_packages"
    private const val LIVE_TILE_DISABLED_PACKAGES = "feature_live_tile_disabled_packages"
    private const val ICON_PACK_PACKAGE = "feature_icon_pack_package"
    private const val CUSTOM_ICON_PREFIX = "feature_custom_icon_uri_"
    private const val DOUBLE_TAP_ACTION = "feature_start_double_tap_action"
    private const val FOLDERS_JSON = "feature_folders_json"
    private const val WIDGET_STACKS_JSON = "feature_widget_stacks_json"
    private const val LOCK_SLIDESHOW_URIS = "feature_lock_slideshow_uris"
    private const val LOCK_STATUS_PACKAGES = "feature_lock_status_packages"
    private const val LOCK_DETAILED_PACKAGE = "feature_lock_detailed_package"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hiddenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(HIDDEN_PACKAGES, emptySet()).orEmpty().toSet()

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet(HIDDEN_PACKAGES, packages.toSet()) }
    }

    fun privatePackages(context: Context): Set<String> =
        prefs(context).getStringSet(PRIVATE_PACKAGES, emptySet()).orEmpty().toSet()

    fun setPrivatePackages(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet(PRIVATE_PACKAGES, packages.toSet()) }
    }

    fun isLiveTileEnabled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName !in prefs(context)
            .getStringSet(LIVE_TILE_DISABLED_PACKAGES, emptySet()).orEmpty()
    }

    fun setLiveTileEnabled(context: Context, packageName: String, enabled: Boolean) {
        val current = prefs(context)
            .getStringSet(LIVE_TILE_DISABLED_PACKAGES, emptySet()).orEmpty().toMutableSet()
        if (enabled) current.remove(packageName) else current.add(packageName)
        prefs(context).edit { putStringSet(LIVE_TILE_DISABLED_PACKAGES, current) }
    }

    fun selectedIconPack(context: Context): String? =
        prefs(context).getString(ICON_PACK_PACKAGE, null)?.takeIf(String::isNotBlank)

    fun setSelectedIconPack(context: Context, packageName: String?) {
        prefs(context).edit {
            if (packageName.isNullOrBlank()) remove(ICON_PACK_PACKAGE)
            else putString(ICON_PACK_PACKAGE, packageName)
        }
    }

    fun customIconUri(context: Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return prefs(context).getString(CUSTOM_ICON_PREFIX + packageName, null)
    }

    fun setCustomIconUri(context: Context, packageName: String, uri: String?) {
        prefs(context).edit {
            if (uri.isNullOrBlank()) remove(CUSTOM_ICON_PREFIX + packageName)
            else putString(CUSTOM_ICON_PREFIX + packageName, uri)
        }
    }

    fun doubleTapAction(context: Context): StartDoubleTapAction {
        val stored = prefs(context).getString(DOUBLE_TAP_ACTION, StartDoubleTapAction.NONE.name)
        return StartDoubleTapAction.entries.firstOrNull { it.name == stored }
            ?: StartDoubleTapAction.NONE
    }

    fun setDoubleTapAction(context: Context, action: StartDoubleTapAction) {
        prefs(context).edit { putString(DOUBLE_TAP_ACTION, action.name) }
    }

    fun folderPackages(context: Context, tileId: String): List<String> =
        readStringArrayMap(context, FOLDERS_JSON, tileId)

    fun setFolderPackages(context: Context, tileId: String, packages: List<String>) {
        writeStringArrayMap(context, FOLDERS_JSON, tileId, packages.distinct())
    }

    fun removeFolder(context: Context, tileId: String) {
        writeStringArrayMap(context, FOLDERS_JSON, tileId, emptyList())
    }

    fun widgetStackIds(context: Context, tileId: String): List<Int> =
        readStringArrayMap(context, WIDGET_STACKS_JSON, tileId)
            .mapNotNull(String::toIntOrNull)

    fun setWidgetStackIds(context: Context, tileId: String, ids: List<Int>) {
        writeStringArrayMap(context, WIDGET_STACKS_JSON, tileId, ids.distinct().map(Int::toString))
    }

    fun removeWidgetStack(context: Context, tileId: String) {
        writeStringArrayMap(context, WIDGET_STACKS_JSON, tileId, emptyList())
    }

    fun lockSlideshowUris(context: Context): List<String> =
        parseArray(prefs(context).getString(LOCK_SLIDESHOW_URIS, null))

    fun setLockSlideshowUris(context: Context, uris: List<String>) {
        val array = JSONArray()
        uris.distinct().forEach(array::put)
        prefs(context).edit { putString(LOCK_SLIDESHOW_URIS, array.toString()) }
    }

    fun lockStatusPackages(context: Context): List<String> =
        parseArray(prefs(context).getString(LOCK_STATUS_PACKAGES, null)).take(7)

    fun setLockStatusPackages(context: Context, packages: List<String>) {
        val array = JSONArray()
        packages.distinct().take(7).forEach(array::put)
        prefs(context).edit { putString(LOCK_STATUS_PACKAGES, array.toString()) }
    }

    fun lockDetailedPackage(context: Context): String? =
        prefs(context).getString(LOCK_DETAILED_PACKAGE, null)?.takeIf(String::isNotBlank)

    fun setLockDetailedPackage(context: Context, packageName: String?) {
        prefs(context).edit {
            if (packageName.isNullOrBlank()) remove(LOCK_DETAILED_PACKAGE)
            else putString(LOCK_DETAILED_PACKAGE, packageName)
        }
    }

    private fun readStringArrayMap(context: Context, key: String, itemKey: String): List<String> {
        val root = runCatching { JSONObject(prefs(context).getString(key, "{}") ?: "{}") }
            .getOrElse { JSONObject() }
        return parseArray(root.optJSONArray(itemKey))
    }

    private fun writeStringArrayMap(
        context: Context,
        key: String,
        itemKey: String,
        values: List<String>,
    ) {
        val root = runCatching { JSONObject(prefs(context).getString(key, "{}") ?: "{}") }
            .getOrElse { JSONObject() }
        if (values.isEmpty()) {
            root.remove(itemKey)
        } else {
            val array = JSONArray()
            values.forEach(array::put)
            root.put(itemKey, array)
        }
        prefs(context).edit { putString(key, root.toString()) }
    }

    private fun parseArray(raw: String?): List<String> =
        runCatching { parseArray(JSONArray(raw ?: "[]")) }.getOrDefault(emptyList())

    private fun parseArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}
