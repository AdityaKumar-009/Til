package com.flivoro.tile8auncher.ui.animation

import org.junit.Assert.*
import org.junit.Test

class StartEntranceMotionTest {
    @Test fun columnsSettleExactlyWithoutOvershoot() {
        for (column in 0..3) {
            var previous = StartEntranceMotion.frame(0f, column)
            for (ms in 10..680 step 10) {
                val frame = StartEntranceMotion.frame(ms / 680f, column)
                assertTrue(frame.offsetFraction <= previous.offsetFraction)
                assertTrue(frame.scale >= previous.scale && frame.scale <= 1f)
                assertTrue(frame.alpha >= previous.alpha && frame.alpha <= 1f)
                previous = frame
            }
            assertEquals(EntranceFrame(0f, 1f, 1f), previous)
        }
    }
}
