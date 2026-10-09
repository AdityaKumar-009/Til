package com.flivoro.tile8auncher.ui.components

import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LiveTileNotification
import com.flivoro.tile8auncher.features.LiveTileNotificationStore
import com.flivoro.tile8auncher.ui.animation.metroTilePress
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WindowsTileView(
    tile: TileModel,
    appIcon: ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: () -> Unit,
    dragEnabled: Boolean = false,
    onDragStart: (bounds: Rect) -> Unit = {},
    onDrag: (delta: Offset) -> Unit = {},
    onDragLayoutShift: (delta: Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = onDragEnd,
) {
    WindowsTileFace(
        tile = tile,
        appIcon = appIcon,
        modifier = modifier
            .metroTilePress(
                tileSize = tile.size,
                onClick = onClick,
                onLongClick = onLongClick,
                dragEnabled = dragEnabled,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onLayoutShift = onDragLayoutShift,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            ),
    )
}

@Composable
internal fun WindowsTileFace(
    tile: TileModel,
    appIcon: ImageBitmap?,
    modifier: Modifier = Modifier,
    logoModifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    backgroundAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val tileColor = tile.colorValue.toTileColor()
    val liveTilesRevision = LauncherFeatureRuntime.liveTilesRevision
    val liveTileEnabled = remember(tile.packageName, liveTilesRevision) {
        LauncherFeatureStore.isLiveTileEnabled(context, tile.packageName)
    }
    val liveQueue = if (liveTileEnabled) {
        LiveTileNotificationStore.notifications(context, tile.packageName)
    } else {
        emptyList()
    }

    Box(
        modifier = modifier.background(tileColor.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
    ) {
        // Keep the tile's colored face on its outer layer. Windows Phone 8.1's
        // home-return transition turns the inner icon/text layer independently.
        Box(Modifier.fillMaxSize().then(innerModifier)) {
        if (tile.size == TileSize.SMALL) {
            // Compact Windows-style templates keep glanceable built-in information usable even
            // at 1x1. App notification tiles still stay logo-first and use a corner count.
            SmallTileContent(
                tile = tile,
                appIcon = appIcon,
                logoModifier = logoModifier,
            )
            liveQueue.firstOrNull()?.count?.takeIf { it > 0 }?.let { count ->
                LiveTileBadge(
                    count = count,
                    color = readableTileLabelColor(tileColor),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                )
            }
        } else {
            when {
            liveQueue.isNotEmpty() && tile.size == TileSize.LARGE -> {
                // Windows 8.1 had large list templates that showed several updates at once. This
                // is intentionally stable instead of constantly rotating an already-dense tile.
                LargeLiveNotificationTileContent(
                    tile = tile,
                    live = liveQueue.take(3),
                    appIcon = appIcon,
                )
            }

            liveQueue.isNotEmpty() -> {
                LiveNotificationCarousel(
                    tile = tile,
                    live = liveQueue,
                    appIcon = appIcon,
                )
            }

            else -> {
                DefaultTileContent(
                    tile = tile,
                    appIcon = appIcon,
                    logoModifier = logoModifier,
                )
            }
            }
        }
        }
    }
}

@Composable
private fun DefaultTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
    logoModifier: Modifier,
) {
    if (tile.size == TileSize.SMALL) {
        SmallTileContent(
            tile = tile,
            appIcon = appIcon,
            logoModifier = logoModifier,
        )
        return
    }

    val contentColor = readableTileLabelColor(tile.colorValue.toTileColor())
    when (tile.tileType) {
        TileType.DESKTOP -> DesktopTileContent(title = tile.title)
        TileType.CLOCK -> ClockTileContent(
            title = tile.title,
            isWide = tile.size == TileSize.WIDE,
            contentColor = contentColor,
        )
        TileType.WEATHER -> WeatherTileContent(
            title = tile.title,
            isWide = tile.size == TileSize.WIDE,
            logoModifier = logoModifier,
            contentColor = contentColor,
        )
        TileType.CALENDAR -> CalendarTileContent(title = tile.title, contentColor = contentColor)
        TileType.MONEY -> MoneyTileContent(
            title = tile.title,
            isWide = tile.size == TileSize.WIDE,
            logoModifier = logoModifier,
            contentColor = contentColor,
        )
        else -> StaticAppTileContent(tile = tile, appIcon = appIcon, logoModifier = logoModifier)
    }
}

@Composable
private fun SmallTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
    logoModifier: Modifier,
) {
    val context = LocalContext.current
    val contentColor = readableTileLabelColor(tile.colorValue.toTileColor())
    val iconsRevision = LauncherFeatureRuntime.iconsRevision
    val iconStyle = remember(iconsRevision) { LauncherFeatureStore.appIconStyle(context) }
    val tintMonochrome = iconStyle == com.flivoro.tile8auncher.features.AppIconStyle.WHITE_MONOCHROME

    when (tile.tileType) {
        TileType.CLOCK -> CompactClockTileContent(contentColor)
        TileType.WEATHER -> CompactWeatherTileContent(contentColor, logoModifier)
        TileType.CALENDAR -> CompactCalendarTileContent(contentColor)
        TileType.MONEY -> CompactMoneyTileContent(contentColor)
        TileType.BATTERY -> CompactBatteryTileContent(contentColor, logoModifier)
        else -> {
            val builtInGlyph = tile.iconGlyph.takeIf { it.isNotBlank() && it != "app" } ?: when (tile.tileType) {
                TileType.PHOTOS -> "photos"
                TileType.STORE -> "store"
                TileType.DESKTOP -> "desktop"
                TileType.READING_LIST -> "reading"
                TileType.SETTINGS -> "settings"
                TileType.INTERNET_EXPLORER -> "ie"
                TileType.MAIL -> "mail"
                else -> null
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    builtInGlyph != null -> MetroIcon(
                        glyph = builtInGlyph,
                        color = contentColor,
                        size = 28.dp,
                        modifier = logoModifier,
                    )
                    appIcon != null -> Image(
                        bitmap = appIcon,
                        contentDescription = tile.title,
                        colorFilter = if (tintMonochrome) ColorFilter.tint(contentColor) else null,
                        modifier = logoModifier.size(30.dp),
                    )
                    else -> MetroIcon(
                        glyph = "app",
                        color = contentColor,
                        size = 28.dp,
                        modifier = logoModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactClockTileContent(contentColor: Color) {
    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var timeText by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = format.format(Date())
            delay(60_000L - System.currentTimeMillis().mod(60_000L))
        }
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
        Text(
            timeText,
            color = contentColor,
            style = WindowsTypography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CompactWeatherTileContent(contentColor: Color, logoModifier: Modifier) {
    Column(
        modifier = Modifier.fillMaxSize().padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MetroIcon(glyph = "weather", color = contentColor, size = 20.dp, modifier = logoModifier)
        Text(
            "24°",
            color = contentColor,
            style = WindowsTypography.titleMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactCalendarTileContent(contentColor: Color) {
    val dayNum = remember { SimpleDateFormat("d", Locale.getDefault()).format(Date()) }
    val dayName = remember { SimpleDateFormat("EEE", Locale.getDefault()).format(Date()) }
    Column(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dayNum,
            color = contentColor,
            style = WindowsTypography.displayLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Light),
            maxLines = 1,
        )
        Text(
            dayName,
            color = contentColor.copy(alpha = .88f),
            style = WindowsTypography.labelSmall.copy(fontSize = 8.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactMoneyTileContent(contentColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "NASDAQ",
            color = contentColor,
            style = WindowsTypography.labelSmall.copy(fontSize = 7.5.sp),
            maxLines = 1,
        )
        Text(
            "▲ 0.92%",
            color = contentColor,
            style = WindowsTypography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactBatteryTileContent(contentColor: Color, logoModifier: Modifier) {
    val context = LocalContext.current
    val battery = remember(context) { context.getSystemService(BatteryManager::class.java) }
    var percentage by remember {
        mutableIntStateOf(battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
    }
    LaunchedEffect(battery) {
        while (true) {
            percentage = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            delay(60_000L)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MetroIcon(glyph = "battery", color = contentColor, size = 19.dp, modifier = logoModifier)
        if (percentage in 0..100) {
            Text(
                "$percentage%",
                color = contentColor,
                style = WindowsTypography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Windows 8/8.1 peek templates move tile content vertically, and a notification queue can cycle up
 * to five updates. Unlike the old Tile8 implementation, an active update does not disappear back
 * to the default icon every few seconds: Windows keeps the updated tile content until it is
 * replaced, expires, is cleared, or the user turns the live tile off.
 */
@Composable
private fun LiveNotificationCarousel(
    tile: TileModel,
    live: List<LiveTileNotification>,
    appIcon: ImageBitmap?,
) {
    var index by remember(tile.id) { mutableIntStateOf(0) }
    val queueIdentity = live.map { "${it.key}:${it.postTime}" }

    LaunchedEffect(tile.id, queueIdentity) {
        index = 0
        if (live.size <= 1) return@LaunchedEffect

        // Windows owns the live-tile schedule; apps do not all flip on one shared metronome.
        // Stagger each Android projection deterministically so a populated Start screen does not
        // animate every live tile at the same instant.
        val phase = kotlin.math.abs(tile.id.hashCode().toLong())
        delay(1_200L + phase % 3_600L)
        val cycleMillis = 7_000L + phase % 2_500L
        while (true) {
            index = (index + 1) % live.size
            delay(cycleMillis)
        }
    }

    val selected = live[index.coerceIn(0, live.lastIndex)]
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val enter: EnterTransition = slideInVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(90))
            val exit: ExitTransition = slideOutVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                targetOffsetY = { -it },
            ) + fadeOut(tween(100))
            enter togetherWith exit
        },
        label = "Win81LiveTile:${tile.id}",
    ) { notification ->
        LiveNotificationTileContent(
            tile = tile,
            live = notification,
            appIcon = appIcon,
        )
    }
}

@Composable
private fun StaticAppTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
    logoModifier: Modifier,
) {
    val isSmall = tile.size == TileSize.SMALL
    val labelColor = readableTileLabelColor(tile.colorValue.toTileColor())
    val iconSize = when (tile.size) {
        TileSize.SMALL -> 26.dp
        TileSize.MEDIUM -> 44.dp
        TileSize.WIDE -> 48.dp
        TileSize.LARGE -> 64.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = if (isSmall) 0.dp else 12.dp),
        ) {
            LiveTileAppIcon(
                tile = tile,
                appIcon = appIcon,
                size = iconSize,
                modifier = logoModifier,
            )
        }

        if (!isSmall) {
            Text(
                text = tile.title,
                style = WindowsTypography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun LiveNotificationTileContent(
    tile: TileModel,
    live: LiveTileNotification,
    appIcon: ImageBitmap?,
) {
    val contentColor = readableTileLabelColor(tile.colorValue.toTileColor())
    Box(Modifier.fillMaxSize().padding(9.dp)) {
        if (tile.size == TileSize.WIDE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveTileAppIcon(
                    tile = tile,
                    appIcon = appIcon,
                    size = 42.dp,
                )
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    LiveTileText(
                        live = live,
                        titleSize = 13f,
                        bodySize = 11f,
                        bodyLineHeight = 14f,
                        titleLines = 1,
                        bodyLines = 3,
                        contentColor = contentColor,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(bottom = 18.dp),
            ) {
                LiveTileText(
                    live = live,
                    titleSize = 13f,
                    bodySize = 11f,
                    bodyLineHeight = 14f,
                    titleLines = 2,
                    bodyLines = 4,
                    contentColor = contentColor,
                )
            }
        }

        LiveTileBranding(tile = tile, count = live.count)
    }
}

@Composable
private fun LargeLiveNotificationTileContent(
    tile: TileModel,
    live: List<LiveTileNotification>,
    appIcon: ImageBitmap?,
) {
    val count = live.firstOrNull()?.count ?: 0
    val contentColor = readableTileLabelColor(tile.colorValue.toTileColor())
    Box(Modifier.fillMaxSize().padding(11.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LiveTileAppIcon(tile = tile, appIcon = appIcon, size = 34.dp)
                Text(
                    text = tile.title,
                    color = contentColor,
                    style = WindowsTypography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            live.take(3).forEach { notification ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = notification.title,
                        color = contentColor,
                        style = WindowsTypography.titleMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (notification.text.isNotBlank()) {
                        Text(
                            text = notification.text,
                            color = contentColor.copy(alpha = .92f),
                            style = WindowsTypography.bodyMedium.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        LiveTileBranding(tile = tile, count = count)
    }
}

private fun readableTileLabelColor(background: Color): Color {
    // Windows keeps the saturated Metro palette white, but yellow/amber and the light neutral
    // (white/very-light-gray) variants use dark branding for readable contrast.
    val maxChannel = maxOf(background.red, background.green, background.blue)
    val minChannel = minOf(background.red, background.green, background.blue)
    val isLightNeutral =
        minChannel >= 0.80f &&
            maxChannel - minChannel <= 0.14f
    val isYellowFamily =
        background.red >= 0.58f &&
            background.green >= 0.45f &&
            background.blue <= 0.30f &&
            background.red + background.green - (2f * background.blue) >= 0.85f

    return if (isYellowFamily || isLightNeutral) Color.Black else Color.White
}

@Composable
private fun LiveTileText(
    live: LiveTileNotification,
    titleSize: Float,
    bodySize: Float,
    bodyLineHeight: Float,
    titleLines: Int,
    bodyLines: Int,
    contentColor: Color,
) {
    if (live.title.isNotBlank()) {
        Text(
            text = live.title,
            color = contentColor,
            style = WindowsTypography.titleMedium.copy(
                fontSize = titleSize.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (live.title.isNotBlank() && live.text.isNotBlank()) {
        Spacer(Modifier.height(3.dp))
    }
    if (live.text.isNotBlank()) {
        Text(
            text = live.text,
            color = contentColor.copy(alpha = .94f),
            style = WindowsTypography.bodyMedium.copy(
                fontSize = bodySize.sp,
                lineHeight = bodyLineHeight.sp,
            ),
            maxLines = bodyLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoxScope.LiveTileBranding(
    tile: TileModel,
    count: Int,
) {
    val labelColor = readableTileLabelColor(tile.colorValue.toTileColor())
    Text(
        text = tile.title,
        style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
        color = labelColor,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.BottomStart),
    )
    if (count > 0) {
        LiveTileBadge(
            count = count,
            color = labelColor,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun LiveTileBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Text(
        text = count.coerceIn(1, 99).toString(),
        style = WindowsTypography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun LiveTileAppIcon(
    tile: TileModel,
    appIcon: ImageBitmap?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contentColor = readableTileLabelColor(tile.colorValue.toTileColor())
    val iconsRevision = LauncherFeatureRuntime.iconsRevision
    val iconStyle = remember(iconsRevision) { LauncherFeatureStore.appIconStyle(context) }
    val tintMonochrome =
        iconStyle == com.flivoro.tile8auncher.features.AppIconStyle.WHITE_MONOCHROME

    if (tile.iconGlyph.isNotEmpty()) {
        MetroIcon(
            glyph = tile.iconGlyph,
            color = contentColor,
            size = size,
            modifier = modifier,
        )
    } else if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = tile.title,
            colorFilter = if (tintMonochrome) ColorFilter.tint(contentColor) else null,
            modifier = modifier.size(size),
        )
    } else {
        MetroIcon(
            glyph = "app",
            color = contentColor,
            size = size,
            modifier = modifier,
        )
    }
}

@Composable
private fun DesktopTileContent(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D254C),
                        Color(0xFF091428),
                        Color(0xFF040A14),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp, 40.dp)
                .background(Color(0x22FFFFFF)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .padding(top = 70.dp)
                .background(Color(0x88000000)),
        )
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun ClockTileContent(title: String, isWide: Boolean, contentColor: Color) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    var timeText by remember { mutableStateOf(timeFormat.format(Date())) }
    var dateText by remember { mutableStateOf(dateFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)
            delay(60_000L - System.currentTimeMillis().mod(60_000L))
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = timeText,
                style = WindowsTypography.displayLarge.copy(fontSize = if (isWide) 36.sp else 28.sp),
                color = contentColor,
            )
            if (isWide) {
                Text(
                    text = dateText,
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = contentColor,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun WeatherTileContent(title: String, isWide: Boolean, logoModifier: Modifier, contentColor: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "24°",
                    style = WindowsTypography.displayLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Light),
                    color = contentColor,
                )
                Text(
                    text = "Mostly Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = contentColor.copy(alpha = 0.9f),
                )
            }
            MetroIcon(
                glyph = "weather",
                color = contentColor,
                size = 46.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).then(logoModifier),
            )
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroIcon(glyph = "weather", color = contentColor, size = 36.dp, modifier = logoModifier)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "24° Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = contentColor,
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = contentColor,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun CalendarTileContent(title: String, contentColor: Color) {
    val dayNum = remember { SimpleDateFormat("d", Locale.getDefault()).format(Date()) }
    val dayName = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }

    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = dayNum,
                style = WindowsTypography.displayLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Light),
                color = contentColor,
            )
            Text(
                text = dayName,
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                color = contentColor.copy(alpha = 0.85f),
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = contentColor,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun MoneyTileContent(title: String, isWide: Boolean, logoModifier: Modifier, contentColor: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "NASDAQ",
                    style = WindowsTypography.titleMedium.copy(fontSize = 14.sp),
                    color = contentColor,
                )
                Text(
                    text = "19,842.10  ▲ +0.92%",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = contentColor.copy(alpha = .88f),
                )
            }
            MetroIcon(
                glyph = "money",
                color = contentColor,
                size = 40.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).then(logoModifier),
            )
        } else {
            MetroIcon(
                glyph = "money",
                color = contentColor,
                size = 38.dp,
                modifier = Modifier.align(Alignment.Center).then(logoModifier),
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = contentColor,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
