package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Offset
import com.flivoro.tile8auncher.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class TilePressEffectTest {

    @Test
    fun smallTileKeepsExistingPressEffect() {
        val transform = tilePressTransform(
            tileSize = TileSize.SMALL,
            widthPx = 64f,
            heightPx = 64f,
            gapPx = 8f,
            touch = Offset(64f, 32f),
        )

        assertEquals(0.96f, transform.scaleX, 0.0001f)
        assertEquals(0.96f, transform.scaleY, 0.0001f)
        assertEquals(0f, transform.rotationX, 0.0001f)
        assertEquals(10f, transform.rotationY, 0.0001f)
    }

    @Test
    fun largerTilesContractBySamePhysicalAmountAsSmallTile() {
        val small = tilePressTransform(
            tileSize = TileSize.SMALL,
            widthPx = 64f,
            heightPx = 64f,
            gapPx = 8f,
            touch = Offset.Zero,
        )
        val medium = tilePressTransform(
            tileSize = TileSize.MEDIUM,
            widthPx = 136f,
            heightPx = 136f,
            gapPx = 8f,
            touch = Offset.Zero,
        )
        val wide = tilePressTransform(
            tileSize = TileSize.WIDE,
            widthPx = 280f,
            heightPx = 136f,
            gapPx = 8f,
            touch = Offset.Zero,
        )
        val large = tilePressTransform(
            tileSize = TileSize.LARGE,
            widthPx = 280f,
            heightPx = 280f,
            gapPx = 8f,
            touch = Offset.Zero,
        )

        val expectedContraction = 64f * (1f - small.scaleX)

        assertEquals(expectedContraction, 136f * (1f - medium.scaleX), 0.001f)
        assertEquals(expectedContraction, 136f * (1f - medium.scaleY), 0.001f)
        assertEquals(expectedContraction, 280f * (1f - wide.scaleX), 0.001f)
        assertEquals(expectedContraction, 136f * (1f - wide.scaleY), 0.001f)
        assertEquals(expectedContraction, 280f * (1f - large.scaleX), 0.001f)
        assertEquals(expectedContraction, 280f * (1f - large.scaleY), 0.001f)
    }

    @Test
    fun largerTilesAlsoReducePerspectiveTilt() {
        val wide = tilePressTransform(
            tileSize = TileSize.WIDE,
            widthPx = 280f,
            heightPx = 136f,
            gapPx = 8f,
            touch = Offset(280f, 68f),
        )
        val large = tilePressTransform(
            tileSize = TileSize.LARGE,
            widthPx = 280f,
            heightPx = 280f,
            gapPx = 8f,
            touch = Offset(280f, 140f),
        )

        assertEquals(10f * 64f / 280f, wide.rotationY, 0.001f)
        assertEquals(10f * 64f / 280f, large.rotationY, 0.001f)
    }
}
