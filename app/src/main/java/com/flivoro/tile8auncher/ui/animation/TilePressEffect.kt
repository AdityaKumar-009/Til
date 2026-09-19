package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flivoro.tile8auncher.data.TileSize

/**
 * Windows 8.1 Tile Press and Click interaction.
 *
 * Phase 1: 00:04 - 00:05 (Touch Down)
 * Tile tilts dynamically into 3D towards the exact touch point:
 * - SMALL keeps the original dynamic 3D tilt up to about ±10° and 0.96 scale.
 * - MEDIUM/WIDE/LARGE normalize that deformation to the same physical edge movement as SMALL,
 *   preventing larger tiles from looking disproportionately crushed.
 * - Perspective camera distance: 10f
 * - Seamless gesture integration with scroll container (no lag during scrolling).
 */
class TileCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

fun Modifier.metroTilePress(
    tileSize: TileSize,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: (() -> Unit)? = null,
    dragEnabled: Boolean = false,
    onDragStart: (bounds: Rect) -> Unit = {},
    onDrag: (delta: Offset) -> Unit = {},
    onLayoutShift: (delta: Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = onDragEnd,
): Modifier = composed {
    val coordsHolder = remember { TileCoordinatesHolder() }
    val density = LocalDensity.current
    val gridGapPx = with(density) { 8.dp.toPx() }

    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestLayoutShift by rememberUpdatedState(onLayoutShift)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestDragCancel by rememberUpdatedState(onDragCancel)

    var isPressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var lastLayoutTopLeft by remember { mutableStateOf<Offset?>(null) }
    var targetScaleX by remember { mutableFloatStateOf(1f) }
    var targetScaleY by remember { mutableFloatStateOf(1f) }
    var targetRotX by remember { mutableFloatStateOf(0f) }
    var targetRotY by remember { mutableFloatStateOf(0f) }

    val scaleX = animateFloatAsState(
        targetValue = if (isPressed) targetScaleX else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TileScaleX",
    )
    val scaleY = animateFloatAsState(
        targetValue = if (isPressed) targetScaleY else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TileScaleY",
    )
    val rotationX = animateFloatAsState(
        targetValue = if (isPressed) targetRotX else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TileRotX",
    )
    val rotationY = animateFloatAsState(
        targetValue = if (isPressed) targetRotY else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "TileRotY",
    )

    fun clearPressedVisual() {
        isPressed = false
        targetScaleX = 1f
        targetScaleY = 1f
        targetRotX = 0f
        targetRotY = 0f
    }

    this
        .onGloballyPositioned { coordinates ->
            coordsHolder.coordinates = coordinates
            if (!coordinates.isAttached) return@onGloballyPositioned

            val topLeft = coordinates.boundsInWindow().topLeft
            val previous = lastLayoutTopLeft
            if (dragging && previous != null) {
                val layoutDelta = topLeft - previous
                if (kotlin.math.abs(layoutDelta.x) > 0.5f ||
                    kotlin.math.abs(layoutDelta.y) > 0.5f
                ) {
                    latestLayoutShift(Offset(-layoutDelta.x, -layoutDelta.y))
                }
            }
            lastLayoutTopLeft = topLeft
        }
        .pointerInput(tileSize, gridGapPx, dragEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val transform = tilePressTransform(
                    tileSize = tileSize,
                    widthPx = size.width.toFloat(),
                    heightPx = size.height.toFloat(),
                    gapPx = gridGapPx,
                    touch = down.position,
                )
                targetScaleX = transform.scaleX
                targetScaleY = transform.scaleY
                targetRotX = transform.rotationX
                targetRotY = transform.rotationY
                isPressed = true

                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null) {
                    clearPressedVisual()

                    if (dragEnabled) {
                        val bounds = coordsHolder.coordinates
                            ?.takeIf { it.isAttached }
                            ?.boundsInWindow()
                            ?: Rect.Zero
                        lastLayoutTopLeft = bounds.topLeft
                        dragging = true
                        latestDragStart(bounds)

                        val completed = drag(longPress.id) { change ->
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                latestDrag(delta)
                                change.consume()
                            }
                        }

                        dragging = false
                        lastLayoutTopLeft = null
                        if (completed) {
                            currentEvent.changes.forEach { change ->
                                if (!change.pressed) change.consume()
                            }
                            latestDragEnd()
                        } else {
                            latestDragCancel()
                        }
                    } else {
                        currentLongClick?.invoke()
                    }
                } else {
                    clearPressedVisual()

                    // A genuine tap ends with every pointer lifted. If a parent LazyRow consumed
                    // motion first, awaitLongPressOrCancellation returns null while a pointer is
                    // still down, so no launch is fired. This is the key difference from the old
                    // two-detector chain that could cancel the long-press drag recognizer.
                    val endedAsTap = currentEvent.changes.isNotEmpty() &&
                        currentEvent.changes.all { !it.pressed }
                    if (endedAsTap) {
                        val bounds = coordsHolder.coordinates
                            ?.takeIf { it.isAttached }
                            ?.boundsInWindow()
                            ?: Rect.Zero
                        currentClick(bounds)
                    }
                }
            }
        }
        .graphicsLayer {
            this.scaleX = scaleX.value
            this.scaleY = scaleY.value
            this.rotationX = rotationX.value
            this.rotationY = rotationY.value
            this.cameraDistance = 10f
            this.transformOrigin = TransformOrigin(0.5f, 0.5f)
        }
}

