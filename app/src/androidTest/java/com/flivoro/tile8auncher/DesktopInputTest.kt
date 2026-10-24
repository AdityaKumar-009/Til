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

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DesktopInputTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rightClickOpensAppCommandsWithoutLaunching() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val repository = com.flivoro.tile8auncher.data.AppsRepository(context)
        val app = com.flivoro.tile8auncher.data.AppInfo(
            "Alpha", "com.example.alpha", "com.example.alpha.Main")
        var launches = 0
        compose.setContent {
            androidx.compose.material3.MaterialTheme {
                com.flivoro.tile8auncher.ui.apps.AllAppsScreen(
                    sections = listOf(com.flivoro.tile8auncher.data.AppSection("A", listOf(app))),
                    appsRepository = repository, onAppClick = { _, _ -> launches++ },
                    isAppPinned = { false }, onPinApp = {}, onUnpinApp = {},
                    onOpenAppInfo = {}, onUninstallApp = {}, onNavigateToStart = {})
            }
        }
        compose.onNodeWithText("Alpha").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("Pin to Start").assertIsDisplayed()
        compose.runOnIdle { assertEquals("A context-menu gesture must not launch the app", 0, launches) }
    }

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
