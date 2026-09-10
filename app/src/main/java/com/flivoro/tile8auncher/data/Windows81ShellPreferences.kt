package com.flivoro.tile8auncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit

/** Small, launcher-shell-only state kept separate from the existing animation/settings store. */
class Windows81ShellPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tile8_windows81_shell", Context.MODE_PRIVATE)
    private val packageManager = context.packageManager

    fun getAppsSortMode(): Windows81AppsSortMode = runCatching {
        Windows81AppsSortMode.valueOf(
            prefs.getString(KEY_SORT_MODE, Windows81AppsSortMode.NAME.name)
                ?: Windows81AppsSortMode.NAME.name,
        )
    }.getOrDefault(Windows81AppsSortMode.NAME)

    fun setAppsSortMode(mode: Windows81AppsSortMode) {
        prefs.edit { putString(KEY_SORT_MODE, mode.name) }
    }

    /**
     * First run establishes the current app inventory as already seen. Later package additions
     * remain NEW until the user launches them, mirroring Windows 8.1 Update behavior.
     */
    fun initializeSeenAppsIfNeeded(apps: List<AppInfo>) {
        if (prefs.getBoolean(KEY_SEEN_INITIALIZED, false)) return
        prefs.edit {
            putStringSet(KEY_SEEN_PACKAGES, apps.mapTo(linkedSetOf()) { it.packageName })
            putBoolean(KEY_SEEN_INITIALIZED, true)
        }
    }

    fun isAppNew(packageName: String): Boolean {
        if (!prefs.getBoolean(KEY_SEEN_INITIALIZED, false)) return false
        return packageName !in (prefs.getStringSet(KEY_SEEN_PACKAGES, emptySet()) ?: emptySet())
    }

    fun markAppSeen(packageName: String) {
        val seen = (prefs.getStringSet(KEY_SEEN_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()
        if (seen.add(packageName)) prefs.edit { putStringSet(KEY_SEEN_PACKAGES, seen) }
    }

    fun newAppCount(apps: List<AppInfo>): Int = apps.count { isAppNew(it.packageName) }

    fun recordLaunch(packageName: String) {
        markAppSeen(packageName)
        val countKey = launchCountKey(packageName)
        val lastKey = lastLaunchKey(packageName)
        prefs.edit {
            putInt(countKey, prefs.getInt(countKey, 0) + 1)
            putLong(lastKey, System.currentTimeMillis())
        }
    }

    fun launchCount(packageName: String): Int = prefs.getInt(launchCountKey(packageName), 0)
    fun lastLaunch(packageName: String): Long = prefs.getLong(lastLaunchKey(packageName), 0L)

    fun getLiveTileDisabledIds(): Set<String> =
        (prefs.getStringSet(KEY_LIVE_TILE_DISABLED, emptySet()) ?: emptySet()).toSet()

    fun setLiveTileEnabled(tileIds: Collection<String>, enabled: Boolean) {
        val disabled = getLiveTileDisabledIds().toMutableSet()
        if (enabled) disabled.removeAll(tileIds.toSet()) else disabled.addAll(tileIds)
        prefs.edit { putStringSet(KEY_LIVE_TILE_DISABLED, disabled) }
    }

    fun categoryLabel(packageName: String): String {
        val category = runCatching {
            packageManager.getApplicationInfo(packageName, 0).category
        }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO -> "Music & audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_MAPS -> "Maps & travel"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
            else -> "Other"
        }
    }

    private fun launchCountKey(packageName: String) = "launch_count::$packageName"
    private fun lastLaunchKey(packageName: String) = "last_launch::$packageName"

    private companion object {
        const val KEY_SORT_MODE = "apps_sort_mode"
        const val KEY_SEEN_INITIALIZED = "seen_apps_initialized"
        const val KEY_SEEN_PACKAGES = "seen_app_packages"
        const val KEY_LIVE_TILE_DISABLED = "live_tile_disabled_ids"
    }
}

enum class Windows81AppsSortMode(val label: String) {
    NAME("by name"),
    DATE_INSTALLED("by date installed"),
    MOST_USED("by most used"),
    CATEGORY("by category"),
}
