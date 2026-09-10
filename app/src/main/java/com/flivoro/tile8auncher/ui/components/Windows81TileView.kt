package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.notifications.TileNotificationSummary
import com.flivoro.tile8auncher.ui.animation.metroTilePress
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

@Composable
fun Windows81TileView(
    tile: TileModel,
    appIcon: ImageBitmap?,
    liveTileEnabled: Boolean,
    notification: TileNotificationSummary?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val displayTile = if (!liveTileEnabled && tile.tileType in DYNAMIC_TILE_TYPES) {
        tile.copy(tileType = TileType.APP)
    } else tile

    Box(
        modifier = modifier.metroTilePress(onClick = onClick, onLongClick = onLongClick),
    ) {
        WindowsTileFace(
            tile = displayTile,
            appIcon = appIcon,
            modifier = Modifier.fillMaxSize(),
        )

        if (liveTileEnabled && notification != null && tile.size != TileSize.SMALL) {
            NotificationLiveContent(
                notification = notification,
                showBody = tile.size == TileSize.WIDE || tile.size == TileSize.LARGE,
            )
        }

        if (liveTileEnabled && notification != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color(0xCC111111))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = notification.count.coerceAtMost(99).toString(),
                    style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Color.White.copy(alpha = 0.96f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.White)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF40105E),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NotificationLiveContent(
    notification: TileNotificationSummary,
    showBody: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
    ) {
        if (notification.title.isNotBlank()) {
            Text(
                text = notification.title,
                style = WindowsTypography.titleMedium.copy(fontSize = 12.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showBody && notification.text.isNotBlank()) {
            Text(
                text = notification.text,
                style = WindowsTypography.bodyMedium.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val DYNAMIC_TILE_TYPES = setOf(
    TileType.CLOCK,
    TileType.WEATHER,
    TileType.CALENDAR,
    TileType.PHOTOS,
    TileType.MAIL,
    TileType.MONEY,
)
