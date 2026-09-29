package com.flivoro.tile8auncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/**
 * Android-semantic bindings for Tile8's Windows 8.1-inspired stock Start tiles.
 *
 * These are intentionally based on platform intents/categories instead of OEM package names so a
 * fresh Tile8 install can bind Mail, Calendar, Browser, Camera, etc. on Samsung, Xiaomi, Pixel and
 * other Android builds without guessing package strings.
 */
val STOCK_DEFAULT_TILE_IDS: Set<String> = linkedSetOf(
    "tile_mail",
    "tile_calendar",
    "tile_people",
    "tile_skype",
    "tile_desktop",
    "tile_weather",
    "tile_settings",
    "tile_clock",
    "tile_ie",
    "tile_video",
    "tile_music",
    "tile_games",
    "tile_camera",
    "tile_help",
    "tile_onedrive",
    "tile_photos",
    "tile_news",
    "tile_money",
    "tile_maps",
    "tile_reading_list",
    "tile_store",
)

fun isStockDefaultTileId(tileId: String): Boolean = tileId in STOCK_DEFAULT_TILE_IDS

fun defaultTileSemanticIntent(tileId: String): Intent? = when (tileId) {
    "tile_mail" -> mainCategoryIntent(Intent.CATEGORY_APP_EMAIL)
    "tile_calendar" -> mainCategoryIntent(Intent.CATEGORY_APP_CALENDAR)
    "tile_people" -> mainCategoryIntent(Intent.CATEGORY_APP_CONTACTS)
    "tile_skype" -> mainCategoryIntent(Intent.CATEGORY_APP_MESSAGING)
    "tile_desktop", "tile_onedrive" -> mainCategoryIntent("android.intent.category.APP_FILES")
    "tile_weather" -> mainCategoryIntent("android.intent.category.APP_WEATHER")
    "tile_settings" -> Intent(Settings.ACTION_SETTINGS)
    "tile_clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    "tile_ie", "tile_reading_list" ->
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))
    "tile_video" -> Intent(Intent.ACTION_VIEW).setType("video/*")
    "tile_music" -> mainCategoryIntent(Intent.CATEGORY_APP_MUSIC)
    "tile_camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    "tile_photos" -> mainCategoryIntent("android.intent.category.APP_GALLERY")
    "tile_news" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com/"))
    "tile_money" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/finance/"))
    "tile_maps" -> mainCategoryIntent(Intent.CATEGORY_APP_MAPS)
    "tile_store" -> mainCategoryIntent("android.intent.category.APP_MARKET")
    // Games and Help have no stable Android-wide app category. They intentionally fall through
    // to Tile8's user picker when no sensible installed app was found during default seeding.
    "tile_games", "tile_help" -> null
    else -> null
}

fun resolveDefaultTileApp(
    context: Context,
    tileId: String,
): AppInfo? {
    val baseIntent = defaultTileSemanticIntent(tileId) ?: return null
    val pm = context.packageManager

    val candidates = runCatching {
        pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrDefault(emptyList())
        .filter { it.activityInfo.packageName != context.packageName }

    if (candidates.isEmpty()) return null

    // Prefer the user's current Android default when one exists. Android's resolver itself is not
    // an app binding, so accept it only when it maps to one of the actual candidates.
    val resolved = runCatching {
        pm.resolveActivity(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()
    val preferred = resolved?.let { preferredInfo ->
        candidates.firstOrNull {
            it.activityInfo.packageName == preferredInfo.activityInfo.packageName &&
                it.activityInfo.name == preferredInfo.activityInfo.name
        }
    }

    val chosen = preferred ?: candidates.singleOrNull() ?: return null
    return AppInfo(
        label = chosen.loadLabel(pm).toString(),
        packageName = chosen.activityInfo.packageName,
        activityName = chosen.activityInfo.name,
        firstInstallTime = 0L,
    )
}

/**
 * Base intent for Android's activity picker. If the semantic category has no handlers on a device,
 * fall back to all launcher apps so the user can still bind that Start tile manually.
 */
fun defaultTilePickerBaseIntent(
    context: Context,
    tileId: String,
): Intent {
    val semantic = defaultTileSemanticIntent(tileId)
    if (semantic != null) {
        val handlers = runCatching {
            context.packageManager.queryIntentActivities(
                semantic,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrDefault(emptyList())
            .filter { it.activityInfo.packageName != context.packageName }
        if (handlers.isNotEmpty()) return semantic
    }

    return Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
}

private fun mainCategoryIntent(category: String): Intent =
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
