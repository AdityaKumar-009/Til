package com.flivoro.tile8auncher.ui.components

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.ui.animation.metroTilePress
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

import com.flivoro.tile8auncher.ui.theme.toTileColor

@Composable
fun WindowsTileView(
    tile: TileModel,
    appIcon: ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val tileColor = tile.colorValue.toTileColor()

    Box(
        modifier = modifier
            .metroTilePress(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(tileColor),
    ) {
        when (tile.tileType) {
            TileType.DESKTOP -> {
                // Desktop tile with Windows blue desktop wallpaper preview
                DesktopTileContent(title = tile.title)
            }

            TileType.CLOCK -> {
                // Live clock tile
                ClockTileContent(title = tile.title, isWide = tile.size == TileSize.WIDE)
            }

            TileType.WEATHER -> {
                // Weather tile
                WeatherTileContent(title = tile.title, isWide = tile.size == TileSize.WIDE)
            }

            TileType.CALENDAR -> {
                // Calendar tile
                CalendarTileContent(title = tile.title)
            }

            TileType.MONEY -> {
                // Money / Stocks tile
                MoneyTileContent(title = tile.title, isWide = tile.size == TileSize.WIDE)
            }

            else -> {
                // Standard app tile
                StandardAppTileContent(
                    tile = tile,
                    appIcon = appIcon,
                )
            }
        }
    }
}

@Composable
private fun StandardAppTileContent(
    tile: TileModel,
    appIcon: ImageBitmap?,
) {
    val isSmall = tile.size == TileSize.SMALL
    val iconSize = when (tile.size) {
        TileSize.SMALL -> 26.dp
        TileSize.MEDIUM -> 44.dp
        TileSize.WIDE -> 48.dp
        TileSize.LARGE -> 64.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Centered Icon
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
                )
            } else if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = tile.title,
                    modifier = Modifier.size(iconSize),
                )
            } else {
                MetroIcon(
                    glyph = "app",
                    color = Color.White,
                    size = iconSize,
                )
            }
        }

        // Title at bottom left (omitted on small 1x1 tiles to match Windows 8.1)
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
        // Desktop wallpaper accent swirl
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp, 40.dp)
                .background(Color(0x22FFFFFF)),
        )
        // Mini taskbar at bottom
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
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            dateText = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            delay(1.seconds)
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
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun WeatherTileContent(title: String, isWide: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "24°",
                    style = WindowsTypography.displayLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Light),
                    color = Color.White
                )
                Text(
                    text = "Mostly Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            MetroIcon(
                glyph = "weather",
                color = Color.White,
                size = 46.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
            )
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroIcon(glyph = "weather", color = Color.White, size = 36.dp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "24° Sunny",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color.White
                )
            }
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart)
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
                color = Color.White
            )
            Text(
                text = dayName,
                style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun MoneyTileContent(title: String, isWide: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (isWide) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "NASDAQ",
                    style = WindowsTypography.titleMedium.copy(fontSize = 14.sp),
                    color = Color.White
                )
                Text(
                    text = "19,842.10  ▲ +0.92%",
                    style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color(0xFFC8FFC8)
                )
            }
            MetroIcon(
                glyph = "money",
                color = Color.White,
                size = 40.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            )
        } else {
            MetroIcon(
                glyph = "money",
                color = Color.White,
                size = 38.dp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            text = title,
            style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
