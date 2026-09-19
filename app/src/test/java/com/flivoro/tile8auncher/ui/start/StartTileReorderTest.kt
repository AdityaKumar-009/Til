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
    fun crossingGroupClearsOldGridAnchor() {
        val anchored = tile("a", "One").copy(startBand = 1, startColumn = 2, startRow = 3)
        val result = reorderStartTiles(
            listOf(anchored, tile("b", "Two")),
            draggedId = "a",
            targetId = "b",
            placeAfterTarget = true,
        )

        val moved = result.first { it.id == "a" }
        assertEquals("Two", moved.groupName)
        assertEquals(null, moved.startBand)
        assertEquals(null, moved.startColumn)
        assertEquals(null, moved.startRow)
    }

    @Test
    fun newGroupDropCreatesAStableGroupAtRequestedGutter() {
        val source = listOf(
            tile("a", "One").copy(groupId = "g1"),
            tile("b", "One").copy(groupId = "g1"),
            tile("c", "Two").copy(groupId = "g2"),
        )

        val result = moveDraggedTileToNewGroup(
            tiles = source,
            draggedId = "b",
            newGroupId = "new",
            insertBeforeGroupId = "g2",
        )

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        val moved = result[1]
        assertEquals("new", moved.groupId)
        assertEquals("", moved.groupName)
        assertEquals(0, moved.startBand)
        assertEquals(0, moved.startColumn)
        assertEquals(0, moved.startRow)
    }

    @Test
    fun movingIntoExistingGroupAdoptsItsStableIdentity() {
        val source = listOf(
            tile("a", "One").copy(groupId = "g1"),
            tile("b", "Two").copy(groupId = "g2"),
        )

        val result = moveDraggedTileToExistingGroup(
            tiles = source,
            draggedId = "a",
            targetGroupId = "g2",
            targetGroupName = "Two",
        )

        assertEquals(listOf("b", "a"), result.map { it.id })
        assertEquals("g2", result.last().groupId)
        assertEquals("Two", result.last().groupName)
    }

    @Test
    fun gutterDropCreatesIndependentGroupAtRequestedBoundary() {
        val source = listOf(
            tile("a", "One").copy(groupId = "one"),
            tile("b", "One").copy(groupId = "one"),
            tile("c", "Two").copy(groupId = "two"),
        )

        val result = moveDraggedTileToNewGroup(
            tiles = source,
            draggedId = "b",
            newGroupId = "new",
            insertBeforeGroupId = "two",
        )

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        val moved = result.first { it.id == "b" }
        assertEquals("new", moved.groupId)
        assertEquals("", moved.groupName)
        assertEquals(0, moved.startBand)
        assertEquals(0, moved.startColumn)
        assertEquals(0, moved.startRow)
    }

    @Test
    fun invalidTargetDoesNotLoseTiles() {
        val source = listOf(tile("a", order = 7), tile("b", order = 9))
        val result = reorderStartTiles(source, "a", "missing", false)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.order })
    }
}
