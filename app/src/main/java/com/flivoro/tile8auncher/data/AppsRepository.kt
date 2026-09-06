package com.flivoro.tile8auncher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AppsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tile8_launcher_prefs_v2", Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager

    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        val apps = resolveInfoList.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null

            val label = resolveInfo.loadLabel(packageManager).toString()
            val activityName = resolveInfo.activityInfo.name
            val installTime = try {
                packageManager.getPackageInfo(pkg, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
            AppInfo(label = label, packageName = pkg, activityName = activityName, firstInstallTime = installTime)
        }
        return apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun getCategorizedApps(): List<AppSection> {
        val apps = getInstalledApps()
        val groups = apps.groupBy { app ->
            val firstChar = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar in 'A'..'Z') firstChar.toString() else "#"
        }
        return groups.map { (letter, sectionApps) ->
            AppSection(
                letter = letter,
                apps = sectionApps.sortedBy { it.label.lowercase(Locale.getDefault()) }
            )
        }.sortedWith { a, b ->
            when {
                a.letter == "#" -> 1
                b.letter == "#" -> -1
                else -> a.letter.compareTo(b.letter)
            }
        }
    }

    fun getAppIcon(packageName: String): ImageBitmap? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        val bitmap = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable).asImageBitmap()
        } catch (e: Exception) {
            null
        }
        iconCache[packageName] = bitmap
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
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
                            groupName = obj.optString("groupName", "Start"),
                            order = obj.optInt("order", i)
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
                put("groupName", tile.groupName)
                put("order", index)
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
}
