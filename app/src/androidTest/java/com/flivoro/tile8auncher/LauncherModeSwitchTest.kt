package com.flivoro.tile8auncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun selectingWp81InsideLauncherSettingsDoesNotCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousMode = LauncherFeatureStore.launcherUiMode(context)
        compose.mainClock.autoAdvance = false

        try {
            Thread.sleep(350)
            compose.runOnUiThread {
                LauncherFeatureStore.setLauncherUiMode(context, LauncherUiMode.DESKTOP)
            }
            compose.mainClock.advanceTimeBy(1500)

            // Open the desktop Charms bar, then the app's own PC settings page.
            compose.onRoot().performTouchInput {
                down(Offset(width - 4f, height * .5f))
                moveBy(Offset(-130f, 0f), 120)
                up()
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("Change PC settings").performClick()

            val interfaceRow = compose.onNodeWithText("Windows interface")
            interfaceRow.performScrollTo()
            interfaceRow.performClick()
            compose.onNodeWithText("Windows Phone 8.1", substring = true)
                .performClick()

            Thread.sleep(700)
            assertEquals(LauncherUiMode.PHONE_8, LauncherFeatureStore.launcherUiMode(context))
            compose.runOnUiThread {
                assertFalse("Launcher Activity finished after choosing WP8.1", compose.activity.isFinishing)
                assertTrue("Launcher window detached after choosing WP8.1",
                    compose.activity.window.decorView.isAttachedToWindow)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                LauncherFeatureStore.setLauncherUiMode(context, previousMode)
            }
        }
    }
}
