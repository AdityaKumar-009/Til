package com.flivoro.tile8auncher.ui.start

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

@Composable
fun StartScreen(
    tiles: List<TileModel>,
    appsRepository: AppsRepository,
    onTileClick: (tile: TileModel, bounds: Rect) -> Unit,
    onTileLongClick: (tile: TileModel) -> Unit,
    onPowerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Start Screen Header: "Start" and User/Power/Search controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // "Start" Title
                Text(
                    text = "Start",
                    style = WindowsTypography.displayLarge.copy(fontSize = 42.sp),
                    color = Color.White,
                )

                // User Profile & System Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // User Name
                    Text(
                        text = "Aditya Kumar",
                        style = WindowsTypography.titleMedium.copy(fontSize = 15.sp),
                        color = Color.White,
                    )

                    // User Profile Square Avatar
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFEEEEEE))
                            .border(1.dp, Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "AK",
                            style = WindowsTypography.titleMedium.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF1E1E1E),
                            ),
                        )
                    }

                    // Power Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onPowerClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(
                            glyph = "power",
                            color = Color.White,
                            size = 20.dp,
                        )
                    }

                    // Search Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onSearchClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(
                            glyph = "search",
                            color = Color.White,
                            size = 20.dp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val gridSpanCount = if (isLandscape) 8 else 4
            val baseUnitHeight = if (isLandscape) 120.dp else 72.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridSpanCount),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(
                    items = tiles,
                    key = { it.id },
                    span = { tile ->
                        val span = when (tile.size) {
                            TileSize.SMALL -> 1
                            TileSize.MEDIUM -> 2
                            TileSize.WIDE -> if (gridSpanCount >= 6) 4 else 4
                            TileSize.LARGE -> 4
                        }
                        GridItemSpan(span.coerceAtMost(gridSpanCount))
                    },
                ) { tile ->
                    val tileHeight = when (tile.size) {
                        TileSize.SMALL -> baseUnitHeight
                        TileSize.MEDIUM -> (baseUnitHeight * 2) + 8.dp
                        TileSize.WIDE -> (baseUnitHeight * 2) + 8.dp
                        TileSize.LARGE -> (baseUnitHeight * 4) + 24.dp
                    }

                    val appIcon = tile.packageName?.let { appsRepository.getAppIcon(it) }

                    WindowsTileView(
                        tile = tile,
                        appIcon = appIcon,
                        modifier = Modifier.height(tileHeight),
                        onClick = { bounds -> onTileClick(tile, bounds) },
                    ) {
                        onTileLongClick(tile)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Navigation Arrow (Pointing DOWN to go to Apps by name)
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(42.dp)
                    .clickable { onNavigateToAllApps() },
                contentAlignment = Alignment.Center,
            ) {
                MetroIcon(
                    glyph = "arrow_down",
                    color = Color.White.copy(alpha = 0.85f),
                    size = 38.dp,
                )
            }
        }
    }
}