internal data class TilePressTransform(
    val scaleX: Float,
    val scaleY: Float,
    val rotationX: Float,
    val rotationY: Float,
)

/**
 * Preserve the SMALL tile's press feel across every tile size.
 *
 * A uniform 0.96 scale is subtle on a small tile but removes many more physical pixels from the
 * edges of a medium/wide/large tile. Windows' press feedback reads more like a fixed edge inset
 * than a fixed percentage. Derive the underlying one-cell size from the tile span and apply the
 * same 4% one-cell contraction to each axis. SMALL remains exactly 0.96; larger tiles therefore
 * move their edges by roughly the same physical amount instead of looking heavily squeezed.
 *
 * Perspective tilt is normalized the same way so a wide/large tile does not swing through the
 * same 10-degree angle as a 1x1 tile.
 */
internal fun tilePressTransform(
    tileSize: TileSize,
    widthPx: Float,
    heightPx: Float,
    gapPx: Float,
    touch: Offset,
): TilePressTransform {
    if (widthPx <= 0f || heightPx <= 0f) {
        return TilePressTransform(1f, 1f, 0f, 0f)
    }

    val (columns, rows) = when (tileSize) {
        TileSize.SMALL -> 1 to 1
        TileSize.MEDIUM -> 2 to 2
        TileSize.WIDE -> 4 to 2
        TileSize.LARGE -> 4 to 4
    }

    fun underlyingCell(totalPx: Float, span: Int): Float =
        ((totalPx - gapPx.coerceAtLeast(0f) * (span - 1)) / span)
            .coerceIn(1f, totalPx)

    val cellWidth = underlyingCell(widthPx, columns)
    val cellHeight = underlyingCell(heightPx, rows)

    val smallTileContraction = 0.04f
    val scaleX = (1f - smallTileContraction * (cellWidth / widthPx)).coerceIn(0.96f, 1f)
    val scaleY = (1f - smallTileContraction * (cellHeight / heightPx)).coerceIn(0.96f, 1f)

    val nx = ((touch.x - widthPx / 2f) / (widthPx / 2f)).coerceIn(-1f, 1f)
    val ny = ((touch.y - heightPx / 2f) / (heightPx / 2f)).coerceIn(-1f, 1f)

    val maxSmallTileTilt = 10f
    val rotationX = -ny * maxSmallTileTilt * (cellHeight / heightPx)
    val rotationY = nx * maxSmallTileTilt * (cellWidth / widthPx)

    return TilePressTransform(
        scaleX = scaleX,
        scaleY = scaleY,
        rotationX = rotationX,
        rotationY = rotationY,
    )
}
