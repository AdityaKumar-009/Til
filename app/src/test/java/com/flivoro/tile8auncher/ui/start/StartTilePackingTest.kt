package com.flivoro.tile8auncher.ui.start

import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartTilePackingTest {

    @Test
    fun tileSizesKeepTheirWindowsGridSpans() {
        assertEquals(StartTileSpan(1, 1), TileSize.SMALL.startTileSpan())
        assertEquals(StartTileSpan(2, 2), TileSize.MEDIUM.startTileSpan())
        assertEquals(StartTileSpan(4, 2), TileSize.WIDE.startTileSpan())
        assertEquals(StartTileSpan(4, 4), TileSize.LARGE.startTileSpan())
    }

    @Test
    fun mixedTilesArePackedWithoutOverlapAndEveryTileIsVisible() {
        val tiles = listOf(
            tile("small-1", TileSize.SMALL),
            tile("medium-1", TileSize.MEDIUM),
            tile("wide-1", TileSize.WIDE),
            tile("small-2", TileSize.SMALL),
            tile("large-1", TileSize.LARGE),
            tile("medium-2", TileSize.MEDIUM),
            tile("wide-2", TileSize.WIDE),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(tiles.size, packed.tiles.size)
        assertEquals(tiles.map { it.id }, packed.tiles.map { it.tile.id })
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun fullHeightStartsAHorizontalContinuationBand() {
        val tiles = listOf(
            tile("large-1", TileSize.LARGE),
            tile("large-2", TileSize.LARGE),
            tile("wide-1", TileSize.WIDE),
            tile("wide-2", TileSize.WIDE),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(3, packed.bands.size)
        assertEquals(listOf("large-1"), packed.bands[0].tiles.map { it.tile.id })
        assertEquals(listOf("large-2"), packed.bands[1].tiles.map { it.tile.id })
        assertEquals(listOf("wide-1", "wide-2"), packed.bands[2].tiles.map { it.tile.id })
        assertEquals(listOf(0, 1, 2), packed.bands.map { it.continuationIndex })
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun groupOrderIsIndependentAndEachGroupGetsStableBandKeys() {
        val tiles = listOf(
            tile("a-1", TileSize.MEDIUM, group = "Work"),
            tile("b-1", TileSize.LARGE, group = "Play"),
            tile("a-2", TileSize.SMALL, group = "Work"),
            tile("a-3", TileSize.WIDE, group = "Work"),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(listOf("Work", "Play"), packed.bands.map { it.groupName })
        assertEquals(
            listOf("a-1", "a-2", "a-3"),
            packed.bands.filter { it.groupName == "Work" }
                .flatMap { it.tiles }
                .map { it.tile.id },
        )
        assertEquals(packed.bands.size, packed.bands.map { it.key }.toSet().size)
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun groupsWithSameVisibleNameStaySeparatedByStableIdentity() {
        val tiles = listOf(
            tile("a", TileSize.SMALL, group = "").copy(groupId = "group-a"),
            tile("b", TileSize.SMALL, group = "").copy(groupId = "group-b"),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(2, packed.bands.size)
        assertEquals(listOf("group-a", "group-b"), packed.bands.map { it.groupId })
        assertEquals(listOf("", ""), packed.bands.map { it.groupName })
    }

    @Test
    fun placementRemainsValidAcrossPhoneLandscapeAndTabletViewports() {
        val tiles = listOf(
            tile("small-1", TileSize.SMALL),
            tile("small-2", TileSize.SMALL),
            tile("medium-1", TileSize.MEDIUM),
            tile("wide-1", TileSize.WIDE),
            tile("large-1", TileSize.LARGE),
            tile("medium-2", TileSize.MEDIUM),
            tile("small-3", TileSize.SMALL),
            tile("wide-2", TileSize.WIDE),
            tile("large-2", TileSize.LARGE),
        )
        val viewports = listOf(
            320f to 480f,
            360f to 740f,
            480f to 320f,
            600f to 960f,
            1280f to 720f,
            1920f to 1080f,
            2560f to 1600f,
        )

        viewports.forEach { (width, height) ->
            val metrics = calculateStartGridMetrics(width, height)
            val packed = packStartTiles(tiles, maxRows = metrics.rows)
            val context = "viewport ${width}x$height"

            assertTrue("$context cell must be finite", metrics.cellDp.isFinite())
            assertTrue("$context cell must be positive", metrics.cellDp > 0f)
            assertTrue("$context gap must be finite", metrics.gapDp.isFinite())
            assertTrue("$context band width must be finite", metrics.bandWidthDp.isFinite())
            assertTrue("$context band height must be finite", metrics.bandHeightDp.isFinite())
            assertTrue("$context cell is too large", metrics.cellDp <= START_GRID_MAX_CELL_DP)
            assertTrue("$context band is wider than the viewport", metrics.bandWidthDp <= width + .001f)
            assertTrue("$context band is taller than the viewport", metrics.bandHeightDp <= height + .001f)
            assertEquals(START_GRID_COLUMNS, packed.bands.first().columns)
            assertEquals(metrics.rows, packed.bands.first().rows)
            assertEquals(tiles.map { it.id }, packed.tiles.map { it.tile.id })
            assertBandGeometryIsValid(packed)
        }
    }

    @Test
    fun groupsWithTheSameVisibleNameRemainSeparatedByStableIds() {
        val tiles = listOf(
            tile("left", TileSize.SMALL, group = "").copy(groupId = "group:left"),
            tile("right", TileSize.SMALL, group = "").copy(groupId = "group:right"),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(2, packed.bands.size)
        assertEquals(listOf("group:left", "group:right"), packed.bands.map { it.groupId })
        assertEquals(listOf("", ""), packed.bands.map { it.groupName })
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun explicitSnapPositionLeavesAStableHole() {
        val anchored = tile("anchored", TileSize.SMALL).copy(
            startBand = 0,
            startColumn = 2,
            startRow = 1,
        )
        val filler = tile("filler", TileSize.SMALL)

        val packed = packStartTiles(listOf(anchored, filler), maxRows = 4)
        val placedAnchor = packed.tiles.first { it.tile.id == "anchored" }
        val placedFiller = packed.tiles.first { it.tile.id == "filler" }

        assertEquals(2, placedAnchor.column)
        assertEquals(1, placedAnchor.row)
        assertEquals(0, placedFiller.column)
        assertEquals(0, placedFiller.row)
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun explicitContinuationBandKeepsChosenHorizontalLocation() {
        val anchored = tile("anchored", TileSize.SMALL).copy(
            startBand = 1,
            startColumn = 1,
            startRow = 0,
        )
        val filler = tile("filler", TileSize.SMALL)

        val packed = packStartTiles(listOf(anchored, filler), maxRows = 4)

        assertEquals(2, packed.bands.size)
        assertEquals(listOf("filler"), packed.bands[0].tiles.map { it.tile.id })
        assertEquals(listOf("anchored"), packed.bands[1].tiles.map { it.tile.id })
        assertEquals(1, packed.bands[1].tiles.single().column)
        assertBandGeometryIsValid(packed)
    }

    @Test
    fun emptyInputProducesNoLazyItems() {
        val packed = packStartTiles(emptyList(), maxRows = 4)

        assertTrue(packed.bands.isEmpty())
        assertTrue(packed.tiles.isEmpty())
    }

    @Test
    fun metricsStayPositiveForShortButUsableWindows() {
        val viewports = listOf(
            200f to 96f,
            240f to 128f,
            320f to 240f,
            400f to 300f,
        )

        viewports.forEach { (width, height) ->
            val metrics = calculateStartGridMetrics(width, height)
            assertTrue(metrics.cellDp.isFinite() && metrics.cellDp > 0f)
            assertTrue(metrics.bandWidthDp.isFinite() && metrics.bandWidthDp > 0f)
            assertTrue(metrics.bandHeightDp.isFinite() && metrics.bandHeightDp > 0f)
            assertTrue(metrics.rows >= START_GRID_MIN_ROWS)
            assertTrue(metrics.bandWidthDp <= width + .001f)
            assertTrue(metrics.bandHeightDp <= height + .001f)
        }
    }

    @Test
    fun inputOrderIsRetainedWhenTilesShareTheSameFirstFitRow() {
        val tiles = listOf(
            tile("first", TileSize.SMALL),
            tile("second", TileSize.SMALL),
            tile("third", TileSize.SMALL),
            tile("fourth", TileSize.SMALL),
            tile("wide", TileSize.WIDE),
        )

        val packed = packStartTiles(tiles, maxRows = 4)

        assertEquals(tiles.map { it.id }, packed.tiles.map { it.tile.id })
        assertBandGeometryIsValid(packed)
    }

    private fun assertBandGeometryIsValid(layout: PackedStartTiles) {
        layout.bands.forEach { band ->
            assertTrue("${band.key} must have positive columns", band.columns > 0)
            assertTrue("${band.key} must have positive rows", band.rows > 0)
            band.tiles.forEach { placed ->
                assertTrue("${placed.tile.id} starts left of its band", placed.column >= 0)
                assertTrue("${placed.tile.id} starts above its band", placed.row >= 0)
                assertTrue(
                    "${placed.tile.id} exceeds band width",
                    placed.column + placed.columns <= band.columns,
                )
                assertTrue(
                    "${placed.tile.id} exceeds band height",
                    placed.row + placed.rows <= band.rows,
                )
                assertEquals(placed.tile.size.startTileSpan().columns, placed.columns)
                assertEquals(placed.tile.size.startTileSpan().rows, placed.rows)
            }

            for (firstIndex in band.tiles.indices) {
                for (secondIndex in firstIndex + 1 until band.tiles.size) {
                    val first = band.tiles[firstIndex]
                    val second = band.tiles[secondIndex]
                    val overlaps = first.column < second.column + second.columns &&
                        first.column + first.columns > second.column &&
                        first.row < second.row + second.rows &&
                        first.row + first.rows > second.row
                    assertFalse(
                        "${first.tile.id} overlaps ${second.tile.id} in ${band.key}",
                        overlaps,
                    )
                }
            }
        }

        // A band must contain the same exact tile instances as the input; this
        // also catches accidental drops while starting a continuation band.
        assertEquals(layout.tiles.map { it.tile.id }.distinct().size, layout.tiles.size)
        assertTrue(layout.tiles.all { it.columns > 0 && it.rows > 0 })
        assertTrue(layout.bands.zipWithNext().all { (first, second) ->
            first.key != second.key &&
                if (first.groupId == second.groupId) {
                    second.continuationIndex == first.continuationIndex + 1
                } else {
                    second.continuationIndex == 0
                }
        })
    }

    private fun tile(id: String, size: TileSize, group: String = "Start") = TileModel(
        id = id,
        title = id,
        size = size,
        groupName = group,
    )
}
