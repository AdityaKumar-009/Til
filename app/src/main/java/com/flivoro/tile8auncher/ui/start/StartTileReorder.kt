package com.flivoro.tile8auncher.ui.start

import com.flivoro.tile8auncher.data.TileModel

/**
 * Reorders Start tiles without introducing a second layout model.
 *
 * StartTilePacking remains the single source of truth for geometry. Dragging only changes the
 * stable tile order (and, when crossing a group, the dragged tile's group), then the existing
 * packer fills the same 4-column bands exactly as before.
 */
internal fun reorderStartTiles(
    tiles: List<TileModel>,
    draggedId: String,
    targetId: String,
    placeAfterTarget: Boolean,
): List<TileModel> {
    if (draggedId == targetId) return normalizeStartTileOrder(tiles)

    val draggedIndex = tiles.indexOfFirst { it.id == draggedId }
    val target = tiles.firstOrNull { it.id == targetId }
    if (draggedIndex < 0 || target == null) return normalizeStartTileOrder(tiles)

    val working = tiles.toMutableList()
    val dragged = working.removeAt(draggedIndex)
    val targetIndexAfterRemoval = working.indexOfFirst { it.id == targetId }
    if (targetIndexAfterRemoval < 0) return normalizeStartTileOrder(tiles)

    val moved = if (dragged.groupName == target.groupName) {
        dragged
    } else {
        dragged.copy(
            groupName = target.groupName,
            startBand = null,
            startColumn = null,
            startRow = null,
        )
    }
    val insertionIndex = (targetIndexAfterRemoval + if (placeAfterTarget) 1 else 0)
        .coerceIn(0, working.size)
    working.add(insertionIndex, moved)
    return normalizeStartTileOrder(working)
}

internal fun normalizeStartTileOrder(tiles: List<TileModel>): List<TileModel> =
    tiles.mapIndexed { index, tile ->
        if (tile.order == index) tile else tile.copy(order = index)
    }
