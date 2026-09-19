package com.flivoro.tile8auncher.ui.start

import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import kotlin.math.floor
import kotlin.math.min

/**
 * The Start surface is a horizontal strip of fixed-height bands. Each band is
 * a small two-dimensional grid, so a tile never has to be squeezed into a
 * single row just because the surface scrolls horizontally.
 */
internal const val START_GRID_COLUMNS = 4
internal const val START_GRID_GAP_DP = 8f
internal const val START_GRID_MIN_ROWS = 4
internal const val START_GRID_MIN_CELL_DP = 56f
internal const val START_GRID_MAX_CELL_DP = 72f

internal data class StartGridMetrics(
    val cellDp: Float,
    val gapDp: Float,
    val columns: Int,
    val rows: Int,
    val bandWidthDp: Float,
    val bandHeightDp: Float,
)

/**
 * Chooses a cell which makes a 4x4 tile fit vertically and keeps a normal
 * four-cell band usable on phones and tablets. On unusually short landscape
 * windows the cell is allowed to go below 56dp; overflowing the screen would
 * make the large tile impossible to use.
 */
internal fun calculateStartGridMetrics(
    availableWidthDp: Float,
    availableHeightDp: Float,
    gapDp: Float = START_GRID_GAP_DP,
): StartGridMetrics {
    val width = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = availableHeightDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val gap = gapDp.takeIf { it.isFinite() && it >= 0f } ?: START_GRID_GAP_DP

    val widthBoundCell = (width - gap * (START_GRID_COLUMNS - 1)) / START_GRID_COLUMNS
    val heightBoundCell = (height - gap * (START_GRID_MIN_ROWS - 1)) / START_GRID_MIN_ROWS
    val cell = min(
        START_GRID_MAX_CELL_DP,
        min(widthBoundCell, heightBoundCell),
    ).coerceAtLeast(1f)

    // A taller tablet can use more rows in the same finite-height band. The
    // band never becomes a vertical scroller; extra content continues right.
    val rows = floor((height + gap) / (cell + gap))
        .toInt()
        .coerceAtLeast(START_GRID_MIN_ROWS)

    val bandWidth = START_GRID_COLUMNS * cell + (START_GRID_COLUMNS - 1) * gap
    val bandHeight = rows * cell + (rows - 1) * gap

    return StartGridMetrics(
        cellDp = cell,
        gapDp = gap,
        columns = START_GRID_COLUMNS,
        rows = rows,
        bandWidthDp = bandWidth,
        bandHeightDp = bandHeight,
    )
}

internal data class StartTileSpan(
    val columns: Int,
    val rows: Int,
)

internal fun TileSize.startTileSpan(): StartTileSpan = when (this) {
    TileSize.SMALL -> StartTileSpan(columns = 1, rows = 1)
    TileSize.MEDIUM -> StartTileSpan(columns = 2, rows = 2)
    TileSize.WIDE -> StartTileSpan(columns = 4, rows = 2)
    TileSize.LARGE -> StartTileSpan(columns = 4, rows = 4)
}

internal fun TileModel.effectiveStartGroupId(): String =
    groupId.takeIf(String::isNotBlank)
        ?: "legacy:${groupName.trim().ifEmpty { "Start" }}"

internal data class PackedTile(
    val tile: TileModel,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
)

internal data class StartTileBand(
    val key: String,
    val groupId: String,
    val groupName: String,
    val continuationIndex: Int,
    val columns: Int,
    val rows: Int,
    val tiles: List<PackedTile>,
)

internal data class PackedStartTiles(
    val bands: List<StartTileBand>,
) {
    val tiles: List<PackedTile>
        get() = bands.flatMap { it.tiles }
}

/**
 * Packs each Windows Start group in input order into four-cell-wide bands. A tile is
 * placed at the first free position that can contain its complete span. When
 * the current band's height is full, a continuation band is emitted to the
 * right. Keeping the scan order stable makes pin order predictable while
 * still filling holes left by larger tiles.
 */
