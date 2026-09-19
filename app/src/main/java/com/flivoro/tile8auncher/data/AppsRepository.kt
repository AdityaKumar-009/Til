package com.flivoro.tile8auncher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class AppsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tile8_launcher_prefs_v2", Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager

    /**
     * Package icons can be surprisingly large (adaptive icons are often 512px or more), so
     * bound this cache by its approximate ARGB byte cost instead of by item count. Access to
     * Android's LruCache is guarded because icons are read and populated from IO coroutines
     * while the launcher may read the cache on the main thread.
     */
    private val iconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL
            return bytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
    private val iconCacheLock = Any()
    private val failedIcons = ConcurrentHashMap.newKeySet<String>()
    private val iconLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightIconLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    // PackageManager and bitmap decoding are both CPU/Binder-heavy. A screenful of Compose
    // icon requests should not fan out into dozens of simultaneous decodes on low-end phones.
    private val iconDecodePermits = Semaphore(ICON_DECODE_CONCURRENCY)

    // The largest app icon currently rendered by the launcher is ~104 dp during the launch
    // overlay. Keeping a small guard above that preserves visual fidelity while preventing a
    // 512/1024 px source icon from consuming megabytes in the cache and causing GC churn.
    private val maxCachedIconPx: Int =
        (ICON_CACHE_MAX_DP * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceIn(ICON_CACHE_MIN_PX, ICON_CACHE_MAX_PX)

    @Volatile
    private var installedAppsCache: List<AppInfo>? = null
    private val installedAppsCacheLock = Any()

    @Volatile
    private var categorizedAppsCache: List<AppSection>? = null
    private val categorizedAppsCacheLock = Any()

    /**
     * Returns the launcher-visible apps from one process-local PackageManager scan.
     *
     * Previously startup could run this scan concurrently from repository initialization,
     * All Apps loading and default-tile creation. On slower devices those Binder/resource calls
     * competed with first-frame work. The immutable result is now shared for this repository.
     */
    fun getInstalledApps(): List<AppInfo> {
        installedAppsCache?.let { return it }

        return synchronized(installedAppsCacheLock) {
            installedAppsCache?.let { return@synchronized it }

            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val locale = Locale.getDefault()
            val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
            val apps = resolveInfoList.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager).toString()
                val activityName = resolveInfo.activityInfo.name

                // firstInstallTime is not consumed by the launcher UI. Avoid an additional
                // getPackageInfo() Binder call for every installed app during cold start.
                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = activityName,
                    firstInstallTime = 0L,
                )
            }.sortedBy { it.label.lowercase(locale) }

            installedAppsCache = apps
            apps
        }
    }

    fun getCategorizedApps(): List<AppSection> {
        categorizedAppsCache?.let { return it }

        return synchronized(categorizedAppsCacheLock) {
            categorizedAppsCache?.let { return@synchronized it }

            val locale = Locale.getDefault()
            val groups = getInstalledApps().groupBy { app ->
                val firstChar = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (firstChar in 'A'..'Z') firstChar.toString() else "#"
            }
            val sections = groups.map { (letter, sectionApps) ->
                AppSection(
                    letter = letter,
                    apps = sectionApps.sortedBy { it.label.lowercase(locale) },
                )
            }.sortedWith { a, b ->
                when {
                    a.letter == "#" -> 1
                    b.letter == "#" -> -1
                    else -> a.letter.compareTo(b.letter)
                }
            }

            categorizedAppsCache = sections
            sections
        }
    }

    /** Returns an icon only when it is already in memory; this never touches PackageManager. */
    fun getCachedAppIcon(packageName: String): ImageBitmap? {
        synchronized(iconCacheLock) {
            return iconCache.get(packageName)
        }
    }

    /**
     * Loads and caches an icon without blocking the caller's thread. Concurrent requests for
     * the same package share one decode, while total decode concurrency is deliberately bounded
     * to protect animation frames from CPU, Binder and GC bursts on slower devices.
     */
    suspend fun loadAppIcon(packageName: String): ImageBitmap? {
        getCachedAppIcon(packageName)?.let { return it }
        if (failedIcons.contains(packageName)) return null

        val candidate = iconLoadScope.async(start = CoroutineStart.LAZY) {
            try {
                iconDecodePermits.withPermit {
                    decodeAndCacheAppIcon(packageName)
                }
            } finally {
                inFlightIconLoads.remove(packageName)
            }
        }
        val active = inFlightIconLoads.putIfAbsent(packageName, candidate)
        if (active == null) {
            candidate.start()
        } else {
            candidate.cancel()
        }
        return (active ?: candidate).await()
    }

    /**
     * Synchronous callers (notably a tile/app tap starting the launch overlay) must never perform
     * PackageManager or bitmap work on the main thread. A cache miss simply uses the existing
     * launcher fallback icon while the normal composable loader fills the cache asynchronously.
     */
    fun getAppIcon(packageName: String): ImageBitmap? = getCachedAppIcon(packageName)

    private fun decodeAndCacheAppIcon(packageName: String): ImageBitmap? {
        getCachedAppIcon(packageName)?.let { return it }
        if (failedIcons.contains(packageName)) return null

        val bitmap = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable).asImageBitmap()
        } catch (e: Exception) {
            failedIcons.add(packageName)
            null
        }
        if (bitmap != null) {
            synchronized(iconCacheLock) {
                iconCache.put(packageName, bitmap)
            }
        } else {
            failedIcons.add(packageName)
        }
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return downsampleBitmapIfNeeded(drawable.bitmap)
        }

        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxCachedIconPx
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxCachedIconPx
        val longestSide = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
        val scale = minOf(1f, maxCachedIconPx.toFloat() / longestSide.toFloat())
        val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun downsampleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= maxCachedIconPx) return bitmap

        val scale = maxCachedIconPx.toFloat() / longestSide.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    fun findAppForKeywords(installed: List<AppInfo>, vararg keywords: String): AppInfo? {
        for (keyword in keywords) {
            val lower = keyword.lowercase(Locale.getDefault())
            val match = installed.firstOrNull {
                it.packageName.lowercase().contains(lower) || it.label.lowercase().contains(lower)
            }
            if (match != null) return match
        }
        return null
    }

    private fun upgradeLegacyStockStartIfNeeded(tiles: List<TileModel>): List<TileModel> {
        val generation = prefs.getInt(DEFAULT_LAYOUT_GENERATION_KEY, 0)
        if (generation >= DEFAULT_LAYOUT_GENERATION) return tiles

        val ids = tiles.mapTo(linkedSetOf()) { it.id }
        val isLegacyStockSet = ids == LEGACY_DEFAULT_TILE_IDS

        val result = if (isLegacyStockSet) {
            // One-time migration for the old 18-tile demo set. The exact stock ID set is narrow
            // enough to avoid touching layouts where the user pinned or unpinned anything, while
            // still upgrading testers who tried rearranging those stock tiles on earlier builds.
            getDefaultTiles()
        } else {
            tiles
        }

        prefs.edit { putInt(DEFAULT_LAYOUT_GENERATION_KEY, DEFAULT_LAYOUT_GENERATION) }
        if (result !== tiles) savePinnedTiles(result)
        return result
    }

    fun loadPinnedTiles(): List<TileModel> {
        val savedJson = prefs.getString("pinned_tiles_json", null)
        if (!savedJson.isNullOrEmpty()) {
            try {
                val list = mutableListOf<TileModel>()
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    var colorVal = obj.optLong("colorValue", 0xFF0078D7L)
                    // Ensure alpha is 100% opaque
                    if ((colorVal and 0xFF000000L) == 0L || colorVal == 0L) {
                        colorVal = (colorVal and 0x00FFFFFFL) or 0xFF000000L
                        if (colorVal == 0xFF000000L) colorVal = 0xFF0078D7L
                    }

                    list.add(
                        TileModel(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
                            activityName = obj.optString("activityName").takeIf { it.isNotEmpty() },
                            size = TileSize.valueOf(obj.optString("size", TileSize.MEDIUM.name)),
                            colorValue = colorVal,
                            tileType = TileType.valueOf(obj.optString("tileType", TileType.APP.name)),
                            iconGlyph = obj.optString("iconGlyph", ""),
                            groupId = obj.optString("groupId", "").takeIf { it.isNotBlank() }
                                ?: "legacy:${obj.optString("groupName", "Start").trim().ifEmpty { "Start" }}",
                            groupName = obj.optString("groupName", "Start"),
                            order = obj.optInt("order", i),
                            startBand = obj.optInt("startBand", -1).takeIf { it >= 0 },
                            startColumn = obj.optInt("startColumn", -1).takeIf { it >= 0 },
                            startRow = obj.optInt("startRow", -1).takeIf { it >= 0 },
                        )
                    )
                }
                if (list.isNotEmpty()) return upgradeLegacyStockStartIfNeeded(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultTiles = getDefaultTiles()
        savePinnedTiles(defaultTiles)
        prefs.edit { putInt(DEFAULT_LAYOUT_GENERATION_KEY, DEFAULT_LAYOUT_GENERATION) }
        return defaultTiles
    }

    fun savePinnedTiles(tiles: List<TileModel>) {
        val array = JSONArray()
        tiles.forEachIndexed { index, tile ->
            val obj = JSONObject().apply {
                put("id", tile.id)
                put("title", tile.title)
                put("packageName", tile.packageName ?: "")
                put("activityName", tile.activityName ?: "")
                put("size", tile.size.name)
                put("colorValue", tile.colorValue)
                put("tileType", tile.tileType.name)
                put("iconGlyph", tile.iconGlyph)
                put(
                    "groupId",
                    tile.groupId.takeIf(String::isNotBlank)
                        ?: "legacy:${tile.groupName.trim().ifEmpty { "Start" }}",
                )
                put("groupName", tile.groupName)
                put("order", index)
                tile.startBand?.let { put("startBand", it) }
                tile.startColumn?.let { put("startColumn", it) }
                tile.startRow?.let { put("startRow", it) }
            }
            array.put(obj)
        }
        prefs.edit { putString("pinned_tiles_json", array.toString()) }
    }

    fun getDefaultTiles(): List<TileModel> {
        val installed = getInstalledApps()

        val mailApp = findAppForKeywords(installed, "mail", "gmail", "outlook")
        val calendarApp = findAppForKeywords(installed, "calendar")
        val peopleApp = findAppForKeywords(installed, "contact", "people", "dialer")
        val chatApp = findAppForKeywords(installed, "skype", "whatsapp", "telegram", "message")
        val weatherApp = findAppForKeywords(installed, "weather")
        val browserApp = findAppForKeywords(installed, "chrome", "browser", "firefox", "edge")
        val storeApp = findAppForKeywords(installed, "vending", "store", "play")
        val photosApp = findAppForKeywords(installed, "gallery", "photos", "photo")
        val settingsApp = findAppForKeywords(installed, "settings")
        val cameraApp = findAppForKeywords(installed, "camera")
        val clockApp = findAppForKeywords(installed, "clock", "deskclock")
        val driveApp = findAppForKeywords(installed, "onedrive", "drive")
        val mapsApp = findAppForKeywords(installed, "maps", "map")
        val musicApp = findAppForKeywords(installed, "music", "spotify", "audio")
        val videoApp = findAppForKeywords(installed, "youtube", "video", "movies")
        val gamesApp = findAppForKeywords(installed, "play games", "games", "gaming")
        val newsApp = findAppForKeywords(installed, "news")

        fun tile(
            id: String,
            title: String,
            groupId: String,
            size: TileSize,
            color: Long,
            type: TileType = TileType.APP,
            glyph: String = "app",
            app: AppInfo? = null,
        ) = TileModel(
            id = id,
            title = title,
            packageName = app?.packageName,
            activityName = app?.activityName,
            size = size,
            colorValue = color,
            tileType = type,
            iconGlyph = glyph,
            groupId = groupId,
            // Microsoft's own 8.1 product-guide Start example uses separated unnamed clusters.
            // Keep categories as stable identities while leaving the labels blank by default.
            groupName = "",
        )

        val connect = "default:connect"
        val windows = "default:windows"
        val explore = "default:explore"

        return listOf(
            // Cluster 1 — the at-a-glance block from the period Windows 8.1 Start layout.
            // It intentionally spans more than one four-cell band, with ordinary 8dp tile spacing
            // inside the cluster and no artificial empty category row.
            tile(
                "tile_mail", "Mail", connect, TileSize.WIDE, WindowsColors.MailBlue,
                TileType.MAIL, "mail", mailApp,
            ),
            tile(
                "tile_calendar", "Calendar", connect, TileSize.WIDE, WindowsColors.CalendarPurple,
                TileType.CALENDAR, "calendar", calendarApp,
            ),
            tile(
                "tile_people", "People", connect, TileSize.MEDIUM, WindowsColors.PeopleOrange,
                TileType.APP, "people", peopleApp,
            ),
            tile(
                "tile_skype", "Skype", connect, TileSize.MEDIUM, WindowsColors.SkypeCyan,
                TileType.APP, "skype", chatApp,
            ),
            tile(
                "tile_desktop", "Desktop", connect, TileSize.MEDIUM, WindowsColors.DesktopBlue,
                TileType.DESKTOP, "desktop",
            ),
            tile(
                "tile_weather", "Weather", connect, TileSize.WIDE, WindowsColors.WeatherCyan,
                TileType.WEATHER, "weather", weatherApp,
            ),
            tile(
                "tile_settings", "PC settings", connect, TileSize.SMALL, WindowsColors.SettingsPurple,
                TileType.SETTINGS, "settings", settingsApp,
            ),
            tile(
                "tile_clock", "Alarms & Clock", connect, TileSize.SMALL, WindowsColors.SportsPurple,
                TileType.CLOCK, "clock", clockApp,
            ),

            // Cluster 2 — browser/cloud/photos plus the four small media tiles. This follows the
            // visual rhythm of Microsoft's 8.1 Product Guide screenshot, where IE and four small
            // media tiles sit beside Help+Tips/OneDrive/Photos.
            tile(
                "tile_ie", "Internet Explorer", windows, TileSize.MEDIUM,
                WindowsColors.InternetExplorerBlue, TileType.INTERNET_EXPLORER, "ie", browserApp,
            ),
            tile(
                "tile_video", "Video", windows, TileSize.SMALL, WindowsColors.NewsRed,
                TileType.APP, "video", videoApp,
            ),
            tile(
                "tile_music", "Music", windows, TileSize.SMALL, WindowsColors.MusicOrange,
                TileType.APP, "music", musicApp,
            ),
            tile(
                "tile_games", "Games", windows, TileSize.SMALL, WindowsColors.StoreGreen,
                TileType.APP, "games", gamesApp,
            ),
            tile(
                "tile_camera", "Camera", windows, TileSize.SMALL, WindowsColors.CameraPink,
                TileType.APP, "camera", cameraApp,
            ),
            tile(
                "tile_help", "Help+Tips", windows, TileSize.MEDIUM, WindowsColors.HelpOrange,
                TileType.APP, "help",
            ),
            tile(
                "tile_onedrive", "OneDrive", windows, TileSize.MEDIUM, WindowsColors.InternetExplorerBlue,
                TileType.APP, "cloud", driveApp,
            ),
            tile(
                "tile_photos", "Photos", windows, TileSize.WIDE, WindowsColors.Teal,
                TileType.PHOTOS, "photos", photosApp,
            ),

            // Cluster 3 — information/discovery. At the common six-row phone layout this fills a
            // single band rather than leaving a mostly empty continuation column.
            tile(
                "tile_news", "News", explore, TileSize.WIDE, WindowsColors.NewsRed,
                TileType.APP, "news", newsApp,
            ),
            tile(
                "tile_money", "Money", explore, TileSize.MEDIUM, WindowsColors.MoneyGreen,
                TileType.MONEY, "money",
            ),
            tile(
                "tile_maps", "Maps", explore, TileSize.MEDIUM, WindowsColors.Purple,
                TileType.APP, "maps", mapsApp,
            ),
            tile(
                "tile_reading_list", "Reading List", explore, TileSize.MEDIUM,
                WindowsColors.ReadingListCrimson, TileType.READING_LIST, "reading_list",
            ),
            tile(
                "tile_store", "Store", explore, TileSize.MEDIUM, WindowsColors.StoreGreen,
                TileType.STORE, "store", storeApp,
            ),
        ).mapIndexed { index, tile -> tile.copy(order = index) }
    }

    fun getFlipAnimationMode(): FlipAnimationMode {
        val modeStr = prefs.getString("flip_animation_mode", FlipAnimationMode.CLASSIC.name)
        return try {
            FlipAnimationMode.valueOf(modeStr ?: FlipAnimationMode.CLASSIC.name)
        } catch (e: Exception) {
            FlipAnimationMode.CLASSIC
        }
    }

    fun setFlipAnimationMode(mode: FlipAnimationMode) {
        prefs.edit { putString("flip_animation_mode", mode.name) }
    }

    fun getWallpaperStyle(): Int = prefs.getInt("wallpaper_style", 0).coerceIn(0, 9)

    fun setWallpaperStyle(style: Int) {
        prefs.edit().putInt("wallpaper_style", style.coerceIn(0, 9)).apply()
    }

    fun getWallpaperParallaxEnabled(): Boolean =
        prefs.getBoolean(WALLPAPER_PARALLAX_ENABLED, true)

    fun setWallpaperParallaxEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(WALLPAPER_PARALLAX_ENABLED, enabled) }
    }

    fun getStartWallpaperScrollPx(): Float =
        prefs.getFloat(START_WALLPAPER_SCROLL_PX, 0f)
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f

    fun getStartScrollIndex(): Int =
        prefs.getInt(START_SCROLL_INDEX, 0).coerceAtLeast(0)

    fun getStartScrollOffsetPx(): Int =
        prefs.getInt(START_SCROLL_OFFSET_PX, 0).coerceAtLeast(0)

    fun setStartViewState(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        wallpaperScrollPx: Float,
    ) {
        val safeWallpaper =
            wallpaperScrollPx.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        prefs.edit {
            putInt(START_SCROLL_INDEX, firstVisibleItemIndex.coerceAtLeast(0))
            putInt(START_SCROLL_OFFSET_PX, firstVisibleItemScrollOffset.coerceAtLeast(0))
            putFloat(START_WALLPAPER_SCROLL_PX, safeWallpaper)
        }
    }

    fun getLaunchTiming(allApps: Boolean = false): LaunchTiming {
        fun key(name: String) = if (allApps) "all_apps_$name" else name
        val defaults = LaunchTiming()
        val curveName = readPreference(defaults.curve.name) {
            prefs.getString(key(LAUNCH_TIMING_CURVE), defaults.curve.name) ?: defaults.curve.name
        }
        val curve = TimeCurve.values().firstOrNull { it.name == curveName } ?: defaults.curve

        return LaunchTiming(
            durationMillis = readPreference(defaults.durationMillis) {
                prefs.getInt(key(LAUNCH_TIMING_DURATION), defaults.durationMillis)
            },
            curve = curve,
            customX1 = readPreference(defaults.customX1) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_X1), defaults.customX1)
            },
            customY1 = readPreference(defaults.customY1) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_Y1), defaults.customY1)
            },
            customX2 = readPreference(defaults.customX2) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_X2), defaults.customX2)
            },
            customY2 = readPreference(defaults.customY2) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_Y2), defaults.customY2)
            },
            strength = readPreference(defaults.strength) {
                prefs.getFloat(key(LAUNCH_TIMING_STRENGTH), defaults.strength)
            },
            steps = readPreference(defaults.steps) {
                prefs.getInt(key(LAUNCH_TIMING_STEPS), defaults.steps)
            },
        ).sanitized()
    }

    fun setLaunchTiming(timing: LaunchTiming, allApps: Boolean = false) {
        fun key(name: String) = if (allApps) "all_apps_$name" else name
        val safeTiming = timing.sanitized()
        prefs.edit {
            putInt(key(LAUNCH_TIMING_DURATION), safeTiming.durationMillis)
            putString(key(LAUNCH_TIMING_CURVE), safeTiming.curve.name)
            putFloat(key(LAUNCH_TIMING_CUSTOM_X1), safeTiming.customX1)
            putFloat(key(LAUNCH_TIMING_CUSTOM_Y1), safeTiming.customY1)
            putFloat(key(LAUNCH_TIMING_CUSTOM_X2), safeTiming.customX2)
            putFloat(key(LAUNCH_TIMING_CUSTOM_Y2), safeTiming.customY2)
            putFloat(key(LAUNCH_TIMING_STRENGTH), safeTiming.strength)
            putInt(key(LAUNCH_TIMING_STEPS), safeTiming.steps)
        }
    }

    private fun <T> readPreference(default: T, read: () -> T): T = try {
        read()
    } catch (_: Exception) {
        default
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4L
        const val ICON_CACHE_MAX_BYTES = 8 * 1024 * 1024
        const val ICON_DECODE_CONCURRENCY = 2
        const val ICON_CACHE_MAX_DP = 112f
        const val ICON_CACHE_MIN_PX = 96
        const val ICON_CACHE_MAX_PX = 384
        const val LAUNCH_TIMING_DURATION = "launch_timing_duration_millis"
        const val LAUNCH_TIMING_CURVE = "launch_timing_curve"
        const val LAUNCH_TIMING_CUSTOM_X1 = "launch_timing_custom_x1"
        const val LAUNCH_TIMING_CUSTOM_Y1 = "launch_timing_custom_y1"
        const val LAUNCH_TIMING_CUSTOM_X2 = "launch_timing_custom_x2"
        const val LAUNCH_TIMING_CUSTOM_Y2 = "launch_timing_custom_y2"
        const val LAUNCH_TIMING_STRENGTH = "launch_timing_strength"
        const val LAUNCH_TIMING_STEPS = "launch_timing_steps"
        const val WALLPAPER_PARALLAX_ENABLED = "wallpaper_parallax_enabled"
        const val START_WALLPAPER_SCROLL_PX = "start_wallpaper_scroll_px"
        const val START_SCROLL_INDEX = "start_scroll_index"
        const val START_SCROLL_OFFSET_PX = "start_scroll_offset_px"
        const val DEFAULT_LAYOUT_GENERATION_KEY = "default_start_layout_generation"
        const val DEFAULT_LAYOUT_GENERATION = 2

        val LEGACY_DEFAULT_TILE_IDS = linkedSetOf(
            "tile_mail",
            "tile_weather",
            "tile_store",
            "tile_clock",
            "tile_calendar",
            "tile_people",
            "tile_skype",
            "tile_ie",
            "tile_music",
            "tile_camera",
            "tile_settings",
            "tile_desktop",
            "tile_help",
            "tile_reading_list",
            "tile_maps",
            "tile_photos",
            "tile_money",
            "tile_news",
        )
    }
}
