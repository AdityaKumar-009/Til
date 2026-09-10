package com.flivoro.tile8auncher.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.launch

private const val START_BAND_SPACING_DP = 24f

private data class EntranceViewportSnapshot(
    val startBand: Int,
    val startOffsetPx: Int,
)

@OptIn(ExperimentalFoundationApi::class)
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
    onCharmsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    launchingTileId: String? = null,
    entranceRequest: Int = 0,
    entranceKind: StartEntranceKind = StartEntranceKind.RETURN,
    entranceEnabled: Boolean = true,
    interactionEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    val entrance = remember { Animatable(0f) }
    var playingKind by remember { mutableStateOf(entranceKind) }
    val scope = rememberCoroutineScope()
    var lastStartedRequest by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var entranceRunning by remember { mutableStateOf(false) }
    var showBandOverview by remember { mutableStateOf(false) }
    var viewportSnapshot by remember {
        mutableStateOf(EntranceViewportSnapshot(startBand = 0, startOffsetPx = 0))
    }
    val latestInteractionEnabled = rememberUpdatedState(interactionEnabled)
    val latestOnTileClick = rememberUpdatedState(onTileClick)
    val latestOnTileLongClick = rememberUpdatedState(onTileLongClick)

    LaunchedEffect(entranceRequest, entranceEnabled, tiles.isNotEmpty()) {
        if (tiles.isEmpty()) {
            entrance.stop()
            entranceRunning = false
            lastStartedRequest = Int.MIN_VALUE
            entrance.snapTo(0f)
            return@LaunchedEffect
        }

        if (!entranceEnabled) {
            entrance.stop()
            entranceRunning = false
            return@LaunchedEffect
        }

        if (entranceRequest == lastStartedRequest) {
            entrance.snapTo(1f)
            return@LaunchedEffect
        }

        lastStartedRequest = entranceRequest
        viewportSnapshot = EntranceViewportSnapshot(
            startBand = listState.firstVisibleItemIndex,
            startOffsetPx = listState.firstVisibleItemScrollOffset,
        )
        entrance.stop()
        playingKind = entranceKind
        entrance.snapTo(0f)
        entranceRunning = true
        try {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = tween(StartEntranceMotion.durationMillis(playingKind), easing = LinearEasing),
            )
        } finally {
            entranceRunning = false
        }
    }

    LaunchedEffect(interactionEnabled) {
        if (!interactionEnabled) showBandOverview = false
    }

    BackHandler(enabled = interactionEnabled && showBandOverview) {
        showBandOverview = false
    }

    fun handleTileClick(tile: TileModel, bounds: Rect) {
        if (!latestInteractionEnabled.value) return
        if (!entranceRunning) {
            latestOnTileClick.value(tile, bounds)
            return
        }

        // Keep the frame that produced the live bounds. The launch overlay can
        // then start from the same transformed tile while the motion stops.
        scope.launch {
            entrance.stop()
            entranceRunning = false
            if (latestInteractionEnabled.value) latestOnTileClick.value(tile, bounds)
        }
    }

    fun handleTileLongClick(tile: TileModel) {
        if (!latestInteractionEnabled.value) return
        if (!entranceRunning) {
            latestOnTileLongClick.value(tile)
            return
        }
        scope.launch {
            entrance.stop()
            entranceRunning = false
            entrance.snapTo(1f)
            if (latestInteractionEnabled.value) latestOnTileLongClick.value(tile)
        }
    }

    fun openBandOverview() {
        if (!interactionEnabled) return
        if (entranceRunning) {
            scope.launch {
                entrance.stop()
                entranceRunning = false
                entrance.snapTo(1f)
                showBandOverview = true
            }
        } else {
            showBandOverview = true
        }
    }

    fun zoomToBand(index: Int) {
        if (!interactionEnabled) return
        showBandOverview = false
        scope.launch {
            if (entranceRunning) {
                entrance.stop()
                entranceRunning = false
            }
            entrance.snapTo(1f)
            listState.scrollToItem(index)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = StartEntranceMotion.headerAlpha(entrance.value, playingKind) },
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
                                .border(1.dp, Color.White)
                                .combinedClickable(
                                    enabled = interactionEnabled,
                                    onClick = onCharmsClick,
                                    onLongClick = onAddAppsClick,
                                )
                                .semantics { contentDescription = "Open charms" },
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
                                .clickable(
                                    enabled = interactionEnabled,
                                    onClick = onPowerClick,
                                ),
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
                                .clickable(
                                    enabled = interactionEnabled,
                                    onClick = onSearchClick,
                                ),
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
                val density = LocalDensity.current
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val bandExtentPx = with(density) {
                    metrics.bandWidthDp.dp.toPx() + START_BAND_SPACING_DP.dp.toPx()
                }

                // Snapshot the state-list contents so the placement pass is
                // rerun when pins change, while ordinary scroll and animation
                // recompositions reuse the same layout.
                val tileSnapshot by remember(tiles) {
                    derivedStateOf { tiles.toList() }
                }
                val packed = remember(tileSnapshot, metrics.rows, metrics.columns) {
                    packStartTiles(
                        tiles = tileSnapshot,
                        maxRows = metrics.rows,
                        maxColumns = metrics.columns,
                    )
                }

                val rowModifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (interactionEnabled) {
                            Modifier.elasticHorizontalScroll()
                        } else {
                            Modifier
                        },
                    )

                Box(Modifier.fillMaxSize()) {
                    LazyRow(
                        state = listState,
                        userScrollEnabled = interactionEnabled,
                        modifier = rowModifier,
                        horizontalArrangement = Arrangement.spacedBy(START_BAND_SPACING_DP.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        itemsIndexed(
                            items = packed.bands,
                            key = { _, band -> band.key },
                        ) { bandIndex, band ->
                            Box(
                                modifier = Modifier
                                    .width(metrics.bandWidthDp.dp)
                                    .height(metrics.bandHeightDp.dp)
                                    .graphicsLayer {
                                        val position = StartEntranceMotion.viewportBandPosition(
                                            bandIndex, viewportSnapshot.startBand,
                                            viewportSnapshot.startOffsetPx, bandExtentPx)
                                        val frame = StartEntranceMotion.frame(entrance.value, position, playingKind)
                                        translationX = frame.offsetFraction * viewportWidthPx
                                        transformOrigin = TransformOrigin(.5f,
                                            viewportHeightPx / (2f * size.height.coerceAtLeast(1f)))
                                        scaleX = frame.scale
                                        scaleY = frame.scale
                                        alpha = frame.alpha
                                    },
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
                                                .alpha(if (tile.id == launchingTileId) 0f else 1f),
                                            onClick = { bounds -> handleTileClick(tile, bounds) },
                                        ) {
                                            handleTileLongClick(tile)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBandOverview && interactionEnabled,
                        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 1.12f),
                        exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 1.12f),
                    ) {
                        StartBandOverview(
                            bands = packed.bands,
                            onBandClick = ::zoomToBand,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keep both controls in the chrome row so they never move tile cells.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-9).dp)
                        .size(48.dp)
                        .clickable(
                            enabled = interactionEnabled,
                            onClick = onNavigateToAllApps,
                        )
                        .semantics { contentDescription = "All apps" },
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon(
                        glyph = "arrow_down",
                        color = Color.White.copy(alpha = 0.9f),
                        size = 30.dp,
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .clickable(
                            enabled = interactionEnabled,
                            onClick = ::openBandOverview,
                        )
                        .semantics { contentDescription = "Open Start overview" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFF8A8A8A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(2.dp)
                                .background(Color(0xFF303030)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartBandOverview(
    bands: List<StartTileBand>,
    onBandClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.background(Color(0xFF180052))) {
        val thumbnailHeight = minOf(220.dp, maxHeight - 40.dp).coerceAtLeast(48.dp)
        LazyRow(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(bands, key = { _, band -> band.key }) { index, band ->
                val aspect = band.columns.toFloat() / band.rows.coerceAtLeast(1)
                Column(Modifier.width((thumbnailHeight * aspect).coerceAtLeast(64.dp))
                    .clickable { onBandClick(index) }
                    .semantics { contentDescription = "Open ${band.groupName} group ${index + 1}" }) {
                    Text(band.groupName, color = Color.White, fontSize = 14.sp,
                        maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
                    Canvas(Modifier.fillMaxWidth().height(thumbnailHeight)) {
                        val cell = minOf(size.width / band.columns, size.height / band.rows)
                        val gap = cell * .08f
                        band.tiles.forEach { placed ->
                            drawRect(placed.tile.colorValue.toTileColor(),
                                topLeft = Offset(placed.column * cell, placed.row * cell),
                                size = Size(placed.columns * cell - gap, placed.rows * cell - gap))
                        }
                    }
                }
            }
        }
    }
}
