package com.flivoro.tile8auncher

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
}
