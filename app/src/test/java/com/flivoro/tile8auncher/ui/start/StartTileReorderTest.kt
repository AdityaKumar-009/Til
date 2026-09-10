package com.flivoro.tile8auncher.ui.start

import com.flivoro.tile8auncher.data.TileModel
import org.junit.Assert.assertEquals
import org.junit.Test

class StartTileReorderTest {
    private fun tile(id: String, group: String = "Start", order: Int = 0) =
        TileModel(id = id, title = id, groupName = group, order = order)

    @Test
    fun moveBeforeTargetPreservesStableOrder() {
        val result = reorderStartTiles(
            listOf(tile("a"), tile("b"), tile("c"), tile("d")),
            draggedId = "d",
            targetId = "b",
            placeAfterTarget = false,
        )
        assertEquals(listOf("a", "d", "b", "c"), result.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), result.map { it.order })
    }

    @Test
    fun moveAfterTargetWorksWhenMovingForward() {
        val result = reorderStartTiles(
            listOf(tile("a"), tile("b"), tile("c"), tile("d")),
            draggedId = "a",
            targetId = "c",
            placeAfterTarget = true,
        )
        assertEquals(listOf("b", "c", "a", "d"), result.map { it.id })
    }

    @Test
    fun crossingGroupAdoptsTargetGroup() {
        val result = reorderStartTiles(
            listOf(tile("a", "One"), tile("b", "Two"), tile("c", "Two")),
            draggedId = "a",
            targetId = "c",
            placeAfterTarget = false,
        )
        assertEquals(listOf("b", "a", "c"), result.map { it.id })
        assertEquals("Two", result.first { it.id == "a" }.groupName)
    }

    @Test
    fun invalidTargetDoesNotLoseTiles() {
        val source = listOf(tile("a", order = 7), tile("b", order = 9))
        val result = reorderStartTiles(source, "a", "missing", false)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.order })
    }
}