internal fun packStartTiles(
    tiles: List<TileModel>,
    maxRows: Int,
    maxColumns: Int = START_GRID_COLUMNS,
): PackedStartTiles {
    if (tiles.isEmpty()) return PackedStartTiles(emptyList())

    val requiredColumns = tiles.maxOf { it.size.startTileSpan().columns }
    val columnCount = maxOf(
        1,
        maxColumns.coerceAtMost(START_GRID_COLUMNS),
        requiredColumns,
    )
    val requiredRows = tiles.maxOf { it.size.startTileSpan().rows }
    val rowCount = maxOf(START_GRID_MIN_ROWS, maxRows, requiredRows)

    // Linked grouping preserves the order in which stable group ids first appear. Visible names
    // are labels only: Windows 8.1 allowed unnamed groups, so two groups must not collapse merely
    // because they share an empty or identical label.
    val grouped = linkedMapOf<String, MutableList<TileModel>>()
    tiles.forEach { tile ->
        grouped.getOrPut(tile.effectiveStartGroupId()) { mutableListOf() }.add(tile)
    }

    val bands = mutableListOf<StartTileBand>()
    grouped.entries.forEach { (groupId, groupTiles) ->
        val groupName = groupTiles.firstOrNull()?.groupName.orEmpty()
        val builders = mutableListOf<BandBuilder>()

        fun builderAt(index: Int): BandBuilder {
            while (builders.size <= index) {
                builders += BandBuilder(columnCount, rowCount)
            }
            return builders[index]
        }

        // Explicit snap positions are placed first so a user-dragged tile owns the exact Windows
        // grid cell it was dropped on. Tiles without coordinates then first-fit around those
        // anchors, which preserves deliberate holes instead of compacting the dragged tile away.
        val pending = mutableListOf<TileModel>()
        val maxReasonableBand = groupTiles.size.coerceAtLeast(1)
        groupTiles.forEach { tile ->
            val band = tile.startBand
            val column = tile.startColumn
            val row = tile.startRow
            val canTryExplicit = band != null && column != null && row != null &&
                band in 0..maxReasonableBand

            val placed = if (canTryExplicit) {
                builderAt(band!!).placeAt(tile, column!!, row!!)
            } else {
                null
            }
            if (placed == null) pending += tile
        }

        pending.forEach { tile ->
            var placed: PackedTile? = null
            for (builder in builders) {
                placed = builder.place(tile)
                if (placed != null) break
            }
            if (placed == null) {
                val builder = BandBuilder(columnCount, rowCount)
                builders += builder
                placed = builder.place(tile)
            }

            // rowCount is always large enough for the largest supported tile,
            // so a fresh band can accept every valid TileSize.
            check(placed != null) { "Unable to place tile ${tile.id} in a Start band" }
        }

        // Empty intermediate bands are intentional when the user drops a tile into a later band.
        // Keeping them is what makes a chosen horizontal location stable across recompositions.
        builders.forEachIndexed { continuationIndex, builder ->
            if (builder.hasTiles || continuationIndex < builders.lastIndex) {
                bands += builder.build(
                    groupId = groupId,
                    groupName = groupName,
                    continuationIndex = continuationIndex,
                )
            }
        }
    }

    return PackedStartTiles(bands)
}

private class BandBuilder(
    private val columns: Int,
    private val rows: Int,
) {
    private val occupied = Array(rows) { BooleanArray(columns) }
    private val placedTiles = mutableListOf<PackedTile>()

    val hasTiles: Boolean
        get() = placedTiles.isNotEmpty()

    fun place(tile: TileModel): PackedTile? {
        val span = tile.size.startTileSpan()
        if (span.columns > columns || span.rows > rows) return null

        for (row in 0..(rows - span.rows)) {
            for (column in 0..(columns - span.columns)) {
                placeAt(tile, column, row)?.let { return it }
            }
        }
        return null
    }

    fun placeAt(tile: TileModel, column: Int, row: Int): PackedTile? {
        val span = tile.size.startTileSpan()
        if (column < 0 || row < 0) return null
        if (column + span.columns > columns || row + span.rows > rows) return null
        if (!isFree(column, row, span)) return null

        for (dy in 0 until span.rows) {
            for (dx in 0 until span.columns) {
                occupied[row + dy][column + dx] = true
            }
        }

        return PackedTile(
            tile = tile,
            column = column,
            row = row,
            columns = span.columns,
            rows = span.rows,
        ).also(placedTiles::add)
    }

    fun build(groupId: String, groupName: String, continuationIndex: Int): StartTileBand =
        StartTileBand(
            key = "start-band:$groupId:$continuationIndex",
            groupId = groupId,
            groupName = groupName,
            continuationIndex = continuationIndex,
            columns = columns,
            rows = rows,
            tiles = placedTiles.toList(),
        )

    private fun isFree(column: Int, row: Int, span: StartTileSpan): Boolean {
        for (dy in 0 until span.rows) {
            for (dx in 0 until span.columns) {
                if (occupied[row + dy][column + dx]) return false
            }
        }
        return true
    }
}
