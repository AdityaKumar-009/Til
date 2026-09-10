package com.flivoro.tile8auncher.ui.start

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.notifications.TileNotificationSummary
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.animation.Windows81Motion
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.Windows81TileView
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Windows 8.1 Start surface. The entrance track is the existing motion fitted
 * frame-by-frame to Tile8's bundled Windows 8.1 recording. The recording also
 * shows the entrance replay when returning from Desktop, so entranceRequest is
 * intentionally honored instead of treating the motion as login-only.
 */
@Composable
fun Windows81StartScreen(
    tiles: List<TileModel>,
    appsRepository: AppsRepository,
    selectedTileIds: Set<String>,
    namingGroups: Boolean,
    liveTileDisabledIds: Set<String>,
    notifications: Map<String, TileNotificationSummary>,
    onTileClick: (tile: TileModel, bounds: Rect) -> Unit,
    onTileLongClick: (tile: TileModel) -> Unit,
    onToggleSelection: (tile: TileModel) -> Unit,
    onMoveTile: (tileId: String, direction: Int) -> Unit,
    onGroupNameChange: (oldName: String, newName: String) -> Unit,
    onPowerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToAllApps: () -> Unit,
    modifier: Modifier = Modifier,
    launchingTileId: String? = null,
    entranceRequest: Int = 0,
    newAppCount: Int = 0,
    profileName: String = "Aditya Kumar",
    listState: LazyListState = rememberLazyListState(),
) {
    val entrance = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var semanticZoom by remember { mutableStateOf(false) }

    LaunchedEffect(entranceRequest, tiles.isNotEmpty()) {
        if (tiles.isNotEmpty()) {
            entrance.snapTo(0f)
            entrance.animateTo(
                1f,
                tween(StartEntranceMotion.DurationMillis, easing = LinearEasing),
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            StartHeader(profileName, onPowerClick, onSearchClick)
            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            ) {
                val groupHeaderHeight = 30.dp
                val metrics = remember(maxWidth, maxHeight) {
                    calculateStartGridMetrics(
                        availableWidthDp = maxWidth.value,
                        availableHeightDp = (maxHeight - groupHeaderHeight).value,
                    )
                }
                val snapshot = tiles.toList()
                val packed = remember(snapshot, metrics.rows, metrics.columns) {
                    packStartTiles(snapshot, metrics.rows, metrics.columns)
                }
                val zoomProgress by animateFloatAsState(
                    targetValue = if (semanticZoom) 1f else 0f,
                    animationSpec = tween(
                        Windows81Motion.SemanticZoomDurationMillis,
                        easing = Windows81Motion.SemanticZoomEase,
                    ),
                    label = "Windows81StartSemanticZoom",
                )

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .elasticHorizontalScroll()
                        .graphicsLayer {
                            val zoomScale = 1f -
                                (1f - Windows81Motion.SemanticZoomFactor) * zoomProgress
                            scaleX = zoomScale
                            scaleY = zoomScale
                            alpha = 1f - zoomProgress
                        },
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    items(packed.bands, key = { it.key }) { band ->
                        Column(Modifier.width(metrics.bandWidthDp.dp)) {
                            GroupHeader(
                                groupName = band.groupName,
                                namingGroups = namingGroups,
                                onNameChanged = { onGroupNameChange(band.groupName, it) },
                                modifier = Modifier.height(groupHeaderHeight),
                            )
                            Box(
                                Modifier.width(metrics.bandWidthDp.dp).height(metrics.bandHeightDp.dp),
                            ) {
                                band.tiles.forEach { placed ->
                                    key(placed.tile.id) {
                                        val tile = placed.tile
                                        val selected = tile.id in selectedTileIds
                                        val appIcon = tile.packageName?.let {
                                            rememberAppIcon(appsRepository, it)
                                        }
                                        val notification = tile.packageName?.let(notifications::get)
                                        val tileWidth = (
                                            placed.columns * metrics.cellDp +
                                                (placed.columns - 1) * metrics.gapDp
                                            ).dp
                                        val tileHeight = (
                                            placed.rows * metrics.cellDp +
                                                (placed.rows - 1) * metrics.gapDp
                                            ).dp
                                        val targetX = (placed.column * (metrics.cellDp + metrics.gapDp)).dp
                                        val targetY = (placed.row * (metrics.cellDp + metrics.gapDp)).dp
                                        val animatedX by animateDpAsState(
                                            targetValue = targetX,
                                            animationSpec = tween(
                                                Windows81Motion.RepositionDurationMillis,
                                                easing = Windows81Motion.Fluid,
                                            ),
                                            label = "Windows81TileX:${tile.id}",
                                        )
                                        val animatedY by animateDpAsState(
                                            targetValue = targetY,
                                            animationSpec = tween(
                                                Windows81Motion.RepositionDurationMillis,
                                                easing = Windows81Motion.Fluid,
                                            ),
                                            label = "Windows81TileY:${tile.id}",
                                        )
                                        var dragX by remember(tile.id) { mutableFloatStateOf(0f) }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = animatedX, y = animatedY)
                                                .size(tileWidth, tileHeight)
                                                .graphicsLayer {
                                                    val frame = StartEntranceMotion.frame(
                                                        entrance.value,
                                                        placed.column,
                                                    )
                                                    val cellPx = metrics.cellDp.dp.toPx() + metrics.gapDp.dp.toPx()
                                                    val centerY = placed.row * cellPx + size.height / 2f
                                                    translationX = frame.offsetFraction *
                                                        metrics.bandWidthDp.dp.toPx() + dragX
                                                    translationY = (1f - frame.scale) *
                                                        (metrics.bandHeightDp.dp.toPx() / 2f - centerY)
                                                    scaleX = frame.scale
                                                    scaleY = frame.scale
                                                    alpha = frame.alpha
                                                }
                                                .alpha(if (tile.id == launchingTileId) 0f else 1f)
                                                .then(
                                                    if (selected) {
                                                        Modifier.pointerInput(tile.id) {
                                                            detectDragGestures(
                                                                onDragEnd = {
                                                                    val threshold = size.width * 0.34f
                                                                    if (abs(dragX) >= threshold) {
                                                                        onMoveTile(
                                                                            tile.id,
                                                                            if (dragX < 0f) -1 else 1,
                                                                        )
                                                                    }
                                                                    dragX = 0f
                                                                },
                                                                onDragCancel = { dragX = 0f },
                                                            ) { change, amount ->
                                                                change.consume()
                                                                dragX += amount.x
                                                            }
                                                        }
                                                    } else Modifier,
                                                ),
                                        ) {
                                            Windows81TileView(
                                                tile = tile,
                                                appIcon = appIcon,
                                                liveTileEnabled = tile.id !in liveTileDisabledIds,
                                                notification = notification,
                                                selected = selected,
                                                modifier = Modifier.fillMaxSize(),
                                                onClick = { bounds ->
                                                    if (selectedTileIds.isNotEmpty()) {
                                                        onToggleSelection(tile)
                                                    } else {
                                                        onTileClick(tile, bounds)
                                                    }
                                                },
                                                onLongClick = { onTileLongClick(tile) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (zoomProgress > 0.001f) {
                    StartSemanticZoom(
                        groups = packed.bands.map { it.groupName }.distinct(),
                        enabled = semanticZoom,
                        progress = zoomProgress,
                        onGroupClick = { group ->
                            val index = packed.bands.indexOfFirst { it.groupName == group }.coerceAtLeast(0)
                            semanticZoom = false
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (newAppCount > 0) {
                    Text(
                        text = "$newAppCount new ${if (newAppCount == 1) "app" else "apps"} installed",
                        style = WindowsTypography.bodyMedium.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
                Spacer(Modifier.weight(1f))
                SemanticZoomButton { semanticZoom = !semanticZoom }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(46.dp).clickable(onClick = onNavigateToAllApps),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon("arrow_down", color = Color.White.copy(alpha = 0.92f), size = 40.dp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(34.dp))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StartHeader(
    profileName: String,
    onPowerClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 400.dp
        val veryNarrow = maxWidth < 290.dp
        val titleSize = when {
            veryNarrow -> 32.sp
            compact -> 36.sp
            else -> 42.sp
        }
        val initials = profileName.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "U" }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Start",
                style = WindowsTypography.displayLarge.copy(fontSize = titleSize),
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    profileName,
                    style = WindowsTypography.titleMedium.copy(fontSize = 15.sp),
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(
                Modifier.size(if (veryNarrow) 29.dp else 34.dp)
                    .background(Color(0xFFE6E6E6)).border(1.dp, Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = Color(0xFF222222), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(if (compact) 7.dp else 12.dp))
            HeaderIcon("power", onPowerClick)
            Spacer(Modifier.width(if (compact) 5.dp else 10.dp))
            HeaderIcon("search", onSearchClick)
        }
    }
}

@Composable
private fun HeaderIcon(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(31.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        MetroIcon(glyph, color = Color.White, size = 20.dp)
    }
}

@Composable
private fun GroupHeader(
    groupName: String,
    namingGroups: Boolean,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (namingGroups) {
            val shown = if (groupName == "Start") "" else groupName
            BasicTextField(
                value = shown,
                onValueChange = onNameChanged,
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (shown.isBlank()) {
                        Text("Name group", color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
                    }
                    inner()
                },
                modifier = Modifier.width(180.dp),
            )
        } else if (groupName != "Start") {
            Text(
                groupName,
                style = WindowsTypography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun StartSemanticZoom(
    groups: List<String>,
    enabled: Boolean,
    progress: Float,
    onGroupClick: (String) -> Unit,
) {
    val incomingStartScale = 1f / Windows81Motion.SemanticZoomFactor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                val scale = incomingStartScale - (incomingStartScale - 1f) * progress
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xE72A0A3A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            groups.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { group ->
                        Box(
                            Modifier.size(width = 112.dp, height = 72.dp)
                                .background(Color(0xFF5A1780))
                                .clickable(enabled = enabled) { onGroupClick(group) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (group == "Start") "Start" else group,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticZoomButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).border(1.dp, Color.White.copy(alpha = 0.6f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("−", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun Windows81StartCustomizationBar(
    selectedTiles: List<TileModel>,
    namingGroups: Boolean,
    liveDisabledIds: Set<String>,
    onUnpin: () -> Unit,
    onResize: (TileSize) -> Unit,
    onToggleLive: () -> Unit,
    onToggleNamingGroups: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var resizeMenu by remember { mutableStateOf(false) }
    val visible = selectedTiles.isNotEmpty() || namingGroups
    val selectedIds = selectedTiles.map { it.id }
    val turnLiveOn = selectedIds.isNotEmpty() && selectedIds.all { it in liveDisabledIds }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(Windows81Motion.EdgeUiDurationMillis, easing = Windows81Motion.Fluid),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(Windows81Motion.EdgeUiDurationMillis, easing = Windows81Motion.Fluid),
            targetOffsetY = { it },
        ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (resizeMenu && selectedTiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-86).dp)
                        .background(Color(0xFA24102F))
                        .border(1.dp, Color.White.copy(alpha = 0.32f)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TileSize.entries.forEach { size ->
                        Box(
                            Modifier.background(Color(0xFF5A1780)).clickable {
                                onResize(size)
                                resizeMenu = false
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                size.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xF3191024))
                    .border(1.dp, Color.White.copy(alpha = 0.24f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommandButton("⊖", "Unpin from Start", selectedTiles.isNotEmpty(), onUnpin)
                CommandButton("□", "Resize", selectedTiles.isNotEmpty()) { resizeMenu = !resizeMenu }
                CommandButton(
                    icon = if (turnLiveOn) "▶" else "■",
                    label = if (turnLiveOn) "Turn live tile on" else "Turn live tile off",
                    enabled = selectedTiles.isNotEmpty(),
                    onClick = onToggleLive,
                )
                CommandButton(
                    "✎",
                    if (namingGroups) "Done naming" else "Name groups",
                    true,
                    onToggleNamingGroups,
                )
                CommandButton("×", "Clear selection", selectedTiles.isNotEmpty(), onClearSelection)
            }
        }
    }
}

@Composable
private fun CommandButton(
    icon: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp),
    ) {
        Box(Modifier.size(34.dp).border(2.dp, Color.White), contentAlignment = Alignment.Center) {
            Text(icon, color = Color.White, fontSize = 17.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}
