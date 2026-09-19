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
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultTiles = getDefaultTiles()
        savePinnedTiles(defaultTiles)
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
        val browserApp = findAppForKeywords(installed, "chrome", "browser", "firefox", "edge")
        val storeApp = findAppForKeywords(installed, "vending", "store", "play")
        val photosApp = findAppForKeywords(installed, "gallery", "photos", "photo")
        val calendarApp = findAppForKeywords(installed, "calendar")
        val clockApp = findAppForKeywords(installed, "clock", "deskclock")
        val settingsApp = findAppForKeywords(installed, "settings")
        val cameraApp = findAppForKeywords(installed, "camera")
        val musicApp = findAppForKeywords(installed, "music", "spotify", "audio")
        val mapsApp = findAppForKeywords(installed, "maps", "map")
        val weatherApp = findAppForKeywords(installed, "weather")

        return listOf(
            // Row 1 - Mail (Wide, Blue), Weather (Wide, Cyan), Store (Large, Green)
            TileModel(
                id = "tile_mail",
                title = "Mail",
                packageName = mailApp?.packageName,
                activityName = mailApp?.activityName,
                size = TileSize.WIDE,
                colorValue = WindowsColors.MailBlue,
                tileType = TileType.MAIL,
                iconGlyph = "mail",
                order = 0
            ),
            TileModel(
                id = "tile_weather",
                title = "Weather",
                packageName = weatherApp?.packageName,
                activityName = weatherApp?.activityName,
                size = TileSize.WIDE,
                colorValue = WindowsColors.WeatherCyan,
                tileType = TileType.WEATHER,
                iconGlyph = "weather",
                order = 1
            ),
            TileModel(
                id = "tile_store",
                title = "Store",
                packageName = storeApp?.packageName,
                activityName = storeApp?.activityName,
                size = TileSize.LARGE,
                colorValue = WindowsColors.StoreGreen,
                tileType = TileType.STORE,
                iconGlyph = "store",
                order = 2
            ),

            // Row 2 - Clock & Calendar
            TileModel(
                id = "tile_clock",
                title = "Alarms & Clock",
                packageName = clockApp?.packageName,
                activityName = clockApp?.activityName,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.SportsPurple,
                tileType = TileType.CLOCK,
                iconGlyph = "clock",
                order = 3
            ),
            TileModel(
                id = "tile_calendar",
                title = "Calendar",
                packageName = calendarApp?.packageName,
                activityName = calendarApp?.activityName,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.CalendarPurple,
                tileType = TileType.CALENDAR,
                iconGlyph = "calendar",
                order = 4
            ),

            // Mini / Small Tiles (People, Skype, IE, Music, Camera, Settings)
            TileModel(
                id = "tile_people",
                title = "People",
                packageName = findAppForKeywords(installed, "contact", "people", "dialer")?.packageName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.PeopleOrange,
                tileType = TileType.APP,
                iconGlyph = "people",
                order = 5
            ),
            TileModel(
                id = "tile_skype",
                title = "Skype",
                packageName = findAppForKeywords(installed, "skype", "whatsapp", "telegram", "message")?.packageName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.SkypeCyan,
                tileType = TileType.APP,
                iconGlyph = "skype",
                order = 6
            ),
            TileModel(
                id = "tile_ie",
                title = "Internet Explorer",
                packageName = browserApp?.packageName,
                activityName = browserApp?.activityName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.InternetExplorerBlue,
                tileType = TileType.INTERNET_EXPLORER,
                iconGlyph = "ie",
                order = 7
            ),
            TileModel(
                id = "tile_music",
                title = "Music",
                packageName = musicApp?.packageName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.MusicOrange,
                tileType = TileType.APP,
                iconGlyph = "music",
                order = 8
            ),
            TileModel(
                id = "tile_camera",
                title = "Camera",
                packageName = cameraApp?.packageName,
                activityName = cameraApp?.activityName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.CameraPink,
                tileType = TileType.APP,
                iconGlyph = "camera",
                order = 9
            ),
            TileModel(
                id = "tile_settings",
                title = "PC settings",
                packageName = settingsApp?.packageName,
                activityName = settingsApp?.activityName,
                size = TileSize.SMALL,
                colorValue = WindowsColors.SettingsPurple,
                tileType = TileType.SETTINGS,
                iconGlyph = "settings",
                order = 10
            ),

            // Row 3 - Desktop, Help+Tips, Reading List, Maps, Photos, Money, News
            TileModel(
                id = "tile_desktop",
                title = "Desktop",
                packageName = null,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.DesktopBlue,
                tileType = TileType.DESKTOP,
                iconGlyph = "desktop",
                order = 11
            ),
            TileModel(
                id = "tile_help",
                title = "Help+Tips",
                packageName = null,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.HelpOrange,
                tileType = TileType.APP,
                iconGlyph = "help",
                order = 12
            ),
            TileModel(
                id = "tile_reading_list",
                title = "Reading List",
                packageName = null,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.ReadingListCrimson,
                tileType = TileType.READING_LIST,
                iconGlyph = "reading_list",
                order = 13
            ),
            TileModel(
                id = "tile_maps",
                title = "Maps",
                packageName = mapsApp?.packageName,
                activityName = mapsApp?.activityName,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.Purple,
                tileType = TileType.APP,
                iconGlyph = "maps",
                order = 14
            ),
            TileModel(
                id = "tile_photos",
                title = "Photos",
                packageName = photosApp?.packageName,
                activityName = photosApp?.activityName,
                size = TileSize.MEDIUM,
                colorValue = WindowsColors.Teal,
                tileType = TileType.PHOTOS,
                iconGlyph = "photos",
                order = 15
            ),
            TileModel(
                id = "tile_money",
                title = "Money",
                packageName = null,
                size = TileSize.WIDE,
                colorValue = WindowsColors.MoneyGreen,
                tileType = TileType.MONEY,
                iconGlyph = "money",
                order = 16
            ),
            TileModel(
                id = "tile_news",
                title = "News",
                packageName = null,
                size = TileSize.WIDE,
                colorValue = WindowsColors.NewsRed,
                tileType = TileType.APP,
                iconGlyph = "news",
                order = 17
            )
        )
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
    }
}
