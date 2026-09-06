package com.flivoro.tile8auncher.data

enum class TileSize {
    SMALL,   // 1x1 unit
    MEDIUM,  // 2x2 unit (standard square)
    WIDE,    // 4x2 unit (standard wide rectangle)
    LARGE    // 4x4 unit (large square)
}

enum class TileType {
    APP,
    CLOCK,
    BATTERY,
    WEATHER,
    CALENDAR,
    PHOTOS,
    STORE,
    DESKTOP,
    READING_LIST,
    SETTINGS,
    INTERNET_EXPLORER,
    MAIL,
    MONEY
}

data class TileModel(
    val id: String,
    val title: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val size: TileSize = TileSize.MEDIUM,
    val colorValue: Long = 0xFF0078D7,
    val tileType: TileType = TileType.APP,
    val iconGlyph: String = "",
    val groupName: String = "Start",
    val order: Int = 0,
)

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val firstInstallTime: Long = 0L,
)

data class AppSection(
    val letter: String,
    val apps: List<AppInfo>,
)
