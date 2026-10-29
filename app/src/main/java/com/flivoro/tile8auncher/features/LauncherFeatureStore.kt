package com.flivoro.tile8auncher.features

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.flivoro.tile8auncher.ui.components.LauncherImageCache
import org.json.JSONArray
import org.json.JSONObject

enum class StartDoubleTapAction {
    NONE,
    SEARCH,
    ALL_APPS,
    CHARMS,
    LOCK_DEVICE,
}

/** Independent launch surfaces; DESKTOP deliberately retains all previous rendering defaults. */
enum class LauncherUiMode(val title: String, val detail: String) {
    DESKTOP("Windows 8.1 Desktop", "Original horizontal Start and desktop motion"),
    PHONE_8("Windows Phone 8.1", "Classic phone Start, observed spatial tile cascade and turnstile motion"),
    MOBILE_10("Windows 10 Mobile", "Phone Start, translucent tiles and modern motion"),
}

/** One shared app-launch choice; DEFAULT retains each mode's original motion. */
enum class MasterLaunchAnimation(val title: String, val detail: String) {
    DEFAULT("Default (follow interface)", "Each interface uses its own original animation"),
    DESKTOP("Windows 8.1 Desktop", "Original Windows 8.1 tile flip and expansion"),
    PHONE_8("Windows Phone 8.1", "Original Lumia/Disco turnstile exit choreography"),
    MOBILE_10("Windows 10 Mobile", "Original Windows 10 Mobile spatial zoom"),
    LIFT("Lift", "Lift a tile out of the mosaic and step into its space"),
    DOOR("Door", "Open a hinged tile as the surrounding mosaic parts"),
    RIPPLE("Ripple", "A touch sends a wave through nearby tiles and opens the app"),
    PAGE_TURN("Page turn", "Lift a page from the mosaic and unfold it into the app"),
    GLIDE("Glide", "Draw the app forward on a smooth, sliding sheet"),
    FOCUS("Focus", "Bring the tile straight into focus as the mosaic softly recedes"),
    ARC("Arc", "Carry the tile along a gentle arc and settle into the app"),
    UNFOLD("Uncover", "Reveal the app by widening the tile into a continuous sheet"),
    PASSAGE("Passage", "Open a tall doorway from the tile as nearby columns gently part"),
    CASCADE("Cascade", "Let the mosaic fall away row by row as the tile rolls into the app"),

    ;

    val isStory: Boolean
        get() = this == LIFT || this == DOOR || this == RIPPLE ||
            this == PAGE_TURN || this == GLIDE || this == FOCUS ||
            this == ARC || this == UNFOLD || this == PASSAGE || this == CASCADE

    companion object {
        // Focus the picker on the quieter, visually distinct optional
        // effects. Older saved choices remain fully readable and runnable,
        // so curating this list does not reset users or break existing presets.
        val selectableChoices: List<MasterLaunchAnimation> = entries.filterNot {
            it == LIFT || it == GLIDE || it == PAGE_TURN || it == ARC
        }
    }
}

enum class AppIconStyle {
    DISCO_LAYERED,
    DEFAULT,
    ANDROID_ADAPTIVE,
    WHITE_MONOCHROME,
}

/** The two Start picture treatments offered by Windows 10 Mobile. */
enum class PhoneBackgroundStyle(val displayName: String) {
    FULL_SCREEN("Full screen picture"),
    TILE_PICTURE("Tile picture"),
}

/** Process-local signals for feature changes that visible launcher surfaces should re-read. */
object LauncherFeatureRuntime {
    var launcherModeRevision by mutableIntStateOf(0)
        private set

    // Updating a launch preset must not invalidate or rebuild launcher-mode surfaces.
    var masterLaunchAnimationRevision by mutableIntStateOf(0)
        private set

    fun notifyLauncherModeChanged() {
        launcherModeRevision++
    }

    fun notifyMasterLaunchAnimationChanged() {
        masterLaunchAnimationRevision++
    }

    var pinnedTilesRevision by mutableIntStateOf(0)
        private set
    var iconsRevision by mutableIntStateOf(0)
        private set
    var liveTilesRevision by mutableIntStateOf(0)
        private set
    var startAppearanceRevision by mutableIntStateOf(0)
        private set
    var allAppsAppearanceRevision by mutableIntStateOf(0)
        private set

