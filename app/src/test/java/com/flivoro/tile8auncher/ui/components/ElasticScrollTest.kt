package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.*
import org.junit.Test

class ElasticScrollTest {
    @Test fun resistanceIsSymmetricBoundedAndEventRateIndependent() {
        val single = elasticOffset(0f, 500f, 100f, .52f)
        var split = 0f
        repeat(100) { split = elasticOffset(split, 5f, 100f, .52f) }
        assertEquals(single, split, .001f)
        assertEquals(-single, elasticOffset(0f, -500f, 100f, .52f), .001f)
        assertTrue(single in 0f..100f)
        assertTrue(elasticOffset(single, 100f, 100f, .52f) > single)
        assertEquals(100f, elasticOffset(0f, 100000f, 100f, .52f), .001f)
    }
}
