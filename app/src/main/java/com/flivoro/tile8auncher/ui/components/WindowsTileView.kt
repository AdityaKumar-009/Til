package com.flivoro.tile8auncher.ui.components

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LiveTileNotification
import com.flivoro.tile8auncher.features.LiveTileNotificationStore
import com.flivoro.tile8auncher.ui.animation.metroTileLongPressDrag
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
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = onDragEnd,
) {
    WindowsTileFace(
        tile = tile,
        appIcon = appIcon,
        modifier = modifier
            .metroTileLongPressDrag(
                enabled = dragEnabled,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            .metroTilePress(
                onClick = onClick,
                // The drag recognizer owns the long-press threshold when enabled. Keeping
                // this null prevents the old modal action from firing at the same instant.
                onLongClick = if (dragEnabled) null else onLongClick,
            ),
    )
}

@Composable
internal fun WindowsTileFace(
    tile: TileModel,
    appIcon: ImageBitmap?,
    modifier: Modifier = Modifier,
    logoModifier: Modifier = Modifier,
) {
    val tileColor = tile.colorValue.toTileColor()

    Box(
        modifier = modifier.background(tileColor),
    ) {
        when (tile.tileType) {
            TileType.DESKTOP -> DesktopTileContent(title = tile.title)
            TileType.CLOCK -> ClockTileContent(title = tile.title, isWide = tile.size == TileSize.WIDE)
            TileType.WEATHER -> WeatherTileContent(
                title = tile.title,
                isWide = tile.size == TileSize.WIDE,
                logoModifier = logoModifier,
            )
            TileType.CALENDAR -> CalendarTileContent(title = tile.title)
            TileType.MONEY -> MoneyTileContent(
                title = tile.title,
                isWide = tile.size == TileSize.WIDE,
                logoModifier = logoModifier,
            )
            else -> StandardAppTileContent(tile = tile, appIcon = appIcon, logoModifier = logoModifier)
        }
    }
}

/**
 * Windows 8.1 desktop Start tiles did not use the Windows Phone 3D flip for ordinary updates.
 * Recorded Surface behavior and period reports show the iconic face moving vertically upward.
 * The content transition below is therefore a flat one-axis translation and never touches the
 * launch transform/graphicsLayer used by FlipLaunchOverlay.
 */
@Composable
private fun StandardAppTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
    logoModifier: Modifier,
) {
    val context = LocalContext.current
    val isSmall = tile.size == TileSize.SMALL
    val live = LiveTileNotificationStore.latest(context, tile.packageName)
        ?.takeIf { LauncherFeatureStore.isLiveTileEnabled(context, tile.packageName) }
        ?.takeIf { !isSmall }
    var showLiveFace by remember(tile.id) { mutableStateOf(false) }

    LaunchedEffect(live?.postTime, tile.id) {
        if (live == null) {
            showLiveFace = false
            return@LaunchedEffect
        }
        // Fresh updates surface promptly, then periodically reappear like Windows live tiles.
        while (true) {
            showLiveFace = true
            delay(5_500L)
            showLiveFace = false
            delay(7_500L)
        }
    }

    AnimatedContent(
        targetState = showLiveFace && live != null,
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
    ) { showingLive ->
        if (showingLive && live != null) {
            LiveNotificationTileContent(tile = tile, live = live)
        } else {
            StaticAppTileContent(tile = tile, appIcon = appIcon, logoModifier = logoModifier)
        }
    }
}

@Composable
private fun StaticAppTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
    logoModifier: Modifier,
) {
    val isSmall = tile.size == TileSize.SMALL
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
            if (tile.iconGlyph.isNotEmpty()) {
                MetroIcon(
                    glyph = tile.iconGlyph,
                    color = Color.White,
                    size = iconSize,
                    modifier = logoModifier,
                )
            } else if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = tile.title,
                    modifier = logoModifier.size(iconSize),
                )
            } else {
                MetroIcon(
                    glyph = "app",
                    color = Color.White,
                    size = iconSize,
                    modifier = logoModifier,
                )
            }
        }

        if (!isSmall) {
            Text(
                text = tile.title,
                style = WindowsTypography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun LiveNotificationTileContent(tile: TileModel, live: LiveTileNotification) {
    Box(Modifier.fillMaxSize().padding(9.dp)) {
        Column(Modifier.align(Alignment.TopStart).padding(bottom = 18.dp)) {
            if (live.title.isNotBlank()) {
                Text(
                    text = live.title,
                    color = Color.White,
                    style = WindowsTypography.titleMedium.copy(
                        fontSize = if (tile.size == TileSize.LARGE) 15.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = if (tile.size == TileSize.WIDE) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (live.text.isNotBlank()) {
                Text(
                    text = live.text,
                    color = Color.White.copy(alpha = .94f),
                    style = WindowsTypography.bodyMedium.copy(
                        fontSize = if (tile.size == TileSize.LARGE) 13.sp else 11.sp,
                        lineHeight = if (tile.size == TileSize.LARGE) 17.sp else 14.sp,
                    ),
                    maxLines = when (tile.size) {
                        TileSize.LARGE -> 7
                        TileSize.WIDE -> 3
                        else -> 4
                    },
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = tile.title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        if (live.count > 1) {
            Text(
                text = live.count.toString(),
                style = WindowsTypography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
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
private fun ClockTileContent(title: String, isWide: Boolean) {
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
                color = Color.White,
            )
            if (isWide) {
                Text(
                    text = dateText,
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun WeatherTileContent(title: String, isWide: Boolean, logoModifier: Modifier) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "24°",
                    style = WindowsTypography.displayLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Light),
                    color = Color.White,
                )
                Text(
                    text = "Mostly Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            MetroIcon(
                glyph = "weather",
                color = Color.White,
                size = 46.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).then(logoModifier),
            )
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroIcon(glyph = "weather", color = Color.White, size = 36.dp, modifier = logoModifier)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "24° Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color.White,
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun CalendarTileContent(title: String) {
    val dayNum = remember { SimpleDateFormat("d", Locale.getDefault()).format(Date()) }
    val dayName = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }

    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = dayNum,
                style = WindowsTypography.displayLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Light),
                color = Color.White,
            )
            Text(
                text = dayName,
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun MoneyTileContent(title: String, isWide: Boolean, logoModifier: Modifier) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "NASDAQ",
                    style = WindowsTypography.titleMedium.copy(fontSize = 14.sp),
                    color = Color.White,
                )
                Text(
                    text = "19,842.10  ▲ +0.92%",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color(0xFFC8FFC8),
                )
            }
            MetroIcon(
                glyph = "money",
                color = Color.White,
                size = 40.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).then(logoModifier),
            )
        } else {
            MetroIcon(
                glyph = "money",
                color = Color.White,
                size = 38.dp,
                modifier = Modifier.align(Alignment.Center).then(logoModifier),
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