    fun notifyPinnedTilesChanged() {
        pinnedTilesRevision++
    }

    fun notifyIconsChanged() {
        iconsRevision++
    }

    fun notifyLiveTilesChanged() {
        liveTilesRevision++
    }

    fun notifyStartAppearanceChanged() {
        startAppearanceRevision++
    }

    fun notifyAllAppsAppearanceChanged() {
        allAppsAppearanceRevision++
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

    private const val LAUNCHER_UI_MODE = "launcher_ui_mode"
    private const val MASTER_LAUNCH_ANIMATION = "master_app_launch_animation"
    private const val PHONE_RENDER_PENDING = "phone_render_pending"
    private const val PHONE_RENDER_ATTEMPTED = "phone_render_attempted"
    private const val PHONE_RENDER_PROCESS_ID = "phone_render_process_id"
    private const val PHONE_RENDER_STARTED_AT = "phone_render_started_at"
    private const val PHONE_RENDER_MODE = "mode"
    private const val PHONE_RENDER_SETTINGS = "settings"
    private const val PHONE_LAST_CRASH = "phone_last_crash"
    private const val PHONE_SMALL_COLUMNS = "phone_small_columns"
    private const val PHONE_TILE_OPACITY = "phone_tile_opacity"
    private const val PHONE_BACKGROUND_STYLE = "phone_background_style"
    private const val PHONE_RECENTLY_INSTALLED_APPS = "phone_recently_installed_apps"
    private const val HIDDEN_PACKAGES = "feature_hidden_packages"
    private const val PRIVATE_PACKAGES = "feature_private_packages"
    private const val LIVE_TILE_DISABLED_PACKAGES = "feature_live_tile_disabled_packages"
    private const val ICON_PACK_PACKAGE = "feature_icon_pack_package"
    private const val APP_ICON_STYLE = "feature_app_icon_style"
    private const val ALL_APPS_APP_ACCENT_COLORS = "feature_all_apps_app_accent_colors"
    private const val CUSTOM_ICON_PREFIX = "feature_custom_icon_uri_"
    private const val DOUBLE_TAP_ACTION = "feature_start_double_tap_action"
    private const val START_GROUP_HEADINGS = "feature_start_group_headings"
    private const val FOLDERS_JSON = "feature_folders_json"
    private const val WIDGET_STACKS_JSON = "feature_widget_stacks_json"
    private const val LOCK_SLIDESHOW_URIS = "feature_lock_slideshow_uris"
    private const val LOCK_STATUS_PACKAGES = "feature_lock_status_packages"
    private const val LOCK_DETAILED_PACKAGE = "feature_lock_detailed_package"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** One preference for Desktop, Windows Phone 8.1 and Windows 10 Mobile. */
    fun masterLaunchAnimation(context: Context): MasterLaunchAnimation {
        val stored = prefs(context).getString(
            MASTER_LAUNCH_ANIMATION, MasterLaunchAnimation.DEFAULT.name)
        return MasterLaunchAnimation.entries.firstOrNull { it.name == stored }
            ?: MasterLaunchAnimation.DEFAULT
    }

    fun setMasterLaunchAnimation(context: Context, animation: MasterLaunchAnimation) {
        prefs(context).edit { putString(MASTER_LAUNCH_ANIMATION, animation.name) }
        LauncherFeatureRuntime.notifyMasterLaunchAnimationChanged()
    }

    fun launcherUiMode(context: Context): LauncherUiMode {
        val saved = prefs(context).getString(LAUNCHER_UI_MODE, LauncherUiMode.DESKTOP.name)
        return LauncherUiMode.entries.firstOrNull { it.name == saved } ?: LauncherUiMode.DESKTOP
    }

    fun setLauncherUiMode(
        context: Context,
        mode: LauncherUiMode,
        phoneRenderAttempted: Boolean = false,
    ) {
        if (launcherUiMode(context) == mode) return
        // Persist the pending render with the mode itself. If switching to a phone
        // surface crashes before it displays, recover on the next cold launch.
        prefs(context).edit(commit = true) {
            putString(LAUNCHER_UI_MODE, mode.name)
            if (mode == LauncherUiMode.DESKTOP) {
                remove(PHONE_RENDER_PENDING)
                remove(PHONE_RENDER_ATTEMPTED)
                remove(PHONE_RENDER_PROCESS_ID)
                remove(PHONE_RENDER_STARTED_AT)
            } else {
                putString(PHONE_RENDER_PENDING, PHONE_RENDER_MODE)
                // Selection and rendering are different. A persisted selection made by
                // an instrumentation test (or just before a cold launch) is not a crash.
                putBoolean(PHONE_RENDER_ATTEMPTED, phoneRenderAttempted)
                if (phoneRenderAttempted) {
                    putInt(PHONE_RENDER_PROCESS_ID, Process.myPid())
                    putLong(PHONE_RENDER_STARTED_AT, System.currentTimeMillis())
                } else {
                    remove(PHONE_RENDER_PROCESS_ID)
                    remove(PHONE_RENDER_STARTED_AT)
                }
            }
        }
        LauncherFeatureRuntime.notifyLauncherModeChanged()
    }

    /**
     * A different process ID is NOT a crash: Android freely stops HOME processes after
     * backgrounding, swiping them away, memory pressure and package updates. Only an
     * independently confirmed fatal exit may roll a pending phone-mode launch back.
     *
     * The optional exit reason is a test seam for simulated process deaths. Production
     * callers always ask Android for the real last exit of the recorded process.
     */
    fun recoverInterruptedPhoneRender(
        context: Context,
        currentProcessId: Int = Process.myPid(),
        previousExitReasonForTest: Int? = null,
    ): Boolean {
        val preferences = prefs(context)
        if (preferences.getString(PHONE_RENDER_PENDING, null) == null) return false
        val attempted = preferences.getBoolean(PHONE_RENDER_ATTEMPTED, false)
        val renderProcessId = preferences.getInt(PHONE_RENDER_PROCESS_ID, -1)
        if (!attempted || renderProcessId <= 0 || renderProcessId == currentProcessId) return false

        val startedAt = preferences.getLong(PHONE_RENDER_STARTED_AT, 0L)
        val exitReason = previousExitReasonForTest ?: historicalExitReason(
            context, renderProcessId, startedAt)
        val verifiedCrash = exitReason == ApplicationExitInfo.REASON_CRASH ||
            exitReason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
            exitReason == ApplicationExitInfo.REASON_ANR
        val failedMode = launcherUiMode(context)
        val needsRecovery = verifiedCrash && failedMode != LauncherUiMode.DESKTOP
        // Clear a stale marker even after normal termination. Otherwise a later
        // cold start could misattribute an unrelated death to the chosen mode.
        preferences.edit(commit = true) {
            remove(PHONE_RENDER_PENDING)
            remove(PHONE_RENDER_ATTEMPTED)
            remove(PHONE_RENDER_PROCESS_ID)
            remove(PHONE_RENDER_STARTED_AT)
            if (needsRecovery) {
                putString(LAUNCHER_UI_MODE, LauncherUiMode.DESKTOP.name)
                putString(
                    PHONE_LAST_CRASH,
                    "Recovered ${failedMode.title} after Android confirmed a startup crash (exit reason $exitReason).",
                )
            }
        }
        if (needsRecovery) LauncherFeatureRuntime.notifyLauncherModeChanged()
        return needsRecovery
    }

    private fun historicalExitReason(context: Context, processId: Int, startedAt: Long): Int? {
        // Previous builds left markers without a timestamp: there is no proof an
        // old PID's exit belongs to this rendering attempt. Preserve the mode.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || startedAt <= 0L) return null
        return runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, processId, 1)
                ?.firstOrNull { info ->
                    info.pid == processId &&
                        info.timestamp >= startedAt &&
                        info.timestamp <= System.currentTimeMillis()
                }?.reason
        }.getOrNull()
    }

    fun beginPhoneRender(context: Context, processId: Int = Process.myPid()) {
        if (launcherUiMode(context) == LauncherUiMode.DESKTOP) return
        val preferences = prefs(context)
        // An Activity recreated inside the same healthy process must not force a
        // second synchronous disk commit before the first frame. A new process
        // still writes the durable crash-recovery marker, unchanged.
        if (preferences.getString(PHONE_RENDER_PENDING, null) == PHONE_RENDER_MODE &&
            preferences.getBoolean(PHONE_RENDER_ATTEMPTED, false) &&
            preferences.getInt(PHONE_RENDER_PROCESS_ID, -1) == processId
        ) return
        preferences.edit(commit = true) {
            putString(PHONE_RENDER_PENDING, PHONE_RENDER_MODE)
            putBoolean(PHONE_RENDER_ATTEMPTED, true)
            putInt(PHONE_RENDER_PROCESS_ID, processId)
            putLong(PHONE_RENDER_STARTED_AT, System.currentTimeMillis())
        }
    }

    fun beginPhoneSettings(context: Context) {
        if (launcherUiMode(context) != LauncherUiMode.DESKTOP) {
            prefs(context).edit(commit = true) {
                putString(PHONE_RENDER_PENDING, PHONE_RENDER_SETTINGS)
                putBoolean(PHONE_RENDER_ATTEMPTED, true)
                putInt(PHONE_RENDER_PROCESS_ID, Process.myPid())
                putLong(PHONE_RENDER_STARTED_AT, System.currentTimeMillis())
            }
        }
    }

    fun confirmPhoneRender(context: Context) {
        clearPendingRender(context, PHONE_RENDER_MODE)
    }

    fun confirmPhoneSettings(context: Context) {
        clearPendingRender(context, PHONE_RENDER_SETTINGS)
    }

    private fun clearPendingRender(context: Context, expected: String) {
        val preferences = prefs(context)
        if (preferences.getString(PHONE_RENDER_PENDING, null) == expected) {
            preferences.edit {
                remove(PHONE_RENDER_PENDING)
                remove(PHONE_RENDER_ATTEMPTED)
                remove(PHONE_RENDER_PROCESS_ID)
                remove(PHONE_RENDER_STARTED_AT)
            }
        }
    }

    /**
     * Called from the process exception handler, including failures after the initial
     * successful frame. Restore Desktop before Android restarts the HOME Activity.
     * Do not clear pinned tiles, mode layouts, icon caches, or user personalization.
     */
    fun recoverFatalPhoneCrash(context: Context, cause: Throwable): Boolean {
        val mode = launcherUiMode(context)
        if (mode == LauncherUiMode.DESKTOP) return false
        val summary = buildString {
            append(mode.name).append(": ").append(cause.javaClass.name)
            append(": ").append(cause.message?.take(400).orEmpty())
            cause.stackTrace.take(35).forEach { append("\n at ").append(it.toString()) }
            cause.cause?.let { nested ->
                append("\nCaused by: ").append(nested.javaClass.name)
                append(": ").append(nested.message?.take(250).orEmpty())
            }
        }.take(6000)
        prefs(context).edit(commit = true) {
            putString(PHONE_LAST_CRASH, summary)
            putString(LAUNCHER_UI_MODE, LauncherUiMode.DESKTOP.name)
            remove(PHONE_RENDER_PENDING)
            remove(PHONE_RENDER_ATTEMPTED)
            remove(PHONE_RENDER_PROCESS_ID)
            remove(PHONE_RENDER_STARTED_AT)
        }
        return true
    }

    fun lastPhoneCrash(context: Context): String? =
        prefs(context).getString(PHONE_LAST_CRASH, null)?.takeIf(String::isNotBlank)

    fun phoneSmallColumns(context: Context, mode: LauncherUiMode): Int =
        prefs(context).getInt(PHONE_SMALL_COLUMNS + mode.name, if (mode == LauncherUiMode.PHONE_8) 4 else 6)
            .let { if (it == 4) 4 else 6 }

    fun setPhoneSmallColumns(context: Context, mode: LauncherUiMode, columns: Int) {
        require(columns == 4 || columns == 6)
        prefs(context).edit { putInt(PHONE_SMALL_COLUMNS + mode.name, columns) }
        LauncherFeatureRuntime.notifyLauncherModeChanged()
    }

    fun phoneBackgroundStyle(context: Context): PhoneBackgroundStyle =
        PhoneBackgroundStyle.entries.firstOrNull {
            it.name == prefs(context).getString(PHONE_BACKGROUND_STYLE, PhoneBackgroundStyle.FULL_SCREEN.name)
        } ?: PhoneBackgroundStyle.FULL_SCREEN

    fun setPhoneBackgroundStyle(context: Context, style: PhoneBackgroundStyle) {
        prefs(context).edit { putString(PHONE_BACKGROUND_STYLE, style.name) }
        LauncherFeatureRuntime.notifyLauncherModeChanged()
    }

    /** Appearance only: three modes keep their own tile-picture choice while
     *  WP8.1 and W10M still share a single pinned-tile workspace. */
    fun backgroundStyle(context: Context, mode: LauncherUiMode): PhoneBackgroundStyle =
        if (mode == LauncherUiMode.MOBILE_10) phoneBackgroundStyle(context)
        else {
            val fallback = if (mode == LauncherUiMode.PHONE_8)
                PhoneBackgroundStyle.TILE_PICTURE else PhoneBackgroundStyle.FULL_SCREEN
            val stored = prefs(context).getString("mode_background_${mode.name}", fallback.name)
            PhoneBackgroundStyle.entries.firstOrNull { it.name == stored } ?: fallback
        }

    fun setBackgroundStyle(
        context: Context, mode: LauncherUiMode, style: PhoneBackgroundStyle,
    ) {
        if (mode == LauncherUiMode.MOBILE_10) setPhoneBackgroundStyle(context, style)
        else {
            prefs(context).edit { putString("mode_background_${mode.name}", style.name) }
            LauncherFeatureRuntime.notifyLauncherModeChanged()
        }
    }

    fun phoneTileOpacity(context: Context): Float =
        prefs(context).getInt(PHONE_TILE_OPACITY, 82).coerceIn(0, 100) / 100f

    fun setPhoneTileOpacity(context: Context, percent: Int) {
        prefs(context).edit { putInt(PHONE_TILE_OPACITY, percent.coerceIn(0, 100)) }
        LauncherFeatureRuntime.notifyLauncherModeChanged()
    }

    fun showRecentlyInstalledPhoneApps(context: Context): Boolean =
        prefs(context).getBoolean(PHONE_RECENTLY_INSTALLED_APPS, true)

    fun setShowRecentlyInstalledPhoneApps(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PHONE_RECENTLY_INSTALLED_APPS, enabled) }
        LauncherFeatureRuntime.notifyLauncherModeChanged()
    }

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
        LauncherFeatureRuntime.notifyLiveTilesChanged()
    }

    fun selectedIconPack(context: Context): String? =
        prefs(context).getString(ICON_PACK_PACKAGE, null)?.takeIf(String::isNotBlank)

    fun appIconStyle(context: Context): AppIconStyle {
        val stored = prefs(context).getString(APP_ICON_STYLE, AppIconStyle.DISCO_LAYERED.name)
        return AppIconStyle.entries.firstOrNull { it.name == stored } ?: AppIconStyle.DISCO_LAYERED
    }

    fun setAppIconStyle(context: Context, style: AppIconStyle) {
        prefs(context).edit { putString(APP_ICON_STYLE, style.name) }
        LauncherFeatureRuntime.notifyIconsChanged()
    }

    fun useAppAccentColorsInAllApps(context: Context): Boolean =
        prefs(context).getBoolean(ALL_APPS_APP_ACCENT_COLORS, false)

    fun setUseAppAccentColorsInAllApps(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(ALL_APPS_APP_ACCENT_COLORS, enabled) }
        LauncherFeatureRuntime.notifyAllAppsAppearanceChanged()
    }

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

    fun showStartGroupHeadings(context: Context): Boolean =
        prefs(context).getBoolean(START_GROUP_HEADINGS, false)

    fun setShowStartGroupHeadings(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(START_GROUP_HEADINGS, enabled) }
        LauncherFeatureRuntime.notifyStartAppearanceChanged()
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
        val distinctUris = uris.distinct()
        distinctUris.forEach { LauncherImageCache.preload(context, it, maxSide = 2560) }
        val array = JSONArray()
        distinctUris.forEach(array::put)
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
