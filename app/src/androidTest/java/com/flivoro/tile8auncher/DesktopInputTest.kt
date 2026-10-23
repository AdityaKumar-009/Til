package com.flivoro.tile8auncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flivoro.tile8auncher.ui.start.desktopStartInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopInputTest {
    @get:Rule val compose = createComposeRule()
    @Test fun keyboardAndPinchReachSemanticZoomAndSearch() {
        val requests = mutableListOf<Boolean>()
        val searches = mutableListOf<String>()
        compose.setContent {
            Box(Modifier.fillMaxSize().desktopStartInput(true, { requests += it }, { true },
                { searches += it }, {}, {}))
        }
        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Minus); keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle { assertEquals(listOf(false), requests) }
        compose.onRoot().performKeyInput { pressKey(Key.A) }
        compose.runOnIdle { assertEquals(listOf("a"), searches) }
        compose.onRoot().performTouchInput {
            down(0, Offset(width * .2f, height * .5f))
            down(1, Offset(width * .8f, height * .5f))
            moveTo(0, Offset(width * .4f, height * .5f), 100)
            moveTo(1, Offset(width * .6f, height * .5f), 100)
            up(0); up(1)
        }
        compose.runOnIdle { assertEquals(listOf(false, false), requests) }
    }
}
