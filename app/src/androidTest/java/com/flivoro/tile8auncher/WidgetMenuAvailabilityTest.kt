package com.flivoro.tile8auncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.*
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.phone.PhoneAppContextMenu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetMenuAvailabilityTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun phoneMenusOnlyOfferWidgetsForARealProviderPackage() {
        var mode by mutableStateOf(LauncherUiMode.PHONE_8)
        var pkg by mutableStateOf(instrumentation.context.packageName)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            PhoneAppContextMenu(mode, pkg, false, false, 100, 100,
                rowBoundsInWindow = Rect(0f, 100f, 1080f, 250f),
                viewportBoundsInWindow = Rect(0f, 0f, 1080f, 2160f),
                onPinChanged = {}, onAddWidgets = {}, onAppInfo = {}, onDismiss = {})
        } } }
        for (style in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
            compose.runOnIdle { mode = style; pkg = instrumentation.context.packageName }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("phone-app-command-add-widgets").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("phone-app-command-add-widgets").assertIsDisplayed()
            // The launcher has no widget providers. Switching packages must clear
            // the prior positive result immediately, not leave an empty picker action.
            compose.runOnIdle { pkg = instrumentation.targetContext.packageName }
            compose.onNodeWithTag("phone-app-command-add-widgets").assertDoesNotExist()
            compose.onNodeWithTag("phone-app-command-app-info").assertExists()
        }
    }

    @Test fun desktopMenuRemovesTheWidgetCommandWhenSwitchingToAnAppWithoutProviders() {
        val context = instrumentation.targetContext
        val fixture = AppInfo("Widget fixture", instrumentation.context.packageName, "Fixture")
        val plain = AppInfo("Plain launcher", context.packageName, "MainActivity")
        val repository = AppsRepository(context)
        compose.setContent { MaterialTheme {
            AllAppsScreen(listOf(AppSection("Apps", listOf(fixture, plain))), repository,
                onAppClick = { _, _ -> }, isAppPinned = { false }, onPinApp = {},
                onUnpinApp = {}, onOpenAppInfo = {}, onUninstallApp = {}, onNavigateToStart = {})
        } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(fixture.label).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(fixture.label).performMouseInput { click(button = MouseButton.Secondary) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add widgets").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText(plain.label).performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("App info").assertExists()
        compose.onNodeWithText("Add widgets").assertDoesNotExist()
    }
}
