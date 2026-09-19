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
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
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
import com.flivoro.tile8auncher.features.HostedWidgetTile
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.passiveDoubleTap
import com.flivoro.tile8auncher.features.performStartDoubleTapAction
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.components.MetroIcon
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val START_WITHIN_GROUP_SPACING_DP = 8f
private const val START_GROUP_GUTTER_DP = 36f
private const val START_END_GROUP_DROP_ZONE_DP = 56f
private const val TILE_REORDER_DURATION_MS = 180

private data class EntranceViewportSnapshot(
    val startBand: Int,
    val startOffsetPx: Int,
)

private data class GroupDialogRequest(
    val title: String,
    val initialValue: String,
    val selectedIds: Set<String>,
    val renameWholeGroup: Boolean,
)

private data class StartBandDropTarget(
    val key: String,
    val groupId: String,
    val groupName: String,
    val continuationIndex: Int,
    val columns: Int,
    val rows: Int,
    val cellPx: Float,
    val gapPx: Float,
    val bounds: Rect,
)

private data class StartTileGridPosition(
    val groupId: String,
    val groupName: String,
    val continuationIndex: Int,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
)

private data class StartGridDropKey(
    val groupId: String,
    val groupName: String,
    val continuationIndex: Int,
    val column: Int,
    val row: Int,
)

