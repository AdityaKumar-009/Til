package com.flivoro.tile8auncher.ui.components

import kotlin.math.floor

/** Pure, allocation-free world-coordinate helpers used by the infinite Start artwork renderer. */
internal object BackgroundWorldMath {
    fun positiveModulo(value: Double, period: Double): Double {
        if (!value.isFinite() || !period.isFinite() || period <= 0.0) return 0.0
        val remainder = value % period
        return if (remainder < 0.0) remainder + period else remainder
    }

    fun cellIndex(worldPosition: Double, period: Double): Long {
        if (!worldPosition.isFinite() || !period.isFinite() || period <= 0.0) return 0L
        val quotient = floor(worldPosition / period)
        return when {
            quotient >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
            quotient <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE
            else -> quotient.toLong()
        }
    }

    /**
     * Returns a viewport-local X for [cellIndex]. Calculating in Double first avoids Float precision
     * loss even after the logical world has travelled millions of screens.
     */
    fun localCellX(worldPosition: Double, period: Double, cellIndex: Long): Float {
        if (!worldPosition.isFinite() || !period.isFinite() || period <= 0.0) return 0f
        return (cellIndex.toDouble() * period - worldPosition).toFloat()
    }

    fun variant(cellIndex: Long, count: Int): Int {
        if (count <= 1) return 0
        val remainder = cellIndex % count
        return if (remainder < 0L) (remainder + count).toInt() else remainder.toInt()
    }
}
