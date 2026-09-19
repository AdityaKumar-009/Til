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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.flivoro.tile8auncher.ui.components.WindowsTileFace
import com.flivoro.tile8auncher.ui.components.WindowsTileView
import com.flivoro.tile8auncher.ui.components.elasticHorizontalScroll
import com.flivoro.tile8auncher.ui.components.rememberAppIcon
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import com.flivoro.tile8auncher.ui.theme.toTileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val START_WITHIN_GROUP_SPACING_DP = 8f
private const val START_GROUP_GUTTER_DP = 24f
private const val START_END_GROUP_DROP_ZONE_DP = 32f
private const val START_GROUP_LABEL_HEIGHT_DP = 24f
private const val TILE_REORDER_DURATION_MS = 170
private const val TILE_REORDER_DWELL_MS = 140L

private data class EntranceViewportSnapshot(
    val startBand: Int,
    val startOffsetPx: Int,
)

private data class GroupDialogRequest(
    val title: String,
    val initialValue: String,
    val selectedIds: Set<String>,
    val renameWholeGroup: Boolean,
    val groupId: String? = null,
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

private sealed interface StartDropProposal {
    data class Grid(
        val key: StartGridDropKey,
    ) : StartDropProposal

    data class NewGroup(
        val gutterKey: String,
        val beforeGroupId: String?,
    ) : StartDropProposal
}

private data class StartWallpaperScrollFrame(
    val isScrolling: Boolean,
    val firstIndex: Int,
    val firstOffsetPx: Int,
    val visibleOffsets: List<Pair<Any, Int>>,
)

internal fun absoluteStartScrollPx(
    itemWidthsPx: List<Float>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Float {
    val index = firstVisibleItemIndex.coerceIn(0, itemWidthsPx.size)
    var before = 0f
    for (i in 0 until index) before += itemWidthsPx[i]
    return (before + firstVisibleItemScrollOffset).coerceAtLeast(0f)
}

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
    initialWallpaperScrollPx: Float = 0f,
    onWallpaperScrollOffsetChanged: (Float) -> Unit = {},
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
    // The held tile is driven from the absolute pointer position in window coordinates.
    // This avoids accumulated-delta loss when child layouts recompose, scroll or consume events.
    var dragPointerWindow by remember { mutableStateOf(Offset.Zero) }
    var dragContactOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOriginBounds by remember { mutableStateOf(Rect.Zero) }
    var lastGridDrop by remember { mutableStateOf<StartGridDropKey?>(null) }
    var pendingDropProposal by remember { mutableStateOf<StartDropProposal?>(null) }
    var appliedDropProposal by remember { mutableStateOf<StartDropProposal?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var dragAutoScrollActive by remember { mutableStateOf(false) }
    var dragStartGridPositions by remember {
        mutableStateOf<Map<String, StartTileGridPosition>>(emptyMap())
    }
    var packedGridPositions by remember {
        mutableStateOf<Map<String, StartTileGridPosition>>(emptyMap())
    }
    var dragNewGroupId by remember { mutableStateOf<String?>(null) }
    var activeGutterKey by remember { mutableStateOf<String?>(null) }
    var tileViewportBounds by remember { mutableStateOf(Rect.Zero) }
    var showResizeChoices by remember { mutableStateOf(false) }
    var openFolderTile by remember { mutableStateOf<TileModel?>(null) }
    var groupDialog by remember { mutableStateOf<GroupDialogRequest?>(null) }
    var trackedWallpaperScrollPx by remember {
        mutableFloatStateOf(
            initialWallpaperScrollPx.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f,
        )
    }
    val tileBounds = remember { mutableMapOf<String, Rect>() }
    val bandDropTargets = remember { mutableMapOf<String, StartBandDropTarget>() }
    val gutterDropTargets = remember { mutableMapOf<String, StartGroupGutterDropTarget>() }
    val tileGridPositions = remember { mutableMapOf<String, StartTileGridPosition>() }

    val latestInteractionEnabled = rememberUpdatedState(interactionEnabled)
    val latestOnTileClick = rememberUpdatedState(onTileClick)
    val latestOnTileLongClick = rememberUpdatedState(onTileLongClick)
    val latestOnTilesChanged = rememberUpdatedState(onTilesChanged)
    val latestOnWallpaperScrollOffsetChanged = rememberUpdatedState(onWallpaperScrollOffsetChanged)
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
            previewJob?.cancel()
            previewJob = null
            dragAutoScrollActive = false
            dragTiles = null
            draggingTileId = null
            dragPointerWindow = Offset.Zero
            dragContactOffset = Offset.Zero
            dragStartGridPositions = emptyMap()
            pendingDropProposal = null
            appliedDropProposal = null
            lastGridDrop = null
            activeGutterKey = null
            dragNewGroupId = null
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
            previewJob?.cancel()
            previewJob = null
            dragAutoScrollActive = false
            dragTiles = null
            draggingTileId = null
            dragPointerWindow = Offset.Zero
            dragContactOffset = Offset.Zero
            dragStartGridPositions = emptyMap()
            pendingDropProposal = null
            appliedDropProposal = null
            lastGridDrop = null
            activeGutterKey = null
            dragNewGroupId = null
            openFolderTile = null
            groupDialog = null
        }
    }

    LaunchedEffect(tiles.map { it.id }) {
        val validIds = tiles.mapTo(mutableSetOf()) { it.id }
        selectedTileIds = selectedTileIds.filterTo(mutableSetOf()) { it in validIds }
        if (draggingTileId !in validIds) {
            previewJob?.cancel()
            previewJob = null
            dragAutoScrollActive = false
            dragTiles = null
            draggingTileId = null
            dragPointerWindow = Offset.Zero
            dragContactOffset = Offset.Zero
            dragStartGridPositions = emptyMap()
            pendingDropProposal = null
            appliedDropProposal = null
            lastGridDrop = null
            activeGutterKey = null
            dragNewGroupId = null
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
                previewJob?.cancel()
                previewJob = null
                dragAutoScrollActive = false
                dragTiles = null
                draggingTileId = null
                dragPointerWindow = Offset.Zero
                dragContactOffset = Offset.Zero
                dragStartGridPositions = emptyMap()
                pendingDropProposal = null
                appliedDropProposal = null
                lastGridDrop = null
                activeGutterKey = null
                dragNewGroupId = null
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

    fun beginTileDrag(
        tile: TileModel,
        bounds: Rect,
        pointerWindow: Offset = bounds.center,
    ) {
        if (!latestInteractionEnabled.value) return

        fun enterCustomization() {
            previewJob?.cancel()
            previewJob = null
            showBandOverview = false
            openFolderTile = null
            selectedTileIds = setOf(tile.id)
            showResizeChoices = false
            dragTiles = tiles.toList()
            draggingTileId = tile.id
            dragOriginBounds = bounds
            dragPointerWindow = pointerWindow
            dragContactOffset = Offset(
                x = (pointerWindow.x - bounds.left).coerceIn(0f, bounds.width),
                y = (pointerWindow.y - bounds.top).coerceIn(0f, bounds.height),
            )
            dragStartGridPositions = packedGridPositions.ifEmpty { tileGridPositions.toMap() }
            pendingDropProposal = null
            appliedDropProposal = null
            lastGridDrop = null
            activeGutterKey = null
            dragNewGroupId = "group:${System.currentTimeMillis()}:${tile.id}"
        }

        // Activate synchronously on the long-press frame. Entrance animation cleanup can finish
        // independently; the pointer must never wait for an Animatable coroutine.
        if (entranceRunning) {
            entranceRunning = false
            scope.launch {
                entrance.stop()
                entrance.snapTo(1f)
            }
        }
        enterCustomization()
    }

    fun dragVisualTopLeft(): Offset =
        Offset(
            x = dragPointerWindow.x - dragContactOffset.x,
            y = dragPointerWindow.y - dragContactOffset.y,
        )

    fun dragVisualCenter(): Offset {
        val topLeft = dragVisualTopLeft()
        return Offset(
            x = topLeft.x + dragOriginBounds.width / 2f,
            y = topLeft.y + dragOriginBounds.height / 2f,
        )
    }

    fun applyDropProposal(
        proposal: StartDropProposal,
        finalDrop: Boolean,
    ) {
        val draggedId = draggingTileId ?: return
        val base = tiles.toList()
        // Freeze every tile at the exact grid position it occupied when the drag started. Windows
        // tiles have explicit row/column positions; leaving most Tile8 tiles as first-fit/null was
        // why one hover could make half a group reshuffle. Only true conflicts are released.
        val anchoredBase = base.map { tile ->
            val position = dragStartGridPositions[tile.id]
            if (position == null) {
                tile
            } else {
                tile.copy(
                    groupId = position.groupId,
                    groupName = position.groupName,
                    startBand = position.continuationIndex,
                    startColumn = position.column,
                    startRow = position.row,
                )
            }
        }
        val dragged = anchoredBase.firstOrNull { it.id == draggedId } ?: return

        when (proposal) {
            is StartDropProposal.NewGroup -> {
                activeGutterKey = proposal.gutterKey

                // Windows shows the separator while hovering the gutter, but it does not rip the
                // source tile into a new group before release. Commit the group only on drop.
                if (!finalDrop) return

                val newGroupId = dragNewGroupId ?: "group:${System.currentTimeMillis()}:$draggedId"
                dragNewGroupId = newGroupId
                dragTiles = moveDraggedTileToNewGroup(
                    tiles = anchoredBase,
                    draggedId = draggedId,
                    newGroupId = newGroupId,
                    insertBeforeGroupId = proposal.beforeGroupId,
                )
                appliedDropProposal = proposal
                lastGridDrop = null
            }

            is StartDropProposal.Grid -> {
                activeGutterKey = null
                val key = proposal.key
                val targetBand = bandDropTargets.values.firstOrNull {
                    it.groupId == key.groupId &&
                        it.continuationIndex == key.continuationIndex
                } ?: return

                val span = dragged.size.startTileSpan()
                val conflictIds = dragStartGridPositions
                    .filter { (id, position) ->
                        id != draggedId &&
                            position.groupId == key.groupId &&
                            position.continuationIndex == key.continuationIndex &&
                            gridRectanglesOverlap(
                                columnA = key.column,
                                rowA = key.row,
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
                    tiles = anchoredBase,
                    draggedId = draggedId,
                    targetGroupId = key.groupId,
                    targetGroupName = targetBand.groupName,
                )

                working = working.map { tile ->
                    when {
                        tile.id == draggedId -> tile.copy(
                            groupId = key.groupId,
                            groupName = targetBand.groupName,
                            startBand = key.continuationIndex,
                            startColumn = key.column,
                            startRow = key.row,
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
                appliedDropProposal = proposal
                lastGridDrop = key
            }
        }
    }

    fun scheduleDropProposal(proposal: StartDropProposal?) {
        if (draggingTileId == null) return
        if (proposal == pendingDropProposal) return

        pendingDropProposal = proposal
        previewJob?.cancel()
        previewJob = null

        if (proposal == null) {
            activeGutterKey = null
            // If the pointer leaves a valid target, let neighbors glide back to their committed
            // positions while the held tile remains under the finger.
            dragTiles = tiles.toList()
            appliedDropProposal = null
            lastGridDrop = null
            return
        }

        if (proposal is StartDropProposal.NewGroup) {
            // Immediate separator only. Windows does not tear the source group apart merely by
            // hovering over a group gutter; the new group is committed on release.
            activeGutterKey = proposal.gutterKey
            dragTiles = tiles.toList()
            appliedDropProposal = null
            lastGridDrop = null
            return
        }

        activeGutterKey = null
        previewJob = scope.launch {
            delay(TILE_REORDER_DWELL_MS)
            if (draggingTileId != null && pendingDropProposal == proposal) {
                applyDropProposal(proposal, finalDrop = false)
            }
        }
    }

    fun proposalAt(visualCenter: Offset): StartDropProposal? {
        val draggedId = draggingTileId ?: return null
        val dragged = tiles.firstOrNull { it.id == draggedId } ?: return null

        gutterDropTargets.values
            .firstOrNull { target -> target.bounds.contains(visualCenter) }
            ?.let { gutter ->
                return StartDropProposal.NewGroup(
                    gutterKey = gutter.key,
                    beforeGroupId = gutter.beforeGroupId,
                )
            }

        val viewport = tileViewportBounds
        val candidates = bandDropTargets.values
            .filter { target -> viewport == Rect.Zero || target.bounds.overlaps(viewport) }
        val targetBand = candidates.firstOrNull { it.bounds.contains(visualCenter) }
            ?: candidates.minByOrNull { distanceSquaredToRect(visualCenter, it.bounds) }
            ?: return null

        val span = dragged.size.startTileSpan()
        if (span.columns > targetBand.columns || span.rows > targetBand.rows) return null

        val stepPx = (targetBand.cellPx + targetBand.gapPx).coerceAtLeast(1f)
        val tileWidthPx =
            span.columns * targetBand.cellPx + (span.columns - 1) * targetBand.gapPx
        val tileHeightPx =
            span.rows * targetBand.cellPx + (span.rows - 1) * targetBand.gapPx
        val visualLeft = visualCenter.x - tileWidthPx / 2f
        val visualTop = visualCenter.y - tileHeightPx / 2f
        val rawColumn = (visualLeft - targetBand.bounds.left) / stepPx
        val rawRow = (visualTop - targetBand.bounds.top) / stepPx

        val previousKey = when (val pending = pendingDropProposal) {
            is StartDropProposal.Grid -> pending.key
            else -> (appliedDropProposal as? StartDropProposal.Grid)?.key
        }?.takeIf {
            it.groupId == targetBand.groupId &&
                it.continuationIndex == targetBand.continuationIndex
        }

        val column = snapStartCell(
            rawCell = rawColumn,
            previousCell = previousKey?.column,
            maxStart = targetBand.columns - span.columns,
        )
        val row = snapStartCell(
            rawCell = rawRow,
            previousCell = previousKey?.row,
            maxStart = targetBand.rows - span.rows,
        )

        return StartDropProposal.Grid(
            StartGridDropKey(
                groupId = targetBand.groupId,
                groupName = targetBand.groupName,
                continuationIndex = targetBand.continuationIndex,
                column = column,
                row = row,
            ),
        )
    }

    fun moveDraggedPointer(pointerWindow: Offset) {
        if (draggingTileId == null) return
        dragPointerWindow = pointerWindow
        scheduleDropProposal(proposalAt(dragVisualCenter()))
    }

    fun finishTileDrag(commit: Boolean) {
        if (draggingTileId == null) return

        previewJob?.cancel()
        previewJob = null
        dragAutoScrollActive = false

        if (commit) {
            pendingDropProposal?.let { proposal ->
                applyDropProposal(proposal, finalDrop = true)
            }
        }

        val result = dragTiles
        draggingTileId = null
        dragPointerWindow = Offset.Zero
        dragContactOffset = Offset.Zero
        dragStartGridPositions = emptyMap()
        pendingDropProposal = null
        appliedDropProposal = null
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
        val edgePx = with(dragDensity) { 88.dp.toPx() }
        val maxStepPx = with(dragDensity) { 12.dp.toPx() }
        while (draggingTileId != null) {
            val viewport = tileViewportBounds
            if (viewport.width > 0f && dragPointerWindow != Offset.Zero) {
                val pointerX = dragPointerWindow.x
                val leftStrength = ((viewport.left + edgePx - pointerX) / edgePx).coerceIn(0f, 1f)
                val rightStrength = ((pointerX - (viewport.right - edgePx)) / edgePx).coerceIn(0f, 1f)

                val step = when {
                    rightStrength > 0f && listState.canScrollForward ->
                        maxStepPx * rightStrength * rightStrength
                    leftStrength > 0f && listState.canScrollBackward ->
                        -maxStepPx * leftStrength * leftStrength
                    else -> 0f
                }

                if (step != 0f) {
                    dragAutoScrollActive = true
                    val consumed = listState.scrollBy(step)
                    if (kotlin.math.abs(consumed) > 0.5f) {
                        delay(16L)
                        if (draggingTileId != null) {
                            scheduleDropProposal(proposalAt(dragVisualCenter()))
                        }
                        dragAutoScrollActive = false
                        continue
                    }
                    dragAutoScrollActive = false
                }
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
                val hasVisibleGroupLabels = remember(visibleTiles) {
                    visibleTiles.any { tile ->
                        tile.groupName.isNotBlank() &&
                            !(tile.effectiveStartGroupId() == "legacy:Start" && tile.groupName == "Start")
                    }
                }
                val groupLabelHeightDp = if (hasVisibleGroupLabels) START_GROUP_LABEL_HEIGHT_DP else 0f
                val tileAreaHeight = (maxHeight - groupLabelHeightDp.dp).coerceAtLeast(1.dp)
                val metrics = remember(layoutWidth, tileAreaHeight) {
                    calculateStartGridMetrics(
                        availableWidthDp = layoutWidth.value,
                        availableHeightDp = tileAreaHeight.value,
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

                val currentPackedGridPositions = remember(packed) {
                    buildMap<String, StartTileGridPosition> {
                        packed.bands.forEach { band ->
                            band.tiles.forEach { placed ->
                                put(
                                    placed.tile.id,
                                    StartTileGridPosition(
                                        groupId = band.groupId,
                                        groupName = band.groupName,
                                        continuationIndex = band.continuationIndex,
                                        column = placed.column,
                                        row = placed.row,
                                        columns = placed.columns,
                                        rows = placed.rows,
                                    ),
                                )
                            }
                        }
                    }
                }
                SideEffect {
                    packedGridPositions = currentPackedGridPositions
                }

                // LazyRow items are not equal-width once Windows group gutters are included.
                // Track the wallpaper from actual visible-item motion so crossing a group/band
                // boundary cannot teleport the background.
                val bandItemWidthsPx = remember(packed.bands, metrics.bandWidthDp, density.density) {
                    packed.bands.mapIndexed { index, band ->
                        val previous = packed.bands.getOrNull(index - 1)
                        val startsNewGroup = previous != null && previous.groupId != band.groupId
                        val leadingDp = when {
                            index == 0 -> 0f
                            startsNewGroup -> START_GROUP_GUTTER_DP
                            else -> START_WITHIN_GROUP_SPACING_DP
                        }
                        val trailingDp =
                            if (index == packed.bands.lastIndex) START_END_GROUP_DROP_ZONE_DP else 0f
                        with(density) {
                            (metrics.bandWidthDp + leadingDp + trailingDp).dp.toPx()
                        }
                    }
                }

                val latestBandItemWidthsPx = rememberUpdatedState(bandItemWidthsPx)

                LaunchedEffect(listState, bandItemWidthsPx.isNotEmpty()) {
                    if (bandItemWidthsPx.isEmpty()) return@LaunchedEffect

                    var previousOffsets = emptyMap<Any, Int>()
                    var previousFirstIndex: Int? = null
                    var wasScrolling = false

                    snapshotFlow {
                        StartWallpaperScrollFrame(
                            isScrolling = listState.isScrollInProgress,
                            firstIndex = listState.firstVisibleItemIndex,
                            firstOffsetPx = listState.firstVisibleItemScrollOffset,
                            visibleOffsets = listState.layoutInfo.visibleItemsInfo.map { info ->
                                info.key to info.offset
                            },
                        )
                    }.collect { frame ->
                        val widths = latestBandItemWidthsPx.value
                        if (widths.isEmpty()) return@collect

                        val currentOffsets = frame.visibleOffsets.toMap()
                        if (!trackedWallpaperScrollPx.isFinite()) {
                            trackedWallpaperScrollPx = absoluteStartScrollPx(
                                itemWidthsPx = widths,
                                firstVisibleItemIndex = frame.firstIndex,
                                firstVisibleItemScrollOffset = frame.firstOffsetPx,
                            )
                        } else if (previousOffsets.isNotEmpty()) {
                            val commonDelta = frame.visibleOffsets.firstNotNullOfOrNull {
                                    (key, currentOffset) ->
                                previousOffsets[key]?.let { previousOffset ->
                                    previousOffset - currentOffset
                                }
                            }

                            when {
                                // Real list motion advances the wallpaper by the same consumed
                                // pixels. Reflow while stationary only refreshes the baseline.
                                (frame.isScrolling || wasScrolling || dragAutoScrollActive) &&
                                    commonDelta != null -> {
                                    trackedWallpaperScrollPx =
                                        (trackedWallpaperScrollPx + commonDelta).coerceAtLeast(0f)
                                }

                                // scrollToItem can replace every visible key in one frame without
                                // entering an animated scroll. That is a genuine navigation jump,
                                // so resolve its exact world coordinate from the variable widths.
                                currentOffsets.keys.none { it in previousOffsets } &&
                                    previousFirstIndex != null &&
                                    frame.firstIndex != previousFirstIndex -> {
                                    trackedWallpaperScrollPx = absoluteStartScrollPx(
                                        itemWidthsPx = widths,
                                        firstVisibleItemIndex = frame.firstIndex,
                                        firstVisibleItemScrollOffset = frame.firstOffsetPx,
                                    )
                                }
                            }
                        }

                        previousOffsets = currentOffsets
                        previousFirstIndex = frame.firstIndex
                        wasScrolling = frame.isScrolling
                        latestOnWallpaperScrollOffsetChanged.value(trackedWallpaperScrollPx)
                    }
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
                        }
                        .pointerInput(interactionEnabled, launchingTileId) {
                            if (!interactionEnabled || launchingTileId != null) return@pointerInput

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val viewport = tileViewportBounds
                                if (viewport == Rect.Zero) return@awaitEachGesture

                                val downInWindow = Offset(
                                    x = viewport.left + down.position.x,
                                    y = viewport.top + down.position.y,
                                )
                                val hitId = tileBounds.entries
                                    .lastOrNull { (_, bounds) -> bounds.contains(downInWindow) }
                                    ?.key
                                    ?: return@awaitEachGesture
                                val hitTile = tiles.firstOrNull { it.id == hitId }
                                    ?: return@awaitEachGesture

                                val longPress = awaitLongPressOrCancellation(down.id)
                                    ?: return@awaitEachGesture
                                val bounds = tileBounds[hitId] ?: return@awaitEachGesture
                                val currentViewport = tileViewportBounds
                                if (currentViewport == Rect.Zero) return@awaitEachGesture

                                beginTileDrag(
                                    tile = hitTile,
                                    bounds = bounds,
                                    pointerWindow = Offset(
                                        x = currentViewport.left + longPress.position.x,
                                        y = currentViewport.top + longPress.position.y,
                                    ),
                                )

                                // After long-press, the stable viewport owns the stream at Initial
                                // pass. We use the absolute pointer coordinate, not accumulated
                                // deltas, so no child re-layout or consumed event can make the tile
                                // lag behind or detach from the finger.
                                var completed = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    val liveViewport = tileViewportBounds
                                    if (liveViewport != Rect.Zero) {
                                        moveDraggedPointer(
                                            Offset(
                                                x = liveViewport.left + change.position.x,
                                                y = liveViewport.top + change.position.y,
                                            ),
                                        )
                                    }

                                    change.consume()
                                    if (!change.pressed) {
                                        completed = true
                                        break
                                    }
                                }

                                finishTileDrag(commit = completed)
                            }
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
                            val trailingEndDp = if (bandIndex == packed.bands.lastIndex) {
                                START_END_GROUP_DROP_ZONE_DP
                            } else {
                                0f
                            }
                            val trailingEndPx = with(density) { trailingEndDp.dp.toPx() }
                            val labelHeightPx = with(density) { groupLabelHeightDp.dp.toPx() }
                            val gutterKey = "start-group-gutter:${band.groupId}:before"
                            val startGutterKey = "start-group-gutter:start"
                            val endGutterKey = "start-group-gutter:end"
                            val visibleGroupName = band.groupName.takeIf {
                                it.isNotBlank() &&
                                    !(band.groupId == "legacy:Start" && it == "Start")
                            }

                            DisposableEffect(band.key, startsNewGroup, trailingEndDp) {
                                onDispose {
                                    bandDropTargets.remove(band.key)
                                    if (startsNewGroup) gutterDropTargets.remove(gutterKey)
                                    if (bandIndex == 0) gutterDropTargets.remove(startGutterKey)
                                    if (trailingEndDp > 0f) gutterDropTargets.remove(endGutterKey)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width((metrics.bandWidthDp + leadingSpacingDp + trailingEndDp).dp)
                                    .height((metrics.bandHeightDp + groupLabelHeightDp).dp)
                                    .onGloballyPositioned { coordinates ->
                                        if (coordinates.isAttached) {
                                            val whole = coordinates.boundsInWindow()
                                            val bandBounds = Rect(
                                                left = whole.left + leadingSpacingPx,
                                                top = whole.top + labelHeightPx,
                                                right = whole.right - trailingEndPx,
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
                                            if (bandIndex == 0) {
                                                val viewportLeft = tileViewportBounds.left
                                                val desiredWidth = with(density) {
                                                    START_END_GROUP_DROP_ZONE_DP.dp.toPx()
                                                }
                                                gutterDropTargets[startGutterKey] =
                                                    StartGroupGutterDropTarget(
                                                        key = startGutterKey,
                                                        beforeGroupId = band.groupId,
                                                        bounds = Rect(
                                                            left = maxOf(viewportLeft, whole.left - desiredWidth),
                                                            top = whole.top + labelHeightPx,
                                                            right = whole.left,
                                                            bottom = whole.bottom,
                                                        ),
                                                    )
                                            }
                                            if (startsNewGroup) {
                                                gutterDropTargets[gutterKey] = StartGroupGutterDropTarget(
                                                    key = gutterKey,
                                                    beforeGroupId = band.groupId,
                                                    bounds = Rect(
                                                        left = whole.left,
                                                        top = whole.top + labelHeightPx,
                                                        right = whole.left + leadingSpacingPx,
                                                        bottom = whole.bottom,
                                                    ),
                                                )
                                            }
                                            if (trailingEndDp > 0f) {
                                                gutterDropTargets[endGutterKey] = StartGroupGutterDropTarget(
                                                    key = endGutterKey,
                                                    beforeGroupId = null,
                                                    bounds = Rect(
                                                        left = whole.right - trailingEndPx,
                                                        top = whole.top + labelHeightPx,
                                                        right = whole.right,
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
                                if (band.continuationIndex == 0 && visibleGroupName != null) {
                                    Text(
                                        text = visibleGroupName,
                                        color = Color.White.copy(alpha = 0.96f),
                                        style = WindowsTypography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier
                                            .offset(x = leadingSpacingDp.dp)
                                            .height(groupLabelHeightDp.dp),
                                    )
                                }

                                if (bandIndex == 0 && activeGutterKey == startGutterKey) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (-6).dp)
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .padding(top = groupLabelHeightDp.dp + 6.dp, bottom = 6.dp)
                                            .background(Color.White.copy(alpha = 0.92f)),
                                    )
                                }
                                if (startsNewGroup && activeGutterKey == gutterKey) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = ((leadingSpacingDp / 2f) - 2f).dp)
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .padding(top = groupLabelHeightDp.dp + 6.dp, bottom = 6.dp)
                                            .background(Color.White.copy(alpha = 0.92f)),
                                    )
                                }
                                if (trailingEndDp > 0f && activeGutterKey == endGutterKey) {
                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = (
                                                    leadingSpacingDp +
                                                        metrics.bandWidthDp +
                                                        trailingEndDp / 2f -
                                                        2f
                                                    ).dp,
                                            )
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .padding(top = groupLabelHeightDp.dp + 6.dp, bottom = 6.dp)
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
                                        val targetY = (
                                            groupLabelHeightDp +
                                                placed.row * (metrics.cellDp + metrics.gapDp)
                                            ).dp
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
                                                .zIndex(if (isSelected && !isDragging) 1f else 0f)
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
                                                // During a drag the grid copy is only the live
                                                // placeholder. A separate absolute proxy follows
                                                // the finger, so reflow can never tug the held tile.
                                                .alpha(
                                                    if (tile.id == launchingTileId || isDragging) 0f else 1f,
                                                ),
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
                                                    dragEnabled = false,
                                                )
                                            }

                                            if (isSelected && !isDragging) {
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

                    draggingTileId?.let { draggedId ->
                        val draggedTile = tiles.firstOrNull { it.id == draggedId }
                        if (draggedTile != null && tileViewportBounds != Rect.Zero) {
                            val span = draggedTile.size.startTileSpan()
                            val dragWidth = (
                                span.columns * metrics.cellDp +
                                    (span.columns - 1) * metrics.gapDp
                                ).dp
                            val dragHeight = (
                                span.rows * metrics.cellDp +
                                    (span.rows - 1) * metrics.gapDp
                                ).dp
                            val dragIcon = draggedTile.packageName?.let {
                                rememberAppIcon(appsRepository, it)
                            }
                            val visualTopLeft = dragVisualTopLeft()
                            val localLeft = visualTopLeft.x - tileViewportBounds.left
                            val localTop = visualTopLeft.y - tileViewportBounds.top

                            Box(
                                modifier = Modifier
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            localLeft.roundToInt(),
                                            localTop.roundToInt(),
                                        )
                                    }
                                    .size(dragWidth, dragHeight)
                                    .zIndex(100f)
                                    .graphicsLayer {
                                        scaleX = 1.045f
                                        scaleY = 1.045f
                                        shadowElevation = 18f
                                    },
                            ) {
                                WindowsTileFace(
                                    tile = draggedTile,
                                    appIcon = dragIcon,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(20.dp)
                                        .background(Color(0xCC6E6E6E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "✓",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
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
            val oneGroupId = selectedTiles.map { it.effectiveStartGroupId() }.distinct().singleOrNull()
            val oneGroupName = oneGroupId?.let { id ->
                tiles.firstOrNull { it.effectiveStartGroupId() == id }?.groupName.orEmpty()
            }
            val existingGroups = tiles
                .distinctBy { it.effectiveStartGroupId() }
                .map { it.groupName.trim().ifEmpty { "Start" } }

            StartCustomizationBar(
                selectedTiles = selectedTiles,
                showResizeChoices = showResizeChoices,
                onToggleResizeChoices = { showResizeChoices = !showResizeChoices },
                onResize = { size ->
                    val selected = selectedTiles.singleOrNull() ?: return@StartCustomizationBar
                    val updated = normalizeStartTileOrder(
                        tiles.map { tile ->
                            if (tile.id == selected.id) {
                                tile.copy(
                                    size = size,
                                    startBand = null,
                                    startColumn = null,
                                    startRow = null,
                                )
                            } else tile
                        },
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
                            groupId = first.effectiveStartGroupId(),
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
                onRenameGroup = oneGroupId?.let { groupId ->
                    {
                        groupDialog = GroupDialogRequest(
                            title = "Name group",
                            initialValue = oneGroupName.orEmpty(),
                            selectedIds = selectedTileIds,
                            renameWholeGroup = true,
                            groupId = groupId,
                        )
                    }
                },
                onMoveGroup = {
                    groupDialog = GroupDialogRequest(
                        title = "Move to group",
                        initialValue = oneGroupName?.takeIf(String::isNotBlank)
                            ?: existingGroups.firstOrNull().orEmpty(),
                        selectedIds = selectedTileIds,
                        renameWholeGroup = false,
                    )
                },
                onCreateGroup = {
                    val selectedIdsSnapshot = selectedTileIds
                    val newGroupId = "group:${System.currentTimeMillis()}"
                    val selected = tiles.filter { it.id in selectedIdsSnapshot }
                    val remaining = tiles.filterNot { it.id in selectedIdsSnapshot }
                    val moved = selected.mapIndexed { index, tile ->
                        tile.copy(
                            groupId = newGroupId,
                            groupName = "",
                            startBand = if (index == 0) 0 else null,
                            startColumn = if (index == 0) 0 else null,
                            startRow = if (index == 0) 0 else null,
                        )
                    }
                    latestOnTilesChanged.value(normalizeStartTileOrder(remaining + moved))
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
                val normalizedName = newName.trim()
                val targetGroupId = if (request.renameWholeGroup) {
                    request.groupId
                } else {
                    tiles.firstOrNull {
                        it.groupName.trim().equals(normalizedName, ignoreCase = true)
                    }?.effectiveStartGroupId()
                        ?: "group:${System.currentTimeMillis()}"
                }
                val updated = tiles.map { tile ->
                    when {
                        request.renameWholeGroup &&
                            request.groupId != null &&
                            tile.effectiveStartGroupId() == request.groupId ->
                            tile.copy(groupName = normalizedName)
                        !request.renameWholeGroup && tile.id in request.selectedIds -> tile.copy(
                            groupId = targetGroupId.orEmpty(),
                            groupName = normalizedName,
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

internal fun snapStartCell(
    rawCell: Float,
    previousCell: Int?,
    maxStart: Int,
    hysteresis: Float = 0.62f,
): Int {
    val max = maxStart.coerceAtLeast(0)
    val previous = previousCell?.coerceIn(0, max)
    if (previous != null && kotlin.math.abs(rawCell - previous) < hysteresis) {
        return previous
    }
    return rawCell.roundToInt().coerceIn(0, max)
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
    onCreateGroup: (() -> Unit)?,
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
            onMoveGroup?.let { StartCommandButton("Move to group", "arrow_down", it) }
            onCreateGroup?.let { StartCommandButton("New group", "app", it) }
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