private data class StartGroupGutterDropTarget(
    val key: String,
    val beforeGroupId: String?,
    val bounds: Rect,
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
    val context = LocalContext.current
    val dragDensity = LocalDensity.current
    val entrance = remember { Animatable(0f) }
    var playingKind by remember { mutableStateOf(entranceKind) }
    val scope = rememberCoroutineScope()
    var lastStartedRequest by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var entranceRunning by remember { mutableStateOf(false) }
    var showBandOverview by remember { mutableStateOf(false) }
    var viewportSnapshot by remember {
        mutableStateOf(EntranceViewportSnapshot(startBand = 0, startOffsetPx = 0))
    }

    // Start customization state. packStartTiles remains the single geometry source; drag can now
    // persist a snapped band/column/row so intentional Windows-style gaps survive recomposition.
    var selectedTileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragTiles by remember { mutableStateOf<List<TileModel>?>(null) }
    var draggingTileId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOriginBounds by remember { mutableStateOf(Rect.Zero) }
    var lastGridDrop by remember { mutableStateOf<StartGridDropKey?>(null) }
    var dragNewGroupId by remember { mutableStateOf<String?>(null) }
    var activeGutterKey by remember { mutableStateOf<String?>(null) }
    var tileViewportBounds by remember { mutableStateOf(Rect.Zero) }
    var showResizeChoices by remember { mutableStateOf(false) }
    var openFolderTile by remember { mutableStateOf<TileModel?>(null) }
    var groupDialog by remember { mutableStateOf<GroupDialogRequest?>(null) }
    val tileBounds = remember { mutableMapOf<String, Rect>() }
    val bandDropTargets = remember { mutableMapOf<String, StartBandDropTarget>() }
    val gutterDropTargets = remember { mutableMapOf<String, StartGroupGutterDropTarget>() }
    val tileGridPositions = remember { mutableMapOf<String, StartTileGridPosition>() }

    val latestInteractionEnabled = rememberUpdatedState(interactionEnabled)
    val latestOnTileClick = rememberUpdatedState(onTileClick)
    val latestOnTileLongClick = rememberUpdatedState(onTileLongClick)
    val latestOnTilesChanged = rememberUpdatedState(onTilesChanged)
    val externalPinnedRevision = LauncherFeatureRuntime.pinnedTilesRevision
    val liveTilesRevision = LauncherFeatureRuntime.liveTilesRevision
    val doubleTapAction = LauncherFeatureStore.doubleTapAction(context)

    // Widget picker / backup restore run outside MainActivity by design. A tiny process-local revision
    // refreshes only the persisted tile list; it does not touch navigation or entrance requests.
    LaunchedEffect(externalPinnedRevision) {
        if (externalPinnedRevision > 0) {
            val reloaded = withContext(Dispatchers.IO) { appsRepository.loadPinnedTiles() }
            if (reloaded.map { it.id } != tiles.map { it.id } || reloaded != tiles) {
                latestOnTilesChanged.value(reloaded)
            }
        }
    }

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
            openFolderTile = null
            groupDialog = null
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
            openFolderTile = null
            groupDialog = null
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

    BackHandler(
        enabled = interactionEnabled &&
            (showBandOverview || selectedTileIds.isNotEmpty() || openFolderTile != null || groupDialog != null),
    ) {
        when {
            groupDialog != null -> groupDialog = null
            openFolderTile != null -> openFolderTile = null
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

    fun openTileOrFolder(tile: TileModel, bounds: Rect) {
        val folderPackages = LauncherFeatureStore.folderPackages(context, tile.id)
        if (folderPackages.isNotEmpty()) {
            openFolderTile = tile
        } else {
            latestOnTileClick.value(tile, bounds)
        }
    }

    fun handleTileClick(tile: TileModel, bounds: Rect) {
        if (!latestInteractionEnabled.value) return
        if (selectedTileIds.isNotEmpty()) {
            selectedTileIds = if (tile.id in selectedTileIds) selectedTileIds - tile.id else selectedTileIds + tile.id
            showResizeChoices = false
            return
        }
        if (!entranceRunning) {
            openTileOrFolder(tile, bounds)
            return
        }

        // Keep the frame that produced the live bounds. The launch overlay can then start from the
        // same transformed tile while the motion stops. Folder opening uses the same stop point.
        scope.launch {
            entrance.stop()
            entranceRunning = false
            if (latestInteractionEnabled.value) openTileOrFolder(tile, bounds)
        }
    }

    fun beginTileDrag(tile: TileModel, bounds: Rect) {
        if (!latestInteractionEnabled.value) return
        fun enterCustomization() {
            showBandOverview = false
            openFolderTile = null
            selectedTileIds = setOf(tile.id)
            showResizeChoices = false
            dragTiles = tiles.toList()
            draggingTileId = tile.id
            dragOriginBounds = bounds
            dragOffset = Offset.Zero
            lastGridDrop = null
            activeGutterKey = null
            dragNewGroupId = "group:${System.currentTimeMillis()}:${tile.id}"
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
        val visualCenter = dragOriginBounds.center + dragOffset
        val current = dragTiles ?: tiles.toList()
        val dragged = current.firstOrNull { it.id == draggedId } ?: return

        // The wider Windows 8.1 inter-group gutter is a real drop target. Dropping on it creates
        // a new group at that exact position, signalled by a vertical separator.
        val gutter = gutterDropTargets.values
            .firstOrNull { target -> target.bounds.contains(visualCenter) }
        if (gutter != null) {
            val newGroupId = dragNewGroupId ?: "group:${System.currentTimeMillis()}:$draggedId"
            dragNewGroupId = newGroupId
            activeGutterKey = gutter.key
            lastGridDrop = null
            dragTiles = moveDraggedTileToNewGroup(
                tiles = current,
                draggedId = draggedId,
                newGroupId = newGroupId,
                insertBeforeGroupId = gutter.beforeGroupId,
            )
            return
        }
        activeGutterKey = null

        val viewport = tileViewportBounds
        val candidates = bandDropTargets.values
            .filter { target -> viewport == Rect.Zero || target.bounds.overlaps(viewport) }
        val targetBand = candidates.firstOrNull { it.bounds.contains(visualCenter) }
            ?: candidates.minByOrNull { distanceSquaredToRect(visualCenter, it.bounds) }
            ?: return

        val span = dragged.size.startTileSpan()
        if (span.columns > targetBand.columns || span.rows > targetBand.rows) return

        val stepPx = (targetBand.cellPx + targetBand.gapPx).coerceAtLeast(1f)
        val tileWidthPx =
            span.columns * targetBand.cellPx + (span.columns - 1) * targetBand.gapPx
        val tileHeightPx =
            span.rows * targetBand.cellPx + (span.rows - 1) * targetBand.gapPx
        val visualLeft = visualCenter.x - tileWidthPx / 2f
        val visualTop = visualCenter.y - tileHeightPx / 2f
        val localLeft = visualLeft - targetBand.bounds.left
        val localTop = visualTop - targetBand.bounds.top
        val column = (localLeft / stepPx).roundToInt()
            .coerceIn(0, targetBand.columns - span.columns)
        val row = (localTop / stepPx).roundToInt()
            .coerceIn(0, targetBand.rows - span.rows)

        val dropKey = StartGridDropKey(
            groupId = targetBand.groupId,
            groupName = targetBand.groupName,
            continuationIndex = targetBand.continuationIndex,
            column = column,
            row = row,
        )
        if (dropKey == lastGridDrop) return

        val conflictIds = tileGridPositions
            .filter { (id, position) ->
                id != draggedId &&
                    position.groupId == targetBand.groupId &&
                    position.continuationIndex == targetBand.continuationIndex &&
                    gridRectanglesOverlap(
                        columnA = column,
                        rowA = row,
                        columnsA = span.columns,
                        rowsA = span.rows,
                        columnB = position.column,
                        rowB = position.row,
                        columnsB = position.columns,
                        rowsB = position.rows,
                    )
            }
            .keys

        var working = moveDraggedTileToExistingGroup(
            tiles = current,
            draggedId = draggedId,
            targetGroupId = targetBand.groupId,
            targetGroupName = targetBand.groupName,
        )

        working = working.map { tile ->
            when {
                tile.id == draggedId -> tile.copy(
                    groupId = targetBand.groupId,
                    groupName = targetBand.groupName,
                    startBand = targetBand.continuationIndex,
                    startColumn = column,
                    startRow = row,
                )
                tile.id in conflictIds -> tile.copy(
                    startBand = null,
                    startColumn = null,
                    startRow = null,
                )
                else -> tile
            }
        }
        dragTiles = normalizeStartTileOrder(working)
        lastGridDrop = dropKey
    }

    fun finishTileDrag(commit: Boolean) {
        if (draggingTileId == null) return
        val result = dragTiles
        draggingTileId = null
        dragOffset = Offset.Zero
        lastGridDrop = null
        activeGutterKey = null
        dragNewGroupId = null
        dragTiles = null
        if (commit && result != null && result != tiles) {
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

    // Keep direct manipulation alive at the horizontal edges. Windows 8.1 lets a held
    // Start tile travel beyond the current viewport; programmatic scrolling keeps the pointer
    // anchored while LazyRow reveals the next/previous band.
    LaunchedEffect(draggingTileId) {
        if (draggingTileId == null) return@LaunchedEffect
        val edgePx = with(dragDensity) { 72.dp.toPx() }
        val maxStepPx = with(dragDensity) { 20.dp.toPx() }
        while (draggingTileId != null) {
            val viewport = tileViewportBounds
            if (viewport.width > 0f) {
                val centerX = (dragOriginBounds.center + dragOffset).x
                val leftStrength = ((viewport.left + edgePx - centerX) / edgePx).coerceIn(0f, 1f)
                val rightStrength = ((centerX - (viewport.right - edgePx)) / edgePx).coerceIn(0f, 1f)
                val step = when {
                    rightStrength > 0f -> maxStepPx * rightStrength
                    leftStrength > 0f -> -maxStepPx * leftStrength
                    else -> 0f
                }
                if (step != 0f) listState.scrollBy(step)
            }
            delay(16L)
        }
    }

    val entranceProgress = if (prehideForEntrance) 0f else entrance.value
    val visibleTiles = dragTiles ?: tiles

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .passiveDoubleTap {
                if (interactionEnabled && selectedTileIds.isEmpty() && openFolderTile == null) {
                    performStartDoubleTapAction(
                        context = context,
                        action = doubleTapAction,
                        onSearch = onSearchClick,
                        onAllApps = onNavigateToAllApps,
                        onCharms = onCharmsClick,
                    )
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(18.dp))

            // Start Screen Header: unchanged Windows 8.1 chrome and entrance alpha.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = StartEntranceMotion.headerAlpha(entranceProgress, playingKind) },
            ) {
                val availableWidth = maxWidth
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            MetroIcon("power", color = Color.White, size = if (isVeryNarrow) 18.dp else 20.dp)
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
                            MetroIcon("search", color = Color.White, size = if (isVeryNarrow) 18.dp else 20.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    metrics.bandWidthDp.dp.toPx() + START_WITHIN_GROUP_SPACING_DP.dp.toPx()
                }

                val tileSnapshot by remember(visibleTiles) { derivedStateOf { visibleTiles.toList() } }
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
                    .then(if (canScrollTiles) Modifier.elasticHorizontalScroll() else Modifier)

                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            if (coordinates.isAttached) tileViewportBounds = coordinates.boundsInWindow()
                        },
                ) {
                    LazyRow(
                        state = listState,
                        userScrollEnabled = canScrollTiles,
                        modifier = rowModifier,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        itemsIndexed(
                            items = packed.bands,
                            key = { _, band -> band.key },
                        ) { bandIndex, band ->
                            val cellPx = with(density) { metrics.cellDp.dp.toPx() }
                            val gapPx = with(density) { metrics.gapDp.dp.toPx() }
                            val previousBand = packed.bands.getOrNull(bandIndex - 1)
                            val startsNewGroup = previousBand != null && previousBand.groupId != band.groupId
                            val leadingSpacingDp = when {
                                bandIndex == 0 -> 0f
                                startsNewGroup -> START_GROUP_GUTTER_DP
                                else -> START_WITHIN_GROUP_SPACING_DP
                            }
                            val leadingSpacingPx = with(density) { leadingSpacingDp.dp.toPx() }
                            val gutterKey = "start-group-gutter:${band.groupId}:before"

                            DisposableEffect(band.key, startsNewGroup) {
                                onDispose {
                                    bandDropTargets.remove(band.key)
                                    if (startsNewGroup) gutterDropTargets.remove(gutterKey)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width((metrics.bandWidthDp + leadingSpacingDp).dp)
                                    .height(metrics.bandHeightDp.dp)
                                    .onGloballyPositioned { coordinates ->
                                        if (coordinates.isAttached) {
                                            val whole = coordinates.boundsInWindow()
                                            val bandBounds = Rect(
                                                left = whole.left + leadingSpacingPx,
                                                top = whole.top,
                                                right = whole.right,
                                                bottom = whole.bottom,
                                            )
                                            bandDropTargets[band.key] = StartBandDropTarget(
                                                key = band.key,
                                                groupId = band.groupId,
                                                groupName = band.groupName,
                                                continuationIndex = band.continuationIndex,
                                                columns = band.columns,
                                                rows = band.rows,
                                                cellPx = cellPx,
                                                gapPx = gapPx,
                                                bounds = bandBounds,
                                            )
                                            if (startsNewGroup) {
                                                gutterDropTargets[gutterKey] = StartGroupGutterDropTarget(
                                                    key = gutterKey,
                                                    beforeGroupId = band.groupId,
                                                    bounds = Rect(
                                                        left = whole.left,
                                                        top = whole.top,
                                                        right = whole.left + leadingSpacingPx,
                                                        bottom = whole.bottom,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        val position = StartEntranceMotion.viewportBandPosition(
                                            bandIndex,
                                            viewportSnapshot.startBand,
                                            viewportSnapshot.startOffsetPx,
                                            bandExtentPx,
                                        )
                                        val frame = StartEntranceMotion.frame(entranceProgress, position, playingKind)
                                        translationX = StartEntranceMotion.translationX(
                                            frame = frame,
                                            kind = playingKind,
                                            viewportWidthPx = viewportWidthPx,
                                            bandWidthPx = bandWidthPx,
                                        )
                                        transformOrigin = TransformOrigin(
                                            .5f,
                                            viewportHeightPx / (2f * size.height.coerceAtLeast(1f)),
                                        )
                                        scaleX = frame.scale
                                        scaleY = frame.scale
                                        alpha = frame.alpha
                                    },
                            ) {
                                if (startsNewGroup && activeGutterKey == gutterKey) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = ((leadingSpacingDp / 2f) - 2f).dp)
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = 6.dp)
                                            .background(Color.White.copy(alpha = 0.92f)),
                                    )
                                }

                                band.tiles.forEach { placed ->
                                    key(placed.tile.id) {
                                        val tile = placed.tile
                                        val appIcon = tile.packageName?.let { rememberAppIcon(appsRepository, it) }
                                        val tileWidth = (
                                            placed.columns * metrics.cellDp +
                                                (placed.columns - 1) * metrics.gapDp
                                            ).dp
                                        val tileHeight = (
                                            placed.rows * metrics.cellDp +
                                                (placed.rows - 1) * metrics.gapDp
                                            ).dp
                                        val targetX = (
                                            leadingSpacingDp + placed.column * (metrics.cellDp + metrics.gapDp)
                                            ).dp
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
                                        val widgetIds = LauncherFeatureStore.widgetStackIds(context, tile.id)

                                        DisposableEffect(tile.id) {
                                            onDispose {
                                                tileBounds.remove(tile.id)
                                                tileGridPositions.remove(tile.id)
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = animatedX, y = animatedY)
                                                .size(tileWidth, tileHeight)
                                                .zIndex(if (isDragging) 3f else if (isSelected) 1f else 0f)
                                                .onGloballyPositioned { coordinates ->
                                                    if (coordinates.isAttached) {
                                                        tileBounds[tile.id] = coordinates.boundsInWindow()
                                                        tileGridPositions[tile.id] = StartTileGridPosition(
                                                            groupId = band.groupId,
                                                            groupName = band.groupName,
                                                            continuationIndex = band.continuationIndex,
                                                            column = placed.column,
                                                            row = placed.row,
                                                            columns = placed.columns,
                                                            rows = placed.rows,
                                                        )
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
                                            // Android widget views are mounted only when the fitted Start entrance is
                                            // settled. During its short/long entrance the ordinary tile face stays in
                                            // the exact existing graphics transform, avoiding AndroidView frame jitter.
                                            if (widgetIds.isNotEmpty() && !entranceRunning && interactionEnabled) {
                                                HostedWidgetTile(widgetIds = widgetIds, modifier = Modifier.fillMaxSize())
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(28.dp)
                                                        .background(Color(0xAA180424))
                                                        .combinedClickable(
                                                            onClick = {
                                                                selectedTileIds = if (tile.id in selectedTileIds) {
                                                                    selectedTileIds - tile.id
                                                                } else {
                                                                    selectedTileIds + tile.id
                                                                }
                                                            },
                                                            onLongClick = {
                                                                tileBounds[tile.id]?.let { beginTileDrag(tile, it) }
                                                            },
                                                        ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text("⋮", color = Color.White, fontSize = 17.sp)
                                                }
                                            } else {
                                                WindowsTileView(
                                                    tile = tile,
                                                    appIcon = appIcon,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onClick = { bounds -> handleTileClick(tile, bounds) },
                                                    onLongClick = { selectedTileIds = setOf(tile.id) },
                                                    dragEnabled = interactionEnabled && launchingTileId == null,
                                                    onDragStart = { bounds -> beginTileDrag(tile, bounds) },
                                                    onDrag = ::moveDraggedTile,
                                                    onDragEnd = { finishTileDrag(commit = true) },
                                                    onDragCancel = { finishTileDrag(commit = false) },
                                                )
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(20.dp)
                                                        .background(Color(0xCC6E6E6E)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    MetroIcon("arrow_down", color = Color.White.copy(alpha = 0.9f), size = 30.dp)
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
                        modifier = Modifier.size(18.dp).background(Color(0xFF8A8A8A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(10.dp).height(2.dp).background(Color(0xFF303030)))
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
            val canCreateFolder = selectedTiles.size >= 2 && selectedTiles.all { !it.packageName.isNullOrBlank() }
            val selectedWidgetIds = selectedTiles.flatMap { LauncherFeatureStore.widgetStackIds(context, it.id) }
            val canStackWidgets = selectedTiles.size >= 2 &&
                selectedTiles.all { LauncherFeatureStore.widgetStackIds(context, it.id).isNotEmpty() }
            val singlePackage = selectedTiles.singleOrNull()?.packageName
            val selectedLiveTileEnabled = remember(singlePackage, liveTilesRevision) {
                singlePackage?.let { LauncherFeatureStore.isLiveTileEnabled(context, it) }
            }
            val oneGroup = selectedTiles.map { it.groupName }.distinct().singleOrNull()
            val existingGroups = tiles.map { it.groupName.trim().ifEmpty { "Start" } }.distinct()

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
                    selectedTiles.forEach { tile ->
                        LauncherFeatureStore.removeFolder(context, tile.id)
                        LauncherFeatureStore.removeWidgetStack(context, tile.id)
                    }
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
                liveTileEnabled = selectedLiveTileEnabled,
                onToggleLiveTile = singlePackage?.let { packageName ->
                    {
                        val enabled = LauncherFeatureStore.isLiveTileEnabled(context, packageName)
                        LauncherFeatureStore.setLiveTileEnabled(context, packageName, !enabled)
                    }
                },
                onCreateFolder = if (canCreateFolder) {
                    {
                        val first = selectedTiles.first()
                        val firstIndex = tiles.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
                        val folder = TileModel(
                            id = "folder_${System.currentTimeMillis()}",
                            title = "Folder",
                            size = TileSize.MEDIUM,
                            colorValue = first.colorValue,
                            iconGlyph = "app",
                            groupName = first.groupName,
                            order = firstIndex,
                        )
                        LauncherFeatureStore.setFolderPackages(
                            context,
                            folder.id,
                            selectedTiles.mapNotNull { it.packageName }.distinct(),
                        )
                        val updated = tiles.filterNot { it.id in selectedTileIds }.toMutableList()
                        updated.add(firstIndex.coerceAtMost(updated.size), folder)
                        latestOnTilesChanged.value(normalizeStartTileOrder(updated))
                        selectedTileIds = emptySet()
                    }
                } else null,
                onStackWidgets = if (canStackWidgets) {
                    {
                        val first = selectedTiles.first()
                        val mergedIds = selectedWidgetIds.distinct()
                        LauncherFeatureStore.setWidgetStackIds(context, first.id, mergedIds)
                        selectedTiles.drop(1).forEach { LauncherFeatureStore.removeWidgetStack(context, it.id) }
                        val updated = tiles
                            .filterNot { it.id in selectedTileIds && it.id != first.id }
                            .map { tile ->
                                if (tile.id == first.id) tile.copy(title = "Widget stack", size = TileSize.WIDE)
                                else tile
                            }
                        latestOnTilesChanged.value(normalizeStartTileOrder(updated))
                        selectedTileIds = emptySet()
                    }
                } else null,
                onRenameGroup = oneGroup?.let { groupName ->
                    {
                        groupDialog = GroupDialogRequest(
                            title = "Name group",
                            initialValue = groupName,
                            selectedIds = selectedTileIds,
                            renameWholeGroup = true,
                        )
                    }
                },
                onMoveGroup = {
                    groupDialog = GroupDialogRequest(
                        title = "Move to group",
                        initialValue = oneGroup ?: existingGroups.firstOrNull().orEmpty(),
                        selectedIds = selectedTileIds,
                        renameWholeGroup = false,
                    )
                },
                onDone = {
                    selectedTileIds = emptySet()
                    showResizeChoices = false
                },
            )
        }
    }

    openFolderTile?.let { folder ->
        StartFolderDialog(
            tile = folder,
            appsRepository = appsRepository,
            onDismiss = { openFolderTile = null },
        )
    }

    groupDialog?.let { request ->
        StartGroupNameDialog(
            title = request.title,
            initialValue = request.initialValue,
            suggestions = tiles.map { it.groupName.trim().ifEmpty { "Start" } }.distinct(),
            onDismiss = { groupDialog = null },
            onConfirm = { newName ->
                val oldGroup = request.initialValue
                val updated = tiles.map { tile ->
                    when {
                        request.renameWholeGroup && tile.groupName == oldGroup -> tile.copy(groupName = newName)
                        !request.renameWholeGroup && tile.id in request.selectedIds -> tile.copy(
                            groupName = newName,
                            startBand = null,
                            startColumn = null,
                            startRow = null,
                        )
                        else -> tile
                    }
                }
                latestOnTilesChanged.value(normalizeStartTileOrder(updated))
                groupDialog = null
                selectedTileIds = emptySet()
            },
        )
    }
}

internal fun moveDraggedTileToExistingGroup(
    tiles: List<TileModel>,
    draggedId: String,
    targetGroupId: String,
    targetGroupName: String,
): List<TileModel> {
    val dragged = tiles.firstOrNull { it.id == draggedId } ?: return tiles
    if (dragged.effectiveStartGroupId() == targetGroupId) return tiles

    val working = tiles.filterNot { it.id == draggedId }.toMutableList()
    val insertionIndex = working.indexOfLast { it.effectiveStartGroupId() == targetGroupId }
        .let { if (it >= 0) it + 1 else working.size }
    working.add(
        insertionIndex.coerceIn(0, working.size),
        dragged.copy(
            groupId = targetGroupId,
            groupName = targetGroupName,
            startBand = null,
            startColumn = null,
            startRow = null,
        ),
    )
    return normalizeStartTileOrder(working)
}

internal fun moveDraggedTileToNewGroup(
    tiles: List<TileModel>,
    draggedId: String,
    newGroupId: String,
    insertBeforeGroupId: String?,
): List<TileModel> {
    val dragged = tiles.firstOrNull { it.id == draggedId } ?: return tiles
    val working = tiles.filterNot { it.id == draggedId }.toMutableList()
    val insertionIndex = insertBeforeGroupId
        ?.let { groupId -> working.indexOfFirst { it.effectiveStartGroupId() == groupId } }
        ?.takeIf { it >= 0 }
        ?: working.size

    working.add(
        insertionIndex.coerceIn(0, working.size),
        dragged.copy(
            groupId = newGroupId,
            groupName = "",
            startBand = 0,
            startColumn = 0,
            startRow = 0,
        ),
    )
    return normalizeStartTileOrder(working)
}

private fun gridRectanglesOverlap(
    columnA: Int,
    rowA: Int,
    columnsA: Int,
    rowsA: Int,
    columnB: Int,
    rowB: Int,
    columnsB: Int,
    rowsB: Int,
): Boolean =
    columnA < columnB + columnsB &&
        columnA + columnsA > columnB &&
        rowA < rowB + rowsB &&
        rowA + rowsA > rowB

private fun distanceSquaredToRect(point: Offset, rect: Rect): Float {
    val dx = when {
        point.x < rect.left -> rect.left - point.x
        point.x > rect.right -> point.x - rect.right
        else -> 0f
    }
    val dy = when {
        point.y < rect.top -> rect.top - point.y
        point.y > rect.bottom -> point.y - rect.bottom
        else -> 0f
    }
    return dx * dx + dy * dy
}

@Composable
private fun StartCustomizationBar(
    selectedTiles: List<TileModel>,
    showResizeChoices: Boolean,
    onToggleResizeChoices: () -> Unit,
    onResize: (TileSize) -> Unit,
    onUnpin: () -> Unit,
    onCustomize: () -> Unit,
    liveTileEnabled: Boolean?,
    onToggleLiveTile: (() -> Unit)?,
    onCreateFolder: (() -> Unit)?,
    onStackWidgets: (() -> Unit)?,
    onRenameGroup: (() -> Unit)?,
    onMoveGroup: (() -> Unit)?,
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
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartCommandButton("Unpin from Start", "unpin", onUnpin)
            if (singleTile != null) {
                StartCommandButton("Resize", "app", onToggleResizeChoices)
                if (onToggleLiveTile != null && liveTileEnabled != null) {
                    StartCommandButton(
                        if (liveTileEnabled) "Turn live tile off" else "Turn live tile on",
                        "mail",
                        onToggleLiveTile,
                    )
                }
                StartCommandButton("Customize", "settings", onCustomize)
            }
            onCreateFolder?.let { StartCommandButton("Create folder", "app", it) }
            onStackWidgets?.let { StartCommandButton("Stack widgets", "app", it) }
            onRenameGroup?.let { StartCommandButton("Name group", "settings", it) }
            onMoveGroup?.let { StartCommandButton("Move group", "arrow_down", it) }
            StartCommandButton("Done", "arrow_down", onDone)
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
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).border(2.dp, Color.White, CircleShape),
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
        LazyRow(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(bands, key = { _, band -> band.key }) { index, band ->
                val aspect = band.columns.toFloat() / band.rows.coerceAtLeast(1)
                Column(
                    Modifier
                        .width((thumbnailHeight * aspect).coerceAtLeast(64.dp))
                        .clickable { onBandClick(index) }
                        .semantics { contentDescription = "Open ${band.groupName} group ${index + 1}" },
                ) {
                    Text(
                        band.groupName,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Canvas(Modifier.fillMaxWidth().height(thumbnailHeight)) {
                        val cell = minOf(size.width / band.columns, size.height / band.rows)
                        val gap = cell * .08f
                        band.tiles.forEach { placed ->
                            drawRect(
                                placed.tile.colorValue.toTileColor(),
                                topLeft = Offset(placed.column * cell, placed.row * cell),
                                size = Size(placed.columns * cell - gap, placed.rows * cell - gap),
                            )
                        }
                    }
                }
            }
        }
    }
}
