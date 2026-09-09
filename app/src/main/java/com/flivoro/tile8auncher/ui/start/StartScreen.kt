package com.flivoro.tile8auncher.ui.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography

@Composable
fun StartScreen(
    tiles: List<TileModel>,
    appsRepository: AppsRepository,
    onTileClick: (tile: TileModel, bounds: Rect) -> Unit,
    onTileLongClick: (tile: TileModel) -> Unit,
    onPowerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAddAppsClick: () -> Unit,
    onNavigateToAllApps: () -> Unit,
    modifier: Modifier = Modifier,
    launchingTileId: String? = null,
    entranceRequest: Int = 0,
    listState: LazyListState = rememberLazyListState(),
) {
    val entrance = remember { Animatable(1f) }
    LaunchedEffect(entranceRequest, tiles.isNotEmpty()) {
        if (tiles.isNotEmpty()) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(StartEntranceMotion.DurationMillis, easing = LinearEasing))
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Start Screen Header: "Start" and User/Power/Search/Add controls
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val availableWidth = maxWidth

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isCompact = availableWidth < 400.dp
                    val isVeryNarrow = availableWidth < 280.dp
                    val controlSize = when {
                        isVeryNarrow -> 26.dp
                        isCompact -> 28.dp
                        else -> 32.dp
                    }
                    val avatarSize = when {
                        isVeryNarrow -> 28.dp
                        isCompact -> 30.dp
                        else -> 34.dp
                    }
                    val controlSpacing = when {
                        isVeryNarrow -> 2.dp
                        isCompact -> 6.dp
                        else -> 14.dp
                    }
                    val titleSize = when {
                        isVeryNarrow -> 32.sp
                        isCompact -> 36.sp
                        else -> 42.sp
                    }

                    Text(
                        text = "Start",
                        style = WindowsTypography.displayLarge.copy(fontSize = titleSize),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                    ) {
                        if (availableWidth >= 400.dp) {
                            Text(
                                text = "Aditya Kumar",
                                style = WindowsTypography.titleMedium.copy(fontSize = 15.sp),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .background(Color(0xFFEEEEEE))
                                .border(1.dp, Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "AK",
                                style = WindowsTypography.titleMedium.copy(
                                    fontSize = if (isVeryNarrow) 11.sp else 13.sp,
                                    color = Color(0xFF1E1E1E),
                                ),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(controlSize)
                                .clickable { onAddAppsClick() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroIcon(
                                glyph = "plus",
                                color = Color.White,
                                size = if (isVeryNarrow) 18.dp else 20.dp,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(controlSize)
                                .clickable { onPowerClick() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroIcon(
                                glyph = "power",
                                color = Color.White,
                                size = if (isVeryNarrow) 18.dp else 20.dp,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(controlSize)
                                .clickable { onSearchClick() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroIcon(
                                glyph = "search",
                                color = Color.White,
                                size = if (isVeryNarrow) 18.dp else 20.dp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val metrics = remember(maxWidth, maxHeight) {
                    calculateStartGridMetrics(
                        availableWidthDp = maxWidth.value,
                        availableHeightDp = maxHeight.value,
                    )
                }

                // Snapshot the state-list contents so the placement pass is
                // rerun when pins change, while ordinary scroll and animation
                // recompositions reuse the same layout.
                val tileSnapshot = tiles.toList()
                val packed = remember(tileSnapshot, metrics.rows, metrics.columns) {
                    packStartTiles(
                        tiles = tileSnapshot,
                        maxRows = metrics.rows,
                        maxColumns = metrics.columns,
                    )
                }

                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize().elasticHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    items(
                        items = packed.bands,
                        key = { band -> band.key },
                    ) { band ->
                        Box(
                            modifier = Modifier
                                .width(metrics.bandWidthDp.dp)
                                .height(metrics.bandHeightDp.dp),
                        ) {
                            band.tiles.forEach { placed ->
                                key(placed.tile.id) {
                                    val tile = placed.tile
                                    val appIcon = tile.packageName?.let { packageName ->
                                        rememberAppIcon(appsRepository, packageName)
                                    }
                                    val tileWidth = (
                                        placed.columns * metrics.cellDp +
                                            (placed.columns - 1) * metrics.gapDp
                                        ).dp
                                    val tileHeight = (
                                        placed.rows * metrics.cellDp +
                                            (placed.rows - 1) * metrics.gapDp
                                        ).dp

                                    WindowsTileView(
                                        tile = tile,
                                        appIcon = appIcon,
                                        modifier = Modifier
                                            .offset(
                                                x = (placed.column * (metrics.cellDp + metrics.gapDp)).dp,
                                                y = (placed.row * (metrics.cellDp + metrics.gapDp)).dp,
                                            )
                                            .size(tileWidth, tileHeight)
                                            .graphicsLayer {
                                                val frame = StartEntranceMotion.frame(entrance.value, placed.column)
                                                val cellPx = metrics.cellDp.dp.toPx() + metrics.gapDp.dp.toPx()
                                                val centerY = placed.row * cellPx + size.height / 2f
                                                translationX = frame.offsetFraction * metrics.bandWidthDp.dp.toPx()
                                                translationY = (1f - frame.scale) * (metrics.bandHeightDp.dp.toPx() / 2f - centerY)
                                                scaleX = frame.scale
                                                scaleY = frame.scale
                                                alpha = frame.alpha
                                            }
                                            .alpha(if (tile.id == launchingTileId) 0f else 1f),
                                        onClick = { bounds -> onTileClick(tile, bounds) },
                                    ) {
                                        onTileLongClick(tile)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // All Apps is still a vertical navigation action from Start.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { onNavigateToAllApps() },
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon(
                        glyph = "arrow_down",
                        color = Color.White.copy(alpha = 0.9f),
                        size = 40.dp,
                    )
                }
            }
        }
    }
}
