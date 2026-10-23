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
    @Test fun phoneSettingsKeepsLauncherCustomizationAccessible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.MOBILE_10)
        compose.setContent { MaterialTheme {
            WindowsAppView(TileModel(id = "tile_settings", title = "Mosaic Settings", tileType = TileType.SETTINGS),
                appsRepository = AppsRepository(context), onClose = {})
        } }
        compose.onNodeWithTag("phone-native-settings").assertIsDisplayed()
        compose.captureFidelity("mobile-settings-root")
        compose.onNodeWithTag("phone-native-advanced-settings").performScrollTo().performClick()
        compose.onNodeWithTag("til-advanced-settings-content").assertIsDisplayed()
    }
}
