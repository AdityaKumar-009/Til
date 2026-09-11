package com.flivoro.tile8auncher.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val START_BAND_SPACING_DP = 24f
private const val TILE_REORDER_DURATION_MS = 180

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
    onTilesChanged: (List<TileModel>) -> Unit = {},
    modifier: Modifier = Modifier,
    launchingTileId: String? = null,
    entranceRequest: Int = 0,
    entranceKind: StartEntranceKind = StartEntranceKind.RETURN,
    entranceEnabled: Boolean = true,
    prehideForEntrance: Boolean = false,
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

    // Start customization state. The underlying grid remains packStartTiles; drag changes only
    // the stable input order, so no second layout model can drift away from the existing layout.
    var selectedTileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragTiles by remember { mutableStateOf<List<TileModel>?>(null) }
    var draggingTileId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOriginBounds by remember { mutableStateOf(Rect.Zero) }
    var lastSwapTargetId by remember { mutableStateOf<String?>(null) }
    var showResizeChoices by remember { mutableStateOf(false) }
    val tileBounds = remember { mutableMapOf<String, Rect>() }

    val latestInteractionEnabled = rememberUpdatedState(interactionEnabled)
    val latestOnTileClick = rememberUpdatedState(onTileClick)
    val latestOnTileLongClick = rememberUpdatedState(onTileLongClick)
    val latestOnTilesChanged = rememberUpdatedState(onTilesChanged)

    // Screen-off must prepare frame zero before Android reveals this window again. This is a
    // rendering gate only; StartEntranceMotion itself is intentionally untouched.
    LaunchedEffect(prehideForEntrance) {
        if (prehideForEntrance) {
            entrance.stop()
            entranceRunning = false
            lastStartedRequest = Int.MIN_VALUE
            entrance.snapTo(0f)
            selectedTileIds = emptySet()
            dragTiles = null
            draggingTileId = null
            dragOffset = Offset.Zero
        }
    }

    LaunchedEffect(entranceRequest, entranceEnabled, prehideForEntrance, tiles.isNotEmpty()) {
        if (tiles.isEmpty()) {
            entrance.stop()
            entranceRunning = false
            lastStartedRequest = Int.MIN_VALUE
            entrance.snapTo(0f)
            return@LaunchedEffect
        }

        if (prehideForEntrance) return@LaunchedEffect

        if (!entranceEnabled) {
            entrance.stop()
            entranceRunning = false
            // A return request received while Start is not the visible surface is stale. Consume it
            // now so returning from an app opened in All Apps cannot replay the short Start entrance
            // later when the user swipes back to Start.
            lastStartedRequest = entranceRequest
            entrance.snapTo(1f)
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
        if (!interactionEnabled) {
            showBandOverview = false
            selectedTileIds = emptySet()
            dragTiles = null
            draggingTileId = null
            dragOffset = Offset.Zero
        }
    }

    LaunchedEffect(tiles.map { it.id }) {
        val validIds = tiles.mapTo(mutableSetOf()) { it.id }
        selectedTileIds = selectedTileIds.filterTo(mutableSetOf()) { it in validIds }
        if (draggingTileId !in validIds) {
            dragTiles = null
            draggingTileId = null
            dragOffset = Offset.Zero
        }
    }

    BackHandler(enabled = interactionEnabled && (showBandOverview || selectedTileIds.isNotEmpty())) {
        when {
            showBandOverview -> showBandOverview = false
            selectedTileIds.isNotEmpty() -> {
                selectedTileIds = emptySet()
                showResizeChoices = false
                dragTiles = null
                draggingTileId = null
                dragOffset = Offset.Zero
            }
        }
    }

    fun handleTileClick(tile: TileModel, bounds: Rect) {
        if (!latestInteractionEnabled.value) return
        if (selectedTileIds.isNotEmpty()) {
            selectedTileIds = if (tile.id in selectedTileIds) {
                selectedTileIds - tile.id
            } else {
                selectedTileIds + tile.id
            }
            showResizeChoices = false
            return
        }
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

    fun beginTileDrag(tile: TileModel, bounds: Rect) {
        if (!latestInteractionEnabled.value) return
        fun enterCustomization() {
            showBandOverview = false
            selectedTileIds = setOf(tile.id)
            showResizeChoices = false
            dragTiles = tiles.toList()
            draggingTileId = tile.id
            dragOriginBounds = bounds
            dragOffset = Offset.Zero
            lastSwapTargetId = null
        }
        if (entranceRunning) {
            scope.launch {
                entrance.stop()
                entranceRunning = false
                entrance.snapTo(1f)
                enterCustomization()
            }
        } else {
            enterCustomization()
        }
    }

    fun moveDraggedTile(delta: Offset) {
        val draggedId = draggingTileId ?: return
        dragOffset += delta
        val center = dragOriginBounds.center + dragOffset
        val targetEntry = tileBounds.entries.firstOrNull { (id, bounds) ->
            id != draggedId && bounds.contains(center)
        }
        val targetId = targetEntry?.key
        if (targetId == null) {
            lastSwapTargetId = null
            return
        }
        if (targetId == lastSwapTargetId) return

        val targetBounds = targetEntry.value
        val sameVisualRow = abs(center.y - targetBounds.center.y) <= targetBounds.height * 0.45f
        val placeAfter = if (sameVisualRow) center.x >= targetBounds.center.x
            else center.y >= targetBounds.center.y
        val current = dragTiles ?: tiles.toList()
        dragTiles = reorderStartTiles(current, draggedId, targetId, placeAfter)
        lastSwapTargetId = targetId
    }

    fun finishTileDrag(commit: Boolean) {
        if (draggingTileId == null) return
        val result = dragTiles
        draggingTileId = null
        dragOffset = Offset.Zero
        lastSwapTargetId = null
        dragTiles = null
        if (commit && result != null && result.map { it.id } != tiles.map { it.id }) {
            latestOnTilesChanged.value(result)
        }
    }

    fun openBandOverview() {
        if (!interactionEnabled) return
        selectedTileIds = emptySet()
        showResizeChoices = false
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

    val entranceProgress = if (prehideForEntrance) 0f else entrance.value
    val visibleTiles = dragTiles ?: tiles

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Start Screen Header: "Start" and User/Power/Search/Add controls
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = StartEntranceMotion.headerAlpha(entranceProgress, playingKind) },
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
                                    enabled = interactionEnabled && selectedTileIds.isEmpty(),
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
                                    enabled = interactionEnabled && selectedTileIds.isEmpty(),
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
                                    enabled = interactionEnabled && selectedTileIds.isEmpty(),
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
                // Keep the existing tile/grid sizing based on the old padded viewport, while the
                // actual LazyRow viewport spans the screen. Endpoint spacing now belongs to the
                // scroll content, so it naturally scrolls away instead of becoming a permanent gutter.
                val layoutWidth = maxWidth - 48.dp
                val metrics = remember(layoutWidth, maxHeight) {
                    calculateStartGridMetrics(
                        availableWidthDp = layoutWidth.value,
                        availableHeightDp = maxHeight.value,
                    )
                }
                val density = LocalDensity.current
                val viewportWidthPx = with(density) { layoutWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val bandWidthPx = with(density) { metrics.bandWidthDp.dp.toPx() }
                val bandExtentPx = with(density) {
                    metrics.bandWidthDp.dp.toPx() + START_BAND_SPACING_DP.dp.toPx()
                }

                val tileSnapshot by remember(visibleTiles) {
                    derivedStateOf { visibleTiles.toList() }
                }
                val packed = remember(tileSnapshot, metrics.rows, metrics.columns) {
                    packStartTiles(
                        tiles = tileSnapshot,
                        maxRows = metrics.rows,
                        maxColumns = metrics.columns,
                    )
                }

                val canScrollTiles = interactionEnabled && draggingTileId == null
                val rowModifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canScrollTiles) Modifier.elasticHorizontalScroll() else Modifier,
                    )

                Box(Modifier.fillMaxSize()) {
                    LazyRow(
                        state = listState,
                        userScrollEnabled = canScrollTiles,
                        modifier = rowModifier,
                        contentPadding = PaddingValues(horizontal = 24.dp),
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
                                        val frame = StartEntranceMotion.frame(entranceProgress, position, playingKind)
                                        translationX = StartEntranceMotion.translationX(
                                            frame = frame,
                                            kind = playingKind,
                                            viewportWidthPx = viewportWidthPx,
                                            bandWidthPx = bandWidthPx,
                                        )
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
                                        val targetX = (placed.column * (metrics.cellDp + metrics.gapDp)).dp
                                        val targetY = (placed.row * (metrics.cellDp + metrics.gapDp)).dp
                                        val animatedX by animateDpAsState(
                                            targetValue = targetX,
                                            animationSpec = tween(TILE_REORDER_DURATION_MS, easing = FastOutSlowInEasing),
                                            label = "StartTileX:${tile.id}",
                                        )
                                        val animatedY by animateDpAsState(
                                            targetValue = targetY,
                                            animationSpec = tween(TILE_REORDER_DURATION_MS, easing = FastOutSlowInEasing),
                                            label = "StartTileY:${tile.id}",
                                        )
                                        val isDragging = tile.id == draggingTileId
                                        val isSelected = tile.id in selectedTileIds
                                        val selectionScale by animateFloatAsState(
                                            targetValue = if (isDragging) 1.055f else 1f,
                                            animationSpec = tween(110, easing = FastOutSlowInEasing),
                                            label = "StartTileLift:${tile.id}",
                                        )

                                        Box(
                                            modifier = Modifier
                                                .offset(x = animatedX, y = animatedY)
                                                .size(tileWidth, tileHeight)
                                                .zIndex(if (isDragging) 3f else if (isSelected) 1f else 0f)
                                                .onGloballyPositioned { coordinates ->
                                                    if (coordinates.isAttached) {
                                                        tileBounds[tile.id] = coordinates.boundsInWindow()
                                                    }
                                                }
                                                .graphicsLayer {
                                                    scaleX = selectionScale
                                                    scaleY = selectionScale
                                                    if (isDragging) {
                                                        translationX = dragOffset.x
                                                        translationY = dragOffset.y
                                                        shadowElevation = 18f
                                                    }
                                                }
                                                .alpha(if (tile.id == launchingTileId) 0f else 1f),
                                        ) {
                                            WindowsTileView(
                                                tile = tile,
                                                appIcon = appIcon,
                                                modifier = Modifier.fillMaxSize(),
                                                onClick = { bounds -> handleTileClick(tile, bounds) },
                                                onLongClick = {
                                                    // Long press is owned by drag on Start. This fallback
                                                    // remains for accessibility/non-drag callers.
                                                    selectedTileIds = setOf(tile.id)
                                                },
                                                dragEnabled = interactionEnabled && launchingTileId == null,
                                                onDragStart = { bounds -> beginTileDrag(tile, bounds) },
                                                onDrag = ::moveDraggedTile,
                                                onDragEnd = { finishTileDrag(commit = true) },
                                                onDragCancel = { finishTileDrag(commit = false) },
                                            )

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(20.dp)
                                                        .background(Color(0xCC6E6E6E)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = "✓",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showBandOverview && interactionEnabled,
                        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 1.12f),
                        exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 1.12f),
                    ) {
                        StartBandOverview(
                            bands = packed.bands,
                            onBandClick = ::zoomToBand,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keep both controls in the chrome row so they never move tile cells.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-9).dp)
                        .size(48.dp)
                        .clickable(
                            enabled = interactionEnabled && selectedTileIds.isEmpty(),
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
                            enabled = interactionEnabled && selectedTileIds.isEmpty(),
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

        AnimatedVisibility(
            visible = selectedTileIds.isNotEmpty() && interactionEnabled,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(100)),
        ) {
            val selectedTiles = tiles.filter { it.id in selectedTileIds }
            StartCustomizationBar(
                selectedTiles = selectedTiles,
                showResizeChoices = showResizeChoices,
                onToggleResizeChoices = { showResizeChoices = !showResizeChoices },
                onResize = { size ->
                    val selected = selectedTiles.singleOrNull() ?: return@StartCustomizationBar
                    val updated = normalizeStartTileOrder(
                        tiles.map { tile -> if (tile.id == selected.id) tile.copy(size = size) else tile },
                    )
                    latestOnTilesChanged.value(updated)
                    showResizeChoices = false
                },
                onUnpin = {
                    val updated = normalizeStartTileOrder(tiles.filterNot { it.id in selectedTileIds })
                    latestOnTilesChanged.value(updated)
                    selectedTileIds = emptySet()
                    showResizeChoices = false
                },
                onCustomize = {
                    selectedTiles.singleOrNull()?.let { latestOnTileLongClick.value(it) }
                    selectedTileIds = emptySet()
                    showResizeChoices = false
                },
                onDone = {
                    selectedTileIds = emptySet()
                    showResizeChoices = false
                },
            )
        }
    }
}

