package com.flivoro.tile8auncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StartBackgroundSceneStateTest {
    @Test
    fun startAndAppsDeltasAdvanceOneSharedWorldWithoutReset() {
        val state = StartBackgroundSceneState()

        state.onHorizontalScroll(120f) // Start
        state.onHorizontalScroll(75f)  // All Apps
        state.onHorizontalScroll(-20f) // Back on Start

        assertEquals(175.0, state.worldX, 0.0001)
        assertEquals(3, state.interactionSerial)
        assertEquals(-20f, state.lastScrollDeltaPx, 0f)
    }

    @Test
    fun disabledInputCannotMoveTheBackground() {
        val state = StartBackgroundSceneState()
        state.onHorizontalScroll(80f)
        state.inputEnabled = false
        state.onHorizontalScroll(500f)

        assertEquals(80.0, state.worldX, 0.0001)
        assertEquals(1, state.interactionSerial)
    }

    @Test
    fun runtimeDetachesPreviewOrDisposedScenesSafely() {
        val state = StartBackgroundSceneState()
        StartBackgroundScrollRuntime.attach(state)
        StartBackgroundScrollRuntime.onListConsumedScroll(-50f)
        StartBackgroundScrollRuntime.detach(state)
        StartBackgroundScrollRuntime.onListConsumedScroll(-50f)

        assertEquals(50.0, state.worldX, 0.0001)
    }
}
