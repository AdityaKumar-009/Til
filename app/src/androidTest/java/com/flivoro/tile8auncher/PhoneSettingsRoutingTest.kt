package com.flivoro.tile8auncher

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.*
import com.flivoro.tile8auncher.features.*
import com.flivoro.tile8auncher.ui.components.WindowsAppView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneSettingsRoutingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun phoneSettingsShowsOneHomeAndModeScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.MOBILE_10)
        compose.setContent { MaterialTheme {
            WindowsAppView(TileModel(id = "tile_settings", title = "Mosaic Settings", tileType = TileType.SETTINGS),
                appsRepository = AppsRepository(context), onClose = {})
        } }
        compose.onNodeWithTag("til-settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("launcher-default-home-action").assertIsDisplayed()
        compose.onNodeWithTag("launcher-default-home-status").assertIsDisplayed()
        compose.captureFidelity("mobile-settings-root")
        compose.onNodeWithTag("launcher-style-action").performClick()
        compose.onNodeWithTag("launcher-mode-MOBILE_10").assertExists()
    }

    @Test fun essentialSettingsRemainAccessibleWithoutDuplicateTabs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.MOBILE_10)
        compose.setContent { MaterialTheme {
            WindowsAppView(TileModel(id = "tile_settings", title = "TIL Settings", tileType = TileType.SETTINGS),
                appsRepository = AppsRepository(context), onClose = {})
        } }

        compose.onNodeWithText("Personalization").performScrollTo().performClick()
        compose.onNodeWithText("App icon style").assertExists()
        compose.onNodeWithText("Tile density").assertExists()
        compose.onNodeWithTag("launcher-settings-back").performClick()

        compose.onNodeWithText("Start & motion").performScrollTo().performClick()
        compose.onNodeWithText("Live tiles & notifications").assertExists()
        compose.onNodeWithText("Animation style").assertExists()
        compose.onNodeWithTag("launcher-settings-back").performClick()

        compose.onNodeWithText("Apps & privacy").performScrollTo().performClick()
        compose.onNodeWithText("Hidden apps").assertExists()
        compose.onNodeWithText("Private apps").assertExists()
        compose.onNodeWithTag("launcher-settings-back").performClick()

        compose.onNodeWithText("Backup & reset").performScrollTo().performClick()
        compose.onNodeWithText("Back up launcher").assertExists()
        compose.onNodeWithText("Restore backup").assertExists()
    }
}