@Composable
private fun StartCustomizationBar(
    selectedTiles: List<TileModel>,
    showResizeChoices: Boolean,
    onToggleResizeChoices: () -> Unit,
    onResize: (TileSize) -> Unit,
    onUnpin: () -> Unit,
    onCustomize: () -> Unit,
    onDone: () -> Unit,
) {
    val singleTile = selectedTiles.singleOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF0180424))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        AnimatedVisibility(visible = showResizeChoices && singleTile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TileSize.entries.forEach { size ->
                    val selected = singleTile?.size == size
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .background(if (selected) Color(0xFF6B4AA5) else Color(0xFF32106B))
                            .border(1.dp, if (selected) Color.White else Color(0x66FFFFFF))
                            .clickable { onResize(size) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = size.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartCommandButton(label = "Unpin from Start", glyph = "unpin", onClick = onUnpin)
            if (singleTile != null) {
                StartCommandButton(label = "Resize", glyph = "app", onClick = onToggleResizeChoices)
                StartCommandButton(label = "Customize", glyph = "settings", onClick = onCustomize)
            }
            StartCommandButton(label = "Done", glyph = "arrow_down", onClick = onDone)
        }
    }
}

@Composable
private fun StartCommandButton(
    label: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MetroIcon(glyph = glyph, color = Color.White, size = 19.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = WindowsTypography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White,
            maxLines = 1,
        )
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