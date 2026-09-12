package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorldMathTest {
    @Test
    fun positiveModuloNeverLeavesThePatternRange() {
        val period = 2_137.5
        listOf(
            -9_000_000_000.25,
            -2_137.5,
            -1.0,
            0.0,
            1.0,
            2_137.5,
            9_000_000_000.25,
        ).forEach { value ->
            val phase = BackgroundWorldMath.positiveModulo(value, period)
            assertTrue(phase >= 0.0)
            assertTrue(phase < period)
        }
    }

    @Test
    fun localCellsStillCoverViewportAfterOneHundredThousandScreens() {
        val viewport = 1_080.0
        val period = viewport * 2.05
        val scenePosition = viewport * 100_000.0 * 0.08
        val base = BackgroundWorldMath.cellIndex(scenePosition, period)
        val current = BackgroundWorldMath.localCellX(scenePosition, period, base)
        val next = BackgroundWorldMath.localCellX(scenePosition, period, base + 1)

        assertTrue(current <= 0f)
        assertTrue(next > current)
        assertEquals(period.toFloat(), next - current, 0.05f)
    }

    @Test
    fun negativeWorldTravelUsesStableCellVariants() {
        assertEquals(2, BackgroundWorldMath.variant(-1L, 3))
        assertEquals(1, BackgroundWorldMath.variant(-2L, 3))
        assertEquals(0, BackgroundWorldMath.variant(-3L, 3))
    }
}
