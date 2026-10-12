package com.flivoro.tile8auncher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherModeSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchingActiveLauncherToWp81DoesNotCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        compose.mainClock.autoAdvance = false

        try {
            Thread.sleep(350)
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
            }
            compose.mainClock.advanceTimeBy(1500)

            // This is the same store callback invoked by the Windows interface choice row. Keeping
            // the Activity in place covers the live root-surface replacement from the settings path.
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
            }
            compose.mainClock.advanceTimeBy(1500)

            Thread.sleep(700)
            assertEquals(LauncherUiMode.PHONE_8, LauncherFeatureStore.launcherUiMode(context))
            compose.runOnUiThread {
                assertFalse("Launcher Activity finished after choosing WP8.1", compose.activity.isFinishing)
                assertTrue(
                    "Launcher window detached after choosing WP8.1",
                    compose.activity.window.decorView.isAttachedToWindow,
                )
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
            }
        }
    }
    @Test
    fun openingSettingsInBothPhoneModesKeepsLauncherAlive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        try {
            for (mode in listOf(LauncherUiMode.PHONE_8, LauncherUiMode.MOBILE_10)) {
                compose.runOnUiThread {
                    LauncherFeatureStore.setLauncherUiMode(context, mode)
                }
                compose.waitUntil(timeoutMillis = 20_000) {
                    compose.onAllNodesWithTag("phone-bottom-search")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("phone-bottom-search").performClick()
                if (mode == LauncherUiMode.PHONE_8) {
                    compose.onNodeWithTag("phone-app-search-toggle").performClick()
                }
                compose.onNodeWithTag("phone-launcher-settings").performClick()
                compose.onNodeWithText("Open Android System Settings").assertExists()
                compose.onNodeWithText("✕").performClick()
                compose.waitForIdle()
                assertEquals(mode, LauncherFeatureStore.launcherUiMode(context))
                assertFalse(compose.activity.isFinishing)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }

    @Test
    fun interruptedPhoneRenderFallsBackWithoutLosingSavedModeSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        val previousColumns = LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8)
        try {
            compose.runOnUiThread {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, 6)
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.PHONE_8)
            }
            assertTrue(LauncherFeatureStore.recoverInterruptedPhoneRender(context))
            assertEquals(LauncherUiMode.DESKTOP, LauncherFeatureStore.launcherUiMode(context))
            assertEquals(6, LauncherFeatureStore.phoneSmallColumns(context, LauncherUiMode.PHONE_8))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setPhoneSmallColumns(context, LauncherUiMode.PHONE_8, previousColumns)
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
                LauncherFeatureStore.confirmPhoneRender(context)
                LauncherFeatureStore.confirmPhoneSettings(context)
            }
        }
    }
}
