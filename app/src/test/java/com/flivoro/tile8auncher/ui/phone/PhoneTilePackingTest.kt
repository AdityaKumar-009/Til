package com.flivoro.tile8auncher.ui.phone

import com.flivoro.tile8auncher.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTilePackingTest {
    @Test fun classicFourColumnPackingHasNoOverlaps() = verifyPacking(4)

    @Test fun denseSixColumnPackingHasNoOverlaps() = verifyPacking(6)

    private fun verifyPacking(columns: Int) {
        val tiles = listOf(
            TileSize.MEDIUM, TileSize.SMALL, TileSize.SMALL, TileSize.WIDE,
            TileSize.LARGE, TileSize.SMALL, TileSize.MEDIUM, TileSize.SMALL,
            TileSize.WIDE, TileSize.MEDIUM, TileSize.SMALL, TileSize.SMALL,
        )
        val slots = packPhoneTiles(tiles, columns)
        assertEquals(tiles.size, slots.size)
        val occupied = mutableSetOf<Pair<Int, Int>>()
        slots.forEach { tile ->
            assertTrue(tile.column >= 0)
            assertTrue(tile.column + tile.columns <= columns)
            assertTrue(tile.row >= 0)
            for (y in tile.row until tile.row + tile.rows) {
                for (x in tile.column until tile.column + tile.columns) {
                    assertTrue("Tiles must not overlap", occupied.add(x to y))
                }
            }
        }
    }

    @Test fun emptyStartProducesNoSlots() {
        assertTrue(packPhoneTiles(emptyList(), 4).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedColumnCountIsRejected() {
        packPhoneTiles(listOf(TileSize.SMALL), 5)
    }
}
